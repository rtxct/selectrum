package com.selectrum.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.selectrum.service.SelectrumScheduler;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kicks off the Selectrum scheduler when the IDE finishes starting.
 */
public final class SelectrumStartupListener implements ProjectActivity {

    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (started.compareAndSet(false, true)) {
            SelectrumScheduler.getInstance().start();
        }

        return Unit.INSTANCE;
    }
}