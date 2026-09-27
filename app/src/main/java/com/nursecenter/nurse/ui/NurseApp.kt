package com.nursecenter.nurse.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nursecenter.nurse.R
import com.nursecenter.nurse.data.ChatAlert
import com.nursecenter.nurse.data.RequestRealtime
import com.nursecenter.nurse.data.SupabaseAuth
import com.nursecenter.nurse.ui.screens.DocumentsScreen
import com.nursecenter.nurse.ui.screens.HomeScreen
import com.nursecenter.nurse.ui.screens.LoginScreen
import com.nursecenter.nurse.ui.screens.MessagesScreen
import com.nursecenter.nurse.ui.screens.MoreScreen
import com.nursecenter.nurse.ui.screens.ProfileScreen
import com.nursecenter.nurse.ui.screens.RequestsScreen
import com.nursecenter.nurse.ui.screens.ReviewsScreen
import com.nursecenter.nurse.ui.screens.SupportScreen
import com.nursecenter.nurse.ui.screens.ScheduleScreen
import com.nursecenter.nurse.ui.screens.Screen
import com.nursecenter.nurse.ui.screens.SplashScreen
import com.nursecenter.nurse.ui.screens.WalletScreen
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { Splash, Login, App }

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Home, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem(Screen.Requests, "Requests", Icons.Outlined.Inbox, Icons.Filled.Inbox),
    NavItem(Screen.Schedule, "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    NavItem(Screen.Messages, "Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    NavItem(Screen.More, "More", Icons.Rounded.MoreHoriz, Icons.Rounded.MoreHoriz),
)

@Composable
fun NurseApp() {
    var phase by rememberSaveable { mutableStateOf(Phase.Splash) }
    var active by rememberSaveable { mutableStateOf(Screen.Home) }
    var online by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(phase) {
        when (phase) {
            Phase.Splash -> {
                delay(1800)
                phase = Phase.Login
            }
            Phase.Login -> RequestRealtime.stop()
            Phase.App -> {
                RequestRealtime.start(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    SystemBarIcons(darkStatusIcons = phase == Phase.App, darkNavIcons = phase != Phase.Splash)

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.98f)) togetherWith fadeOut(tween(300))
        },
        label = "phase",
    ) { current ->
        when (current) {
            Phase.Splash -> SplashScreen()
            Phase.Login -> LoginScreen(onLogin = { active = Screen.Home; phase = Phase.App })
            Phase.App -> MainShell(
                active = active,
                onNavigate = { active = it },
                online = online,
                onOnlineChange = { online = it },
                onSignOut = { scope.launch { SupabaseAuth.signOut() }; phase = Phase.Login },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(
    active: Screen,
    onNavigate: (Screen) -> Unit,
    online: Boolean,
    onOnlineChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
) {
    BackHandler(enabled = active != Screen.Home) {
        onNavigate(if (active.isSubScreen) Screen.More else Screen.Home)
    }
    val imeVisible = WindowInsets.isImeVisible
    var openChat by rememberSaveable { mutableStateOf<String?>(null) }
    // An open conversation takes over the whole screen, like the sub-screens.
    val inChat = active == Screen.Messages && openChat != null

    // A tapped message notification opens its conversation.
    val requestedChat by ChatAlert.requestedConversation.collectAsState()
    LaunchedEffect(requestedChat) {
        val id = requestedChat ?: return@LaunchedEffect
        openChat = id
        onNavigate(Screen.Messages)
        ChatAlert.requestedConversation.value = null
    }

    Column(Modifier.fillMaxSize().background(NC.Background).statusBarsPadding().imePadding()) {
        AnimatedVisibility(
            visible = !active.isSubScreen && !inChat,
            enter = expandVertically(tween(250)) + fadeIn(tween(250)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(150)),
        ) {
            AppHeader(online, onOnlineChange)
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = active,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "screen",
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(goTo = onNavigate)
                    Screen.Requests -> RequestsScreen()
                    Screen.Schedule -> ScheduleScreen()
                    Screen.Messages -> MessagesScreen(openChat = openChat, onOpenChat = { openChat = it })
                    Screen.More -> MoreScreen(goTo = onNavigate, onSignOut = onSignOut)
                    Screen.Wallet -> WalletScreen(back = { onNavigate(Screen.More) })
                    Screen.Profile -> ProfileScreen(back = { onNavigate(Screen.More) })
                    Screen.Documents -> DocumentsScreen(back = { onNavigate(Screen.More) })
                    Screen.Reviews -> ReviewsScreen(back = { onNavigate(Screen.More) })
                    Screen.Support -> SupportScreen(back = { onNavigate(Screen.More) })
                }
            }
        }
        if (!imeVisible) {
            AnimatedVisibility(
                visible = !inChat,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(150)),
            ) {
                BottomNav(active, onNavigate)
            }
        }
    }
}

@Composable
private fun AppHeader(online: Boolean, onOnlineChange: (Boolean) -> Unit) {
    val soon = rememberComingSoon()
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo_icon), "Nurse Center", Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Txt("NURSECENTER", 10, Color(0xFF91A0A6), weight = FontWeight.Bold, spacing = 0.15f)
            Txt("Nurse Portal", 14, Color(0xFF233643), weight = FontWeight.Bold)
        }
        IconBtn(Icons.Outlined.Notifications, "Notifications", { soon("Notifications") }, badge = true)
        Spacer(Modifier.width(8.dp))
        OnlineToggle(online, onOnlineChange)
    }
}

@Composable
private fun OnlineToggle(online: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (online) Color(0xFFE7F8F2) else Color(0xFFEDF0F1), tween(300), label = "toggleBg")
    val fg by animateColorAsState(if (online) Color(0xFF17815F) else Color(0xFF718087), tween(300), label = "toggleFg")
    val dot by animateColorAsState(if (online) Color(0xFF29B77C) else Color(0xFF89969C), tween(300), label = "toggleDot")
    Row(
        Modifier
            .height(40.dp)
            .pressable(CircleShape, pressedScale = 0.93f) { onChange(!online) }
            .background(bg, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = online,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "toggleText",
        ) { isOnline ->
            Txt(if (isOnline) "Online" else "Offline", 13, fg, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomNav(active: Screen, onNavigate: (Screen) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.97f))
            .drawBehind { drawLine(Color(0xFFE4EAEA), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        navItems.forEach { item ->
            val selected = active == item.screen || (item.screen == Screen.More && active.isSubScreen)
            NavButton(item, selected, Modifier.weight(1f)) { onNavigate(item.screen) }
        }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) Color(0xFF148F8D) else Color(0xFF8B999F), tween(250), label = "navFg")
    val pill by animateColorAsState(if (selected) Color(0xFFE3F5F2) else Color.Transparent, tween(250), label = "navPill")
    val pillWidth by animateDpAsState(if (selected) 56.dp else 40.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "navWidth")
    val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "navScale")

    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(pillWidth).height(30.dp).background(pill, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (selected) item.selectedIcon else item.icon, null, tint = fg,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
            if (item.screen == Screen.Requests) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = (pillWidth - 40.dp) / 2 + 7.dp)
                        .size(9.dp).background(Color.White, CircleShape).padding(2.dp).background(Color(0xFFEF918B), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Txt(item.label, 11, fg, weight = FontWeight.Bold)
    }
}

/** Keeps status/navigation bar icon colours readable against each phase's background. */
@Composable
private fun SystemBarIcons(darkStatusIcons: Boolean, darkNavIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkStatusIcons
            isAppearanceLightNavigationBars = darkNavIcons
        }
    }
}
