package com.example.pdftoolkit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R

/** Figma ka font: Inter (Regular / Medium / SemiBold / Bold) -> res/font/ */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** Figma ke exact text styles (size / line-height / letter-spacing). */
object AppTextStyle {
    val Title22SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val Title18Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val Title16Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)

    val Body14Regular = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Body14Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val Body14SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.14.sp)
    val Button14SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.14.sp)
    val Fab14SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.35.sp)

    val Label12Regular = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Label12Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.24.sp)
    val Label12SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.24.sp)

    val Caption11Regular = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
    val Caption11Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.22.sp)
    val NavLabel11Medium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    val NavLabel11SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)

    val Badge10Regular = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 12.5.sp)
    val Badge9Bold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 13.5.sp)
    val Badge9SemiBold = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, lineHeight = 13.5.sp, letterSpacing = 0.45.sp)
}

/** Material3 typography bhi Inter pe set hai, taaki default Text() bhi Inter use kare. */
val AppTypography = Typography(
    headlineSmall = AppTextStyle.Title22SemiBold,
    titleLarge = AppTextStyle.Title18Medium,
    titleMedium = AppTextStyle.Title16Medium,
    bodyLarge = AppTextStyle.Body14Regular,
    bodyMedium = AppTextStyle.Body14Regular,
    labelLarge = AppTextStyle.Button14SemiBold,
    labelMedium = AppTextStyle.Label12Medium,
    labelSmall = AppTextStyle.Caption11Medium
)
