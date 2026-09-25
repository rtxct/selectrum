package com.selectrum.infrastructure.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.selectrum.application.service.SelectrumScheduler;
import com.selectrum.infrastructure.settings.SelectrumSettings;
import com.selectrum.widget.SelectrumStatusBarWidget;
import org.jetbrains.annotations.NotNull;

/**
 * Action that toggles the enabled state of the Selectrum plugin.
 * Accessible via Tools → Selectrum → Enable Theme Switching.
 */
public final class ToggleSelectrumAction extends ToggleAction {

    /**
     * Determines the current selected state of the toggle action.
     *
     * @param e the action event
     * @return true if Selectrum is enabled, false otherwise
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return SelectrumSettings.instance().isEnabled();
    }

    /**
     * Called when the user toggles the action.
     * Updates the global setting and refreshes all active status bar widgets.
     *
     * @param e     the action event
     * @param state the new state of the toggle
     */
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        SelectrumSettings.instance().setEnabled(state);

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            StatusBar statusBar = WindowManager
                    .getInstance().getStatusBar(project);

            if (statusBar != null) {
                statusBar.updateWidget(SelectrumStatusBarWidget.WIDGET_ID);
            }
        }

        if (state) {
            SelectrumScheduler.instance().checkAndApplyTheme();
        }
    }

    /**
     * Specifies the thread on which the action should be updated.
     *
     * @return the Event Dispatch Thread (EDT)
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}