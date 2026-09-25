package com.nursecenter.nurse.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nursecenter.nurse.R

/** Brand palette, taken from the Nurse Center web design. */
object NC {
    val Teal = Color(0xFF20A7A6)
    val TealDark = Color(0xFF168B8A)
    val TealText = Color(0xFF1B9998)
    val Mint = Color(0xFF44C7B5)
    val Navy = Color(0xFF182B3A)
    val DeepTeal = Color(0xFF173F48)

    val Background = Color(0xFFF7FAF9)
    val LoginBackground = Color(0xFFF5FBFA)
    val Border = Color(0xFFE3EAEA)
    val Divider = Color(0xFFEDF1F1)
    val TealSoft = Color(0xFFEDF7F5)

    val Heading = Color(0xFF243642)
    val Body = Color(0xFF536771)
    val Muted = Color(0xFF8A979D)
    val Faint = Color(0xFFA0AAAE)

    val Coral = Color(0xFFEF9992)
    val CoralSoft = Color(0xFFFFF0EE)
    val Danger = Color(0xFFD9615B)
    val Success = Color(0xFF29A77A)
    val SuccessSoft = Color(0xFFE4F8F1)
}

@OptIn(ExperimentalTextApi::class)
val Jakarta = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.plus_jakarta_sans,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

@Composable
fun NurseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NC.Teal,
            onPrimary = Color.White,
            background = NC.Background,
            surface = Color.White,
            onSurface = NC.Navy,
        ),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = Jakarta, color = NC.Navy),
            content = content,
        )
    }
}
