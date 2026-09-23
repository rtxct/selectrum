package com.selectrum.widget;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory that creates the Selectrum status bar widget for each project window.
 * Registered via {@code <statusBarWidgetFactory>} in {@code plugin.xml}.
 */
public final class SelectrumStatusBarWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return SelectrumStatusBarWidget.WIDGET_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Selectrum";
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new SelectrumStatusBarWidget(project);
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
