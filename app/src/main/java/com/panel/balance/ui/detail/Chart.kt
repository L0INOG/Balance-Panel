package com.panel.balance.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/**
 * 平滑折线 + 渐变填充的余额趋势图，带入场动画。
 */
@Composable
fun BalanceLineChart(values: List<Float>, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(0.0001f)
        val w = size.width
        val h = size.height
        val pad = 12.dp.toPx()

        fun pt(i: Int): Offset {
            val x = w * i / (values.size - 1f)
            val y = h - pad - ((values[i] - minV) / range) * (h - 2 * pad)
            return Offset(x, y)
        }

        // 网格线
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            val y = pad + f * (h - 2 * pad)
            drawLine(
                gridColor,
                Offset(0f, y),
                Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f)),
            )
        }

        val path = Path()
        val p0 = pt(0)
        path.moveTo(p0.x, p0.y)
        for (i in 1 until values.size) {
            val a = pt(i - 1)
            val b = pt(i)
            val midX = (a.x + b.x) / 2f
            path.cubicTo(midX, a.y, midX, b.y, b.x, b.y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        clipRect(right = w * anim.value) {
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = 0f,
                    endY = h,
                ),
            )
            drawPath(
                path,
                lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (anim.value >= 1f) {
                val last = pt(values.size - 1)
                drawCircle(lineColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = last)
                drawCircle(lineColor, radius = 3.dp.toPx(), center = last)
            }
        }
    }
}
