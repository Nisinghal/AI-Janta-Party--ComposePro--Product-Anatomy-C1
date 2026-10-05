package com.composepro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Four sizes only (DESIGN_LANGUAGE.md). Headings use the phone's serif, standing in for Source Serif 4,
// because the app has no internet to fetch fonts.
object CPType {
    val Display = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val Heading = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val CardTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val BodyMedium = Body.copy(fontWeight = FontWeight.Medium)
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val CaptionMedium = Caption.copy(fontWeight = FontWeight.Medium)
}

val Typography = Typography(
    bodyLarge = CPType.Body,
    bodyMedium = CPType.Body,
    titleLarge = CPType.Display,
    titleMedium = CPType.Heading,
    labelSmall = CPType.Caption,
)
