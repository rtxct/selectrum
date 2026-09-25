package com.selectrum.widget;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.awt.RelativePoint;
import com.selectrum.service.SelectrumConfigService;
import com.selectrum.service.SelectrumScheduler;
import com.selectrum.settings.SelectrumSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

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

    public static final String WIDGET_ID = "SelectrumWidget";
    private static final Icon ICON = IconLoader.getIcon("/icons/selectrum.svg", SelectrumStatusBarWidget.class);

    private final Project project;
    private StatusBar statusBar;

    public SelectrumStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return WIDGET_ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
    }

    @Override
    public WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getTooltipText() {
        boolean enabled = SelectrumSettings.getInstance().isEnabled();
        return "Selectrum: " + (enabled ? "Enabled" : "Disabled");
    }

    @Override
    public com.intellij.util.Consumer<MouseEvent> getClickConsumer() {
        return this::showPopup;
    }

    private void showPopup(MouseEvent event) {
        DefaultActionGroup group = new DefaultActionGroup();

        addGroupToggle(group);
        group.addSeparator();

        group.add(new AnAction("Open Configuration") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openConfigFile();
            }
        });

        Component component = event.getComponent();
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "Selectrum",
                group,
                DataManager.getInstance().getDataContext(component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false
        );

        Dimension size = popup.getContent().getPreferredSize();
        Point point = new Point(0, -size.height);

        popup.show(new RelativePoint(component, point));
    }

    private void addGroupToggle(DefaultActionGroup group) {
        group.add(new ToggleAction("Theme Switching") {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return SelectrumSettings.getInstance().isEnabled();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                SelectrumSettings.getInstance().setEnabled(state);
                if (statusBar != null) {
                    statusBar.updateWidget(WIDGET_ID);
                }

                if (state) {
                    SelectrumScheduler.getInstance().checkAndApplyTheme();
                }
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
    }

    private void openConfigFile() {
        Path configPath = SelectrumConfigService.getInstance().getConfigFilePath();
        VirtualFile virtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(configPath.toString());

        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true);
        }
    }

    @Override
    public void dispose() { }
}