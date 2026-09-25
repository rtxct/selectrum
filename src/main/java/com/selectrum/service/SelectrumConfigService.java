package com.selectrum.service;

import com.intellij.ide.ApplicationActivationStateManager;
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
import com.selectrum.model.ScheduleEntry;
import com.selectrum.utils.NotificationUtils;
import lombok.Getter;
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
 */
@Getter
@Service(Service.Level.APP)
public final class SelectrumConfigService implements Disposable {

    private static final Logger LOG = Logger.getInstance(SelectrumConfigService.class);

    private static final String CONFIG_FILE_NAME = "selectrum.yaml";

    private final Path configFilePath;
    private volatile List<ScheduleEntry> schedule;

    public SelectrumConfigService() {
        this.configFilePath =
                Path.of(PathManager.getConfigPath(), CONFIG_FILE_NAME);
        this.schedule =
                Collections.emptyList();

        ensureConfigFileExists();
        loadConfigSync();
        subscribeToFileChanges();
    }

    public static SelectrumConfigService getInstance() {
        return ApplicationManager.getApplication().getService(SelectrumConfigService.class);
    }

    @Override
    public void dispose() { }

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
                      "08:00":
                        theme: IntelliJ Light
                      "18:00":
                        theme: Darcula
                        editor: Light
                    """;

            Files.writeString(configFilePath, defaultContent);

            LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(configFilePath.toString());
        } catch (IOException e) {
            LOG.error("Failed to create default Selectrum config file", e);
        }
    }

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

                        triggerSchedulerRecheck();
                    } catch (Exception e) {
                        LOG.warn("Failed to parse Selectrum config", e);

                        NotificationUtils.notifyError(
                                "Invalid YAML in selectrum.yaml: " + e.getMessage());
                    }
                });
    }

    private List<ScheduleEntry> parseSchedule(String yamlContent) {
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

    private String resolveHourKey(Object key) {
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

    private boolean isConfigFileEvent(VFileEvent event) {
        return FileUtil.pathsEqual(event.getPath(), configFilePath.toString());
    }

    private void triggerSchedulerRecheck() {
        SelectrumScheduler scheduler = ApplicationManager
                .getApplication().getServiceIfCreated(SelectrumScheduler.class);

        if (scheduler != null) {
            scheduler.checkAndApplyTheme();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private String getRequiredString(Map<?, ?> map, String field, String hourContext) {
        Object value = map.get(field);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required field '" + field + "' for hour '" + hourContext + "'");
        }
        return value.toString();
    }

    @SuppressWarnings("SameParameterValue")
    private String getOptionalString(Map<?, ?> map, String field) {
        Object value = map.get(field);
        return value != null ? value.toString() : null;
    }
}