package com.example.voltloop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform