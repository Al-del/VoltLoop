package com.example.voltloop

import kotlinx.datetime.Clock
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun showLocalNotification(title: String, body: String) {
    val center = UNUserNotificationCenter.currentNotificationCenter()

    // Request permission (no-op if already granted or denied)
    center.requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    ) { granted, error ->
        if (!granted) return@requestAuthorizationWithOptions

        dispatch_async(dispatch_get_main_queue()) {
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(body)
                setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
            }

            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                timeInterval = 1.0,
                repeats = false
            )

            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = "msg_${Clock.System.now().toEpochMilliseconds()}",
                content = content,
                trigger = trigger
            )

            center.addNotificationRequest(request) { err ->
                if (err != null) println("Notification error: ${err.localizedDescription}")
            }
        }
    }
}
