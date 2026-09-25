package com.selectrum.widget;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.selectrum.application.service.SelectrumScheduler;
import com.selectrum.infrastructure.settings.SelectrumSettings;
import com.selectrum.utils.FileUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Status bar widget that shows the Selectrum icon in the bottom-right corner.
 * <p>
 * Clicking it opens a popup with options to:
 * <ul>
 *   <li>Toggle theme switching on/off</li>
 *   <li>Open the configuration file</li>
 * </ul>
 */
public final class SelectrumStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {

    /**
     * The unique identifier for this widget.
     */
    public static final String WIDGET_ID = "SelectrumWidget";
    
    /**
     * The icon displayed by the widget.
     */
    private static final Icon ICON = IconLoader.getIcon("/icons/selectrum.svg", SelectrumStatusBarWidget.class);

    /**
     * The project associated with this widget.
     */
    private final Project project;
    
    /**
     * The status bar instance where this widget is installed.
     */
    private StatusBar statusBar;

    /**
     * Constructs a new {@link SelectrumStatusBarWidget} for the given project.
     *
     * @param project the project this widget belongs to, must not be null
     */
    public SelectrumStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Disposes the widget. Called when the widget is no longer needed.
     */
    @Override
    public void dispose() { }

    /**
     * Returns the unique ID of the widget.
     *
     * @return the widget ID
     */
    @Override
    public @NotNull String ID() {
        return WIDGET_ID;
    }

    /**
     * Installs the widget into the given status bar.
     *
     * @param statusBar the status bar to install the widget into, must not be null
     */
    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    /**
     * Returns the presentation for this widget, which determines how it is displayed.
     *
     * @return the presentation for this widget
     */
    @Override
    public @NotNull WidgetPresentation getPresentation() {
        return this;
    }

    /**
     * Returns the icon to be displayed in the status bar.
     *
     * @return the widget's icon
     */
    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    /**
     * Returns the tooltip text to be shown when hovering over the widget.
     * The text indicates whether Selectrum theme switching is enabled or disabled.
     *
     * @return the tooltip text
     */
    @Override
    public @NotNull String getTooltipText() {
        boolean enabled = SelectrumSettings.instance().isEnabled();
        return "Selectrum: " + (enabled ? "Enabled" : "Disabled");
    }

    /**
     * Returns a consumer that handles click events on the widget.
     *
     * @return a consumer handling mouse events
     */
    @Override
    public @NotNull Consumer<MouseEvent> getClickConsumer() {
        return this::showPopup;
    }

    /**
     * Displays a popup menu when the widget is clicked.
     *
     * @param event the mouse event triggering the popup
     */
    private void showPopup(@NotNull MouseEvent event) {
        DefaultActionGroup group = new DefaultActionGroup();

        addGroupToggle(group);
        group.addSeparator();
        addGroupConfigOpener(group);

        Component component = event.getComponent();
        ListPopup popup = getPopup(group, component);

        Dimension size = popup.getContent().getPreferredSize();
        Point point = new Point(0, -size.height);

        popup.show(new RelativePoint(component, point));
    }

    /**
     * Creates the popup menu from the action group.
     *
     * @param group     the action group to display in the popup
     * @param component the component triggering the popup
     * @return the created list popup, must not be null
     */
    private @NotNull ListPopup getPopup(@NotNull DefaultActionGroup group, @NotNull Component component) {
        return JBPopupFactory.getInstance().createActionGroupPopup(
                "Selectrum",
                group,
                DataManager.getInstance().getDataContext(component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
        );
    }

    /**
     * Adds an action to the group for opening the configuration file.
     *
     * @param group the action group to add the configuration opener to
     */
    private void addGroupConfigOpener(@NotNull DefaultActionGroup group) {
        group.add(new AnAction("Open Configuration") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                FileUtils.openConfigFile(project);
            }
        });
    }

    /**
     * Adds a toggle action to the group for enabling or disabling theme switching.
     *
     * @param group the action group to add the toggle to
     */
    private void addGroupToggle(@NotNull DefaultActionGroup group) {
        group.add(new ToggleAction("Theme Switching") {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return SelectrumSettings.instance().isEnabled();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                SelectrumSettings.instance().setEnabled(state);
                if (statusBar != null) {
                    statusBar.updateWidget(WIDGET_ID);
                }

                if (state) {
                    SelectrumScheduler.instance().checkAndApplyTheme();
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
    }
}