package com.statproj.wifispatial.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.apache.commons.math3.stat.regression.SimpleRegression
import com.statproj.wifispatial.stats.AnovaResult
import com.statproj.wifispatial.ui.components.AnovaTable
import com.statproj.wifispatial.ui.components.InterpretationSection
import com.statproj.wifispatial.ui.components.InteractionPlot
import com.statproj.wifispatial.ui.components.ScatterPlot
import com.statproj.wifispatial.ui.theme.ChartBlue
import com.statproj.wifispatial.ui.theme.ChartGreen
import com.statproj.wifispatial.ui.theme.Error
import com.statproj.wifispatial.ui.theme.ErrorLight
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.Success
import com.statproj.wifispatial.ui.theme.SuccessLight
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.Tertiary
import com.statproj.wifispatial.ui.theme.TertiaryLight
import com.statproj.wifispatial.ui.theme.Warning
import com.statproj.wifispatial.viewmodel.ReportViewModel

/**
 * Report screen with statistical analysis, scatterplots, ANOVA tables,
 * Tukey HSD results, and interaction plots.
 */
@Composable
fun ReportScreen(viewModel: ReportViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // Reload data whenever this screen is composed (navigated to)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Header ──────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Analytics,
                contentDescription = null,
                tint = Tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Statistical Report",
                style = MaterialTheme.typography.headlineSmall,
                color = TertiaryLight,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${uiState.totalCount} measurements loaded",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Generate Report Button ──────────────────────────────────
        Button(
            onClick = { viewModel.generateReport() },
            enabled = !uiState.isLoading && uiState.totalCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onTertiary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Generating...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.Analytics, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Report", fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Error Message ───────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Error.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = ErrorLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ── Report Content ──────────────────────────────────────────
        val report = uiState.report
        if (report != null) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Linear Regression Section ───────────────────────────
            SectionHeader(
                icon = Icons.Filled.ShowChart,
                title = "Linear Regression"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Regression: Download
            report.regressionDl?.let { reg ->
                Text(
                    text = "dBm vs Download Speed",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                val dlPoints = uiState.measurements
                    .filter { it.dl_mbps != null }
                    .map { it.wifi_dbm.toDouble() to it.dl_mbps!! }
                
                ScatterPlot(
                    dataPoints = dlPoints,
                    slope = reg.slope,
                    intercept = reg.intercept,
                    xLabel = "dBm",
                    yLabel = "DL (Mbps)",
                    rSquared = reg.rSquared,
                    pointColor = ChartBlue,
                    lineColor = Primary
                )

                Spacer(modifier = Modifier.height(8.dp))
                RegressionSummaryCard(
                    label = "Download",
                    slope = reg.slope,
                    intercept = reg.intercept,
                    r = reg.r,
                    rSquared = reg.rSquared,
                    n = reg.n
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Regression: Upload
            report.regressionUl?.let { reg ->
                Text(
                    text = "dBm vs Upload Speed",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                val ulPoints = uiState.measurements
                    .filter { it.ul_mbps != null }
                    .map { it.wifi_dbm.toDouble() to it.ul_mbps!! }

                ScatterPlot(
                    dataPoints = ulPoints,
                    slope = reg.slope,
                    intercept = reg.intercept,
                    xLabel = "dBm",
                    yLabel = "UL (Mbps)",
                    rSquared = reg.rSquared,
                    pointColor = ChartGreen,
                    lineColor = Success
                )

                Spacer(modifier = Modifier.height(8.dp))
                RegressionSummaryCard(
                    label = "Upload",
                    slope = reg.slope,
                    intercept = reg.intercept,
                    r = reg.r,
                    rSquared = reg.rSquared,
                    n = reg.n
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(24.dp))

            // ── ANOVA Section ───────────────────────────────────────
            SectionHeader(
                icon = Icons.Filled.TableChart,
                title = "Two-Way ANOVA"
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ANOVA tables + Tukey HSD + Interaction plots
            report.anovaDbm?.let { anova ->
                AnovaSection(anova, uiState.measurements)
                Spacer(modifier = Modifier.height(20.dp))
            }

            report.anovaDl?.let { anova ->
                AnovaSection(anova, uiState.measurements)
                Spacer(modifier = Modifier.height(20.dp))
            }

            report.anovaUl?.let { anova ->
                AnovaSection(anova, uiState.measurements)
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(24.dp))

            // ── Interpretation Section ───────────────────────────────
            SectionHeader(
                icon = Icons.Filled.Analytics,
                title = "Statistical Interpretation"
            )
            Spacer(modifier = Modifier.height(12.dp))

            InterpretationSection(report = report)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnovaSection(
    anova: AnovaResult,
    measurements: List<com.statproj.wifispatial.data.WifiMeasurement>
) {
    // ANOVA table
    AnovaTable(result = anova, modifier = Modifier.fillMaxWidth())

    // Tukey HSD results (if any significant)
    if (anova.tukeyResults.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Tukey HSD Post-Hoc (${anova.dependentVar})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Warning,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                anova.tukeyResults.forEach { tukey ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${tukey.group1} vs ${tukey.group2}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Δ=${"%.3f".format(tukey.meanDiff)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "q=${"%.3f".format(tukey.qStat)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatPValue(tukey.pValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tukey.significant) Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (tukey.significant) FontWeight.Bold else FontWeight.Normal
                        )
                        if (tukey.significant) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "*",
                                color = Warning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Interaction plot
    Spacer(modifier = Modifier.height(12.dp))

    val factorALevels = measurements.map { it.floor_lvl }.distinct().sorted()
    val factorBLevels = measurements.map { it.building_pos }.distinct().sorted()

    val extractValue: (com.statproj.wifispatial.data.WifiMeasurement) -> Double? = when (anova.dependentVar) {
        "wifi_dbm" -> { m -> m.wifi_dbm.toDouble() }
        "dl_mbps" -> { m -> m.dl_mbps }
        "ul_mbps" -> { m -> m.ul_mbps }
        else -> { _ -> null }
    }

    val means = mutableMapOf<Pair<String, String>, Double>()
    for (aLevel in factorALevels) {
        for (bLevel in factorBLevels) {
            val values = measurements
                .filter { it.floor_lvl == aLevel && it.building_pos == bLevel }
                .mapNotNull(extractValue)
            if (values.isNotEmpty()) {
                means[Pair(aLevel, bLevel)] = values.average()
            }
        }
    }

    InteractionPlot(
        factorALevels = factorALevels,
        factorBLevels = factorBLevels,
        means = means,
        title = "Interaction: ${anova.dependentVar}",
        yLabel = anova.dependentVar,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = PrimaryLight,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryLight,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RegressionSummaryCard(
    label: String,
    slope: Double,
    intercept: Double,
    r: Double,
    rSquared: Double,
    n: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Slope", formatStatValue(slope))
                StatItem("Intercept", formatStatValue(intercept))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("r", "${"%.4f".format(r)}")
                StatItem("R²", "${"%.4f".format(rSquared)}")
                StatItem("n", "$n")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Format a stat value with scientific notation when needed.
 * Values like 2,022,237.878 become "2.02e6" for readability.
 */
private fun formatStatValue(value: Double): String {
    val absVal = kotlin.math.abs(value)
    return when {
        absVal == 0.0 -> "0.0000"
        absVal >= 100000 -> {
            val exp = kotlin.math.floor(kotlin.math.log10(absVal)).toInt()
            val mantissa = value / Math.pow(10.0, exp.toDouble())
            "${"%.2f".format(mantissa)}e$exp"
        }
        absVal >= 1.0 -> "%.4f".format(value)
        absVal >= 0.001 -> "%.6f".format(value)
        else -> {
            val exp = kotlin.math.floor(kotlin.math.log10(absVal)).toInt()
            val mantissa = value / Math.pow(10.0, exp.toDouble())
            "${"%.2f".format(mantissa)}e$exp"
        }
    }
}

/**
 * Format p-value using standard statistical convention.
 * - Tiny p-values → "p<.0001" (never "p=0.0000")
 * - Small p-values → "p=0.0023"
 * - Non-significant → "p=0.4521"
 */
private fun formatPValue(value: Double): String {
    return when {
        value <= 0.0 || value < 0.0001 -> "p<.0001"
        value < 0.001 -> "p=${"%.4f".format(value)}"
        else -> "p=${"%.4f".format(value)}"
    }
}

/**
 * Fit a local OLS regression on the given (x, y) pairs.
 * Used to compute slope/intercept for the dBm-scale chart view
 * so the best-fit line actually matches the displayed axis.
 *
 * @return Pair of (slope, intercept)
 */
private fun fitLocalRegression(points: List<Pair<Double, Double>>): Pair<Double, Double> {
    if (points.size < 2) return 0.0 to 0.0
    val reg = SimpleRegression()
    points.forEach { (x, y) -> reg.addData(x, y) }
    return reg.slope to reg.intercept
}
