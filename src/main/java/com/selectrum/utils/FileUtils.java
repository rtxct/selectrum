package com.selectrum.utils;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.selectrum.application.service.SelectrumConfigService;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class FileUtils {

    public static void openConfigFile(@NotNull Project project) {
        Path configPath = SelectrumConfigService.instance().getConfigFilePath();

        VirtualFile virtualFile = LocalFileSystem
                .getInstance().refreshAndFindFileByPath(configPath.toString());

        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
        }
    }
}