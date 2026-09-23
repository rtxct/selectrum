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

    public static class State {
        public boolean enabled = true;
    }

    private State myState = new State();

    public static SelectrumSettings getInstance() {
        return ApplicationManager.getApplication().getService(SelectrumSettings.class);
    }

    public boolean isEnabled() {
        return myState.enabled;
    }

    public void setEnabled(boolean enabled) {
        myState.enabled = enabled;
    }

    @Override
    public @NotNull State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }
}
