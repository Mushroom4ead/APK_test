package com.example.speedtest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.max

/**
 * График скорости в реальном времени: линия + заливка под ней.
 * Ось X — время (номер сэмпла), ось Y — Мбит/с (авто-масштаб).
 */
@Composable
fun LiveGraph(
    samples: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val w = size.width
            val h = size.height

            // сетка (3 горизонтальные линии)
            val grid = Color(0xFF2A2E37)
            for (k in 1..3) {
                val y = h * k / 4f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            if (samples.size < 2) return@Canvas

            val maxV = max(samples.max(), 1f) * 1.15f
            val n = samples.size
            fun px(i: Int) = w * i / (n - 1).toFloat()
            fun py(v: Float) = h - (v / maxV) * h

            // заливка
            val fill = Path().apply {
                moveTo(0f, h)
                lineTo(px(0), py(samples[0]))
                for (i in 1 until n) lineTo(px(i), py(samples[i]))
                lineTo(px(n - 1), h)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.02f))
                )
            )

            // линия
            val line = Path().apply {
                moveTo(px(0), py(samples[0]))
                for (i in 1 until n) lineTo(px(i), py(samples[i]))
            }
            drawPath(line, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }

        if (samples.isNotEmpty()) {
            Text(
                text = String.format("макс %.1f Мбит/с", samples.max()),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
    }
}
