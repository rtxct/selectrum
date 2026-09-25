package com.selectrum.application.service;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.selectrum.application.component.ThemeFinder;
import com.selectrum.domain.model.ScheduleEntry;
import com.selectrum.infrastructure.settings.SelectrumSettings;
import com.selectrum.utils.NotificationUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
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

    public static SelectrumScheduler instance() {
        return ApplicationManager.getApplication().getService(SelectrumScheduler.class);
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

    public void start() {
        checkAndApplyTheme();
    }

    public synchronized void checkAndApplyTheme() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        if (!SelectrumSettings.instance().isEnabled()) {
            return;
        }

        List<ScheduleEntry> schedule = SelectrumConfigService.instance().getSchedule();
        if (schedule.isEmpty()) {
            return;
        }

        LocalTime now = LocalTime.now();

        ScheduleEntry activeEntry = ThemeFinder
                .instance().findActiveEntry(schedule, now);

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

        UIThemeLookAndFeelInfo targetLaf =
                ThemeFinder.instance().findThemeByName(lafManager, themeName);

        if (targetLaf != null) {
            lafManager.setCurrentUIThemeLookAndFeel(targetLaf);
            lastAppliedTheme = themeName;

            return;
        }

        NotificationUtils.notifyWarning("Theme '" + themeName
                + "' not found. Check your selectrum.yaml configuration.");
    }

    private void applyEditorColorScheme(ScheduleEntry entry) {
        String effectiveEditor =
                entry.getEffectiveEditor();

        EditorColorsScheme targetScheme =
                ThemeFinder.instance().findEditorSchemeByName(effectiveEditor);

        if (targetScheme != null) {
            EditorColorsManager.getInstance().setGlobalScheme(targetScheme);
            lastAppliedEditor = effectiveEditor;

            return;
        }

        if (entry.getEditor() != null) {
            NotificationUtils.notifyWarning("Editor scheme '"
                    + effectiveEditor + "' not found. Check your selectrum.yaml configuration.");
            return;
        }

        lastAppliedEditor = effectiveEditor;
    }

    private void checkAndApplyThemeSafely() {
        try {
            checkAndApplyTheme();
        } catch (Exception e) {
            LOG.error("Error during Selectrum theme check", e);
        }
    }
}