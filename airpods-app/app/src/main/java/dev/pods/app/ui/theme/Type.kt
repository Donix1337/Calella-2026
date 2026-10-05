package dev.pods.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pods.app.R

// Inter is the closest open-source match to Apple's SF Pro. The variable font
// has an optical size axis, so large titles get the tighter "Display" cut.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight, opticalSize: Float) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

val InterText = FontFamily(
    inter(FontWeight.Normal, 14f),
    inter(FontWeight.Medium, 14f),
    inter(FontWeight.SemiBold, 14f),
    inter(FontWeight.Bold, 14f),
)

val InterDisplay = FontFamily(
    inter(FontWeight.Medium, 32f),
    inter(FontWeight.SemiBold, 32f),
    inter(FontWeight.Bold, 32f),
)

object PodsType {
    val largeTitle = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp,
    )
    val title2 = TextStyle(
        fontFamily = InterDisplay, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp,
    )
    val title3 = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.4).sp,
    )
    val headline = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp,
    )
    val body = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp,
    )
    val callout = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.25).sp,
    )
    val subhead = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.15).sp,
    )
    val footnote = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.05).sp,
    )
    val caption = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp,
    )
    val sectionHeader = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    )
    /** Battery percentages: tabular figures so numbers don't jiggle. */
    val number = TextStyle(
        fontFamily = InterText, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum",
    )
}

val PodsMaterialTypography = Typography(
    displayLarge = PodsType.largeTitle,
    headlineMedium = PodsType.title2,
    titleLarge = PodsType.title3,
    titleMedium = PodsType.headline,
    bodyLarge = PodsType.body,
    bodyMedium = PodsType.callout,
    bodySmall = PodsType.footnote,
    labelLarge = PodsType.headline,
    labelMedium = PodsType.footnote,
    labelSmall = PodsType.caption,
)
