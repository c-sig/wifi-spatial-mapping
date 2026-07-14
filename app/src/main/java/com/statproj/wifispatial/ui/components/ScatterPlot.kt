package com.statproj.wifispatial.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.statproj.wifispatial.ui.theme.ChartBlue
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Outline
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Scatter plot with best-fit line rendered using Compose Canvas.
 *
 * @param dataPoints List of (x, y) data points
 * @param slope Regression line slope
 * @param intercept Regression line intercept
 * @param xLabel Label for X-axis
 * @param yLabel Label for Y-axis
 * @param rSquared R² value to display
 * @param pointColor Color for scatter points
 * @param lineColor Color for best-fit line
 */
@Composable
fun ScatterPlot(
    dataPoints: List<Pair<Double, Double>>,
    slope: Double,
    intercept: Double,
    xLabel: String,
    yLabel: String,
    rSquared: Double,
    pointColor: Color = ChartBlue,
    lineColor: Color = Primary,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    val xValues = dataPoints.map { it.first }
    val yValues = dataPoints.map { it.second }
    val xMin = xValues.min()
    val xMax = xValues.max()
    val yMin = yValues.min()
    val yMax = yValues.max()

    // Add margin to ranges
    val xRange = if (xMax - xMin == 0.0) 1.0 else (xMax - xMin) * 1.1
    val yRange = if (yMax - yMin == 0.0) 1.0 else (yMax - yMin) * 1.1
    val xStart = xMin - (xRange - (xMax - xMin)) / 2
    val yStart = yMin - (yRange - (yMax - yMin)) / 2

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        val chartLeft = 80f
        val chartBottom = size.height - 56f
        val chartRight = size.width - 20f
        val chartTop = 30f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw grid lines
        drawGridLines(chartLeft, chartTop, chartRight, chartBottom, 5, 5)

        // Draw axes
        drawLine(
            color = OnSurfaceVariant,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.5f
        )
        drawLine(
            color = OnSurfaceVariant,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartBottom),
            strokeWidth = 1.5f
        )

        // ── Y-axis tick labels ──────────────────────────────────────
        val tickPaint = android.graphics.Paint().apply {
            color = OnSurfaceVariant.hashCode()
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val yDivisions = 5
        for (i in 0..yDivisions) {
            val yVal = yStart + yRange * i / yDivisions
            val py = chartBottom - (chartHeight * i / yDivisions)
            drawContext.canvas.nativeCanvas.drawText(
                formatTickLabel(yVal),
                chartLeft - 8f,
                py + 6f,
                tickPaint
            )
        }

        // ── X-axis tick labels ──────────────────────────────────────
        val xTickPaint = android.graphics.Paint().apply {
            color = OnSurfaceVariant.hashCode()
            textSize = 18f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val xDivisions = 4
        for (i in 0..xDivisions) {
            val xVal = xStart + xRange * i / xDivisions
            val px = chartLeft + (chartWidth * i / xDivisions)
            drawContext.canvas.nativeCanvas.drawText(
                formatTickLabel(xVal),
                px,
                chartBottom + 28f,
                xTickPaint
            )
        }

        // Map and draw data points
        dataPoints.forEach { (x, y) ->
            val px = chartLeft + ((x - xStart) / xRange * chartWidth).toFloat()
            val py = chartBottom - ((y - yStart) / yRange * chartHeight).toFloat()

            // Point shadow
            drawCircle(
                color = pointColor.copy(alpha = 0.3f),
                radius = 5f,
                center = Offset(px, py)
            )
            // Point
            drawCircle(
                color = pointColor,
                radius = 3f,
                center = Offset(px, py)
            )
        }

        // Draw best-fit line
        val lineY1 = slope * xStart + intercept
        val lineY2 = slope * (xStart + xRange) + intercept
        val px1 = chartLeft
        val py1 = chartBottom - ((lineY1 - yStart) / yRange * chartHeight).toFloat()
        val px2 = chartRight
        val py2 = chartBottom - ((lineY2 - yStart) / yRange * chartHeight).toFloat()

        drawLine(
            color = lineColor,
            start = Offset(px1, py1.coerceIn(chartTop, chartBottom)),
            end = Offset(px2, py2.coerceIn(chartTop, chartBottom)),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )

        // Draw R² label
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = lineColor.hashCode()
                textSize = 28f
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }
            drawText(
                "R² = ${"%.4f".format(rSquared)}",
                chartRight - 200f,
                chartTop + 20f,
                paint
            )
        }

        // Axis labels
        drawContext.canvas.nativeCanvas.apply {
            val labelPaint = android.graphics.Paint().apply {
                color = OnSurfaceVariant.hashCode()
                textSize = 22f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            // X-axis label
            drawText(xLabel, (chartLeft + chartRight) / 2, size.height - 2f, labelPaint)

            // Y-axis label (rotated)
            save()
            rotate(-90f, 14f, (chartTop + chartBottom) / 2)
            drawText(yLabel, 14f, (chartTop + chartBottom) / 2, labelPaint)
            restore()
        }
    }
}

/**
 * Format tick labels with scientific notation for very small/large values.
 */
private fun formatTickLabel(value: Double): String {
    if (value == 0.0) return "0"
    val absVal = abs(value)
    return when {
        absVal < 0.001 -> {
            // Scientific notation: 1.5e-5
            val exp = kotlin.math.floor(log10(absVal)).toInt()
            val mantissa = value / 10.0.pow(exp)
            "${"%.1f".format(mantissa)}e$exp"
        }
        absVal < 1.0 -> "%.4f".format(value)
        absVal < 100.0 -> "%.1f".format(value)
        absVal < 10000.0 -> "%.0f".format(value)
        else -> {
            val exp = kotlin.math.floor(log10(absVal)).toInt()
            val mantissa = value / 10.0.pow(exp)
            "${"%.1f".format(mantissa)}e$exp"
        }
    }
}

/**
 * Draw grid lines within the chart area.
 */
private fun DrawScope.drawGridLines(
    left: Float, top: Float, right: Float, bottom: Float,
    xDivisions: Int, yDivisions: Int
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

    for (i in 1 until xDivisions) {
        val x = left + (right - left) * i / xDivisions
        drawLine(
            color = Outline,
            start = Offset(x, top),
            end = Offset(x, bottom),
            pathEffect = dashEffect,
            strokeWidth = 0.5f
        )
    }
    for (i in 1 until yDivisions) {
        val y = top + (bottom - top) * i / yDivisions
        drawLine(
            color = Outline,
            start = Offset(left, y),
            end = Offset(right, y),
            pathEffect = dashEffect,
            strokeWidth = 0.5f
        )
    }
}
