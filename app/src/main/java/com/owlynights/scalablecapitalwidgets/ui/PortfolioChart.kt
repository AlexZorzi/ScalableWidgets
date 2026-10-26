package com.owlynights.scalablecapitalwidgets.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.data.ChartSeries
import com.owlynights.scalablecapitalwidgets.ui.theme.changeColor

/** Line chart with a dashed baseline; intraday charts grey out the previous day. */
@Composable
fun PortfolioChart(series: ChartSeries, intraday: Boolean, modifier: Modifier = Modifier) {
    val points = series.points
    if (points.size < 2) return
    val view = series.view(intraday)
    val baseline = view.baseline ?: points.first().value
    val up = points.last().value >= baseline
    val lineColor = changeColor(up)
    val gainColor = changeColor(true)
    val lossColor = changeColor(false)
    val pastColor = MaterialTheme.colorScheme.outline
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val values = points.map { it.value } + baseline
        var min = values.min()
        var max = values.max()
        if (max - min < 1e-9) {
            min -= 1
            max += 1
        }
        val stroke = 2.dp.toPx()
        val top = stroke
        val height = size.height - stroke * 2
        fun x(i: Int) = i * size.width / (points.size - 1)
        fun y(v: Double) = (top + (max - v) / (max - min) * height).toFloat()

        val baselineY = y(baseline)
        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )

        val start = if (intraday) (view.splitIndex - 1).coerceAtLeast(0) else 0
        if (start > 0) {
            val past = Path().apply {
                moveTo(x(0), y(points[0].value))
                for (i in 1..start) lineTo(x(i), y(points[i].value))
            }
            drawPath(past, pastColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val line = Path().apply {
            moveTo(x(start), y(points[start].value))
            for (i in start + 1 until points.size) lineTo(x(i), y(points[i].value))
        }
        // Shade between the line and the previous close: gains above, losses below.
        val area = Path().apply {
            moveTo(x(start), baselineY)
            for (i in start until points.size) lineTo(x(i), y(points[i].value))
            lineTo(x(points.size - 1), baselineY)
            close()
        }
        val shaded = points.subList(start, points.size).map { y(it.value) }
        clipRect(bottom = baselineY) {
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(gainColor.copy(alpha = 0.32f), gainColor.copy(alpha = 0.03f)),
                    startY = shaded.min(),
                    endY = baselineY,
                ),
            )
        }
        clipRect(top = baselineY) {
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(lossColor.copy(alpha = 0.03f), lossColor.copy(alpha = 0.32f)),
                    startY = baselineY,
                    endY = shaded.max(),
                ),
            )
        }
        drawPath(line, lineColor, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
