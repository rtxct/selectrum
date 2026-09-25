package com.selectrum.service;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.selectrum.model.ScheduleEntry;
import com.selectrum.settings.SelectrumSettings;
import com.selectrum.utils.NotificationUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Checks the current time against the schedule from {@link SelectrumConfigService} and
 * switches the IDE theme when needed.
 * <p>
 * Schedules the next execution precisely at the next required change. Theme switching is
 * always dispatched to the EDT.
 */
@Service(Service.Level.APP)
public final class SelectrumScheduler implements Disposable {

    private static final Logger LOG = Logger.getInstance(SelectrumScheduler.class);

    private ScheduledFuture<?> scheduledTask;

    private volatile String lastAppliedTheme;
    private volatile String lastAppliedEditor;

    public static SelectrumScheduler getInstance() {
        return ApplicationManager.getApplication().getService(SelectrumScheduler.class);
    }

    public void start() {
        checkAndApplyTheme();
    }

    public synchronized void checkAndApplyTheme() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        if (!SelectrumSettings.getInstance().isEnabled()) {
            return;
        }

        List<ScheduleEntry> schedule = SelectrumConfigService.getInstance().getSchedule();
        if (schedule.isEmpty()) {
            return;
        }

        LocalTime now = LocalTime.now();
        ScheduleEntry activeEntry = findActiveEntry(schedule, now);

        if (activeEntry != null) {
            applyThemeChanges(activeEntry);
        }

        scheduleNextRun(schedule, now);
    }

    private void applyThemeChanges(ScheduleEntry activeEntry) {
        String targetTheme = activeEntry.getTheme();
        String targetEditor = activeEntry.getEffectiveEditor();

        boolean themeChanged = !targetTheme.equals(lastAppliedTheme);
        boolean editorChanged = !targetEditor.equals(lastAppliedEditor);

        if (!themeChanged && !editorChanged) {
            return;
        }

        applyThemeAndEditor(activeEntry, themeChanged, editorChanged);
    }

    private void scheduleNextRun(List<ScheduleEntry> schedule, LocalTime now) {
        List<ScheduleEntry> sorted = schedule.stream()
                .sorted(Comparator.comparing(ScheduleEntry::getParsedTime))
                .toList();

        ScheduleEntry nextEntry = null;
        for (ScheduleEntry entry : sorted) {
            if (entry.getParsedTime().isAfter(now)) {
                nextEntry = entry;
                break;
            }
        }

        boolean nextDay = false;
        if (nextEntry == null) {
            nextEntry = sorted.getFirst();
            nextDay = true;
        }

        LocalDateTime nextDateTime =
                LocalDateTime.of(LocalDate.now(), nextEntry.getParsedTime());

        if (nextDay) {
            nextDateTime = nextDateTime.plusDays(1);
        }

        long delayMs = Duration.between(LocalDateTime.now(), nextDateTime).toMillis();
        long finalDelayMs = Math.max(1000, delayMs + 500);

        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(
                        this::checkAndApplyThemeSafely,
                        finalDelayMs,
                        TimeUnit.MILLISECONDS
                );
    }

    private ScheduleEntry findActiveEntry(List<ScheduleEntry> entries, LocalTime now) {
        List<ScheduleEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(ScheduleEntry::getParsedTime))
                .toList();

        ScheduleEntry active = null;
        for (ScheduleEntry entry : sorted) {
            if (!entry.getParsedTime().isAfter(now)) {
                active = entry;
            }
        }

        if (active == null) {
            active = sorted.getLast();
        }

        return active;
    }

    private void applyThemeAndEditor(ScheduleEntry entry, boolean themeChanged, boolean editorChanged) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (themeChanged) {
                applyLafTheme(entry.getTheme());
            }

            if (editorChanged || themeChanged) {
                applyEditorColorScheme(entry);
            }
        });
    }

    private void applyLafTheme(String themeName) {
        LafManager lafManager = LafManager.getInstance();
        UIThemeLookAndFeelInfo targetLaf = findThemeByName(lafManager, themeName);

        if (targetLaf != null) {
            lafManager.setCurrentUIThemeLookAndFeel(targetLaf);
            lastAppliedTheme = themeName;

            return;
        }

        NotificationUtils.notifyWarning("Theme '" + themeName
                + "' is not installed. Check your selectrum.yaml configuration.");
    }

    private void applyEditorColorScheme(ScheduleEntry entry) {
        String effectiveEditor =
                entry.getEffectiveEditor();

        EditorColorsScheme targetScheme =
                findEditorSchemeByName(effectiveEditor);

        if (targetScheme != null) {
            EditorColorsManager.getInstance().setGlobalScheme(targetScheme);
            lastAppliedEditor = effectiveEditor;

            return;
        }

        if (entry.getEditor() != null) {
            notifyEditorNotFound(effectiveEditor, EditorColorsManager.getInstance().getAllSchemes());
            return;
        }

        lastAppliedEditor = effectiveEditor;
    }

    private UIThemeLookAndFeelInfo findThemeByName(LafManager lafManager, String name) {
        Iterator<UIThemeLookAndFeelInfo> themes =
                lafManager.getInstalledThemes().iterator();

        while (themes.hasNext()) {
            UIThemeLookAndFeelInfo theme = themes.next();

            if (theme.getName().equals(name)) {
                return theme;
            }
        }

        return null;
    }

    private EditorColorsScheme findEditorSchemeByName(String name) {
        EditorColorsScheme[] allSchemes =
                EditorColorsManager.getInstance().getAllSchemes();

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equals(name)) return scheme;
        }

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getDisplayName().equals(name)) {
                return scheme;
            }
        }

        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equalsIgnoreCase(name)
                    || scheme.getDisplayName().equalsIgnoreCase(name)) {
                return scheme;
            }
        }

        return null;
    }

    private void checkAndApplyThemeSafely() {
        try {
            checkAndApplyTheme();
        } catch (Exception e) {
            LOG.error("Error during Selectrum theme check", e);
        }
    }

    private void notifyEditorNotFound(String editorName, EditorColorsScheme[] allSchemes) {
        StringBuilder message = new StringBuilder();

        message.append("Editor color scheme '")
                .append(editorName)
                .append("' not found.\n");

        message.append("Available schemes: ");

        for (int i = 0; i < allSchemes.length; i++) {
            if (i > 0) message.append(", ");
            message.append(allSchemes[i].getDisplayName());
        }

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification(
                        "Selectrum",
                        message.toString(),
                        NotificationType.WARNING)
                .notify(null);
    }

    @Override
    public synchronized void dispose() {
        if (scheduledTask == null) {
            LOG.info("Selectrum scheduler stopped");
            return;
        }

        scheduledTask.cancel(false);
        scheduledTask = null;
    }
}