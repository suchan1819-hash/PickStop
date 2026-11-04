@file:OptIn(ExperimentalTextApi::class)

package com.zeroinjury.pickstop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zeroinjury.pickstop.R

/** 픽스탑 색상 토큰 — 야간 차량 실내 + 따뜻한 앰버 (음식·휴식) */
object PS {
    val Bg0 = Color(0xFF0B0D12)
    val Bg1 = Color(0xFF12151C)
    val Glass = Color.White.copy(alpha = 0.055f)
    val Glass2 = Color.White.copy(alpha = 0.03f)
    val Stroke = Color.White.copy(alpha = 0.08f)
    val Text = Color(0xFFF5F3EF)
    val Sub = Color(0xFF9AA0AB)
    val Faint = Color(0xFF5E6470)
    val Amber = Color(0xFFFFB547)
    val Orange = Color(0xFFFF8A3D)
    val Ink = Color(0xFF2A1600)
    val Mint = Color(0xFF4FE3B5)
    val Red = Color(0xFFFF6B5E)
    val Warm = Brush.linearGradient(listOf(Amber, Orange))
}

private fun asta(w: Int) = Font(R.font.asta_sans, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Pleos Connect 공식 서체 Asta Sans */
val Asta = FontFamily(asta(400), asta(500), asta(600), asta(700), asta(800))

/** 숫자 폭이 일정한 스타일 */
val Tnum = TextStyle(fontFamily = Asta, color = PS.Text, fontFeatureSettings = "tnum")

/** 디자인 캔버스(1240×900)를 앱 창 크기에 맞춰 그대로 확대·축소한다. */
const val DESIGN_W = 1240f
const val DESIGN_H = 900f

@Composable
fun PickStopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = PS.Amber, background = PS.Bg0, surface = PS.Bg1)) {
        ProvideTextStyle(TextStyle(fontFamily = Asta, color = PS.Text)) {
            BoxWithConstraints(Modifier.fillMaxSize().background(PS.Bg0), contentAlignment = Alignment.Center) {
                val outer = LocalDensity.current
                val wPx = with(outer) { maxWidth.toPx() }
                val hPx = with(outer) { maxHeight.toPx() }
                val scale = minOf(wPx / DESIGN_W, hPx / DESIGN_H)
                CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
                    Box(Modifier.requiredSize(DESIGN_W.dp, DESIGN_H.dp)) { content() }
                }
            }
        }
    }
}
