package com.selectrum.infrastructure.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.selectrum.domain.vo.SettingState;
import org.jetbrains.annotations.NotNull;

/**
 * Persists the Selectrum enabled/disabled state across IDE restarts.
 * Stored in {@code <IDE_CONFIG>/options/selectrum-settings.xml}.
 */
@Service(Service.Level.APP)
@State(name = "SelectrumSettings", storages = @Storage("selectrum-settings.xml"))
public final class SelectrumSettings implements PersistentStateComponent<SettingState> {

    private final SettingState crrState;

    SelectrumSettings() {
        crrState = new SettingState();
    }

    public static SelectrumSettings instance() {
        return ApplicationManager
                .getApplication().getService(SelectrumSettings.class);
    }

    @Override
    public @NotNull SettingState getState() {
        return crrState;
    }

    @Override
    public void loadState(@NotNull SettingState state) {
        XmlSerializerUtil.copyBean(state, crrState);
    }

    public boolean isEnabled() {
        return crrState.enabled;
    }

    public void setEnabled(boolean enabled) {
        crrState.enabled = enabled;
    }
}