package com.nursecenter.nurse.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.nursecenter.nurse.ui.Eyebrow
import com.nursecenter.nurse.ui.IconBtn
import com.nursecenter.nurse.ui.PageTitle
import com.nursecenter.nurse.ui.SectionTitle
import com.nursecenter.nurse.ui.Txt
import com.nursecenter.nurse.ui.enterUp
import com.nursecenter.nurse.ui.pressable
import com.nursecenter.nurse.ui.rememberComingSoon
import com.nursecenter.nurse.ui.theme.Jakarta
import com.nursecenter.nurse.ui.theme.NC

private data class Booking(val time: String, val title: String, val place: String, val teal: Boolean)

private data class Day(val short: String, val long: String, val date: String)

private val week = listOf(
    Day("MON", "Monday", "23"), Day("TUE", "Tuesday", "24"), Day("WED", "Wednesday", "25"),
    Day("THU", "Thursday", "26"), Day("FRI", "Friday", "27"), Day("SAT", "Saturday", "28"), Day("SUN", "Sunday", "29"),
)

private val bookings = mapOf(
    "25" to listOf(
        Booking("09:00", "Medication & wellness check", "Gardens · 2 hour shift", teal = true),
        Booking("16:30", "Home-based care", "Observatory · 3 hour shift", teal = false),
    ),
    "27" to listOf(Booking("09:00", "Post-surgery check-in", "Claremont · 2 hour shift", teal = true)),
)

@Composable
fun ScheduleScreen() {
    var selected by rememberSaveable { mutableIntStateOf(2) }
    val soon = rememberComingSoon()
    val day = week[selected]

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
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) { if (selected > 0) selected-- }
                Txt("September 2026", 15, Color(0xFF2B3E48), Modifier.weight(1f), weight = FontWeight.Bold, align = androidx.compose.ui.text.style.TextAlign.Center)
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) { if (selected < week.lastIndex) selected++ }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEachIndexed { index, d ->
                    DayChip(d, isSelected = index == selected, hasBookings = bookings.containsKey(d.date), Modifier.weight(1f)) {
                        selected = index
                    }
                }
            }
        }

        Column(Modifier.enterUp(2).padding(top = 28.dp, bottom = 32.dp)) {
            SectionTitle("${day.long}, ${day.date} Sep", action = "Add availability", onAction = { soon("Adding availability") })
            AnimatedContent(
                targetState = day.date,
                transitionSpec = {
                    val forward = targetState > initialState
                    (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { if (forward) it / 6 else -it / 6 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { if (forward) -it / 8 else it / 8 })
                },
                label = "day",
            ) { date ->
                val items = bookings[date].orEmpty()
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
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().background(Color(0xFFEEF8F6), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF1C9891), modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF274B49), fontWeight = FontWeight.Bold)) { append("You are available\n") }
                        append("Requests can arrive between 08:00 and 20:00.")
                    },
                    color = Color(0xFF58716F), fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
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
private fun DayChip(day: Day, isSelected: Boolean, hasBookings: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (isSelected) Color(0xFF1EAAA8) else Color.Transparent, tween(250), label = "dayBg")
    val fg by animateColorAsState(if (isSelected) Color.White else Color(0xFF50636C), tween(250), label = "dayFg")
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
        Txt(day.short, 9, if (isSelected) Color.White.copy(alpha = 0.7f) else Color(0xFFA2ADB1), weight = FontWeight.Bold)
        Txt(day.date, 14, fg, Modifier.padding(top = 4.dp), weight = FontWeight.Bold)
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
private fun TimelineItem(booking: Booking, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(19.dp)
    Box(modifier.padding(bottom = 16.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Color.White, shape).border(1.dp, Color(0xFFE2E9E9), shape).padding(16.dp)
        ) {
            Txt(booking.time, 11, Color(0xFF8D9A9F), weight = FontWeight.Bold, spacing = 0.06f)
            Txt(booking.title, 15, Color(0xFF293C47), Modifier.padding(top = 6.dp), weight = FontWeight.Bold)
            Txt(booking.place, 12, Color(0xFF819096), Modifier.padding(top = 4.dp))
        }
        Box(
            Modifier
                .offset(x = (-31).dp, y = 22.dp)
                .size(14.dp)
                .background(NC.Background, CircleShape)
                .padding(3.dp)
                .background(if (booking.teal) Color(0xFF24AAA5) else NC.Coral, CircleShape)
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

private data class Thread(val initials: String, val name: String, val preview: String, val time: String, val support: Boolean = false)

@Composable
fun MessagesScreen() {
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = chatOpen) { chatOpen = false }

    AnimatedContent(
        targetState = chatOpen,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250))) togetherWith fadeOut(tween(150))
            } else {
                (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "chat",
    ) { open ->
        if (open) ChatView(onBack = { chatOpen = false }) else ThreadList(onOpen = { chatOpen = true })
    }
}

@Composable
private fun ThreadList(onOpen: () -> Unit) {
    val soon = rememberComingSoon()
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
            val threads = listOf(
                Thread("TM", "Thandi M.", "Perfect, please ring the blue gate...", "2 min"),
                Thread("LS", "Lwazi S.", "Thank you for your help today.", "Yesterday"),
                Thread("NC", "Nurse Center Support", "Your payment has been processed.", "Mon", support = true),
            )
            threads.forEachIndexed { index, t ->
                ThreadRow(
                    t, unread = if (index == 0) 2 else 0, online = index == 0,
                    modifier = Modifier.enterUp(index + 2),
                    onClick = if (index == 0) onOpen else { { soon("This conversation") } },
                )
                HorizontalDivider(color = Color(0xFFE7ECEC))
            }
        }
    }
}

@Composable
private fun ThreadRow(t: Thread, unread: Int, online: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().pressable(RoundedCornerShape(12.dp), pressedScale = 0.98f, onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier.size(48.dp).background(
                    when {
                        t.support -> NC.DeepTeal
                        online -> Color(0xFFDFF4F0)
                        else -> Color(0xFFF8EAE8)
                    },
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (t.support) {
                    Icon(Icons.Outlined.HeadsetMic, null, tint = Color.White, modifier = Modifier.size(21.dp))
                } else {
                    Txt(t.initials, 14, if (online) Color(0xFF168D89) else Color(0xFFB36E68), weight = FontWeight.Bold)
                }
            }
            if (online) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(14.dp).background(NC.Background, CircleShape).padding(2.dp)
                        .background(Color(0xFF36BD84), CircleShape)
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row {
                Txt(t.name, 15, Color(0xFF283B46), Modifier.weight(1f), weight = FontWeight.Bold)
                Txt(t.time, 11, Color(0xFF8A989E), weight = FontWeight.SemiBold)
            }
            Txt(t.preview, 12, Color(0xFF71838B), Modifier.padding(top = 4.dp), maxLines = 1)
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

private data class Message(val text: String, val mine: Boolean)

private val messageListSaver = listSaver<MutableList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf(*it.toTypedArray()) },
)

@Composable
private fun ChatView(onBack: () -> Unit) {
    val sent = rememberSaveable(saver = messageListSaver) { mutableStateListOf<String>() }
    var draft by rememberSaveable { mutableStateOf("") }
    val soon = rememberComingSoon()
    val listState = rememberLazyListState()

    val messages = listOf(
        Message("Hello Maurice, thank you for accepting my request.", mine = false),
        Message("Hi Thandi. You're welcome. I'll be there at 16:30.", mine = true),
        Message("Perfect, please ring the blue gate when you arrive.", mine = false),
    ) + sent.map { Message(it, mine = true) }

    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.size) }

    val send = {
        val text = draft.trim()
        if (text.isNotEmpty()) sent.add(text)
        draft = ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(40.dp).background(Color(0xFFE5F6F3), CircleShape), contentAlignment = Alignment.Center) {
                Txt("TM", 14, Color(0xFF168C89), weight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Txt("Thandi M.", 15, Color(0xFF293C47), weight = FontWeight.Bold)
                Txt("Online now", 11, Color(0xFF2AA878), weight = FontWeight.SemiBold)
            }
            IconBtn(Icons.Outlined.Phone, "Call", { soon("Calling") })
        }
        HorizontalDivider(color = Color(0xFFE6ECEC))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Txt(
                    "TODAY", 11, Color(0xFFA0AAAE), Modifier.fillParentMaxWidth(),
                    weight = FontWeight.SemiBold, spacing = 0.08f, align = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            itemsIndexed(messages, key = { index, _ -> index }) { _, message ->
                Bubble(message, Modifier.animateItem())
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .drawBehind { drawLine(Color(0xFFE3EAEA), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
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
            val active = draft.isNotBlank()
            val sendBg by animateColorAsState(if (active) Color(0xFF1DA5A3) else Color(0xFFB9DEDC), label = "sendBg")
            Box(
                Modifier.size(44.dp).pressable(CircleShape, pressedScale = 0.88f, onClick = send).background(sendBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, modifier: Modifier = Modifier) {
    val shape = if (message.mine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    }
    Box(modifier.fillMaxWidth(), contentAlignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Txt(
            message.text, 13, if (message.mine) Color.White else Color(0xFF425660),
            Modifier
                .widthIn(max = 290.dp)
                .then(if (message.mine) Modifier else Modifier.shadow(1.dp, shape))
                .background(if (message.mine) Color(0xFF1B9F9D) else Color.White, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            lineHeight = 20,
        )
    }
}

