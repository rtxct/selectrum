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

    /**
     * Returns the unique identifier for widgets created by this factory.
     *
     * @return the widget ID, must not be null
     */
    @Override
    public @NotNull String getId() {
        return SelectrumStatusBarWidget.WIDGET_ID;
    }

    /**
     * Returns the display name of the widget, shown in settings.
     *
     * @return the display name, must not be null
     */
    @Override
    public @NotNull String getDisplayName() {
        return "Selectrum";
    }

    /**
     * Creates a new instance of the widget for the specified project.
     *
     * @param project the project for which the widget is created, must not be null
     * @return a newly created {@link StatusBarWidget}, must not be null
     */
    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new SelectrumStatusBarWidget(project);
    }

    /**
     * Determines whether the widget is available for the given project.
     *
     * @param project the project to check against, must not be null
     * @return {@code true} if the widget is available, {@code false} otherwise
     */
    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    /**
     * Determines whether the widget can be enabled on the specified status bar.
     *
     * @param statusBar the status bar to check against, must not be null
     * @return {@code true} if the widget can be enabled, {@code false} otherwise
     */
    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
