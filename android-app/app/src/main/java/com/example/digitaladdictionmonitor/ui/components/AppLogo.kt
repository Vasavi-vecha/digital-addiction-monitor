package com.example.digitaladdictionmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.digitaladdictionmonitor.ui.theme.BrandGradientEnd
import com.example.digitaladdictionmonitor.ui.theme.BrandGradientStart

// App mark: a phone silhouette with a heartbeat/pulse line through it, on a
// rounded gradient badge -- reads as "monitoring your phone habits" without
// needing an external image asset. Reused at small size in the top bar and
// large size on the home header so the brand mark stays consistent.
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        drawRoundRect(
            brush = Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)),
            cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)
        )

        val phoneWidth = w * 0.36f
        val phoneHeight = h * 0.58f
        val phoneLeft = (w - phoneWidth) / 2f
        val phoneTop = (h - phoneHeight) / 2f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(phoneLeft, phoneTop),
            size = Size(phoneWidth, phoneHeight),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        val midY = phoneTop + phoneHeight / 2f
        val pulsePath = Path().apply {
            moveTo(phoneLeft + phoneWidth * 0.10f, midY)
            lineTo(phoneLeft + phoneWidth * 0.32f, midY)
            lineTo(phoneLeft + phoneWidth * 0.45f, midY - phoneHeight * 0.24f)
            lineTo(phoneLeft + phoneWidth * 0.58f, midY + phoneHeight * 0.30f)
            lineTo(phoneLeft + phoneWidth * 0.72f, midY)
            lineTo(phoneLeft + phoneWidth * 0.90f, midY)
        }
        drawPath(
            path = pulsePath,
            color = BrandGradientStart,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
