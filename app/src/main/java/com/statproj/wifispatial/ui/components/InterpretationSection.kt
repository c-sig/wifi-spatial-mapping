package com.statproj.wifispatial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statproj.wifispatial.stats.AnovaResult
import com.statproj.wifispatial.stats.RegressionResult
import com.statproj.wifispatial.stats.StatsReport
import com.statproj.wifispatial.ui.theme.Error
import com.statproj.wifispatial.ui.theme.OnSurface
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.Success
import com.statproj.wifispatial.ui.theme.SuccessLight
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.Tertiary
import com.statproj.wifispatial.ui.theme.TertiaryLight
import com.statproj.wifispatial.ui.theme.Warning

private const val ALPHA = 0.05

/**
 * Generates a full statistical interpretation section from the report.
 *
 * Includes:
 * - Formal null/alternative hypotheses for each ANOVA factor
 * - Verdict (reject/fail to reject H₀) based on p < α
 * - Post-hoc observations summarizing which groups differ
 * - Regression interpretation
 * - Effect size commentary
 */
@Composable
fun InterpretationSection(
    report: StatsReport,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ── Regression Interpretation ────────────────────────────────
        report.regressionDl?.let { reg ->
            RegressionInterpretation(
                reg = reg,
                dvName = "Download Speed",
                dvUnit = "Mbps"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        report.regressionUl?.let { reg ->
            RegressionInterpretation(
                reg = reg,
                dvName = "Upload Speed",
                dvUnit = "Mbps"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        // ── ANOVA Interpretations ───────────────────────────────────
        report.anovaDbm?.let { anova ->
            AnovaInterpretation(anova = anova, dvLabel = "Wi-Fi Signal Strength (dBm)")
            Spacer(modifier = Modifier.height(16.dp))
        }

        report.anovaDl?.let { anova ->
            AnovaInterpretation(anova = anova, dvLabel = "Download Speed (Mbps)")
            Spacer(modifier = Modifier.height(16.dp))
        }

        report.anovaUl?.let { anova ->
            AnovaInterpretation(anova = anova, dvLabel = "Upload Speed (Mbps)")
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Regression Interpretation
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun RegressionInterpretation(
    reg: RegressionResult,
    dvName: String,
    dvUnit: String
) {
    val direction = if (reg.slope > 0) "positive" else "negative"
    val strength = when {
        kotlin.math.abs(reg.r) >= 0.7 -> "strong"
        kotlin.math.abs(reg.r) >= 0.4 -> "moderate"
        kotlin.math.abs(reg.r) >= 0.2 -> "weak"
        else -> "negligible"
    }
    val varianceExplained = "%.1f".format(reg.rSquared * 100)
    val significant = kotlin.math.abs(reg.r) >= 0.2 && reg.n >= 30

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = PrimaryLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Regression: P_mW → $dvName",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryLight,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hypotheses
            HypothesisText(
                "H₀: There is no linear relationship between signal power (P_mW) and $dvName."
            )
            HypothesisText(
                "H₁: There is a significant linear relationship between signal power (P_mW) and $dvName."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Verdict
            VerdictBadge(
                rejected = significant,
                summary = if (significant)
                    "Reject H₀ — A $strength $direction linear relationship exists (r = ${"%.4f".format(reg.r)}, R² = ${"%.4f".format(reg.rSquared)}, n = ${reg.n})."
                else
                    "Fail to reject H₀ — No meaningful linear relationship detected (r = ${"%.4f".format(reg.r)}, R² = ${"%.4f".format(reg.rSquared)}, n = ${reg.n})."
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Interpretation
            Text(
                text = buildAnnotatedString {
                    append("Signal power explains ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Primary)) {
                        append("$varianceExplained%")
                    }
                    append(" of the variance in $dvName. ")
                    if (reg.rSquared < 0.25) {
                        append("This suggests that factors other than signal strength (e.g., network congestion, AP load, interference) dominate speed variation.")
                    } else if (reg.rSquared < 0.5) {
                        append("Signal power is a meaningful but not dominant predictor. Other environmental factors also contribute substantially.")
                    } else {
                        append("Signal power is a strong predictor of $dvName in this environment.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ANOVA Interpretation
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun AnovaInterpretation(
    anova: AnovaResult,
    dvLabel: String
) {
    val sigA = anova.pA < ALPHA
    val sigB = anova.pB < ALPHA
    val sigAB = anova.pAB < ALPHA

    val factorADisplay = displayFactorName(anova.factorAName)
    val factorBDisplay = displayFactorName(anova.factorBName)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Text(
                text = "ANOVA Interpretation: $dvLabel",
                style = MaterialTheme.typography.titleSmall,
                color = Warning,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Factor A ────────────────────────────────────────────
            FactorHypothesisBlock(
                factorName = factorADisplay,
                dvLabel = dvLabel,
                fValue = anova.fA,
                pValue = anova.pA,
                df1 = anova.dfA,
                df2 = anova.dfW,
                isSignificant = sigA
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Factor B ────────────────────────────────────────────
            FactorHypothesisBlock(
                factorName = factorBDisplay,
                dvLabel = dvLabel,
                fValue = anova.fB,
                pValue = anova.pB,
                df1 = anova.dfB,
                df2 = anova.dfW,
                isSignificant = sigB
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Interaction ─────────────────────────────────────────
            FactorHypothesisBlock(
                factorName = "$factorADisplay × $factorBDisplay",
                dvLabel = dvLabel,
                fValue = anova.fAB,
                pValue = anova.pAB,
                df1 = anova.dfAB,
                df2 = anova.dfW,
                isSignificant = sigAB,
                isInteraction = true
            )

            // ── Post-Hoc Summary ────────────────────────────────────
            if (anova.tukeyResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Tertiary.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Post-Hoc Observations (Tukey HSD)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TertiaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val sigPairs = anova.tukeyResults.filter { it.significant }
                val nonSigPairs = anova.tukeyResults.filter { !it.significant }

                if (sigPairs.isNotEmpty()) {
                    // Find the pair with the largest and smallest significant difference
                    val maxPair = sigPairs.maxByOrNull { it.meanDiff }!!
                    val minPair = sigPairs.minByOrNull { it.meanDiff }!!

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Success)) {
                                append("${sigPairs.size} of ${anova.tukeyResults.size}")
                            }
                            append(" pairwise comparisons are statistically significant (p < .05).")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Largest difference
                    PostHocBullet(
                        text = "Largest difference: ${maxPair.group1} vs ${maxPair.group2} " +
                                "(Δ = ${"%.3f".format(maxPair.meanDiff)}, q = ${"%.3f".format(maxPair.qStat)}, " +
                                "${formatPValueShort(maxPair.pValue)})",
                        color = Warning
                    )

                    // Smallest significant difference
                    if (minPair != maxPair) {
                        PostHocBullet(
                            text = "Smallest significant difference: ${minPair.group1} vs ${minPair.group2} " +
                                    "(Δ = ${"%.3f".format(minPair.meanDiff)}, q = ${"%.3f".format(minPair.qStat)}, " +
                                    "${formatPValueShort(minPair.pValue)})",
                            color = PrimaryLight
                        )
                    }
                }

                if (nonSigPairs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PostHocBullet(
                        text = "${nonSigPairs.size} pair(s) showed no significant difference: " +
                                nonSigPairs.joinToString(", ") { "${it.group1} vs ${it.group2}" },
                        color = OnSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    PostHocBullet(
                        text = "All pairwise comparisons are significant — every group differs from every other group.",
                        color = Success
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Sub-components
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun FactorHypothesisBlock(
    factorName: String,
    dvLabel: String,
    fValue: Double,
    pValue: Double,
    df1: Int,
    df2: Int,
    isSignificant: Boolean,
    isInteraction: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSignificant) Success.copy(alpha = 0.06f) else Error.copy(alpha = 0.04f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Text(
            text = factorName,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSignificant) SuccessLight else Error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        HypothesisText(
            if (isInteraction)
                "H₀: There is no interaction effect of $factorName on $dvLabel."
            else
                "H₀: $factorName has no significant effect on $dvLabel."
        )
        HypothesisText(
            if (isInteraction)
                "H₁: There is a significant interaction effect of $factorName on $dvLabel."
            else
                "H₁: $factorName has a significant effect on $dvLabel."
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Stats line
        Text(
            text = "F($df1, $df2) = ${"%.4f".format(fValue)}, ${formatPValueShort(pValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.7f),
            fontStyle = FontStyle.Italic
        )

        Spacer(modifier = Modifier.height(4.dp))

        VerdictBadge(
            rejected = isSignificant,
            summary = if (isSignificant)
                "Reject H₀ — $factorName has a statistically significant effect on $dvLabel at α = .05."
            else
                "Fail to reject H₀ — No significant effect of $factorName on $dvLabel detected."
        )
    }
}

@Composable
private fun HypothesisText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = OnSurface.copy(alpha = 0.7f),
        fontStyle = FontStyle.Italic,
        lineHeight = 16.sp
    )
}

@Composable
private fun VerdictBadge(rejected: Boolean, summary: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (rejected) Success.copy(alpha = 0.12f) else SurfaceContainerHigh,
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (rejected) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (rejected) Success else Error.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (rejected) SuccessLight else OnSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PostHocBullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("•", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface.copy(alpha = 0.8f),
            lineHeight = 17.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════

private fun displayFactorName(rawName: String): String = when (rawName) {
    "floor_lvl" -> "Floor Level"
    "building_pos" -> "Building Position"
    else -> rawName.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun formatPValueShort(value: Double): String = when {
    value <= 0.0 || value < 0.0001 -> "p < .0001"
    value < 0.001 -> "p = ${"%.4f".format(value)}"
    else -> "p = ${"%.4f".format(value)}"
}
