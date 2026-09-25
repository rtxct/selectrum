package com.selectrum.utils;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;

public class NotificationUtils {

    public static void notifyError(String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification("Selectrum", message, NotificationType.ERROR)
                .notify(null);
    }

    public static void notifyWarning(String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Selectrum")
                .createNotification("Selectrum", message, NotificationType.WARNING)
                .notify(null);
    }
}