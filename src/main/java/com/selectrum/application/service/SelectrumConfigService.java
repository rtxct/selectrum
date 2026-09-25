package com.selectrum.application.service;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.selectrum.application.component.ThemeFinder;
import com.selectrum.domain.model.ScheduleEntry;
import com.selectrum.utils.NotificationUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Reads, parses, and watches the {@code selectrum.yaml} configuration file.
 * This service is responsible for loading the theme schedule configuration,
 * providing the current schedule, and listening for file system changes to
 * automatically reload the configuration.
 */
@Getter
@Service(Service.Level.APP)
public final class SelectrumConfigService implements Disposable {

    private static final Logger LOG = Logger.getInstance(SelectrumConfigService.class);

    /**
     * The name of the configuration file.
     */
    private static final String CONFIG_FILE_NAME = "selectrum.yaml";

    /**
     * The absolute path to the configuration file.
     */
    private final Path configFilePath;

    /**
     * The current schedule parsed from the configuration file.
     */
    private volatile List<ScheduleEntry> schedule;

    /**
     * Constructs a new {@code SelectrumConfigService}.
     * Initializes the configuration file path, creates the default file if it does not exist,
     * loads the initial configuration synchronously, and subscribes to future file changes.
     */
    public SelectrumConfigService() {
        this.configFilePath = Path.of(PathManager.getConfigPath(), CONFIG_FILE_NAME);
        this.schedule = Collections.emptyList();

        ensureConfigFileExists();
        loadConfigSync();
        subscribeToFileChanges();
    }

    /**
     * Retrieves the singleton instance of the {@code SelectrumConfigService}.
     *
     * @return the instance of {@code SelectrumConfigService}.
     */
    public static @NotNull SelectrumConfigService instance() {
        return ApplicationManager.getApplication().getService(SelectrumConfigService.class);
    }

    /**
     * Disposes of the service resources. This method is called when the service is torn down.
     */
    @Override
    public void dispose() {
    }

    /**
     * Subscribes to virtual file system changes to watch for updates to the configuration file.
     */
    private void subscribeToFileChanges() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        for (VFileEvent event : events) {
                            if (isConfigFileEvent(event)) {
                                loadConfigAsync();
                                return;
                            }
                        }
                    }
                });
    }

    /**
     * Checks whether the given virtual file event corresponds to the configuration file.
     *
     * @param event the file system event.
     * @return {@code true} if the event relates to the config file, {@code false} otherwise.
     */
    private boolean isConfigFileEvent(@NotNull VFileEvent event) {
        return FileUtil.pathsEqual(event.getPath(), configFilePath.toString());
    }

    /**
     * Ensures that the configuration file exists. If it does not exist,
     * creates a default {@code selectrum.yaml} file in the appropriate directory.
     */
    private void ensureConfigFileExists() {
        if (Files.exists(configFilePath)) {
            return;
        }

        try {
            Files.createDirectories(configFilePath.getParent());
            String defaultContent = """
                    # Selectrum — Theme Schedule Configuration
                    #
                    # Each key under "schedule" is a time in 24h format (H:mm or HH:mm).
                    # The last entry whose hour has passed determines the active theme.
                    #
                    # Fields:
                    #   theme  (required) — Name of an installed IDE theme
                    #   editor (optional) — Name of an editor color scheme.
                    #                       If omitted, uses the scheme matching the theme name.
                    #                       If no match is found, keeps the current editor color.
                    #
                    # Available themes:  Settings → Appearance & Behavior → Appearance → Theme
                    # Available editors: Settings → Editor → Color Scheme
                    
                    schedule:
                      08:00:
                        theme: Light
                      18:00:
                        theme: Light
                        editor: Darcula
                    """;

            Files.writeString(configFilePath, defaultContent);

            LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(configFilePath.toString());
        } catch (IOException e) {
            LOG.error("Failed to create default Selectrum config file", e);
        }
    }

    /**
     * Loads the configuration file synchronously. If successful, updates the schedule.
     * Typically used during the initial startup sequence.
     */
    private void loadConfigSync() {
        VirtualFile virtualFile = LocalFileSystem
                .getInstance().refreshAndFindFileByPath(configFilePath.toString());

        if (virtualFile == null || !virtualFile.exists()) {
            schedule = Collections.emptyList();
            return;
        }

        try {
            String content = VfsUtilCore.loadText(virtualFile);
            List<ScheduleEntry> entries = parseSchedule(content);

            schedule = Collections.unmodifiableList(entries);
            validateSchedule(schedule);
        } catch (IOException e) {
            LOG.error("Failed to read Selectrum config file", e);

            NotificationUtils.notifyError(
                    "Failed to read configuration file: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("Failed to parse Selectrum config", e);

            NotificationUtils.notifyError(
                    "Invalid YAML in selectrum.yaml: " + e.getMessage());
        }
    }

    /**
     * Loads the configuration file asynchronously. If successful, updates the schedule
     * and triggers a scheduler recheck.
     */
    private void loadConfigAsync() {
        VirtualFile virtualFile = LocalFileSystem
                .getInstance().findFileByPath(configFilePath.toString());

        if (virtualFile == null || !virtualFile.exists()) {
            schedule = Collections.emptyList();
            triggerSchedulerRecheck();

            return;
        }

        ReadAction.nonBlocking(() -> {
                    try {
                        return VfsUtilCore.loadText(virtualFile);
                    } catch (IOException e) {
                        LOG.error("Failed to read Selectrum config file via VFS", e);
                        return null;
                    }
                }).submit(
                        AppExecutorUtil.getAppExecutorService())
                .onSuccess(content -> {
                    if (content == null) {
                        return;
                    }

                    try {
                        List<ScheduleEntry> entries = parseSchedule(content);
                        schedule = Collections.unmodifiableList(entries);
                        validateSchedule(schedule);

                        triggerSchedulerRecheck();
                    } catch (Exception e) {
                        LOG.warn("Failed to parse Selectrum config", e);

                        NotificationUtils.notifyError(
                                "Invalid YAML in selectrum.yaml: " + e.getMessage());
                    }
                });
    }

    /**
     * Parses the YAML content into a list of schedule entries.
     *
     * @param yamlContent the YAML content of the configuration file as a string.
     * @return a list of parsed {@link ScheduleEntry} instances.
     * @throws IllegalArgumentException if the YAML format is invalid.
     */
    private @NotNull List<ScheduleEntry> parseSchedule(@NotNull String yamlContent) {
        Yaml yaml = new Yaml();

        Map<String, Object> root = yaml.load(yamlContent);
        if (root == null || !root.containsKey("schedule")) {
            return Collections.emptyList();
        }

        Object scheduleObj = root.get("schedule");
        if (!(scheduleObj instanceof Map<?, ?> scheduleMap)) {
            throw new IllegalArgumentException(
                    "'schedule' must be a mapping of hours to theme configs");
        }

        List<ScheduleEntry> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : scheduleMap.entrySet()) {
            String hour = resolveHourKey(entry.getKey());

            if (!(entry.getValue() instanceof Map<?, ?> config)) {
                throw new IllegalArgumentException(
                        "Value for hour '" + hour + "' must be a mapping with at least a 'theme' key");
            }

            String theme = getRequiredString(config, "theme", hour);
            String editor = getOptionalString(config, "editor");

            entries.add(new ScheduleEntry(hour, theme, editor));
        }

        return entries;
    }

    /**
     * Resolves the key from the YAML map into a valid hour string.
     *
     * @param key the key object from the YAML map.
     * @return a formatted hour string (e.g., "08:00").
     * @throws IllegalArgumentException if the key cannot be resolved to a valid hour.
     */
    private @NotNull String resolveHourKey(@NotNull Object key) {
        if (key instanceof String s) {
            return s;
        }

        if (key instanceof Integer i) {
            int hours = i / 60;
            int minutes = i % 60;

            return String.format(
                    "%d:%02d", hours, minutes);
        }

        throw new IllegalArgumentException(
                "Invalid hour key: '" + key + "'. Hours must be quoted strings.");
    }

    /**
     * Triggers a recheck of the theme schedule by invoking the scheduler.
     */
    private void triggerSchedulerRecheck() {
        SelectrumScheduler scheduler = ApplicationManager
                .getApplication().getServiceIfCreated(SelectrumScheduler.class);

        if (scheduler != null) {
            scheduler.checkAndApplyTheme();
        }
    }

    /**
     * Retrieves a required string value from a configuration map.
     *
     * @param map         the configuration map.
     * @param field       the key of the field to retrieve.
     * @param hourContext the hour string for context in error messages.
     * @return the string value of the field.
     * @throws IllegalArgumentException if the field is missing.
     */
    @SuppressWarnings("SameParameterValue")
    private @NotNull String getRequiredString(@NotNull Map<?, ?> map, @NotNull String field, @NotNull String hourContext) {
        Object value = map.get(field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required field '" + field + "' for hour '" + hourContext + "'");
        }
        return value.toString();
    }

    /**
     * Retrieves an optional string value from a configuration map.
     *
     * @param map   the configuration map.
     * @param field the key of the field to retrieve.
     * @return the string value of the field, or {@code null} if the field is missing.
     */
    @SuppressWarnings("SameParameterValue")
    private @Nullable String getOptionalString(@NotNull Map<?, ?> map, @NotNull String field) {
        Object value = map.get(field);
        return value != null ? value.toString() : null;
    }

    /**
     * Validates the parsed schedule entries against the currently installed IDE themes
     * and editor schemes, issuing warnings if they are not found.
     *
     * @param entries the list of schedule entries to validate.
     */
    private void validateSchedule(@NotNull List<ScheduleEntry> entries) {
        LafManager lafManager = LafManager.getInstance();
        ThemeFinder finder = ThemeFinder.instance();

        for (ScheduleEntry entry : entries) {
            String themeName = entry.getTheme();
            if (finder.findThemeByName(lafManager, themeName) == null) {
                com.selectrum.utils.NotificationUtils.notifyWarning("Theme '" + themeName
                        + "' (at " + entry.getHour() + ") not found. Check your selectrum.yaml configuration.");
            }

            String editorName = entry.getEffectiveEditor();
            if (entry.getEditor() != null && finder.findEditorSchemeByName(editorName) == null) {
                com.selectrum.utils.NotificationUtils.notifyWarning("Editor scheme '"
                        + editorName + "' (at " + entry.getHour() + ") not found. Check your selectrum.yaml configuration.");
            }
        }
    }
}