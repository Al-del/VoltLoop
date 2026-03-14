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
import com.example.voltloop.NetworkStuff.lockLocker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val BgDark      = Color(0xFF0D0F14)
private val SurfaceDark = Color(0xFF161920)
private val AccentCyan  = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00FF9C)
private val AccentAmber = Color(0xFFFFB830)
private val AccentRed   = Color(0xFFFF3D5A)
private val TextPrimary = Color(0xFFE8EAF0)
private val TextMuted   = Color(0xFF6B7280)

private enum class TimerState { IDLE, RUNNING, PAUSED, DONE }

private data class EcoEvent(
    val id: Long,
    val text: String,
    val proved: Boolean = false
)

@Composable
fun TimerScreen() {

    var totalSeconds   by remember { mutableStateOf(60) }
    var secondsLeft    by remember { mutableStateOf(totalSeconds) }
    var state          by remember { mutableStateOf(TimerState.IDLE) }
    var provingEvent   by remember { mutableStateOf<EcoEvent?>(null) }
    var runningSeconds by remember { mutableStateOf(0) }
    var totalPoints    by AppState.totalPoints  // ← from AppState now


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

    // ── Periodic event fetch: every 60 s of actual running time ──────────────
    LaunchedEffect(state) {
        if (state == TimerState.RUNNING) {
            if (runningSeconds == 0) {
                try {
                    val response = getEvent()
                    events.add(EcoEvent(id = Clock.System.now().toEpochMilliseconds(), text = response.event))
                } catch (e: Exception) {
                    println("EVENT_ERROR: ${e.message}")
                }
            }
            while (state == TimerState.RUNNING) {
                delay(1_000L)
                runningSeconds++
                if (runningSeconds % 60 == 0) {
                    try {
                        val response = getEvent()
                        events.add(EcoEvent(id = Clock.System.now().toEpochMilliseconds(), text = response.event))
                    } catch (e: Exception) {
                        println("EVENT_ERROR: ${e.message}")
                    }
                }
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
        HeaderSection(totalPoints = totalPoints)

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
                state          = TimerState.IDLE
                secondsLeft    = totalSeconds
                runningSeconds = 0
                events.clear()
            }
        )

        if (state == TimerState.DONE) {
            val provedCount = events.count { it.proved }
            DoneBanner(
                provedCount = provedCount,
                totalCount  = events.size,
                onDismiss   = {
                    AppState.totalPoints.value += (provedCount * 10) + 5  // ← updates AppState
                    state          = TimerState.IDLE
                    secondsLeft    = totalSeconds
                    runningSeconds = 0
                    events.clear()
                }
            )
        }

        if (events.isNotEmpty()) {
            EventList(
                events    = events,
                onProve   = { event -> if (state != TimerState.DONE) provingEvent = event },
                isLocked  = state == TimerState.DONE
            )
        }
    }

    provingEvent?.let { event ->
        ProveItScreen(
            challengeText = event.text,
            onProved = { base64 ->
                val idx = events.indexOfFirst { it.id == event.id }
                if (idx >= 0) events[idx] = events[idx].copy(proved = true)
                println("BASE64_LENGTH: ${base64.length}")
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
    onProve: (EcoEvent) -> Unit,
    isLocked: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ECO CHALLENGES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = TextMuted
            )
            if (isLocked) {
                Text(
                    text = "🔒 LOCKED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = AccentRed.copy(alpha = 0.7f)
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(
                    event    = event,
                    onProve  = { onProve(event) },
                    isLocked = isLocked
                )
            }
        }
    }
}

// ─── Single event card ────────────────────────────────────────────────────────
@Composable
private fun EventCard(event: EcoEvent, onProve: () -> Unit, isLocked: Boolean) {
    val borderColor = when {
        event.proved -> AccentGreen
        isLocked     -> AccentRed.copy(alpha = 0.2f)
        else         -> AccentCyan.copy(alpha = 0.3f)
    }

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
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        event.proved -> AccentGreen
                        isLocked     -> AccentRed.copy(alpha = 0.4f)
                        else         -> AccentCyan
                    }
                )
        )

        Text(
            text = event.text,
            color = if (event.proved || isLocked) TextMuted else TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )

        Button(
            onClick = onProve,
            enabled = !event.proved && !isLocked,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor         = AccentGreen,
                contentColor           = BgDark,
                disabledContainerColor = if (isLocked) AccentRed.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.3f),
                disabledContentColor   = TextMuted
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = when {
                    event.proved -> "✓ Done"
                    isLocked     -> "🔒"
                    else         -> "Prove it"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun HeaderSection(totalPoints: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "VOLTLOOP",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = TextMuted
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(AccentCyan, AccentGreen)),
                        RoundedCornerShape(1.dp)
                    )
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(AccentAmber.copy(alpha = 0.15f))
                .border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "⚡ $totalPoints pts",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentAmber
            )
        }
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
                thumbColor         = AccentCyan,
                activeTrackColor   = AccentCyan,
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
private fun DoneBanner(provedCount: Int, totalCount: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var locking by remember { mutableStateOf(false) }

    val alpha by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )

    val pointsEarned = (provedCount * 10) + 5

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, AccentGreen.copy(alpha = alpha), RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔋", fontSize = 48.sp)

            Text(
                text = "Return your battery!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGreen,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Head to the nearest drop-off point\nand place your battery in the bin.",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDark)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$provedCount/$totalCount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (provedCount == totalCount) AccentGreen else AccentAmber
                        )
                        Text("challenges", fontSize = 11.sp, color = TextMuted)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDark)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+$pointsEarned",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber
                        )
                        Text("⚡ points", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    scope.launch {
                        locking = true
                        try {
                            lockLocker()
                        } catch (e: Exception) {
                            println("LOCK_ERROR: ${e.message}")
                        } finally {
                            locking = false
                            onDismiss()
                        }
                    }
                },
                enabled = !locking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor   = BgDark
                )
            ) {
                if (locking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BgDark,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("✓  I placed it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}