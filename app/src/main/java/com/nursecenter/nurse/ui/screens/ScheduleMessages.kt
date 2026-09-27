package com.nursecenter.nurse.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import android.widget.Toast
import com.nursecenter.nurse.data.AuthException
import com.nursecenter.nurse.data.AvailabilityRepository
import com.nursecenter.nurse.data.BookingSummary
import com.nursecenter.nurse.data.ChatAlert
import com.nursecenter.nurse.data.ChatMessage
import com.nursecenter.nurse.data.ChatRepository
import com.nursecenter.nurse.data.ChatThread
import com.nursecenter.nurse.data.NurseStatus
import com.nursecenter.nurse.data.RequestRealtime
import com.nursecenter.nurse.data.ScheduleRepository
import com.nursecenter.nurse.data.SUPPORT_CHAT_ID
import com.nursecenter.nurse.data.SUPPORT_PHONE
import com.nursecenter.nurse.data.ScheduleWeek
import com.nursecenter.nurse.data.Shift
import com.nursecenter.nurse.data.SupportRepository
import com.nursecenter.nurse.data.SupportSummary
import com.nursecenter.nurse.data.WeeklyAvailability
import com.nursecenter.nurse.ui.Eyebrow
import com.nursecenter.nurse.ui.IconBtn
import com.nursecenter.nurse.ui.NcSheet
import com.nursecenter.nurse.ui.PageTitle
import com.nursecenter.nurse.ui.PrimaryButton
import com.nursecenter.nurse.ui.SectionTitle
import com.nursecenter.nurse.ui.Txt
import com.nursecenter.nurse.ui.enterUp
import com.nursecenter.nurse.ui.pressable
import com.nursecenter.nurse.ui.rememberComingSoon
import com.nursecenter.nurse.ui.theme.Jakarta
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ScheduleScreen() {
    val today = LocalDate.now()
    // Saved as epoch days so the chosen week and day survive rotation and tab switches.
    var weekStartDay by rememberSaveable { mutableLongStateOf(today.with(DayOfWeek.MONDAY).toEpochDay()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val weekStart = LocalDate.ofEpochDay(weekStartDay)
    val days = (0L until 7L).map { weekStart.plusDays(it) }
    val selected = LocalDate.ofEpochDay(selectedDay)

    var data by remember { mutableStateOf<ScheduleWeek?>(null) }
    var loadedWeek by remember { mutableStateOf<LocalDate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val changes by RequestRealtime.changes.collectAsState()
    var availabilityOpen by rememberSaveable { mutableStateOf(false) }

    // Also reloads when Online / Offline changes, so the availability note stays current.
    val online by NurseStatus.online.collectAsState()
    LaunchedEffect(weekStart, reload, changes, online) {
        error = null
        try {
            data = ScheduleRepository.loadWeek(weekStart)
            loadedWeek = weekStart
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your calendar. Please try again."
        }
    }

    fun moveWeek(weeks: Long) {
        val start = weekStart.plusWeeks(weeks)
        weekStartDay = start.toEpochDay()
        // Keep the same weekday selected, or today when landing on the current week.
        selectedDay = (if (!today.isBefore(start) && today.isBefore(start.plusDays(7))) today else selected.plusWeeks(weeks)).toEpochDay()
    }

    // Only trust bookings that belong to the week on screen, so switching weeks never flashes the old week's shifts.
    val weekData = data?.takeIf { loadedWeek == weekStart }
    val byDay = weekData?.bookings.orEmpty().groupBy { it.scheduledAt.toLocalDate() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)
    ) {
        Column(Modifier.enterUp(0)) {
            Eyebrow("Your time")
            PageTitle("Calendar", Modifier.padding(top = 4.dp))
        }

        val cardShape = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .enterUp(1)
                .padding(top = 20.dp)
                .fillMaxWidth()
                .shadow(10.dp, cardShape, ambientColor = Color(0x12214245), spotColor = Color(0x1A214245))
                .background(Color.White, cardShape)
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { moveWeek(-1) }
                Txt(weekLabel(days.first(), days.last()), 15, Color(0xFF2B3E48), Modifier.weight(1f), weight = FontWeight.Bold, align = TextAlign.Center)
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { moveWeek(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { d ->
                    DayChip(
                        d, isSelected = d == selected, isToday = d == today, hasBookings = byDay.containsKey(d), Modifier.weight(1f),
                    ) { selectedDay = d.toEpochDay() }
                }
            }
            if (selected != today) {
                Txt(
                    "Back to today", 12, NC.TealText,
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                        .pressable(RoundedCornerShape(8.dp)) {
                            weekStartDay = today.with(DayOfWeek.MONDAY).toEpochDay()
                            selectedDay = today.toEpochDay()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    weight = FontWeight.Bold,
                )
            }
        }

        Column(Modifier.enterUp(2).padding(top = 28.dp, bottom = 32.dp)) {
            SectionTitle(
                selected.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)),
                action = "Add availability", onAction = { availabilityOpen = true },
            )
            when {
                error != null && weekData == null -> ScheduleMessage(error!!, action = "Try again", onAction = { reload++ })
                weekData == null -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                else -> AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { if (forward) it / 6 else -it / 6 }) togetherWith
                            (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { if (forward) -it / 8 else it / 8 })
                    },
                    label = "day",
                ) { date ->
                    val items = byDay[date].orEmpty()
                    if (items.isEmpty()) {
                        EmptyDay()
                    } else {
                        Column(
                            Modifier
                                .padding(start = 8.dp)
                                .drawBehind {
                                    drawLine(Color(0xFFDBE4E4), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                                }
                                .padding(start = 24.dp)
                        ) {
                            items.forEachIndexed { i, b -> TimelineItem(b, Modifier.enterUp(i)) }
                        }
                    }
                }
            }
            weekData?.available?.let { AvailabilityNote(it) }
        }
    }
    if (availabilityOpen) AvailabilitySheet(onDismiss = { availabilityOpen = false })
}

@Composable
private fun AvailabilitySheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // null until the saved availability has loaded, so a late load can't overwrite the nurse's taps.
    var days by remember { mutableStateOf<Set<DayOfWeek>?>(null) }
    var shift by remember { mutableStateOf(Shift.FullDay) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val existing = runCatching { AvailabilityRepository.load() }.getOrNull()
        shift = existing?.shift ?: Shift.FullDay
        days = existing?.days ?: DayOfWeek.entries.filter { it <= DayOfWeek.FRIDAY }.toSet()
    }

    NcSheet(onDismiss) {
        AnimatedContent(targetState = saved, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "availability") { done ->
            if (done) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).background(Color(0xFFE7F7F2), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.EventAvailable, null, tint = Color(0xFF209A77), modifier = Modifier.size(28.dp))
                    }
                    Txt("Availability updated", 18, Color(0xFF263944), Modifier.padding(top = 16.dp), weight = FontWeight.Bold)
                    Txt(
                        "Your available days and preferred shift have been saved.", 12, Color(0xFF7F8E94),
                        Modifier.padding(top = 8.dp), align = TextAlign.Center, lineHeight = 19,
                    )
                    PrimaryButton("Done", onDismiss, Modifier.padding(top = 24.dp).fillMaxWidth())
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Eyebrow("Work preferences")
                    Txt("Add availability", 21, Color(0xFF253844), Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
                    Txt("Choose when you are available to receive new bookings.", 12, Color(0xFF7D8C92), Modifier.padding(top = 8.dp), lineHeight = 18)
                    val selected = days
                    if (selected == null) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Txt("Available days", 12, Color(0xFF53666F), Modifier.padding(top = 20.dp, bottom = 8.dp), weight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                val on = day in selected
                                val bg by animateColorAsState(if (on) Color(0xFF1DA5A3) else Color(0xFFF0F4F3), label = "availDay")
                                Txt(
                                    day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), 10,
                                    if (on) Color.White else Color(0xFF718188),
                                    Modifier.weight(1f).pressable(RoundedCornerShape(12.dp), pressedScale = 0.92f) {
                                        days = if (on) selected - day else selected + day
                                    }.background(bg, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
                                    weight = FontWeight.Bold, align = TextAlign.Center,
                                )
                            }
                        }
                        Txt("Preferred shift", 12, Color(0xFF53666F), Modifier.padding(top = 20.dp, bottom = 8.dp), weight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Shift.entries.forEach { option -> ShiftOption(option, option == shift) { shift = option } }
                        }
                        PrimaryButton(
                            "Save availability",
                            onClick = {
                                if (selected.isEmpty() || saving) return@PrimaryButton
                                saving = true
                                scope.launch {
                                    try {
                                        AvailabilityRepository.save(WeeklyAvailability(selected, shift))
                                        saved = true
                                    } catch (e: Exception) {
                                        val reason = (e as? AuthException)?.message ?: "Couldn't save your availability. Please try again."
                                        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 20.dp).fillMaxWidth().graphicsLayer { alpha = if (selected.isEmpty()) 0.4f else 1f },
                            loading = saving,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftOption(shift: Shift, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val border by animateColorAsState(if (selected) Color(0xFF4ABDB5) else Color(0xFFE3E9E9), label = "shiftBorder")
    val bg by animateColorAsState(if (selected) Color(0xFFEEF9F7) else Color.White, label = "shiftBg")
    Row(
        Modifier.fillMaxWidth().pressable(shape, pressedScale = 0.98f, onClick = onClick).background(bg, shape).border(1.dp, border, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp)
                .then(if (selected) Modifier.background(Color(0xFF1AA19D), CircleShape) else Modifier.border(1.dp, Color(0xFFC8D1D3), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Txt(shift.label, 12, Color(0xFF344750), Modifier.weight(1f).padding(start = 12.dp), weight = FontWeight.Bold)
        Txt(shift.hours, 11, Color(0xFF8B989D))
    }
}

private fun weekLabel(first: LocalDate, last: LocalDate): String {
    val month = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    return when {
        first.month == last.month -> first.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        first.year == last.year -> "${first.format(month)} – ${last.format(month)} ${last.year}"
        else -> "${first.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))} – " +
            last.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    }
}

@Composable
private fun AvailabilityNote(available: Boolean) {
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth()
            .background(if (available) Color(0xFFEEF8F6) else Color(0xFFF3F5F5), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (available) Icons.Outlined.VerifiedUser else Icons.Outlined.EventBusy, null,
            tint = if (available) Color(0xFF1C9891) else Color(0xFF8B989E), modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (available) Color(0xFF274B49) else Color(0xFF3D4D55), fontWeight = FontWeight.Bold)) {
                    append(if (available) "You are available\n" else "You are unavailable\n")
                }
                append(if (available) "New requests can reach you." else "You won't receive new requests until you're available again.")
            },
            color = Color(0xFF58716F), fontSize = 12.sp, lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ScheduleMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape)
            .padding(20.dp)
    ) {
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
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).pressable(CircleShape, pressedScale = 0.88f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color(0xFF7F8F96), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DayChip(day: LocalDate, isSelected: Boolean, isToday: Boolean, hasBookings: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (isSelected) Color(0xFF1EAAA8) else Color.Transparent, tween(250), label = "dayBg")
    val fg by animateColorAsState(
        when {
            isSelected -> Color.White
            isToday -> Color(0xFF1EAAA8)
            else -> Color(0xFF50636C)
        },
        tween(250), label = "dayFg",
    )
    val lift by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "dayLift")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .graphicsLayer { translationY = -lift * 3.dp.toPx() }
            .shadow((lift * 6).dp, shape, spotColor = Color(0x551EAAA8))
            .pressable(shape, pressedScale = 0.92f, onClick = onClick)
            .background(bg, shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(
            day.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH), 9,
            if (isSelected) Color.White.copy(alpha = 0.7f) else Color(0xFFA2ADB1), weight = FontWeight.Bold,
        )
        Txt(day.dayOfMonth.toString(), 14, fg, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .background(
                    when {
                        !hasBookings -> Color.Transparent
                        isSelected -> Color.White
                        else -> NC.Coral
                    },
                    CircleShape,
                )
        )
    }
}

@Composable
private fun TimelineItem(booking: BookingSummary, modifier: Modifier = Modifier) {
    val start = booking.scheduledAt
    val end = start.plusMinutes((booking.durationHours * 60).toLong())
    val (statusLabel, dot) = when (booking.status) {
        "in_progress" -> "IN PROGRESS" to NC.Coral
        "completed" -> "COMPLETED" to Color(0xFFA4AFB3)
        else -> null to Color(0xFF24AAA5)
    }
    val hours = booking.durationHours
    val shift = if (hours % 1.0 == 0.0) "${hours.toInt()} hour shift" else String.format(Locale.US, "%.1f hour shift", hours)
    val shape = RoundedCornerShape(19.dp)
    Box(modifier.padding(bottom = 16.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("${start.format(HourMinute)} – ${end.format(HourMinute)}", 11, Color(0xFF8D9A9F), Modifier.weight(1f), weight = FontWeight.Bold, spacing = 0.06f)
                statusLabel?.let { Txt(it, 10, dot, weight = FontWeight.Bold, spacing = 0.06f) }
            }
            Txt(booking.serviceName, 15, Color(0xFF293C47), Modifier.padding(top = 6.dp), weight = FontWeight.Bold)
            booking.patientName?.let { Txt(it, 13, Color(0xFF5E7079), Modifier.padding(top = 2.dp), weight = FontWeight.Medium) }
            Txt(listOfNotNull(booking.location, shift).joinToString(" · "), 12, Color(0xFF819096), Modifier.padding(top = 4.dp))
        }
        Box(
            Modifier
                .offset(x = (-31).dp, y = 22.dp)
                .size(14.dp)
                .background(NC.Background, CircleShape)
                .padding(3.dp)
                .background(dot, CircleShape)
        )
    }
}

@Composable
private fun EmptyDay() {
    val shape = RoundedCornerShape(19.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.EventAvailable, null, tint = Color(0xFFA4AFB3), modifier = Modifier.size(28.dp))
        Txt("No bookings", 15, Color(0xFF293C47), Modifier.padding(top = 10.dp), weight = FontWeight.Bold)
        Txt("Requests will alert you when they arrive.", 12, Color(0xFF819096), Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MessagesScreen(openChat: String?, onOpenChat: (String?) -> Unit) {
    BackHandler(enabled = openChat != null) { onOpenChat(null) }

    var threads by remember { mutableStateOf<List<ChatThread>?>(null) }
    var support by remember { mutableStateOf<SupportSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    // Also reloads on leaving a chat, so its unread count clears.
    LaunchedEffect(reload, openChat) {
        error = null
        // Separate from the client chats so a problem with one never hides the other.
        launch { runCatching { SupportRepository.summary() }.onSuccess { support = it } }
        try {
            threads = ChatRepository.loadThreads()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load your messages. Please try again."
        }
    }
    // Keep previews and unread counts current as messages arrive.
    LaunchedEffect(Unit) { RequestRealtime.messages.collect { reload++ } }

    AnimatedContent(
        targetState = openChat,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250))) togetherWith fadeOut(tween(150))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "chat",
    ) { id ->
        if (id != null) {
            ChatView(id, threads?.find { it.id == id }, onBack = { onOpenChat(null) })
        } else {
            ThreadList(threads, support, error, onRetry = { reload++ }, onOpen = { onOpenChat(it) })
        }
    }
}

@Composable
private fun ThreadList(
    threads: List<ChatThread>?,
    support: SupportSummary?,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Column(Modifier.enterUp(0)) {
            Eyebrow("Conversations")
            PageTitle("Messages", Modifier.padding(top = 4.dp))
        }
        Txt(
            "For your safety, keep all client communication within Nurse Center.", 12, Color(0xFF667C7C),
            Modifier.enterUp(1).padding(top = 20.dp).fillMaxWidth().background(Color(0xFFEEF5F4), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            lineHeight = 19,
        )
        Column(Modifier.padding(top = 12.dp, bottom = 24.dp)) {
            // Always pinned first, even before the client chats load.
            SupportThreadRow(support, Modifier.enterUp(2)) { onOpen(SUPPORT_CHAT_ID) }
            HorizontalDivider(color = Color(0xFFE7ECEC))
            when {
                threads == null && error != null -> ScheduleMessage(error, action = "Try again", onAction = onRetry)
                threads == null -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                // Only when there's nothing at all to show, support chat included.
                threads.isEmpty() && support?.lastMessage == null ->
                    ScheduleMessage("No conversations yet. When a client messages you about a booking, it will appear here.")
                threads.isEmpty() -> Unit
                else -> threads.forEachIndexed { index, t ->
                    ThreadRow(t, Modifier.enterUp(index + 3)) { onOpen(t.id) }
                    HorizontalDivider(color = Color(0xFFE7ECEC))
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(t: ChatThread, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().pressable(RoundedCornerShape(12.dp), pressedScale = 0.98f, onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Color(0xFFDFF4F0), CircleShape), contentAlignment = Alignment.Center) {
            Txt(initials(t.clientName), 14, Color(0xFF168D89), weight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row {
                Txt(t.clientName, 15, Color(0xFF283B46), Modifier.weight(1f), weight = FontWeight.Bold, maxLines = 1)
                Txt(shortTime(t.lastAt), 11, Color(0xFF8A989E), weight = FontWeight.SemiBold)
            }
            Txt(
                t.lastMessage ?: "No messages yet", 12, if (t.unread > 0) Color(0xFF3E525B) else Color(0xFF71838B),
                Modifier.padding(top = 4.dp), weight = if (t.unread > 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            )
        }
        if (t.unread > 0) {
            Txt(
                t.unread.toString(), 11, Color.White,
                Modifier.background(Color(0xFFEF9D96), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SupportThreadRow(support: SupportSummary?, modifier: Modifier, onClick: () -> Unit) {
    val unread = support?.unread ?: 0
    Row(
        modifier.fillMaxWidth().pressable(RoundedCornerShape(12.dp), pressedScale = 0.98f, onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(NC.DeepTeal, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.HeadsetMic, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row {
                Txt("Nurse Center Support", 15, Color(0xFF283B46), Modifier.weight(1f), weight = FontWeight.Bold, maxLines = 1)
                Txt(shortTime(support?.lastAt), 11, Color(0xFF8A989E), weight = FontWeight.SemiBold)
            }
            Txt(
                support?.lastMessage ?: "Questions? Chat with our support team.", 12,
                if (unread > 0) Color(0xFF3E525B) else Color(0xFF71838B),
                Modifier.padding(top = 4.dp), weight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            )
        }
        if (unread > 0) {
            Txt(
                unread.toString(), 11, Color.White,
                Modifier.background(Color(0xFFEF9D96), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                weight = FontWeight.Bold,
            )
        }
    }
}

private sealed interface ChatRow {
    data class Day(val date: LocalDate) : ChatRow
    data class Msg(val message: ChatMessage) : ChatRow
}

/** Interleaves a date header before each day's first message. */
private fun chatRows(messages: List<ChatMessage>): List<ChatRow> = buildList {
    var day: LocalDate? = null
    messages.forEach { m ->
        val date = m.createdAt.toLocalDate()
        if (date != day) add(ChatRow.Day(date)).also { day = date }
        add(ChatRow.Msg(m))
    }
}

@Composable
private fun ChatView(conversationId: String, thread: ChatThread?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soon = rememberComingSoon()
    val listState = rememberLazyListState()
    var messages by remember(conversationId) { mutableStateOf<List<ChatMessage>?>(null) }
    var error by remember(conversationId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // The support chat lives in its own tables; client chats in conversations/messages.
    val support = conversationId == SUPPORT_CHAT_ID
    suspend fun markRead() = if (support) SupportRepository.markRead() else ChatRepository.markRead(conversationId)

    // No notifications for this conversation while it's on screen; clear any already showing.
    DisposableEffect(conversationId) {
        ChatAlert.openConversation = conversationId
        ChatAlert.clear(context, conversationId)
        onDispose { if (ChatAlert.openConversation == conversationId) ChatAlert.openConversation = null }
    }

    LaunchedEffect(conversationId, reload) {
        error = null
        try {
            messages = if (support) SupportRepository.loadMessages() else ChatRepository.loadMessages(conversationId)
            markRead()
        } catch (e: AuthException) {
            error = e.message
        } catch (e: Exception) {
            error = "Couldn't load this conversation. Please try again."
        }
    }
    LaunchedEffect(conversationId) {
        RequestRealtime.messages.collect { m ->
            if (m.conversationId != conversationId) return@collect
            val current = messages ?: return@collect
            if (current.none { it.id == m.id }) messages = current + m
            if (!m.mine) markRead()
        }
    }

    val rows = remember(messages) { chatRows(messages.orEmpty()) }
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }

    val send = send@{
        val text = draft.trim()
        if (text.isEmpty() || sending) return@send
        sending = true
        draft = ""
        scope.launch {
            try {
                val sent = if (support) SupportRepository.send(text) else ChatRepository.send(conversationId, text)
                // The realtime echo may already have added it.
                val current = messages.orEmpty()
                if (current.none { it.id == sent.id }) messages = current + sent
            } catch (e: Exception) {
                draft = text
                val reason = (e as? AuthException)?.message ?: "Couldn't send your message. Please try again."
                Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            } finally {
                sending = false
            }
        }
        Unit
    }

    val name = if (support) "Nurse Center Support" else thread?.clientName ?: "Client"
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            if (support) {
                Box(Modifier.size(40.dp).background(NC.DeepTeal, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HeadsetMic, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            } else {
                Box(Modifier.size(40.dp).background(Color(0xFFE5F6F3), CircleShape), contentAlignment = Alignment.Center) {
                    Txt(initials(name), 14, Color(0xFF168C89), weight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Txt(name, 15, Color(0xFF293C47), weight = FontWeight.Bold, maxLines = 1)
                Txt(if (support) "Every day, 06:00–22:00" else "Nurse Center client", 11, Color(0xFF8A989E), weight = FontWeight.SemiBold)
            }
            if (support) {
                IconBtn(Icons.Outlined.Phone, "Call support", {
                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))) }
                })
            } else {
                IconBtn(Icons.Outlined.Phone, "Call", { soon("Calling") })
            }
        }
        HorizontalDivider(color = Color(0xFFE6ECEC))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                messages == null && error != null -> Box(Modifier.padding(20.dp)) {
                    ScheduleMessage(error!!, action = "Try again", onAction = { reload++ })
                }
                messages == null -> CircularProgressIndicator(
                    color = NC.Teal, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
                rows.isEmpty() -> Txt(
                    if (support) "Send us a message and our support team will reply here." else "No messages yet. Say hello to $name.",
                    13, Color(0xFF8A989E),
                    Modifier.align(Alignment.Center).padding(24.dp), align = TextAlign.Center,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { row ->
                        when (row) {
                            is ChatRow.Day -> "day-${row.date}"
                            is ChatRow.Msg -> row.message.id
                        }
                    }) { row ->
                        when (row) {
                            is ChatRow.Day -> Txt(
                                dayLabel(row.date), 11, Color(0xFFA0AAAE), Modifier.fillParentMaxWidth().padding(vertical = 4.dp),
                                weight = FontWeight.SemiBold, spacing = 0.08f, align = TextAlign.Center,
                            )
                            is ChatRow.Msg -> Bubble(row.message, Modifier.animateItem())
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .drawBehind { drawLine(Color(0xFFE3EAEA), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.Rounded.Add, "Attach", { soon("Attachments") })
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, color = Color(0xFF2D424C)),
                cursorBrush = SolidColor(NC.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFF1F5F4), CircleShape).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (draft.isEmpty()) Txt("Write a message...", 13, Color(0xFF9AA7AC))
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            val active = draft.isNotBlank() && !sending && messages != null
            val sendBg by animateColorAsState(if (active) Color(0xFF1DA5A3) else Color(0xFFB9DEDC), label = "sendBg")
            Box(
                Modifier.size(44.dp).pressable(CircleShape, pressedScale = 0.88f, onClick = { if (active) send() }).background(sendBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (sending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val shape = if (message.mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
        Txt(
            message.text, 13, if (message.mine) Color.White else Color(0xFF425660),
            Modifier
                .widthIn(max = 290.dp)
                .then(if (message.mine) Modifier else Modifier.shadow(1.dp, shape))
                .background(if (message.mine) Color(0xFF1B9F9D) else Color.White, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            lineHeight = 20,
        )
        Txt(message.createdAt.format(HourMinute), 10, Color(0xFFA0AAAE), Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp))
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Compact time for the conversation list: "Now", "5 min", "14:30", "Yesterday", "Mon" or "3 Sep". */
private fun shortTime(time: ZonedDateTime?): String {
    if (time == null) return ""
    val now = ZonedDateTime.now(time.zone)
    val minutes = Duration.between(time, now).toMinutes()
    val days = ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate())
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "$minutes min"
        days == 0L -> time.format(HourMinute)
        days == 1L -> "Yesterday"
        days < 7 -> time.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        else -> time.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    }
}
