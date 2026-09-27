package com.nursecenter.nurse.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nursecenter.nurse.ui.theme.NC
import kotlinx.coroutines.delay
import java.util.Locale

/** Compact text helper mirroring the web design's px-based type scale. */
@Composable
fun Txt(
    text: String,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
    lineHeight: Int = 0,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = spacing.em,
        lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified,
        textAlign = align,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

/** Clickable with a springy press-down scale, clipped ripple included. */
fun Modifier.pressable(
    shape: Shape,
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick)
}

/** Staggered fade-and-rise entrance, the native counterpart of the web `screen-in` keyframes. */
fun Modifier.enterUp(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

/** Animates a number from 0 up to [target] the first time it is shown. */
@Composable
fun countUp(target: Float, durationMs: Int = 900): Float {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return value.value
}

fun rand(value: Float, cents: Boolean = false): String =
    "R " + String.format(Locale.US, if (cents) "%,.2f" else "%,.0f", value)

@Composable
fun rememberComingSoon(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { what -> Toast.makeText(context, "$what is coming soon", Toast.LENGTH_SHORT).show() } }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(height)
            .shadow(10.dp, shape, ambientColor = Color(0x33199D99), spotColor = Color(0x55199D99))
            .pressable(shape, enabled = !loading, onClick = onClick)
            .background(Brush.linearGradient(listOf(Color(0xFF159C9A), Color(0xFF23B0A7))), shape)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            leading?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Txt(text, 14, Color.White, weight = FontWeight.Bold)
            trailing?.let { Spacer(Modifier.width(6.dp)); Icon(it, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(50.dp)
            .pressable(shape, onClick = onClick)
            .background(Color.White, shape)
            .border(1.dp, Color(0xFFDCE6E6), shape)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFF65767E), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Txt(text, 13, Color(0xFF65767E), weight = FontWeight.Bold)
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape, ambientColor = Color(0x10254346), spotColor = Color(0x18254346))
            .pressable(CircleShape, pressedScale = 0.9f, onClick = onClick)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color(0xFF526670), modifier = Modifier.size(20.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 9.dp)
                    .size(9.dp)
                    .background(Color.White, CircleShape)
                    .padding(2.dp)
                    .background(Color(0xFFF49B95), CircleShape)
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(title, 16, NC.Heading, Modifier.weight(1f), weight = FontWeight.Bold)
        if (action != null) {
            Txt(
                action, 13, NC.TealText,
                Modifier.pressable(RoundedCornerShape(8.dp), onClick = onAction).padding(horizontal = 4.dp, vertical = 2.dp),
                weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Txt(text.uppercase(), 11, Color(0xFF269A96), modifier, weight = FontWeight.ExtraBold, spacing = 0.14f)

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) =
    Txt(text, 28, Color(0xFF192F3D), modifier, weight = FontWeight.Bold, spacing = -0.03f)

/** White rounded card with a hairline border, used for stat tiles. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(shape, onClick = onClick) else Modifier)
            .background(Color.White, shape)
            .border(1.dp, NC.Border, shape)
            .padding(16.dp)
    ) { content() }
}

/** Bordered list container that draws dividers between rows. */
@Composable
fun CardList(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, NC.Border, shape)) {
        rows.forEachIndexed { index, row ->
            row()
            if (index < rows.lastIndex) HorizontalDivider(color = NC.Divider, thickness = 1.dp)
        }
    }
}

@Composable
fun IconTile(icon: ImageVector, background: Color, tint: Color, size: Dp = 40.dp, iconSize: Dp = 19.dp, corner: Dp = 12.dp) {
    Box(Modifier.size(size).background(background, RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Bottom sheet styled like the design's modal: white, rounded top, a round close button top right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(
                Modifier.align(Alignment.End).padding(top = 16.dp, end = 16.dp).size(32.dp)
                    .pressable(CircleShape, pressedScale = 0.9f, onClick = onDismiss).background(Color(0xFFF0F4F3), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Close", tint = Color(0xFF718087), modifier = Modifier.size(16.dp))
            }
            content()
        }
    }
}
