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
import org.jetbrains.annotations.NotNull;

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

    /**
     * The task scheduled for the next theme update.
     */
    private ScheduledFuture<?> scheduledTask;

    /**
     * The name of the last applied IDE theme.
     */
    private volatile String lastAppliedTheme;

    /**
     * The name of the last applied editor color scheme.
     */
    private volatile String lastAppliedEditor;

    /**
     * Retrieves the singleton instance of the {@code SelectrumScheduler}.
     *
     * @return the instance of {@code SelectrumScheduler}.
     */
    public static @NotNull SelectrumScheduler instance() {
        return ApplicationManager.getApplication().getService(SelectrumScheduler.class);
    }

    /**
     * Disposes of the scheduler, canceling any pending scheduled tasks.
     */
    @Override
    public synchronized void dispose() {
        if (scheduledTask == null) {
            LOG.info("Selectrum scheduler stopped");
            return;
        }

        scheduledTask.cancel(false);
        scheduledTask = null;
    }

    /**
     * Starts the scheduler by immediately checking the current time against the schedule
     * and applying the necessary theme.
     */
    public void start() {
        checkAndApplyTheme();
    }

    /**
     * Checks the current schedule and applies the appropriate theme based on the time.
     * Also schedules the next evaluation.
     */
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

    /**
     * Applies theme and editor changes if they differ from the currently applied ones.
     *
     * @param activeEntry the active schedule entry containing the target theme and editor.
     */
    private void applyThemeChanges(@NotNull ScheduleEntry activeEntry) {
        String targetTheme = activeEntry.getTheme();
        String targetEditor = activeEntry.getEffectiveEditor();

        boolean themeChanged = !targetTheme.equals(lastAppliedTheme);
        boolean editorChanged = !targetEditor.equals(lastAppliedEditor);

        if (!themeChanged && !editorChanged) {
            return;
        }

        applyThemeAndEditor(activeEntry, themeChanged, editorChanged);
    }

    /**
     * Schedules the next run of the scheduler to occur when the next schedule entry becomes active.
     *
     * @param schedule the list of configured schedule entries.
     * @param now      the current local time.
     */
    private void scheduleNextRun(@NotNull List<ScheduleEntry> schedule, @NotNull LocalTime now) {
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

    /**
     * Invokes the theme and editor updates on the EDT (Event Dispatch Thread).
     *
     * @param entry         the active schedule entry.
     * @param themeChanged  whether the IDE theme has changed.
     * @param editorChanged whether the editor color scheme has changed.
     */
    private void applyThemeAndEditor(@NotNull ScheduleEntry entry, boolean themeChanged, boolean editorChanged) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (themeChanged) {
                applyLafTheme(entry.getTheme());
            }

            if (editorChanged || themeChanged) {
                applyEditorColorScheme(entry);
            }
        });
    }

    /**
     * Applies the Look and Feel (LaF) theme by its name.
     *
     * @param themeName the name of the theme to apply.
     */
    private void applyLafTheme(@NotNull String themeName) {
        LafManager lafManager = LafManager.getInstance();

        UIThemeLookAndFeelInfo targetLaf =
                ThemeFinder.instance().findThemeByName(lafManager, themeName);

        if (targetLaf != null) {
            lafManager.setCurrentUIThemeLookAndFeel(targetLaf);
            lastAppliedTheme = themeName;

            return;
        }

        LOG.warn("Selectrum: theme '" + themeName + "' not found.");
    }

    /**
     * Applies the editor color scheme specified in the schedule entry.
     *
     * @param entry the schedule entry containing the target editor color scheme.
     */
    private void applyEditorColorScheme(@NotNull ScheduleEntry entry) {
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
            LOG.warn("Selectrum: editor scheme '" + effectiveEditor + "' not found.");
        }

        lastAppliedEditor = effectiveEditor;
    }

    /**
     * Safely executes the theme check and application, catching and logging any exceptions.
     */
    private void checkAndApplyThemeSafely() {
        try {
            checkAndApplyTheme();
        } catch (Exception e) {
            LOG.error("Error during Selectrum theme check", e);
        }
    }
}