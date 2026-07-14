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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.statproj.wifispatial.ui.theme.ChartColors
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Outline
import com.statproj.wifispatial.ui.theme.SurfaceContainer

/**
 * Interaction plot showing group means across factor levels.
 * Each line represents a level of Factor B, with Factor A levels on the X-axis.
 *
 * @param factorALevels Labels for X-axis (e.g., floor levels)
 * @param factorBLevels Labels for different lines (e.g., buildings)
 * @param means Map of (factorA, factorB) -> mean value
 * @param title Chart title
 * @param yLabel Y-axis label
 */
@Composable
fun InteractionPlot(
    factorALevels: List<String>,
    factorBLevels: List<String>,
    means: Map<Pair<String, String>, Double>,
    title: String,
    yLabel: String,
    modifier: Modifier = Modifier
) {
    if (factorALevels.isEmpty() || factorBLevels.isEmpty() || means.isEmpty()) return

    val allMeans = means.values.toList()
    val yMin = allMeans.min()
    val yMax = allMeans.max()
    val yRange = if (yMax - yMin == 0.0) 1.0 else (yMax - yMin) * 1.2
    val yStart = yMin - (yRange - (yMax - yMin)) / 2

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        val chartLeft = 60f
        val chartBottom = size.height - 50f
        val chartRight = size.width - 20f
        val chartTop = 40f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw grid
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        for (i in 1..4) {
            val y = chartTop + chartHeight * i / 5
            drawLine(
                color = Outline,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                pathEffect = dashEffect,
                strokeWidth = 0.5f
            )
        }

        // Draw axes
        drawLine(OnSurfaceVariant, Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), 1.5f)
        drawLine(OnSurfaceVariant, Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), 1.5f)

        // X-axis labels (Factor A levels)
        drawContext.canvas.nativeCanvas.apply {
            val labelPaint = android.graphics.Paint().apply {
                color = OnSurfaceVariant.hashCode()
                textSize = 22f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            factorALevels.forEachIndexed { i, label ->
                val x = chartLeft + chartWidth * (i + 0.5f) / factorALevels.size
                drawText(label, x, chartBottom + 30f, labelPaint)
            }

            // Title
            val titlePaint = android.graphics.Paint().apply {
                color = OnSurfaceVariant.hashCode()
                textSize = 26f
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(title, (chartLeft + chartRight) / 2, chartTop - 10f, titlePaint)
        }

        // Draw lines for each Factor B level
        factorBLevels.forEachIndexed { bIndex, bLevel ->
            val color = ChartColors[bIndex % ChartColors.size]
            val points = mutableListOf<Offset>()

            factorALevels.forEachIndexed { aIndex, aLevel ->
                val mean = means[Pair(aLevel, bLevel)] ?: return@forEachIndexed
                val x = chartLeft + chartWidth * (aIndex + 0.5f) / factorALevels.size
                val y = chartBottom - ((mean - yStart) / yRange * chartHeight).toFloat()
                points.add(Offset(x, y))

                // Draw point
                drawCircle(color = color, radius = 6f, center = Offset(x, y))
                drawCircle(color = SurfaceContainer, radius = 3f, center = Offset(x, y))
            }

            // Connect points with lines
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // Legend entry
            drawContext.canvas.nativeCanvas.apply {
                val legendPaint = android.graphics.Paint().apply {
                    this.color = color.hashCode()
                    textSize = 20f
                    isAntiAlias = true
                }
                val legendX = chartRight - 140f
                val legendY = chartTop + 20f + bIndex * 24f
                drawText("● $bLevel", legendX, legendY, legendPaint)
            }
        }
    }
}
