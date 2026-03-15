package com.example.voltloop

import android.content.Context
import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

/** Holds the application Context so platform-level helpers (e.g. notifications) can access it. */
object AppContext {
    lateinit var context: Context
}