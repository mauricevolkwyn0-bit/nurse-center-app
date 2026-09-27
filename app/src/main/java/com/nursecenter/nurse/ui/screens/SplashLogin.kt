package com.nursecenter.nurse.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nursecenter.nurse.R
import com.nursecenter.nurse.data.AuthException
import com.nursecenter.nurse.data.SupabaseAuth
import com.nursecenter.nurse.ui.PrimaryButton
import com.nursecenter.nurse.ui.Txt
import com.nursecenter.nurse.ui.enterUp
import com.nursecenter.nurse.ui.pressable
import com.nursecenter.nurse.ui.theme.Jakarta
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LogoEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

@Composable
fun SplashScreen() {
    val logoIn = remember { Animatable(0f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoIn.animateTo(1f, tween(700, easing = LogoEasing)) }
        delay(250)
        textIn.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    val ambient = rememberInfiniteTransition(label = "ambient")
    val breathe by ambient.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val slide by ambient.animateFloat(
        -1.1f, 2.25f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing)), label = "loading",
    )
    val circlePulse by ambient.animateFloat(
        1f, 1.07f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "circlePulse",
    )
    val haloPulse by ambient.animateFloat(
        0.94f, 1.08f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "haloPulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF123B46), Color(0xFF15555C), Color(0xFF168982)),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 150.dp, y = (-90).dp)
                .size(430.dp)
                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                .background(Color(0x264DD5C2), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-230).dp, y = 170.dp)
                .size(520.dp)
                .graphicsLayer { scaleX = 2.08f - breathe; scaleY = 2.08f - breathe }
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .graphicsLayer {
                        val p = logoIn.value
                        alpha = p
                        scaleX = 0.86f + 0.14f * p
                        scaleY = 0.86f + 0.14f * p
                        translationY = (1 - p) * 10.dp.toPx()
                    }
                    .size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(200.dp)
                        .graphicsLayer { scaleX = haloPulse; scaleY = haloPulse }
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                )
                Box(
                    Modifier
                        .size(156.dp)
                        .graphicsLayer { scaleX = haloPulse; scaleY = haloPulse }
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                )
                Box(
                    Modifier
                        .size(120.dp)
                        .graphicsLayer { scaleX = circlePulse; scaleY = circlePulse }
                        .shadow(20.dp, CircleShape, spotColor = Color(0x66031E24))
                        .background(Color.White, CircleShape)
                )
                Image(painterResource(R.drawable.logo_splash), "Nurse Center", Modifier.size(84.dp))
            }
            Column(
                Modifier.graphicsLayer {
                    alpha = textIn.value
                    translationY = (1 - textIn.value) * 12.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                Txt("Care, wherever it is needed.", 14, Color.White.copy(alpha = 0.75f), weight = FontWeight.Medium, spacing = 0.02f)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(72.dp)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
                    .graphicsLayer { clip = true; shape = CircleShape }
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(32.dp)
                        .graphicsLayer { translationX = slide * size.width }
                        .background(Color(0xFF78DFCD), CircleShape)
                )
            }
            Spacer(Modifier.height(12.dp))
            Txt("NURSE PORTAL", 11, Color.White.copy(alpha = 0.5f), weight = FontWeight.Bold, spacing = 0.22f)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val signIn: () -> Unit = signIn@{
        if (signingIn) return@signIn
        if (email.isBlank() || password.isEmpty()) {
            error = "Enter your email and password."
            return@signIn
        }
        focus.clearFocus()
        error = null
        signingIn = true
        scope.launch {
            try {
                SupabaseAuth.signIn(email.trim(), password)
                onLogin()
            } catch (e: AuthException) {
                error = e.message
            } catch (e: Exception) {
                error = "Something went wrong. Please try again."
            } finally {
                signingIn = false
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(NC.LoginBackground)) {
        val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val heroHeight = maxOf(230.dp, maxHeight * 0.34f)
        val screenHeight = maxHeight
        val scroll = rememberScrollState()
        // While the keyboard is open, keep the page scrolled to the bottom so the whole page (header included)
        // slides up with the keyboard and the fields and sign-in button stay visible above it.
        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(imeVisible) {
            if (imeVisible) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
        ) {
            Column(Modifier.heightIn(min = screenHeight)) {
                // The hero takes any spare height so the form below stays compact on tall screens.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = heroHeight + statusBar)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF163E48), Color(0xFF167A76)), Offset.Zero, Offset.Infinite)
                        )
                ) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-160).dp).size(280.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.11f), CircleShape)
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 80.dp, y = 75.dp).size(190.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.11f), CircleShape)
                    )
                    // Centred in the visible hero: below the status bar and above the form's 20dp overlap.
                    Column(
                        Modifier.align(Alignment.Center).statusBarsPadding()
                            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painterResource(R.drawable.logo_icon), null,
                            Modifier
                                .enterUp(0)
                                .size(64.dp)
                                .shadow(8.dp, RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(18.dp))
                                .padding(7.dp),
                        )
                        Spacer(Modifier.height(26.dp))
                        Column(Modifier.enterUp(1), horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt("NURSE PORTAL", 12, Color.White.copy(alpha = 0.65f), weight = FontWeight.Bold, spacing = 0.18f)
                            Spacer(Modifier.height(8.dp))
                            Txt("Welcome back.", 31, Color.White, weight = FontWeight.Bold, spacing = -0.03f)
                            Spacer(Modifier.height(8.dp))
                            Txt(
                                "Your bookings, clients and earnings, all in one place.", 14,
                                Color.White.copy(alpha = 0.75f), Modifier.widthIn(max = 300.dp), lineHeight = 22, align = TextAlign.Center,
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .offset(y = (-20).dp)
                        .background(NC.LoginBackground, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .padding(start = 24.dp, end = 24.dp, top = 32.dp)
                        .navigationBarsPadding()
                ) {
                    Column(Modifier.enterUp(2)) {
                        FieldLabel("Email")
                        LoginField(
                            value = email,
                            onValueChange = { email = it; error = null },
                            icon = Icons.Outlined.Person,
                            placeholder = "Enter your email address",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.enterUp(3)) {
                        FieldLabel("Password")
                        LoginField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            icon = Icons.Outlined.Lock,
                            placeholder = "Enter your password",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { signIn() }),
                            trailing = {
                                Txt(
                                    if (showPassword) "Hide" else "Show", 13, NC.TealDark,
                                    Modifier.pressable(RoundedCornerShape(8.dp)) { showPassword = !showPassword }.padding(6.dp),
                                    weight = FontWeight.Bold,
                                )
                            },
                        )
                    }
                    Txt(
                        "Forgot password?", 13, NC.TealDark,
                        Modifier.align(Alignment.End).padding(top = 10.dp)
                            .pressable(RoundedCornerShape(8.dp)) {}.padding(4.dp),
                        weight = FontWeight.Bold,
                    )
                    error?.let {
                        Txt(it, 13, Color(0xFFD14343), Modifier.padding(top = 12.dp), weight = FontWeight.SemiBold)
                    }
                    PrimaryButton(
                        text = "Sign in securely",
                        onClick = signIn,
                        modifier = Modifier.enterUp(4).padding(top = 24.dp).fillMaxWidth(),
                        trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        loading = signingIn,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF8A999F), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Txt("Your information is private and protected", 12, Color(0xFF8A999F), weight = FontWeight.Medium)
                    }
                }
            }
        }
        // Covers the status bar once the page scrolls, so the header text doesn't show behind the clock and icons.
        if (scroll.value > 0) {
            Box(Modifier.fillMaxWidth().height(statusBar).background(Color(0xFF163E48)))
        }
    }
}

@Composable
private fun FieldLabel(text: String) =
    Txt(text, 13, Color(0xFF52636E), Modifier.padding(bottom = 8.dp), weight = FontWeight.Bold)

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Color(0xFF65C7BE) else Color(0xFFDCE7E5), label = "fieldBorder")
    val iconTint by animateColorAsState(if (focused) NC.Teal else Color(0xFF82929A), label = "fieldIcon")
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2D424C)),
        cursorBrush = SolidColor(NC.Teal),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(if (focused) 6.dp else 2.dp, shape, ambientColor = Color(0x1A20A7A6), spotColor = Color(0x2220A7A6))
                    .background(Color.White, shape)
                    .border(if (focused) 1.5.dp else 1.dp, border, shape)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(11.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) Txt(placeholder, 14, Color(0xFFA3B0B5), weight = FontWeight.Medium, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

