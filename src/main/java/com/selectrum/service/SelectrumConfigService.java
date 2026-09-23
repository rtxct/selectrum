package com.selectrum.service;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.selectrum.model.ScheduleEntry;
import org.jetbrains.annotations.NotNull;
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
 * <p>
 * Uses YAML format which natively supports {@code #} comments.
 * <p>
 * Automatically reloads when the file changes on disk (via VFS listener)
 * and triggers an immediate theme re-evaluation in the scheduler.
 */
@Service(Service.Level.APP)
public final class SelectrumConfigService implements Disposable {

    private static final Logger LOG = Logger.getInstance(SelectrumConfigService.class);
    private static final String CONFIG_FILE_NAME = "selectrum.yaml";

    private final Path configFilePath;
    private volatile List<ScheduleEntry> schedule = Collections.emptyList();

    public SelectrumConfigService() {
        this.configFilePath = Path.of(PathManager.getConfigPath(), CONFIG_FILE_NAME);
        ensureConfigFileExists();
        loadConfig();
        subscribeToFileChanges();
    }

    public static SelectrumConfigService getInstance() {
        return ApplicationManager.getApplication().getService(SelectrumConfigService.class);
    }

    /**
     * Returns the current parsed schedule (immutable). Never null, but may be empty.
     */
    public List<ScheduleEntry> getSchedule() {
        return schedule;
    }

    /**
     * Returns the absolute path to the configuration file.
     */
    public Path getConfigFilePath() {
        return configFilePath;
    }

    /**
     * Forces a reload of the configuration file.
     */
    public void reload() {
        loadConfig();
    }

    // -------------------------------------------------------------------
    // Config file lifecycle
    // -------------------------------------------------------------------

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
                    # Hours MUST be quoted (e.g. "08:00") to prevent YAML from interpreting them as numbers.
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
                      "08:00":
                        theme: IntelliJ Light
                      "18:00":
                        theme: Darcula
                    """;
            Files.writeString(configFilePath, defaultContent);
            LOG.info("Created default Selectrum config at: " + configFilePath);
        } catch (IOException e) {
            LOG.error("Failed to create default Selectrum config file", e);
        }
    }

    private void loadConfig() {
        if (!Files.exists(configFilePath)) {
            schedule = Collections.emptyList();
            return;
        }
        try {
            String content = Files.readString(configFilePath);
            List<ScheduleEntry> entries = parseSchedule(content);
            schedule = Collections.unmodifiableList(entries);
            LOG.info("Loaded " + entries.size() + " schedule entries from Selectrum config");
        } catch (IOException e) {
            LOG.error("Failed to read Selectrum config file", e);
            notifyError("Failed to read configuration file: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("Failed to parse Selectrum config", e);
            notifyError("Invalid YAML in selectrum.yaml: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------

    /**
     * Parses the YAML configuration into schedule entries.
     * <p>
     * Expected format:
     * <pre>
     * schedule:
     *   "08:00":
     *     theme: IntelliJ Light
     *     editor: Darcula
     *   "18:00":
     *     theme: Darcula
     * </pre>
     * <p>
     * Hours must be quoted to prevent YAML from interpreting them as sexagesimal numbers.
     * If an unquoted hour is parsed as an integer (e.g. {@code 8:00 → 480}), it is
     * converted back to {@code H:mm} format gracefully.
     */
    @SuppressWarnings("unchecked")
    private List<ScheduleEntry> parseSchedule(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlContent);
        if (root == null || !root.containsKey("schedule")) {
            return Collections.emptyList();
        }

        Object scheduleObj = root.get("schedule");
        if (!(scheduleObj instanceof Map<?, ?> scheduleMap)) {
            throw new IllegalArgumentException("'schedule' must be a mapping of hours to theme configs");
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
     * Resolves the hour key from the YAML map.
     * If the key is a String (quoted in YAML), returns it directly.
     * If it's an Integer (YAML 1.1 sexagesimal, e.g. {@code 8:00 → 480}),
     * converts it back to {@code H:mm} format.
     */
    private String resolveHourKey(Object key) {
        if (key instanceof String s) {
            return s;
        }
        if (key instanceof Integer i) {
            // YAML 1.1 sexagesimal: e.g. 8:00 → 480, 18:30 → 1110
            int hours = i / 60;
            int minutes = i % 60;
            return String.format("%d:%02d", hours, minutes);
        }
        throw new IllegalArgumentException("Invalid hour key: '" + key + "'. Hours must be quoted strings.");
    }

    private String getRequiredString(Map<?, ?> map, String field, String hourContext) {
        Object value = map.get(field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required field '" + field + "' for hour '" + hourContext + "'");
        }
        return value.toString();
    }

    private String getOptionalString(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value != null ? value.toString() : null;
    }

    // -------------------------------------------------------------------
    // File watching
    // -------------------------------------------------------------------

    private void subscribeToFileChanges() {
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        for (VFileEvent event : events) {
                            if (isConfigFileEvent(event)) {
                                LOG.info("Selectrum config file changed, reloading...");
                                loadConfig();
                                triggerSchedulerRecheck();
                                return; // Only need to reload once per batch
                            }
                        }
                    }
                });
    }

    private boolean isConfigFileEvent(VFileEvent event) {
        String path = event.getPath();
        return path.endsWith(CONFIG_FILE_NAME)
                && path.equals(configFilePath.toString().replace('\\', '/'));
    }

    private void triggerSchedulerRecheck() {
        SelectrumScheduler scheduler = ApplicationManager.getApplication()
                .getServiceIfCreated(SelectrumScheduler.class);
        if (scheduler != null) {
            scheduler.checkAndApplyTheme();
        }
    }

    // -------------------------------------------------------------------
    // Notifications
    // -------------------------------------------------------------------

    private void notifyError(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Selectrum")
                        .createNotification("Selectrum", message, NotificationType.WARNING)
                        .notify(null)
        );
    }

    @Override
    public void dispose() {
        // MessageBus connection is auto-disposed via connect(this)
    }
}
