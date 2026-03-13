package com.example.voltloop.NetworkStuff

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual fun createHttpClient() = HttpClient(OkHttp)