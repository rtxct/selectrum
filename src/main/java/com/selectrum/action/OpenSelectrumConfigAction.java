package com.selectrum.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.selectrum.service.SelectrumConfigService;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Action that opens the {@code selectrum.yaml} configuration file in the IDE editor.
 * <p>
 * Accessible via <b>Tools → Selectrum → Open Configuration</b>. If the config file doesn't
 * exist yet, the config service creates a default one first.
 */
public final class OpenSelectrumConfigAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        SelectrumConfigService configService = SelectrumConfigService.getInstance();
        Path configPath = configService.getConfigFilePath();

        VirtualFile virtualFile = LocalFileSystem
                .getInstance().refreshAndFindFileByPath(configPath.toString());

        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}