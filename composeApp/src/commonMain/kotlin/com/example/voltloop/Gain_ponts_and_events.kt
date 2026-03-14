package com.example.voltloop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voltloop.Camera.ProveItScreen
import com.example.voltloop.NetworkStuff.getEvent
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
// ─── Colour palette ───────────────────────────────────────────────────────────
private val BgDark      = Color(0xFF0D0F14)
private val SurfaceDark = Color(0xFF161920)
private val AccentCyan  = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00FF9C)
private val AccentAmber = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF3D5A)
private val TextPrimary = Color(0xFFE8EAF0)
private val TextMuted   = Color(0xFF6B7280)

private enum class TimerState { IDLE, RUNNING, PAUSED, DONE }

// ─── Event item model ─────────────────────────────────────────────────────────
private data class EcoEvent(
    val id: Long,
    val text: String,
    val proved: Boolean = false
)

// ─── Main screen ──────────────────────────────────────────────────────────────
@Composable
fun TimerScreen() {
    var totalSeconds by remember { mutableStateOf(60) }
    var secondsLeft  by remember { mutableStateOf(totalSeconds) }
    var state        by remember { mutableStateOf(TimerState.IDLE) }

    // Event being proved — when non-null, ProveItScreen slides in
    var provingEvent by remember { mutableStateOf<EcoEvent?>(null) }

    // Event list — grows every 60 s while the timer is running
    val events = remember { mutableStateListOf<EcoEvent>() }

    // ── Timer tick ────────────────────────────────────────────────────────────
    LaunchedEffect(state) {
        if (state == TimerState.RUNNING) {
            while (secondsLeft > 0 && state == TimerState.RUNNING) {
                delay(1_000L)
                secondsLeft--
            }
            if (secondsLeft == 0) state = TimerState.DONE
        }
    }

    // ── Periodic event fetch: immediately on start, then every 60 s ──────────
    LaunchedEffect(state) {
        if (state == TimerState.RUNNING) {
            while (state == TimerState.RUNNING) {
                try {
                    val response = getEvent()

                    events.add(EcoEvent(id = Clock.System.now().toEpochMilliseconds(), text = response.event))

                } catch (e: Exception) {
                    println("EVENT_ERROR: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    val progress = if (totalSeconds > 0) secondsLeft / totalSeconds.toFloat() else 0f

    val barColor by animateColorAsState(
        targetValue = when {
            progress > 0.50f -> AccentCyan
            progress > 0.25f -> AccentAmber
            else             -> AccentRed
        },
        animationSpec = tween(600),
        label = "barColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        HeaderSection()

        ClockDisplay(secondsLeft = secondsLeft, state = state, accentColor = barColor)

        TimerProgressBar(progress = progress, color = barColor)

        if (state == TimerState.IDLE) {
            DurationAdjuster(
                totalSeconds = totalSeconds,
                onValueChange = { newVal ->
                    totalSeconds = newVal
                    secondsLeft  = newVal
                }
            )
        }

        ControlButtons(
            state   = state,
            onStart = { if (state == TimerState.IDLE || state == TimerState.PAUSED) state = TimerState.RUNNING },
            onPause = { state = TimerState.PAUSED },
            onReset = {
                state       = TimerState.IDLE
                secondsLeft = totalSeconds
                events.clear()
            }
        )

        if (state == TimerState.DONE) {
            DoneBanner(onDismiss = {
                state       = TimerState.IDLE
                secondsLeft = totalSeconds
            })
        }

        // ── Event list ────────────────────────────────────────────────────────
        if (events.isNotEmpty()) {
            EventList(
                events  = events,
                onProve = { event -> provingEvent = event }
            )
        }
    }

    // ── ProveIt overlay — sits above everything ───────────────────────────────
    provingEvent?.let { event ->
        ProveItScreen(
            challengeText = event.text,
            onProved = { base64 ->
                // Mark the card as proved
                val idx = events.indexOfFirst { it.id == event.id }
                if (idx >= 0) events[idx] = events[idx].copy(proved = true)
                println("BASE64_LENGTH: ${base64.length}") // TODO: POST to your server
                provingEvent = null
            },
            onDismiss = { provingEvent = null }
        )
    }
}

// ─── Event list ───────────────────────────────────────────────────────────────
@Composable
private fun EventList(
    events: List<EcoEvent>,
    onProve: (EcoEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ECO CHALLENGES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, onProve = { onProve(event) })
            }
        }
    }
}

// ─── Single event card ────────────────────────────────────────────────────────
@Composable
private fun EventCard(event: EcoEvent, onProve: () -> Unit) {
    val borderColor = if (event.proved) AccentGreen else AccentCyan.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (event.proved) AccentGreen else AccentCyan)
        )

        Text(
            text = event.text,
            color = if (event.proved) TextMuted else TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = onProve,
            enabled = !event.proved,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = AccentGreen,
                contentColor           = BgDark,
                disabledContainerColor = AccentGreen.copy(alpha = 0.3f),
                disabledContentColor   = TextMuted
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (event.proved) "✓ Done" else "Prove it",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "TIMER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            color = TextMuted
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(listOf(AccentCyan, AccentGreen)),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

// ─── Clock display ────────────────────────────────────────────────────────────
@Composable
private fun ClockDisplay(secondsLeft: Int, state: TimerState, accentColor: Color) {
    val minutes = secondsLeft / 60
    val secs    = secondsLeft % 60
    val timeStr = "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    val scale by animateFloatAsState(
        targetValue = if (state == TimerState.RUNNING) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .shadow(24.dp, CircleShape, ambientColor = accentColor.copy(alpha = 0.3f))
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(listOf(accentColor, accentColor.copy(alpha = 0.1f), accentColor)),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeStr,
            fontSize = (50 * scale).sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp
        )
    }
}

// ─── Progress bar ─────────────────────────────────────────────────────────────
@Composable
private fun TimerProgressBar(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "REMAINING",
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SurfaceDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0%",   fontSize = 10.sp, color = TextMuted)
            Text("100%", fontSize = 10.sp, color = TextMuted)
        }
    }
}

// ─── Duration adjuster ────────────────────────────────────────────────────────
@Composable
private fun DurationAdjuster(totalSeconds: Int, onValueChange: (Int) -> Unit) {
    val presets = listOf(30, 60, 120, 300, 600)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("SET DURATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = TextMuted)

        Slider(
            value = totalSeconds.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 5f..600f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = AccentCyan.copy(alpha = 0.2f)
            )
        )

        val label = when {
            totalSeconds < 60      -> "${totalSeconds}s"
            totalSeconds % 60 == 0 -> "${totalSeconds / 60}m"
            else                   -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
        }
        Text(
            text = label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentCyan,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )

        Text("QUICK PICK", fontSize = 10.sp, letterSpacing = 3.sp, color = TextMuted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val isSelected  = preset == totalSeconds
                val presetLabel = if (preset < 60) "${preset}s" else "${preset / 60}m"
                OutlinedButton(
                    onClick = { onValueChange(preset) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) AccentCyan.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor   = if (isSelected) AccentCyan else TextMuted
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, if (isSelected) AccentCyan else TextMuted.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(presetLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ─── Control buttons ──────────────────────────────────────────────────────────
@Composable
private fun ControlButtons(
    state: TimerState, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
            border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted.copy(alpha = 0.3f))
        ) { Text("Reset", fontWeight = FontWeight.SemiBold) }

        Button(
            onClick = if (state == TimerState.RUNNING) onPause else onStart,
            modifier = Modifier.weight(2f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state == TimerState.RUNNING) AccentAmber else AccentCyan,
                contentColor   = BgDark
            )
        ) {
            Text(
                text = when (state) {
                    TimerState.RUNNING -> "Pause"
                    TimerState.PAUSED  -> "Resume"
                    TimerState.DONE    -> "Done"
                    TimerState.IDLE    -> "Start"
                },
                fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
        }
    }
}

// ─── Done banner ──────────────────────────────────────────────────────────────
@Composable
private fun DoneBanner(onDismiss: () -> Unit) {
    val alpha by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AccentRed.copy(alpha = 0.15f))
            .border(1.dp, AccentRed.copy(alpha = alpha), RoundedCornerShape(14.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏰  Time's up!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentRed)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = AccentRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}