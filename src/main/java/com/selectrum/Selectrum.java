package com.selectrum;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.selectrum.application.service.SelectrumScheduler;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kicks off the Selectrum scheduler when the IDE finishes starting.
 */
public final class Selectrum implements ProjectActivity {

    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        if (started.compareAndSet(false, true)) {
            SelectrumScheduler.instance().start();
        }

        return Unit.INSTANCE;
    }
}