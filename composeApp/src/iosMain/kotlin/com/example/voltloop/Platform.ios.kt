package com.example.voltloop

import platform.UIKit.UIDevice
import platform.Foundation.NSURL
import io.github.jan.supabase.auth.handleDeeplinks

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

fun handleDeepLink(url: NSURL) {
    supabase.handleDeeplinks(url)
}