package com.zeroinjury.pickstop.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

val Card = RoundedCornerShape(28.dp)

fun Modifier.glass(shape: RoundedCornerShape = Card, fill: Color = PS.Glass, stroke: Color = PS.Stroke) =
    this.clip(shape).background(fill).border(1.dp, stroke, shape)

@Composable
fun Pill(text: String, dot: Color? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(99.dp)).background(PS.Glass2).border(1.dp, PS.Stroke, RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(7.dp)) }
        Text(text, fontSize = 14.sp, color = PS.Sub)
    }
}

@Composable
fun Eyebrow(text: String, dot: Color? = PS.Amber) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(7.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 15.sp, color = PS.Amber, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 76.dp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(22.dp)).background(PS.Warm).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PS.Ink) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 76.dp, onClick: () -> Unit) {
    Box(
        modifier.height(height).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, PS.Stroke, RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = PS.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 20.sp, color = PS.Sub)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor, style = Tnum)
    }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().height(1.dp).background(PS.Stroke)) }

// ---------------- 아이콘 ----------------

/** 24 격자 기준 선 아이콘 */
@Composable
fun LineIcon(size: Dp, color: Color, strokeW: Float = 2.2f, build: Path.(Float) -> Unit) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val p = Path().apply { build(s) }
        drawPath(p, color, style = Stroke(width = strokeW * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconPin(size: Dp, color: Color) = LineIcon(size, color, 2.4f) { s ->
    moveTo(12 * s, 21 * s)
    cubicTo(12 * s, 21 * s, 5 * s, 14.8f * s, 5 * s, 9.5f * s)
    cubicTo(5 * s, 5.6f * s, 8.1f * s, 2.5f * s, 12 * s, 2.5f * s)
    cubicTo(15.9f * s, 2.5f * s, 19 * s, 5.6f * s, 19 * s, 9.5f * s)
    cubicTo(19 * s, 14.8f * s, 12 * s, 21 * s, 12 * s, 21 * s)
    moveTo(9.5f * s, 9.5f * s); lineTo(11.5f * s, 11.5f * s); lineTo(15 * s, 8 * s)
}

@Composable
fun IconMic(size: Dp, color: Color) = LineIcon(size, color, 2.4f) { s ->
    addRoundRect(androidx.compose.ui.geometry.RoundRect(9 * s, 3 * s, 15 * s, 14 * s, 3 * s, 3 * s))
    moveTo(5 * s, 11 * s); cubicTo(5 * s, 15 * s, 8 * s, 18 * s, 12 * s, 18 * s); cubicTo(16 * s, 18 * s, 19 * s, 15 * s, 19 * s, 11 * s)
    moveTo(12 * s, 18 * s); lineTo(12 * s, 21 * s)
}

@Composable
fun IconClock(size: Dp, color: Color) = LineIcon(size, color, 2.2f) { s ->
    addOval(androidx.compose.ui.geometry.Rect(3 * s, 3 * s, 21 * s, 21 * s))
    moveTo(12 * s, 7 * s); lineTo(12 * s, 12 * s); lineTo(15 * s, 14 * s)
}

@Composable
fun IconCheck(size: Dp, color: Color) = LineIcon(size, color, 3f) { s ->
    moveTo(5 * s, 12 * s); lineTo(10 * s, 17 * s); lineTo(19 * s, 7 * s)
}

@Composable
fun IconList(size: Dp, color: Color) = LineIcon(size, color, 2f) { s ->
    moveTo(4 * s, 7 * s); lineTo(20 * s, 7 * s); moveTo(4 * s, 12 * s); lineTo(20 * s, 12 * s); moveTo(4 * s, 17 * s); lineTo(14 * s, 17 * s)
}

@Composable
fun IconArrowUp(size: Dp, color: Color) = LineIcon(size, color, 2f) { s ->
    moveTo(12 * s, 20 * s); lineTo(12 * s, 4 * s); moveTo(5 * s, 11 * s); lineTo(12 * s, 4 * s); lineTo(19 * s, 11 * s)
}

@Composable
fun IconBolt(size: Dp, color: Color) = LineIcon(size, color, 2f) { s ->
    moveTo(13 * s, 2 * s); lineTo(4 * s, 14 * s); lineTo(12 * s, 14 * s); lineTo(11 * s, 22 * s); lineTo(20 * s, 10 * s); lineTo(12 * s, 10 * s); close()
}

@Composable
fun IconCar(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val p = Path().apply {
            moveTo(5 * s, 11 * s); lineTo(6.5f * s, 6.5f * s); cubicTo(6.8f * s, 5.6f * s, 7.5f * s, 5 * s, 8.4f * s, 5 * s)
            lineTo(15.6f * s, 5 * s); cubicTo(16.5f * s, 5 * s, 17.2f * s, 5.6f * s, 17.5f * s, 6.5f * s); lineTo(19 * s, 11 * s)
            lineTo(19 * s, 17 * s); lineTo(16 * s, 17 * s); lineTo(16 * s, 16 * s); lineTo(8 * s, 16 * s); lineTo(8 * s, 17 * s); lineTo(5 * s, 17 * s); close()
        }
        drawPath(p, color)
    }
}

@Composable
fun AppLogo(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(PS.Warm), contentAlignment = Alignment.Center) {
        IconPin(size * 0.55f, PS.Ink)
    }
}

@Composable
fun VoiceBars(active: Boolean, modifier: Modifier = Modifier) {
    val hs = listOf(18, 34, 46, 28, 40, 20, 32)
    Row(modifier.height(46.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hs.forEach { h ->
            Box(Modifier.width(8.dp).height((if (active) h else h / 2).dp).clip(RoundedCornerShape(4.dp)).background(PS.Warm))
        }
    }
}

@Composable
fun QrCode(text: String, size: Dp) {
    val bmp = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240)
        Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until m.width) for (y in 0 until m.height) setPixel(x, y, if (m[x, y]) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(6.dp)) {
        Image(bmp.asImageBitmap(), contentDescription = "동승자 참여 QR", modifier = Modifier.size(size - 12.dp))
    }
}

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

fun won(v: Int) = "%,d원".format(v)
fun mins(sec: Int) = ((sec + 59) / 60).coerceAtLeast(0)
fun durText(sec: Long, ceil: Boolean = true): String { val m = if (ceil) (sec + 59) / 60 else sec / 60; return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분" }

