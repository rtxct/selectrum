package com.selectrum.startup;

import com.intellij.ide.AppLifecycleListener;
import com.selectrum.service.SelectrumScheduler;

/**
 * Kicks off the Selectrum scheduler when the IDE finishes starting.
 * <p>
 * Registered as an {@link AppLifecycleListener} in {@code plugin.xml}
 * so it fires once per application lifecycle (not per project).
 */
public final class SelectrumStartupListener implements AppLifecycleListener {

    @Override
    public void appStarted() {
        SelectrumScheduler.getInstance().start();
    }
}
