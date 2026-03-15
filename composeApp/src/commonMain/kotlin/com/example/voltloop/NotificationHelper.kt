package com.example.voltloop

/**
 * Cross-platform local notification helper.
 * Each platform provides an [actual] implementation.
 */
expect fun showLocalNotification(title: String, body: String)
