package com.selectrum.utils;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.selectrum.application.service.SelectrumConfigService;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Utility class for file-related operations.
 * Provides methods to interact with the file system and IDE editors.
 */
public class FileUtils {

    /**
     * Opens the configuration file associated with the Selectrum plugin in the IDE editor.
     * If the file is found, it will be opened and focused.
     *
     * @param project the current project context, must not be null
     */
    public static void openConfigFile(@NotNull Project project) {
        Path configPath = SelectrumConfigService.instance().getConfigFilePath();

        VirtualFile virtualFile = LocalFileSystem
                .getInstance().refreshAndFindFileByPath(configPath.toString());

        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
        }
    }
}