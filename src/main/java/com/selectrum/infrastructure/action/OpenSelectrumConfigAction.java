package com.selectrum.infrastructure.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.selectrum.utils.FileUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Action that opens the {@code selectrum.yaml} configuration file in the IDE editor.
 * <p>
 * Accessible via <b>Tools → Selectrum → Open Configuration</b>. If the config file doesn't
 * exist yet, the config service creates a default one first.
 */
public final class OpenSelectrumConfigAction extends AnAction {

    /**
     * Performs the action to open the configuration file.
     *
     * @param e the action event carrying information about the current context
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        FileUtils.openConfigFile(project);
    }

    /**
     * Updates the state of the action based on the current context.
     * Enables and makes the action visible only if a project is open.
     *
     * @param e the action event carrying information about the current context
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    /**
     * Specifies the thread on which the {@link #update(AnActionEvent)} method should be executed.
     *
     * @return the thread on which the action should be updated, which is the Event Dispatch Thread (EDT)
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}