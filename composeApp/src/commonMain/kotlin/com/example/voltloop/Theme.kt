package com.example.voltloop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import voltloop.composeapp.generated.resources.Res
import voltloop.composeapp.generated.resources.HostGrotesk_Medium

@Composable
fun VoltLoopTheme(content: @Composable () -> Unit) {
    val fonts = FontFamily(Font(Res.font.HostGrotesk_Medium))

    val typography = Typography().run {
        copy(
            displayLarge    = displayLarge.copy(fontFamily = fonts),
            displayMedium   = displayMedium.copy(fontFamily = fonts),
            displaySmall    = displaySmall.copy(fontFamily = fonts),
            headlineLarge   = headlineLarge.copy(fontFamily = fonts),
            headlineMedium  = headlineMedium.copy(fontFamily = fonts),
            headlineSmall   = headlineSmall.copy(fontFamily = fonts),
            titleLarge      = titleLarge.copy(fontFamily = fonts),
            titleMedium     = titleMedium.copy(fontFamily = fonts),
            titleSmall      = titleSmall.copy(fontFamily = fonts),
            bodyLarge       = bodyLarge.copy(fontFamily = fonts),
            bodyMedium      = bodyMedium.copy(fontFamily = fonts),
            bodySmall       = bodySmall.copy(fontFamily = fonts),
            labelLarge      = labelLarge.copy(fontFamily = fonts),
            labelMedium     = labelMedium.copy(fontFamily = fonts),
            labelSmall      = labelSmall.copy(fontFamily = fonts),
        )
    }

    MaterialTheme(typography = typography, content = content)
}
