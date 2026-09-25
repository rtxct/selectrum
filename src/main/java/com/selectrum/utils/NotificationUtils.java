package com.selectrum.utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for displaying notifications within the IDE.
 * Provides methods to show error and warning notifications using the Selectrum notification group.
 */
public class NotificationUtils {

    /**
     * Displays an error notification to the user.
     *
     * @param message the content of the error message to display
     */
    public static void notifyError(@NotNull String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification("Selectrum", message, NotificationType.ERROR)
                .notify(null);
    }

    /**
     * Displays a warning notification to the user.
     *
     * @param message the content of the warning message to display
     */
    public static void notifyWarning(@NotNull String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification("Selectrum", message, NotificationType.WARNING)
                .notify(null);
    }
}