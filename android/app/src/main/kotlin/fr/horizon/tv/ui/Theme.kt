package fr.horizon.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HColors {
    val Text = Color.White
    val TextDim = Color.White.copy(alpha = 0.66f)
    val TextFaint = Color.White.copy(alpha = 0.40f)
    val Glass = Color.White.copy(alpha = 0.11f)
    val GlassStrong = Color.White.copy(alpha = 0.20f)
    val GlassBorder = Color.White.copy(alpha = 0.16f)
    val Accent = Color(0xFFFFB84D)
    val Danger = Color(0xFFFF6B6B)
    val Ink = Color(0xFF0A0D18)
}

object HType {
    val Display = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = HColors.Text, letterSpacing = (-1).sp)
    val Title = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = HColors.Text)
    val Section = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = HColors.Text)
    val Body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, color = HColors.Text, lineHeight = 23.sp)
    val Label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HColors.TextDim)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HColors.TextFaint)
}
