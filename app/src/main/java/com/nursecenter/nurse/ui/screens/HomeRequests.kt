package com.nursecenter.nurse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nursecenter.nurse.data.AuthException
import com.nursecenter.nurse.data.BookingSummary
import com.nursecenter.nurse.data.HomeData
import com.nursecenter.nurse.data.HomeRepository
import com.nursecenter.nurse.data.RESPONSE_WINDOW
import com.nursecenter.nurse.data.RequestsData
import com.nursecenter.nurse.data.RequestsRepository
import com.nursecenter.nurse.data.RequestRealtime
import com.nursecenter.nurse.ui.CardList
import com.nursecenter.nurse.ui.Eyebrow
import com.nursecenter.nurse.ui.IconTile
import com.nursecenter.nurse.ui.PageTitle
import com.nursecenter.nurse.ui.PrimaryButton
import com.nursecenter.nurse.ui.SecondaryButton
import com.nursecenter.nurse.ui.SectionTitle
import com.nursecenter.nurse.ui.SoftCard
import com.nursecenter.nurse.ui.Txt
import com.nursecenter.nurse.ui.countUp
import com.nursecenter.nurse.ui.enterUp
import com.nursecenter.nurse.ui.pressable
import com.nursecenter.nurse.ui.rand
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val REQUEST_RECHECK_MS = 15_000L

@Composable
fun HomeScreen(goTo: (Screen) -> Unit) {
    var data by remember { mutableStateOf<HomeData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val incoming by RequestRealtime.changes.collectAsState()
    LaunchedEffect(reload, incoming) {
        error = null
        try {
            data = HomeRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your dashboard. Please try again."
        }
    }
    // Backup for realtime: re-check an open request so one cancelled on the website doesn't linger.
    LaunchedEffect(data) {
        if (data?.newRequest != null) {
            delay(REQUEST_RECHECK_MS)
            reload++
        }
    }

    val now = LocalDateTime.now()
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val firstName = data?.fullName?.substringBefore(' ')

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp).enterUp(0)) {
            Txt(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), 14, Color(0xFF7F8D94), weight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Txt(
                if (firstName != null) "$greeting,\n$firstName" else greeting, 28, Color(0xFF172D3B),
                weight = FontWeight.Bold, spacing = -0.03f, lineHeight = 34,
            )
        }

        val loaded = data
        when {
            error != null -> HomeMessage(
                error!!, Modifier.padding(horizontal = 20.dp).padding(top = 24.dp),
                action = "Try again", onAction = { reload++ },
            )
            loaded == null -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            else -> HomeContent(loaded, goTo, onRequestExpired = { reload++ })
        }
    }
}

@Composable
private fun HomeContent(data: HomeData, goTo: (Screen) -> Unit, onRequestExpired: () -> Unit) {
    val request = data.newRequest
    if (request != null) {
        NewRequestCard(request, onView = { goTo(Screen.Requests) }, onExpired = onRequestExpired, modifier = Modifier.enterUp(1))
    } else {
        HomeMessage(
            "No new requests right now. We'll let you know when one comes in.",
            Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).enterUp(1),
        )
    }

    Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp).enterUp(2)) {
        SectionTitle("This month", action = "Wallet", onAction = { goTo(Screen.Wallet) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                icon = Icons.Outlined.AccountBalanceWallet, tileBg = Color(0xFFE4F7F4), tint = Color(0xFF148F8E),
                value = rand(countUp(data.monthEarnings.toFloat())), label = "Total earnings",
                modifier = Modifier.weight(1f), onClick = { goTo(Screen.Wallet) },
            )
            StatTile(
                icon = Icons.Outlined.CalendarMonth, tileBg = Color(0xFFFFF1E8), tint = Color(0xFFE58A61),
                value = countUp(data.completedShifts.toFloat()).toInt().toString(), label = "Completed shifts",
                modifier = Modifier.weight(1f), onClick = { goTo(Screen.Schedule) },
            )
        }
    }

    Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 24.dp).enterUp(3)) {
        SectionTitle("Next booking", action = "View calendar", onAction = { goTo(Screen.Schedule) })
        val next = data.nextBooking
        if (next == null) {
            HomeMessage("No upcoming bookings yet.")
        } else {
            val shape = RoundedCornerShape(20.dp)
            val start = next.scheduledAt
            val end = start.plusMinutes((next.durationHours * 60).toLong())
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(shape) { goTo(Screen.Schedule) }
                    .background(Color.White, shape)
                    .border(1.dp, NC.Border, shape)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarDate(
                    start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH),
                    start.dayOfMonth.toString(),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Txt(next.serviceName, 15, Color(0xFF243743), weight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Txt(
                        listOfNotNull("${start.format(HourMinute)}–${end.format(HourMinute)}", next.location).joinToString(" · "),
                        13, Color(0xFF819097), maxLines = 1,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFFA4AFB3))
            }
        }
    }
}

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

private fun dayLabel(time: ZonedDateTime): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(time.zone), time.toLocalDate())
    return when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> time.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
    }
}

private fun hoursText(hours: Double): String =
    if (hours == 1.0) "1 hour" else hoursLabel(hours) + "s"

private fun hoursLabel(hours: Double): String =
    if (hours % 1.0 == 0.0) "${hours.toInt()} hour" else String.format(Locale.US, "%.1f hour", hours)

@Composable
private fun HomeMessage(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    SoftCard(modifier.fillMaxWidth()) {
        Txt(text, 14, NC.Muted, weight = FontWeight.Medium, lineHeight = 20)
        if (action != null) {
            Txt(
                action, 13, NC.TealText,
                Modifier.padding(top = 10.dp).pressable(RoundedCornerShape(8.dp), onClick = onAction).padding(4.dp),
                weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NewRequestCard(request: BookingSummary, onView: () -> Unit, onExpired: () -> Unit, modifier: Modifier = Modifier) {
    val secondsLeft = rememberSecondsLeft(request.expiresAt, onExpired)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)), label = "ring")
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color(0x2E12424A), spotColor = Color(0x4012424A))
            .clip(shape)
            .background(NC.DeepTeal)
    ) {
        Box {
            // Soft decorative halo in the corner of the card.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp)
                    .size(160.dp)
                    .graphicsLayer { translationX = 60.dp.toPx(); translationY = (-70).dp.toPx() }
                    .background(Color(0xFF44C7B5).copy(alpha = 0.10f), CircleShape)
            )
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .graphicsLayer {
                                            scaleX = 1f + ring * 1.6f; scaleY = 1f + ring * 1.6f; alpha = 1f - ring
                                        }
                                        .background(Color(0xFF78E1BD), CircleShape)
                                )
                                Box(Modifier.size(8.dp).background(Color(0xFF78E1BD), CircleShape))
                            }
                            Spacer(Modifier.width(8.dp))
                            Txt("NEW REQUEST", 11, Color.White.copy(alpha = 0.65f), weight = FontWeight.Bold, spacing = 0.12f)
                        }
                        Spacer(Modifier.height(12.dp))
                        Txt(request.serviceName, 20, Color.White, weight = FontWeight.Bold)
                        request.patientName?.let {
                            Spacer(Modifier.height(2.dp))
                            Txt(it, 14, Color.White.copy(alpha = 0.7f), weight = FontWeight.Medium)
                        }
                    }
                    CountdownPill(secondsLeft, onDark = true)
                }
                Spacer(Modifier.height(16.dp))
                InfoLine(
                    Icons.Outlined.Schedule,
                    "${dayLabel(request.scheduledAt)}, ${request.scheduledAt.format(HourMinute)} · ${hoursLabel(request.durationHours)} shift",
                )
                request.location?.let {
                    Spacer(Modifier.height(8.dp))
                    InfoLine(Icons.Outlined.LocationOn, it)
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Txt("Estimated earnings", 12, Color.White.copy(alpha = 0.55f))
                        Spacer(Modifier.height(2.dp))
                        Txt(rand(countUp(request.payout.toFloat()), cents = true), 22, Color.White, weight = FontWeight.Bold)
                    }
                    Txt(
                        "View request", 13, Color(0xFF12363B),
                        Modifier
                            .pressable(RoundedCornerShape(12.dp), onClick = onView)
                            .background(NC.Mint, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        weight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Txt(text, 14, Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun StatTile(
    icon: ImageVector, tileBg: Color, tint: Color, value: String, label: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    SoftCard(modifier, onClick = onClick) {
        IconTile(icon, tileBg, tint, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.height(12.dp))
        Txt(value, 21, Color(0xFF1E3340), weight = FontWeight.Bold, spacing = -0.01f)
        Spacer(Modifier.height(4.dp))
        Txt(label, 12, NC.Muted, weight = FontWeight.Medium)
    }
}

@Composable
fun CalendarDate(month: String, day: String) {
    Column(
        Modifier.width(54.dp).height(58.dp).background(NC.CoralSoft, RoundedCornerShape(15.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Txt(month, 9, Color(0xFFD97972), weight = FontWeight.ExtraBold, spacing = 0.12f)
        Txt(day, 20, Color(0xFFD97972), weight = FontWeight.Bold)
    }
}

private data class Decision(val accepted: Boolean, val serviceName: String)

@Composable
fun RequestsScreen() {
    var data by remember { mutableStateOf<RequestsData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var decision by remember { mutableStateOf<Decision?>(null) }
    val incoming by RequestRealtime.changes.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload, incoming) {
        error = null
        try {
            data = RequestsRepository.load()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your requests. Please try again."
        }
    }
    // Backup for realtime: re-check open requests so ones cancelled on the website don't linger.
    LaunchedEffect(data) {
        if (data?.pending?.isNotEmpty() == true) {
            delay(REQUEST_RECHECK_MS)
            if (busyId == null) reload++
        }
    }

    fun respond(booking: BookingSummary, accept: Boolean) {
        if (busyId != null) return
        busyId = booking.id
        actionError = null
        scope.launch {
            try {
                if (accept) RequestsRepository.accept(booking) else RequestsRepository.decline(booking)
                decision = Decision(accept, booking.serviceName)
            } catch (e: AuthException) {
                actionError = booking.id to (e.message ?: "Something went wrong.")
            } catch (e: Exception) {
                actionError = booking.id to "Something went wrong. Please try again."
            } finally {
                busyId = null
                reload++
            }
        }
    }

    val pending = data?.pending.orEmpty()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)
    ) {
        Column(Modifier.enterUp(0)) {
            Eyebrow("Incoming work")
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                PageTitle("Requests", Modifier.weight(1f))
                if (pending.isNotEmpty()) {
                    Txt(
                        "${pending.size} new", 12, Color(0xFF18815F),
                        Modifier.padding(bottom = 6.dp).background(Color(0xFFE6F8F3), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        weight = FontWeight.Bold,
                    )
                }
            }
        }

        val loaded = data
        when {
            error != null && loaded == null -> HomeMessage(
                error!!, Modifier.padding(top = 20.dp), action = "Try again", onAction = { reload++ },
            )
            loaded == null -> Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            else -> {
                AnimatedContent(
                    targetState = decision,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.94f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f))
                    },
                    label = "decision",
                    modifier = Modifier.enterUp(1),
                ) { result ->
                    if (result != null) {
                        DecisionResult(result, onDone = { decision = null })
                    } else if (loaded.pending.isEmpty()) {
                        HomeMessage(
                            "No new requests right now. We'll alert you as soon as a client books.",
                            Modifier.padding(top = 20.dp),
                        )
                    } else {
                        Column {
                            loaded.pending.forEach { booking ->
                                PendingRequestCard(
                                    booking = booking,
                                    busy = busyId == booking.id,
                                    error = actionError?.takeIf { it.first == booking.id }?.second,
                                    onDecline = { respond(booking, accept = false) },
                                    onAccept = { respond(booking, accept = true) },
                                    onExpired = { reload++ },
                                )
                            }
                        }
                    }
                }

                Column(Modifier.padding(top = 32.dp, bottom = 32.dp).enterUp(2)) {
                    SectionTitle("Recent requests")
                    if (loaded.recent.isEmpty()) {
                        HomeMessage("No past requests yet.")
                    } else {
                        CardList(loaded.recent.map { booking -> { HistoryRow(booking) } })
                    }
                }
            }
        }
    }
}

/** Seconds until [expiresAt], ticking every second; calls [onExpired] once when it reaches zero. */
@Composable
private fun rememberSecondsLeft(expiresAt: ZonedDateTime, onExpired: () -> Unit): Long {
    fun left() = ChronoUnit.SECONDS.between(ZonedDateTime.now(expiresAt.zone), expiresAt).coerceAtLeast(0)
    var seconds by remember(expiresAt) { mutableLongStateOf(left()) }
    val expired by rememberUpdatedState(onExpired)
    LaunchedEffect(expiresAt) {
        while (seconds > 0) {
            delay(1_000)
            seconds = left()
        }
        expired()
    }
    return seconds
}

@Composable
private fun CountdownPill(seconds: Long, onDark: Boolean, modifier: Modifier = Modifier) {
    val urgent = seconds < 60
    val color = when {
        onDark && urgent -> Color(0xFFFFA48F)
        onDark -> Color.White
        urgent -> NC.Danger
        else -> Color(0xFF148C7A)
    }
    Row(
        modifier
            .background(if (onDark) Color.White.copy(alpha = 0.12f) else Color.White, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Timer, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Txt(String.format(Locale.US, "%d:%02d left", seconds / 60, seconds % 60), 12, color, weight = FontWeight.Bold)
    }
}

@Composable
private fun PendingRequestCard(
    booking: BookingSummary,
    busy: Boolean,
    error: String?,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    onExpired: () -> Unit,
) {
    val secondsLeft = rememberSecondsLeft(booking.expiresAt, onExpired)
    val shape = RoundedCornerShape(24.dp)
    val start = booking.scheduledAt
    val end = start.plusMinutes((booking.durationHours * 60).toLong())
    Column(
        Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = Color(0x141E4246), spotColor = Color(0x221E4246))
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFDCE8E7), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE9F8F5)).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF2FC4A2), CircleShape))
            Spacer(Modifier.width(8.dp))
            Txt("NEW REQUEST", 11, Color(0xFF148C7A), Modifier.weight(1f), weight = FontWeight.Bold, spacing = 0.06f)
            CountdownPill(secondsLeft, onDark = false)
        }
        // Drains as the response window runs out.
        val fraction = (secondsLeft.toFloat() / RESPONSE_WINDOW.seconds).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFFD5EEE9))) {
            Box(
                Modifier.fillMaxWidth(fraction).height(3.dp)
                    .background(if (secondsLeft < 60) NC.Danger else Color(0xFF2FC4A2))
            )
        }
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Txt(booking.serviceName, 13, Color(0xFF819097), weight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Txt(booking.patientName ?: "Client", 20, Color(0xFF213642), weight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Txt(rand(booking.payout.toFloat()), 21, Color(0xFF213642), weight = FontWeight.Bold)
                    Txt("your earnings", 12, Color(0xFF87959B))
                }
            }
            Column(
                Modifier.padding(top = 20.dp).fillMaxWidth().background(Color(0xFFF7F9F9), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailLine(Icons.Outlined.CalendarMonth, "${dayLabel(start)}, ${start.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))}")
                DetailLine(
                    Icons.Outlined.Schedule,
                    "${start.format(HourMinute)}–${end.format(HourMinute)} · ${hoursText(booking.durationHours)}",
                )
                booking.location?.let { DetailLine(Icons.Outlined.LocationOn, it) }
            }
            booking.notes?.let {
                Txt(it, 13, Color(0xFF687A83), Modifier.padding(top = 20.dp), lineHeight = 20)
            }
            error?.let {
                Txt(it, 13, NC.Danger, Modifier.padding(top = 16.dp), weight = FontWeight.SemiBold)
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Decline", Icons.Rounded.Close, { if (!busy) onDecline() }, Modifier.weight(1f))
                PrimaryButton(
                    "Accept request", { if (!busy && secondsLeft > 0) onAccept() }, Modifier.weight(1.7f), height = 50.dp,
                    leading = Icons.Rounded.Check, loading = busy,
                )
            }
        }
    }
}

@Composable
private fun DecisionResult(decision: Decision, onDone: () -> Unit) {
    val accepted = decision.accepted
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.dp, Color(0xFFDFE8E8), shape)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .size(56.dp)
                .background(if (accepted) NC.SuccessSoft else Color(0xFFF3F5F5), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (accepted) Icons.Rounded.Check else Icons.Outlined.Inbox, null,
                tint = if (accepted) Color(0xFF1A9D75) else Color(0xFF809097), modifier = Modifier.size(26.dp),
            )
        }
        Txt(
            if (accepted) "Booking accepted" else "Request declined", 18, Color(0xFF243743),
            Modifier.padding(top = 16.dp), weight = FontWeight.Bold,
        )
        Txt(
            if (accepted) "${decision.serviceName} has been added to your bookings." else "We will let you know when another suitable request arrives.",
            13, Color(0xFF849198), Modifier.padding(top = 8.dp).widthIn(max = 250.dp), lineHeight = 20, align = TextAlign.Center,
        )
        Txt(
            "Done", 13, Color(0xFF168E8D),
            Modifier.padding(top = 16.dp).pressable(RoundedCornerShape(8.dp), onClick = onDone).padding(6.dp),
            weight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF249C9B), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Txt(text, 13, NC.Body, weight = FontWeight.Medium)
    }
}

private fun statusStyle(status: String): Pair<String, Color> = when (status) {
    "completed" -> "Completed" to NC.Success
    "accepted" -> "Accepted" to Color(0xFF168E8D)
    "in_progress" -> "In progress" to Color(0xFFE58A61)
    "cancelled" -> "Cancelled" to Color(0xFF8B989E)
    else -> status.replaceFirstChar { it.uppercase() } to Color(0xFF8B989E)
}

@Composable
private fun HistoryRow(booking: BookingSummary) {
    val (label, color) = statusStyle(booking.status)
    val title = listOfNotNull(booking.serviceName, booking.location).joinToString(" · ")
    val date = "${booking.scheduledAt.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} · ${hoursText(booking.durationHours)}"
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, 14, Color(0xFF2C3E49), weight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Txt(date, 12, Color(0xFF8B989E))
        }
        Column(horizontalAlignment = Alignment.End) {
            Txt(rand(booking.payout.toFloat()), 14, Color(0xFF273A46), weight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Txt(label, 11, color, weight = FontWeight.Bold)
        }
    }
}
