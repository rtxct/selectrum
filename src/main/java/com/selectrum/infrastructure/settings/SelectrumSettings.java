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

    /**
     * The current setting state holding the persistent data.
     */
    private final SettingState crrState;

    /**
     * Constructs a new instance of {@code SelectrumSettings} with a default state.
     */
    SelectrumSettings() {
        crrState = new SettingState();
    }

    /**
     * Retrieves the singleton instance of {@code SelectrumSettings}.
     *
     * @return the application-level instance of this service
     */
    public static @NotNull SelectrumSettings instance() {
        return ApplicationManager
                .getApplication().getService(SelectrumSettings.class);
    }

    /**
     * Retrieves the current state to be persisted.
     *
     * @return the current {@link SettingState}
     */
    @Override
    public @NotNull SettingState getState() {
        return crrState;
    }

    /**
     * Loads the persisted state into the current component.
     *
     * @param state the state to load from storage
     */
    @Override
    public void loadState(@NotNull SettingState state) {
        XmlSerializerUtil.copyBean(state, crrState);
    }

    /**
     * Checks whether the Selectrum service is currently enabled.
     *
     * @return {@code true} if Selectrum is enabled, {@code false} otherwise
     */
    public boolean isEnabled() {
        return crrState.enabled;
    }

    /**
     * Updates the enabled state of the Selectrum service.
     *
     * @param enabled the new enabled status to set
     */
    public void setEnabled(boolean enabled) {
        crrState.enabled = enabled;
    }
}