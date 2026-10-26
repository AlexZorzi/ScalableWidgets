package com.owlynights.scalablecapitalwidgets.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.owlynights.scalablecapitalwidgets.data.ChartSeries

/**
 * A sparkline split into white alpha masks. Widgets stack them and tint each layer with a
 * theme color, so the line follows light/dark mode and dynamic colors without re-rendering.
 */
class SparklineLayers(
    val baseline: Bitmap?,
    val past: Bitmap?,
    val up: Bitmap?,
    val down: Bitmap?,
    /** Area between the line and the baseline where the value is above it. */
    val fillUp: Bitmap?,
    /** Area between the line and the baseline where the value is below it. */
    val fillDown: Bitmap?,
) {
    val isEmpty: Boolean get() = past == null && up == null && down == null
}

object Sparkline {

    /**
     * @param intraday colour the previous day as "past" and compare against the previous close
     * @param splitUpDown colour segments above/below the baseline separately (iOS style);
     *   otherwise the whole line goes into [SparklineLayers.up] or [SparklineLayers.down]
     *   depending on the overall change
     * @param withFill shade the area between the line and the baseline (Material style)
     */
    fun render(
        series: ChartSeries?,
        widthPx: Int,
        heightPx: Int,
        strokePx: Float,
        intraday: Boolean,
        splitUpDown: Boolean,
        withFill: Boolean,
        dashedBaseline: Boolean = false,
    ): SparklineLayers {
        val points = series?.points.orEmpty()
        if (points.size < 2 || widthPx <= 0 || heightPx <= 0) {
            return SparklineLayers(null, null, null, null, null, null)
        }
        val view = series!!.view(intraday)
        val baseline = view.baseline ?: points.first().value

        val values = points.map { it.value } + baseline
        var min = values.min()
        var max = values.max()
        if (max - min < 1e-9) {
            min -= 1.0
            max += 1.0
        }
        val inset = strokePx * 1.5f
        val drawHeight = heightPx - inset * 2
        val stepX = (widthPx - inset * 2) / (points.size - 1).toFloat()
        fun x(i: Int) = inset + i * stepX
        fun y(v: Double) = (inset + (max - v) / (max - min) * drawHeight).toFloat()
        val baselineY = y(baseline)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pastPath = Path()
        val upPath = Path()
        val downPath = Path()
        val overallUp = points.last().value >= baseline

        for (i in 1 until points.size) {
            val x0 = x(i - 1)
            val y0 = y(points[i - 1].value)
            val x1 = x(i)
            val y1 = y(points[i].value)
            when {
                intraday && i <= view.splitIndex - 1 -> pastPath.segment(x0, y0, x1, y1)
                !splitUpDown -> (if (overallUp) upPath else downPath).segment(x0, y0, x1, y1)
                else -> {
                    val above0 = y0 <= baselineY
                    val above1 = y1 <= baselineY
                    if (above0 == above1) {
                        (if (above0) upPath else downPath).segment(x0, y0, x1, y1)
                    } else {
                        val t = (baselineY - y0) / (y1 - y0)
                        val xc = x0 + (x1 - x0) * t
                        (if (above0) upPath else downPath).segment(x0, y0, xc, baselineY)
                        (if (above1) upPath else downPath).segment(xc, baselineY, x1, y1)
                    }
                }
            }
        }

        fun layer(draw: (Canvas) -> Unit): Bitmap =
            createBitmap(widthPx, heightPx).also { draw(Canvas(it)) }

        val baselineLayer = layer { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = (strokePx * 0.5f).coerceAtLeast(1f)
                if (dashedBaseline) pathEffect = DashPathEffect(floatArrayOf(strokePx * 2, strokePx * 2), 0f)
            }
            canvas.drawLine(0f, baselineY, widthPx.toFloat(), baselineY, paint)
        }
        // Shade from the line towards the baseline, strongest at the line, like a finance app.
        val fillStart = if (intraday) (view.splitIndex - 1).coerceIn(0, points.size - 2) else 0
        val area = Path().apply {
            moveTo(x(fillStart), baselineY)
            for (i in fillStart until points.size) lineTo(x(i), y(points[i].value))
            lineTo(x(points.size - 1), baselineY)
            close()
        }
        val shaded = points.subList(fillStart, points.size).map { y(it.value) }
        fun fill(above: Boolean): Bitmap? {
            val extreme = if (above) shaded.min() else shaded.max()
            if (if (above) extreme >= baselineY - 0.5f else extreme <= baselineY + 0.5f) return null
            return layer { canvas ->
                canvas.clipRect(
                    0f, if (above) 0f else baselineY,
                    widthPx.toFloat(), if (above) baselineY else heightPx.toFloat(),
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, extreme, 0f, baselineY,
                        Color.argb(120, 255, 255, 255), Color.argb(10, 255, 255, 255),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawPath(area, paint)
            }
        }

        return SparklineLayers(
            baseline = baselineLayer,
            past = pastPath.takeUnless { it.isEmpty }?.let { path -> layer { it.drawPath(path, linePaint) } },
            up = upPath.takeUnless { it.isEmpty }?.let { path -> layer { it.drawPath(path, linePaint) } },
            down = downPath.takeUnless { it.isEmpty }?.let { path -> layer { it.drawPath(path, linePaint) } },
            fillUp = if (withFill) fill(above = true) else null,
            fillDown = if (withFill) fill(above = false) else null,
        )
    }

    private fun Path.segment(x0: Float, y0: Float, x1: Float, y1: Float) {
        moveTo(x0, y0)
        lineTo(x1, y1)
    }
}
