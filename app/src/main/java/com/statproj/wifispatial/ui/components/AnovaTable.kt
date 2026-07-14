package com.statproj.wifispatial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statproj.wifispatial.stats.AnovaResult
import com.statproj.wifispatial.ui.theme.OnSurface
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Outline
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryDark
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.Warning

/**
 * Composable that renders a Two-Way ANOVA summary table.
 *
 * Displays Source, SS, df, MS, F, and p-value for:
 * - Factor A (floor_lvl)
 * - Factor B (building_pos)
 * - Interaction (A × B)
 * - Within (Error)
 * - Total
 */
@Composable
fun AnovaTable(
    result: AnovaResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
    ) {
        // Title
        Text(
            text = "ANOVA: ${result.dependentVar}",
            style = MaterialTheme.typography.titleSmall,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp, 10.dp, 12.dp, 4.dp)
        )

        // Header row
        AnovaRow(
            source = "Source",
            ss = "SS",
            df = "df",
            ms = "MS",
            f = "F",
            p = "p-value",
            isHeader = true
        )

        // Factor A row
        AnovaRow(
            source = result.factorAName,
            ss = formatStat(result.ssA),
            df = "${result.dfA}",
            ms = formatStat(result.msA),
            f = formatStat(result.fA),
            p = formatPValue(result.pA),
            isSignificant = result.pA < 0.05
        )

        // Factor B row
        AnovaRow(
            source = result.factorBName,
            ss = formatStat(result.ssB),
            df = "${result.dfB}",
            ms = formatStat(result.msB),
            f = formatStat(result.fB),
            p = formatPValue(result.pB),
            isSignificant = result.pB < 0.05
        )

        // Interaction row
        AnovaRow(
            source = "${result.factorAName} × ${result.factorBName}",
            ss = formatStat(result.ssAB),
            df = "${result.dfAB}",
            ms = formatStat(result.msAB),
            f = formatStat(result.fAB),
            p = formatPValue(result.pAB),
            isSignificant = result.pAB < 0.05
        )

        // Within (Error) row
        AnovaRow(
            source = "Within",
            ss = formatStat(result.ssW),
            df = "${result.dfW}",
            ms = formatStat(result.msW),
            f = "",
            p = ""
        )

        // Total row
        AnovaRow(
            source = "Total",
            ss = formatStat(result.ssT),
            df = "${result.dfT}",
            ms = "",
            f = "",
            p = "",
            isTotal = true
        )
    }
}

@Composable
private fun AnovaRow(
    source: String,
    ss: String,
    df: String,
    ms: String,
    f: String,
    p: String,
    isHeader: Boolean = false,
    isTotal: Boolean = false,
    isSignificant: Boolean = false
) {
    val bgColor = when {
        isHeader -> PrimaryDark.copy(alpha = 0.3f)
        isTotal -> SurfaceContainerHigh
        isSignificant -> Warning.copy(alpha = 0.08f)
        else -> SurfaceContainer
    }
    val textColor = when {
        isHeader -> Primary
        isSignificant -> Warning
        else -> OnSurface
    }
    val weight = if (isHeader || isTotal) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .background(bgColor)
            .border(0.5.dp, Outline.copy(alpha = 0.3f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        CellText(source, Modifier.padding(start = 8.dp), 110.dp, textColor, weight, TextAlign.Start)
        CellText(ss, Modifier, 90.dp, textColor, weight)
        CellText(df, Modifier, 40.dp, textColor, weight)
        CellText(ms, Modifier, 90.dp, textColor, weight)
        CellText(f, Modifier, 80.dp, textColor, weight)
        CellText(
            text = p + if (isSignificant && !isHeader) " *" else "",
            modifier = Modifier.padding(end = 8.dp),
            width = 90.dp,
            color = if (isSignificant && !isHeader) Warning else textColor,
            fontWeight = if (isSignificant && !isHeader) FontWeight.Bold else weight
        )
    }
}

@Composable
private fun CellText(
    text: String,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color = OnSurface,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.End
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = modifier
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .then(Modifier.widthIn(min = width))
    )
}

private fun formatStat(value: Double): String {
    return if (value == 0.0) "0.0000"
    else if (kotlin.math.abs(value) >= 1000) "%.1f".format(value)
    else if (kotlin.math.abs(value) >= 1) "%.4f".format(value)
    else "%.6f".format(value)
}

private fun formatPValue(value: Double): String {
    return when {
        value <= 0.0 -> "<.0001"
        value < 0.0001 -> "<.0001"
        value < 0.001 -> "%.4f".format(value)
        else -> "%.4f".format(value)
    }
}
