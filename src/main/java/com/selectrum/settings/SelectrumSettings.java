package com.selectrum.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Persists the Selectrum enabled/disabled state across IDE restarts.
 * Stored in {@code <IDE_CONFIG>/options/selectrum-settings.xml}.
 */
@Service(Service.Level.APP)
@State(name = "SelectrumSettings", storages = @Storage("selectrum-settings.xml"))
public final class SelectrumSettings implements PersistentStateComponent<SelectrumSettings.State> {

    public static SelectrumSettings getInstance() {
        return ApplicationManager
                .getApplication().getService(SelectrumSettings.class);
    }

    private final State crrState = new State();

    public static class State {
        public boolean enabled = true;
    }

    @Override
    public @NotNull State getState() {
        return crrState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, crrState);
    }

    public boolean isEnabled() {
        return crrState.enabled;
    }

    public void setEnabled(boolean enabled) {
        crrState.enabled = enabled;
    }
}