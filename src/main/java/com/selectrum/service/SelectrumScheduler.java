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

import javax.swing.UIManager;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks the current time against the schedule from
 * {@link SelectrumConfigService} and switches the IDE theme when needed.
 * <p>
 * Uses a 1-minute polling interval via {@link AppExecutorUtil}.
 * Theme switching is always dispatched to the EDT.
 */
@Service(Service.Level.APP)
@SuppressWarnings("deprecation") // LafManager.getInstalledLookAndFeels() returns deprecated LookAndFeelInfo type
public final class SelectrumScheduler implements Disposable {

    private static final Logger LOG = Logger.getInstance(SelectrumScheduler.class);
    private static final long POLL_INTERVAL_SECONDS = 60;

    private ScheduledFuture<?> scheduledTask;
    private volatile String lastAppliedTheme;
    private volatile String lastAppliedEditor;

    public static SelectrumScheduler getInstance() {
        return ApplicationManager.getApplication().getService(SelectrumScheduler.class);
    }

    /**
     * Starts the polling loop. Safe to call multiple times — subsequent calls are no-ops.
     */
    public void start() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            LOG.info("Selectrum scheduler already running");
            return;
        }

        LOG.info("Starting Selectrum scheduler (poll interval: " + POLL_INTERVAL_SECONDS + "s)");

        // Apply the correct theme immediately on startup
        checkAndApplyTheme();

        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(
                        this::checkAndApplyThemeSafely,
                        POLL_INTERVAL_SECONDS,
                        POLL_INTERVAL_SECONDS,
                        TimeUnit.SECONDS
                );
    }

    /**
     * Evaluates the schedule against the current time and switches the theme if needed.
     * Can be called from any thread — theme switching is dispatched to the EDT.
     */
    public void checkAndApplyTheme() {
        if (!SelectrumSettings.getInstance().isEnabled()) {
            return;
        }

        List<ScheduleEntry> schedule = SelectrumConfigService.getInstance().getSchedule();
        if (schedule.isEmpty()) {
            return;
        }

        LocalTime now = LocalTime.now();
        ScheduleEntry activeEntry = findActiveEntry(schedule, now);
        if (activeEntry == null) {
            return;
        }

        String targetTheme = activeEntry.getTheme();
        String targetEditor = activeEntry.getEffectiveEditor();

        boolean themeChanged = !targetTheme.equals(lastAppliedTheme);
        boolean editorChanged = !targetEditor.equals(lastAppliedEditor);

        if (!themeChanged && !editorChanged) {
            return;
        }

        applyThemeAndEditor(activeEntry, themeChanged, editorChanged);
    }

    // -------------------------------------------------------------------
    // Schedule evaluation
    // -------------------------------------------------------------------

    /**
     * Finds the schedule entry that should be active at the given time.
     * <p>
     * Entries are sorted by time. The last entry whose time is {@code <=} the current time wins.
     * If the current time is before all entries (e.g. 2:00 AM with first entry at 8:00),
     * the last entry in the schedule is used (midnight rollover).
     */
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

        // Midnight rollover: current time is before the first entry,
        // so the last entry from "yesterday" is still active.
        if (active == null) {
            active = sorted.getLast();
        }

        return active;
    }

    // -------------------------------------------------------------------
    // Theme and editor color switching
    // -------------------------------------------------------------------

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
            LOG.info("Selectrum: switching theme to '" + themeName + "'");
            lafManager.setCurrentLookAndFeel(targetLaf, true);
            lastAppliedTheme = themeName;
        } else {
            LOG.warn("Selectrum: theme '" + themeName + "' not found among installed themes");
            notifyThemeNotFound(themeName);
        }
    }

    /**
     * Applies the editor color scheme for the given entry.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>If {@code entry.getEditor()} is set → use that scheme name</li>
     *   <li>Otherwise → use the theme name as the scheme name</li>
     *   <li>If no matching scheme is found → keep the current editor color scheme</li>
     * </ol>
     */
    private void applyEditorColorScheme(ScheduleEntry entry) {
        String effectiveEditor = entry.getEffectiveEditor();
        EditorColorsScheme targetScheme = findEditorSchemeByName(effectiveEditor);

        if (targetScheme != null) {
            LOG.info("Selectrum: switching editor color scheme to '" + effectiveEditor + "'");
            EditorColorsManager.getInstance().setGlobalScheme(targetScheme);
            lastAppliedEditor = effectiveEditor;
        } else if (entry.getEditor() != null) {
            // Only warn if the user explicitly specified an editor that doesn't exist.
            // When falling back to theme name, silently keep the current scheme.
            LOG.warn("Selectrum: editor scheme '" + effectiveEditor + "' not found");
            notifyEditorNotFound(effectiveEditor, EditorColorsManager.getInstance().getAllSchemes());
        } else {
            LOG.info("Selectrum: no editor scheme matching theme '" + effectiveEditor
                    + "', keeping current editor color scheme");
            lastAppliedEditor = effectiveEditor;
        }
    }

    private UIThemeLookAndFeelInfo findThemeByName(LafManager lafManager, String name) {
        for (UIManager.LookAndFeelInfo laf : lafManager.getInstalledLookAndFeels()) {
            if (laf.getName().equals(name) && laf instanceof UIThemeLookAndFeelInfo themeLaf) {
                return themeLaf;
            }
        }
        return null;
    }

    /**
     * Finds an editor color scheme by name.
     * Matches against both the internal name ({@code getName()}) and the display name
     * ({@code getDisplayName()}) since what the user sees in Settings → Editor → Color Scheme
     * is the display name, which can differ from the internal name.
     */
    private EditorColorsScheme findEditorSchemeByName(String name) {
        EditorColorsScheme[] allSchemes = EditorColorsManager.getInstance().getAllSchemes();

        // First pass: exact match on internal name
        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equals(name)) {
                return scheme;
            }
        }

        // Second pass: exact match on display name (what the user sees in Settings UI)
        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getDisplayName() != null && scheme.getDisplayName().equals(name)) {
                return scheme;
            }
        }

        // Third pass: case-insensitive match on either name
        for (EditorColorsScheme scheme : allSchemes) {
            if (scheme.getName().equalsIgnoreCase(name)
                    || (scheme.getDisplayName() != null && scheme.getDisplayName().equalsIgnoreCase(name))) {
                return scheme;
            }
        }

        // Log available schemes to help the user find the correct name
        StringBuilder available = new StringBuilder("Available editor color schemes: ");
        for (int i = 0; i < allSchemes.length; i++) {
            if (i > 0) available.append(", ");
            available.append("'").append(allSchemes[i].getDisplayName()).append("'");
            if (!allSchemes[i].getDisplayName().equals(allSchemes[i].getName())) {
                available.append(" (internal: '").append(allSchemes[i].getName()).append("')");
            }
        }
        LOG.info(available.toString());

        return null;
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    /** Wraps {@link #checkAndApplyTheme()} with exception safety for the scheduled executor. */
    private void checkAndApplyThemeSafely() {
        try {
            checkAndApplyTheme();
        } catch (Exception e) {
            LOG.error("Error during Selectrum theme check", e);
        }
    }

    private void notifyThemeNotFound(String themeName) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification(
                        "Selectrum",
                        "Theme '" + themeName + "' is not installed. "
                                + "Check your selectrum.json configuration.",
                        NotificationType.WARNING)
                .notify(null);
    }

    private void notifyEditorNotFound(String editorName, EditorColorsScheme[] allSchemes) {
        StringBuilder message = new StringBuilder();
        message.append("Editor color scheme '").append(editorName).append("' not found.\n");
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
    public void dispose() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        LOG.info("Selectrum scheduler stopped");
    }
}
