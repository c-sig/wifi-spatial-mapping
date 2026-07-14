package com.statproj.wifispatial.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.SurfaceContainer

/**
 * Variable to visualize on the heatmap.
 */
enum class HeatmapVariable(val label: String) {
    DBM("dBm"),
    DOWNLOAD("DL Mbps"),
    UPLOAD("UL Mbps")
}

// ── Gradient color stops: Deep Blue (worst) → Cyan → Yellow → Red (best) ──
private val HeatmapDarkNavy = Color(0xFF000040)
private val HeatmapPurple   = Color(0xFF4B0082)
private val HeatmapMagenta  = Color(0xFFB22222)
private val HeatmapOrange   = Color(0xFFFF8C00)
private val HeatmapYellow   = Color(0xFFFFD700)

private val GradientStops = listOf(
    0.00f to HeatmapDarkNavy,
    0.25f to HeatmapPurple,
    0.50f to HeatmapMagenta,
    0.75f to HeatmapOrange,
    1.00f to HeatmapYellow
)

/**
 * Interpolates through the 4-stop gradient at the given [fraction] ∈ [0, 1].
 */
private fun gradientColorAt(fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    for (i in 0 until GradientStops.size - 1) {
        val (startF, startC) = GradientStops[i]
        val (endF, endC) = GradientStops[i + 1]
        if (t in startF..endF) {
            val local = if (endF == startF) 0f else (t - startF) / (endF - startF)
            return lerp(startC, endC, local)
        }
    }
    return GradientStops.last().second
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

/**
 * Extracts the numeric value from a [WifiMeasurement] for the given [variable].
 * Returns null if the measurement doesn't have data for that variable.
 */
private fun WifiMeasurement.valueFor(variable: HeatmapVariable): Double? = when (variable) {
    HeatmapVariable.DBM -> wifi_dbm.toDouble()
    HeatmapVariable.DOWNLOAD -> dl_mbps
    HeatmapVariable.UPLOAD -> ul_mbps
}

/**
 * A Compose Canvas that renders a Wi-Fi spatial heatmap.
 *
 * Features:
 * - Polygon outline from [corners], filled with subtle alpha
 * - Data points as colored circles at (x_coord, y_coord)
 * - Color coding by [selectedVariable] using a red→green gradient
 * - Optional radial gradient overlay around each point
 * - Color legend bar at the bottom
 * - Corner markers (small squares)
 * - Pinch-to-zoom and pan gestures
 *
 * The coordinate system is derived from the bounding box of all points
 * (corners + measurements) with added margin, mapped to canvas pixels.
 */
@Composable
fun HeatmapCanvas(
    corners: List<FloorPlanCorner>,
    measurements: List<WifiMeasurement>,
    selectedVariable: HeatmapVariable,
    showGradient: Boolean,
    modifier: Modifier = Modifier
) {
    // ── Gesture state ───────────────────────────────────────────────
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()

    // ── Compute value range for color mapping ───────────────────────
    val values = remember(measurements, selectedVariable) {
        measurements.mapNotNull { it.valueFor(selectedVariable) }
    }
    val minValue = remember(values, selectedVariable) {
        if (selectedVariable == HeatmapVariable.DBM) -80.0
        else 0.0
    }
    val maxValue = remember(values, selectedVariable) {
        if (selectedVariable == HeatmapVariable.DBM) -30.0
        else values.maxOrNull() ?: 1.0
    }

    Column(modifier = modifier) {
        // ── Canvas ──────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SurfaceContainer, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset = Offset(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // ── Bounding box of ALL points ──────────────────────
            val allX = corners.map { it.x_coord } + measurements.map { it.x_coord }
            val allY = corners.map { it.y_coord } + measurements.map { it.y_coord }
            if (allX.isEmpty() || allY.isEmpty()) return@Canvas

            val dataMinX = allX.min()
            val dataMaxX = allX.max()
            val dataMinY = allY.min()
            val dataMaxY = allY.max()

            val dataW = (dataMaxX - dataMinX).coerceAtLeast(0.001)
            val dataH = (dataMaxY - dataMinY).coerceAtLeast(0.001)
            val margin = maxOf(dataW, dataH) * 0.1

            val worldMinX = dataMinX - margin
            val worldMaxX = dataMaxX + margin
            val worldMinY = dataMinY - margin
            val worldMaxY = dataMaxY + margin
            val worldW = worldMaxX - worldMinX
            val worldH = worldMaxY - worldMinY

            // Maintain aspect ratio
            val scaleX = canvasW / worldW.toFloat()
            val scaleY = canvasH / worldH.toFloat()
            val uniformScale = minOf(scaleX, scaleY)
            val offsetX = (canvasW - worldW.toFloat() * uniformScale) / 2f
            val offsetY = (canvasH - worldH.toFloat() * uniformScale) / 2f

            fun mapX(x: Double): Float = offsetX + ((x - worldMinX) * uniformScale).toFloat()
            fun mapY(y: Double): Float = offsetY + ((worldMaxY - y) * uniformScale).toFloat() // flip Y

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, Offset(canvasW / 2f, canvasH / 2f))
            }) {
                // ── 1. Polygon fill + outline ───────────────────
                if (corners.size >= 3) {
                    val sorted = corners.sortedBy { it.corner_order }
                    val path = Path().apply {
                        moveTo(mapX(sorted[0].x_coord), mapY(sorted[0].y_coord))
                        for (i in 1 until sorted.size) {
                            lineTo(mapX(sorted[i].x_coord), mapY(sorted[i].y_coord))
                        }
                        close()
                    }
                    // Subtle fill
                    drawPath(path, color = Primary.copy(alpha = 0.08f))
                    // Outline
                    drawPath(path, color = PrimaryLight.copy(alpha = 0.6f), style = Stroke(width = 2f))
                }

                // ── 2. Corner markers (small squares) ───────────
                val cornerSize = 8f
                corners.forEach { c ->
                    val cx = mapX(c.x_coord)
                    val cy = mapY(c.y_coord)
                    drawRect(
                        color = PrimaryLight,
                        topLeft = Offset(cx - cornerSize / 2, cy - cornerSize / 2),
                        size = Size(cornerSize, cornerSize)
                    )
                }

                // ── 3. Data points ──────────────────────────────
                measurements.forEach { m ->
                    val raw = m.valueFor(selectedVariable) ?: return@forEach
                    val fraction = if (maxValue == minValue) 0.5f
                    else ((raw - minValue) / (maxValue - minValue)).toFloat()
                    val color = gradientColorAt(fraction)
                    val px = mapX(m.x_coord)
                    val py = mapY(m.y_coord)

                    // Optional radial gradient overlay
                    if (showGradient) {
                        val gradientRadius = 40f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0f)),
                                center = Offset(px, py),
                                radius = gradientRadius
                            ),
                            radius = gradientRadius,
                            center = Offset(px, py)
                        )
                    }

                    // Solid data point
                    drawCircle(color = color, radius = 6f, center = Offset(px, py))
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.4f),
                        radius = 6f,
                        center = Offset(px, py),
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Legend bar ──────────────────────────────────────────────
        LegendBar(
            minLabel = formatValue(minValue, selectedVariable),
            maxLabel = formatValue(maxValue, selectedVariable),
            variableLabel = selectedVariable.label
        )
    }
}

private fun formatValue(value: Double, variable: HeatmapVariable): String = when (variable) {
    HeatmapVariable.DBM -> "${value.toInt()} dBm"
    HeatmapVariable.DOWNLOAD -> String.format("%.1f Mbps", value)
    HeatmapVariable.UPLOAD -> String.format("%.1f Mbps", value)
}

/**
 * Horizontal gradient legend bar showing min → max color mapping.
 */
@Composable
private fun LegendBar(
    minLabel: String,
    maxLabel: String,
    variableLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = variableLabel,
            style = MaterialTheme.typography.labelMedium,
            color = PrimaryLight,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(HeatmapDarkNavy, HeatmapPurple, HeatmapMagenta, HeatmapOrange, HeatmapYellow)
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Worst",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "→",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Best",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant
            )
        }
    }
}
