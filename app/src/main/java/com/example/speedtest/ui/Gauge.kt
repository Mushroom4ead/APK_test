package com.example.speedtest.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Круговой спидометр (дуга 270°). Значение отображается по логарифмической
 * шкале, чтобы одинаково хорошо читались и 5 Мбит/с, и 500 Мбит/с.
 */
@Composable
fun SpeedGauge(
    value: Double,          // текущее значение, Мбит/с
    unit: String = "Мбит/с",
    caption: String = "",
    accent: Color,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat().coerceAtLeast(0f),
        label = "gauge"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(24.dp)
        ) {
            val startAngle = 135f
            val sweep = 270f
            val stroke = size.minDimension * 0.09f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - stroke, size.height - stroke
            )
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            // фон дуги
            drawArc(
                color = Color(0xFF2A2E37),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // заполненная часть (лог. шкала до 1000 Мбит/с)
            val maxVal = 1000f
            val frac = if (animated <= 1f) animated / 10f
            else (ln(animated) / ln(maxVal)).coerceIn(0f, 1f)

            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accent.copy(alpha = 0.5f), accent)
                ),
                startAngle = startAngle,
                sweepAngle = sweep * frac,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // стрелка
            val angleRad = Math.toRadians((startAngle + sweep * frac).toDouble())
            val r = arcSize.minDimension / 2f - stroke
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(
                color = accent,
                start = Offset(cx, cy),
                end = Offset(
                    cx + r * cos(angleRad).toFloat(),
                    cy + r * sin(angleRad).toFloat()
                ),
                strokeWidth = stroke * 0.35f,
                cap = StrokeCap.Round
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (animated <= 0f) "0.0" else String.format("%.1f", animated),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = unit,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
