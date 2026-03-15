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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

// ── Shared palette ────────────────────────────────────────────
private val BlueAccent  = Color(0xFF43BBF7)
private val BlueSoft    = Color(0xFFE8F6FD)
private val GreenAccent = Color(0xFF26C97A)
private val GreenSoft   = Color(0xFFE8F8F1)
private val AmberAccent = Color(0xFFFFB830)
private val AmberSoft   = Color(0xFFFFF3E0)
private val RedAccent   = Color(0xFFE53935)
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecond  = Color(0xFF888888)
private val CardBg      = Color(0xFFFFFFFF)
private val PageBg      = Color(0xFFF4F6F8)

private enum class TimerState { IDLE, RUNNING, PAUSED, DONE }

private data class EcoEvent(
    val id: Long,
    val text: String,
    val proved: Boolean = false
)

// ── Sync points FROM Supabase into AppState ───────────────────
suspend fun syncPointsFromDb() {
    try {
        val email = AppState.currentUser.value?.email ?: return
        val profile = supabase.postgrest["profiles"]
            .select { filter { eq("email", email) } }
            .decodeSingle<Profile>()
        AppState.totalPoints.value = (profile.points ?: 0).toInt()
        AppState.currentMultiplier.value = profile.pointsMultiplier
        println("POINTS_SYNCED: ${AppState.totalPoints.value}, MULTIPLIER: ${AppState.currentMultiplier.value}")
    } catch (e: Exception) {
        println("POINTS_SYNC_ERROR: ${e.message}")
    }
}

// ── Push updated points TO Supabase, then re-sync to confirm ─
private suspend fun pushPointsToDb(newPoints: Int) {
    try {
        val email = AppState.currentUser.value?.email ?: return

        // 1. Write new value
        supabase.postgrest["profiles"]
            .update({ set("points", newPoints) }) {
                filter { eq("email", email) }
            }
        println("POINTS_UPDATED_DB: $newPoints")

        // 2. Re-read from DB so AppState reflects the true persisted value
        val confirmed = supabase.postgrest["profiles"]
            .select { filter { eq("email", email) } }
            .decodeSingle<Profile>()
        AppState.totalPoints.value = (confirmed.points ?: 0).toInt()
        println("POINTS_CONFIRMED_DB: ${AppState.totalPoints.value}")

    } catch (e: Exception) {
        println("POINTS_UPDATE_ERROR: ${e.message}")
    }
}

@Composable
fun TimerScreen() {
    var totalSeconds   by remember { mutableStateOf(60) }
    var secondsLeft    by remember { mutableStateOf(totalSeconds) }
    var state          by remember { mutableStateOf(TimerState.IDLE) }
    var provingEvent   by remember { mutableStateOf<EcoEvent?>(null) }
    var runningSeconds by remember { mutableStateOf(0) }
    val totalPoints    by AppState.totalPoints
    val multiplier     by AppState.currentMultiplier

    val scope  = rememberCoroutineScope()
    val events = remember { mutableStateListOf<EcoEvent>() }

    LaunchedEffect(Unit) { syncPointsFromDb() }

    LaunchedEffect(state) {
        if (state == TimerState.RUNNING) {
            while (secondsLeft > 0 && state == TimerState.RUNNING) {
                delay(1_000L)
                secondsLeft--
            }
            if (secondsLeft == 0) state = TimerState.DONE
        }
    }

    LaunchedEffect(state) {
        if (state == TimerState.RUNNING) {
            if (runningSeconds == 0) {
                try {
                    val response = getEvent()
                    events.add(EcoEvent(id = Clock.System.now().toEpochMilliseconds(), text = response.event))
                } catch (e: Exception) { println("EVENT_ERROR: ${e.message}") }
            }
            while (state == TimerState.RUNNING) {
                delay(1_000L)
                runningSeconds++
                if (runningSeconds % 60 == 0) {
                    try {
                        val response = getEvent()
                        events.add(EcoEvent(id = Clock.System.now().toEpochMilliseconds(), text = response.event))
                    } catch (e: Exception) { println("EVENT_ERROR: ${e.message}") }
                }
            }
        }
    }

    val progress = if (totalSeconds > 0) secondsLeft / totalSeconds.toFloat() else 0f

    val barColor by animateColorAsState(
        targetValue = when {
            progress > 0.50f -> BlueAccent
            progress > 0.25f -> AmberAccent
            else             -> RedAccent
        },
        animationSpec = tween(600),
        label = "barColor"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Header ───────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(top = 52.dp, start = 20.dp, end = 20.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "VOLTLOOP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 5.sp,
                            color = TextSecond
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Ride Timer",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = AmberSoft
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$totalPoints pts",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                        }
                    }
                }
            }
        }

        // ── Clock ────────────────────────────────────────────
        item {
            Spacer(Modifier.height(28.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ClockDisplay(secondsLeft = secondsLeft, state = state, accentColor = barColor)
            }
            Spacer(Modifier.height(28.dp))
        }

        // ── Progress bar ─────────────────────────────────────
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                shadowElevation = 3.dp
            ) {
                TimerProgressBar(progress = progress, color = barColor)
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Duration adjuster (IDLE only) ────────────────────
        if (state == TimerState.IDLE) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = CardBg,
                    shadowElevation = 3.dp
                ) {
                    DurationAdjuster(
                        totalSeconds = totalSeconds,
                        onValueChange = { newVal ->
                            totalSeconds = newVal
                            secondsLeft  = newVal
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Control buttons ───────────────────────────────────
        item {
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
            Spacer(Modifier.height(16.dp))
        }

        // ── Done banner ───────────────────────────────────────
        if (state == TimerState.DONE) {
            item {
                val provedCount = events.count { it.proved }
                DoneBanner(
                    provedCount = provedCount,
                    totalCount  = events.size,
                    onDismiss   = { earned ->
                        scope.launch {
                            // 1. Optimistically update UI right away
                            val newPoints = AppState.totalPoints.value + earned
                            AppState.totalPoints.value = newPoints

                            // 2. Persist to DB and re-read the confirmed value
                            pushPointsToDb(newPoints)

                            // 3. Reset timer state AFTER DB confirmed
                            state          = TimerState.IDLE
                            secondsLeft    = totalSeconds
                            runningSeconds = 0
                            events.clear()
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Eco challenges ────────────────────────────────────
        if (events.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ECO CHALLENGES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextSecond
                    )
                    if (state == TimerState.DONE) {
                        Surface(shape = RoundedCornerShape(50.dp), color = Color(0xFFFFEBEE)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null,
                                    tint = RedAccent, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Locked", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedAccent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            items(events, key = { it.id }) { event ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                    EventCard(
                        event    = event,
                        onProve  = { if (state != TimerState.DONE) provingEvent = event },
                        isLocked = state == TimerState.DONE
                    )
                }
            }
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

// ── Clock display ─────────────────────────────────────────────
@Composable
private fun ClockDisplay(secondsLeft: Int, state: TimerState, accentColor: Color) {
    val minutes = secondsLeft / 60
    val secs    = secondsLeft % 60
    val timeStr = "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    val scale by animateFloatAsState(
        targetValue = if (state == TimerState.RUNNING) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(210.dp)
            .shadow(16.dp, CircleShape,
                ambientColor = accentColor.copy(alpha = 0.25f),
                spotColor    = accentColor.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(CardBg)
            .border(3.dp, accentColor.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(BlueSoft.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text          = timeStr,
                    fontSize      = (48 * scale).sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = TextPrimary,
                    textAlign     = TextAlign.Center,
                    letterSpacing = 2.sp
                )
                Text(
                    text = when (state) {
                        TimerState.IDLE    -> "ready"
                        TimerState.RUNNING -> "running"
                        TimerState.PAUSED  -> "paused"
                        TimerState.DONE    -> "done!"
                    },
                    fontSize      = 12.sp,
                    fontWeight    = FontWeight.SemiBold,
                    color         = accentColor,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// ── Progress bar ──────────────────────────────────────────────
@Composable
private fun TimerProgressBar(progress: Float, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label         = "progress"
    )

    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("REMAINING", fontSize = 11.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold, color = TextSecond)
            Text(
                "${(animatedProgress * 100).toInt()}%",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(BlueSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
            )
        }
    }
}

// ── Duration adjuster ─────────────────────────────────────────
@Composable
private fun DurationAdjuster(totalSeconds: Int, onValueChange: (Int) -> Unit) {
    val presets = listOf(30, 60, 120, 300, 600)

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SET DURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp, color = TextSecond)

        val label = when {
            totalSeconds < 60      -> "${totalSeconds}s"
            totalSeconds % 60 == 0 -> "${totalSeconds / 60}m"
            else                   -> "${totalSeconds / 60}m ${totalSeconds % 60}s"
        }
        Text(
            text       = label,
            fontSize   = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = BlueAccent,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.Center
        )

        Slider(
            value         = totalSeconds.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange    = 5f..600f,
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor         = BlueAccent,
                activeTrackColor   = BlueAccent,
                inactiveTrackColor = BlueSoft
            )
        )

        Text("QUICK PICK", fontSize = 11.sp, letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold, color = TextSecond)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val isSelected  = preset == totalSeconds
                val presetLabel = if (preset < 60) "${preset}s" else "${preset / 60}m"
                Surface(
                    onClick   = { onValueChange(preset) },
                    modifier  = Modifier.weight(1f),
                    shape     = RoundedCornerShape(10.dp),
                    color     = if (isSelected) BlueSoft else PageBg,
                    border    = androidx.compose.foundation.BorderStroke(
                        1.5.dp, if (isSelected) BlueAccent else Color.Transparent
                    )
                ) {
                    Text(
                        text       = presetLabel,
                        fontSize   = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color      = if (isSelected) BlueAccent else TextSecond,
                        modifier   = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Control buttons ───────────────────────────────────────────
@Composable
private fun ControlButtons(
    state: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick  = onReset,
            modifier = Modifier.weight(1f).height(54.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextSecond),
            border   = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDDE3EA))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset", fontWeight = FontWeight.SemiBold)
        }

        Button(
            onClick  = if (state == TimerState.RUNNING) onPause else onStart,
            modifier = Modifier.weight(2f).height(54.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (state == TimerState.RUNNING) AmberAccent else BlueAccent,
                contentColor   = Color.White
            )
        ) {
            Icon(
                imageVector = if (state == TimerState.RUNNING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (state) {
                    TimerState.RUNNING -> "Pause"
                    TimerState.PAUSED  -> "Resume"
                    TimerState.DONE    -> "Done"
                    TimerState.IDLE    -> "Start"
                },
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp
            )
        }
    }
}

// ── Done banner ───────────────────────────────────────────────
// onDismiss now passes back the earned points so the caller owns the DB write
@Composable
private fun DoneBanner(
    provedCount: Int,
    totalCount: Int,
    onDismiss: (earned: Int) -> Unit        // ← passes earned points up
) {
    val scope        = rememberCoroutineScope()
    var locking      by remember { mutableStateOf(false) }
    val basePoints   = (provedCount * 10) + 5
    val multiplier   = AppState.currentMultiplier.value
    val pointsEarned = basePoints * multiplier

    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape           = RoundedCornerShape(24.dp),
        color           = CardBg,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(10.dp, CircleShape,
                        ambientColor = GreenAccent.copy(alpha = 0.3f),
                        spotColor    = GreenAccent.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(GreenSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint               = GreenAccent,
                    modifier           = Modifier.size(32.dp)
                )
            }

            Text(
                "Return your battery!",
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary,
                textAlign  = TextAlign.Center
            )
            Text(
                "Head to the nearest drop-off point\nand place your battery in the bin.",
                fontSize   = 14.sp,
                color      = TextSecond,
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(16.dp),
                    color    = if (provedCount == totalCount) GreenSoft else AmberSoft
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$provedCount/$totalCount",
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = if (provedCount == totalCount) GreenAccent else AmberAccent
                        )
                        Text("challenges", fontSize = 12.sp, color = TextSecond)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(16.dp),
                    color    = AmberSoft
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null,
                                tint = AmberAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "+$pointsEarned",
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = AmberAccent
                            )
                        }
                        if (multiplier > 1) {
                            Text("x$multiplier multiplier!", fontSize = 10.sp, color = AmberAccent, fontWeight = FontWeight.Bold)
                        } else {
                            Text("points earned", fontSize = 12.sp, color = TextSecond)
                        }
                    }
                }
            }

            // CTA — lock locker, then hand earned pts back to parent for DB write
            Button(
                onClick  = {
                    scope.launch {
                        locking = true
                        try {
                            lockLocker()                // lock the physical locker
                        } catch (e: Exception) {
                            println("LOCK_ERROR: ${e.message}")
                        } finally {
                            locking = false
                            onDismiss(pointsEarned)     // ← parent writes + re-syncs DB
                        }
                    }
                },
                enabled  = !locking,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    contentColor   = Color.White
                )
            ) {
                if (locking) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("I placed it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── Event card ────────────────────────────────────────────────
@Composable
private fun EventCard(event: EcoEvent, onProve: () -> Unit, isLocked: Boolean) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(16.dp),
        color           = CardBg,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            event.proved -> GreenAccent
                            isLocked     -> RedAccent.copy(alpha = 0.4f)
                            else         -> BlueAccent
                        }
                    )
            )

            Text(
                text       = event.text,
                color      = if (event.proved || isLocked) TextSecond else TextPrimary,
                fontSize   = 13.sp,
                lineHeight = 18.sp,
                modifier   = Modifier.weight(1f)
            )

            if (event.proved) {
                Surface(shape = RoundedCornerShape(50.dp), color = GreenSoft) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                            tint = GreenAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                    }
                }
            } else {
                Button(
                    onClick  = onProve,
                    enabled  = !isLocked,
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = BlueAccent,
                        contentColor           = Color.White,
                        disabledContainerColor = RedAccent.copy(alpha = 0.1f),
                        disabledContentColor   = TextSecond
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (isLocked) {
                        Icon(Icons.Filled.Lock, contentDescription = null,
                            modifier = Modifier.size(14.dp))
                    } else {
                        Text("Prove it", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}