package com.statproj.wifispatial.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.stats.AnovaResult
import com.statproj.wifispatial.stats.RegressionResult
import com.statproj.wifispatial.stats.StatsReport
import java.io.File

/**
 * Utility object for generating CSV content from measurement data and
 * statistical analysis results, and for sharing the output via the
 * Android share sheet.
 */
object CsvExporter {

    private const val TAG = "CsvExporter"

    /** Column header for the raw-data CSV. */
    private const val RAW_HEADER =
        "id,timestamp,building_pos,floor_lvl,wifi_dbm,dl_mbps,ul_mbps,x_coord,y_coord"

    // ── Raw CSV ──────────────────────────────────────────────────────────────

    /**
     * Generate a CSV string containing one row per [WifiMeasurement].
     *
     * @param measurements The list of measurements to serialise.
     * @return A complete CSV string (header + data rows).
     */
    fun generateRawCsv(measurements: List<WifiMeasurement>): String {
        val sb = StringBuilder()
        sb.appendLine(RAW_HEADER)

        for (m in measurements) {
            sb.appendLine(
                "${m.id}," +
                "${m.timestamp}," +
                "${escapeCsv(m.building_pos)}," +
                "${escapeCsv(m.floor_lvl)}," +
                "${m.wifi_dbm}," +
                "${m.dl_mbps ?: ""}," +
                "${m.ul_mbps ?: ""}," +
                "${m.x_coord}," +
                m.y_coord
            )
        }

        return sb.toString()
    }

    // ── Full report CSV ──────────────────────────────────────────────────────

    /**
     * Generate a comprehensive CSV that includes:
     * 1. Raw measurement data
     * 2. A statistical summary section (regression + ANOVA + Tukey HSD)
     *
     * @param measurements The list of measurements.
     * @param statsReport  Optional statistical report; when `null` only raw data is emitted.
     * @return A complete CSV string.
     */
    fun generateFullReportCsv(
        measurements: List<WifiMeasurement>,
        statsReport: StatsReport?
    ): String {
        val sb = StringBuilder()

        // ── Section 1: Raw data ──────────────────────────────────────────────
        sb.append(generateRawCsv(measurements))

        if (statsReport == null) return sb.toString()

        // ── Section 2: Statistical summary ───────────────────────────────────
        sb.appendLine()
        sb.appendLine()
        sb.appendLine("STATISTICAL SUMMARY")
        sb.appendLine("===================")

        // ── Linear regression ────────────────────────────────────────────────
        sb.appendLine()
        sb.appendLine("LINEAR REGRESSION RESULTS")
        sb.appendLine("dependent_var,slope,intercept,r,r_squared,n")

        statsReport.regressionDl?.let { r ->
            sb.appendLine(formatRegression("dl_mbps", r))
        }
        statsReport.regressionUl?.let { r ->
            sb.appendLine(formatRegression("ul_mbps", r))
        }

        // ── ANOVA tables ─────────────────────────────────────────────────────
        val anovaResults = listOfNotNull(
            statsReport.anovaDbm,
            statsReport.anovaDl,
            statsReport.anovaUl
        )

        for (anova in anovaResults) {
            sb.appendLine()
            sb.appendLine("TWO-WAY ANOVA: ${anova.dependentVar}")
            sb.appendLine("source,SS,df,MS,F,p")
            sb.appendLine(formatAnovaRow(anova.factorAName, anova.ssA, anova.dfA, anova.msA, anova.fA, anova.pA))
            sb.appendLine(formatAnovaRow(anova.factorBName, anova.ssB, anova.dfB, anova.msB, anova.fB, anova.pB))
            sb.appendLine(formatAnovaRow("${anova.factorAName}*${anova.factorBName}", anova.ssAB, anova.dfAB, anova.msAB, anova.fAB, anova.pAB))
            sb.appendLine(formatAnovaRow("Within", anova.ssW, anova.dfW, anova.msW, Double.NaN, Double.NaN))
            sb.appendLine(formatAnovaRow("Total", anova.ssT, anova.dfT, Double.NaN, Double.NaN, Double.NaN))

            // ── Tukey HSD post-hoc ───────────────────────────────────────────
            if (anova.tukeyResults.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("TUKEY HSD: ${anova.dependentVar}")
                sb.appendLine("pair,diff,q,p,significant")
                for (t in anova.tukeyResults) {
                    sb.appendLine(
                        "${escapeCsv(t.group1)} vs ${escapeCsv(t.group2)}," +
                        "${"%.6f".format(t.meanDiff)}," +
                        "${"%.6f".format(t.qStat)}," +
                        "${"%.6f".format(t.pValue)}," +
                        if (t.significant) "yes" else "no"
                    )
                }
            }
        }

        return sb.toString()
    }

    // ── Share ────────────────────────────────────────────────────────────────

    /**
     * Write [csvContent] to a temporary file in the app's cache directory and
     * launch the Android share sheet so the user can send it via email, Drive,
     * etc.
     *
     * @param context    Activity or application context.
     * @param csvContent The full CSV string to share.
     * @param filename   Desired filename (e.g. "wifi_spatial_report.csv").
     */
    fun shareCsv(context: Context, csvContent: String, filename: String) {
        try {
            // Write to cache directory
            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val csvFile = File(cacheDir, filename)
            csvFile.writeText(csvContent, Charsets.UTF_8)

            // Obtain a content URI through FileProvider
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, csvFile)

            // Build and launch the share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi Spatial Data Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share CSV Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share CSV file", e)
        }
    }

    // ── Formatting helpers ───────────────────────────────────────────────────

    /**
     * Format a single regression result as a CSV row.
     */
    private fun formatRegression(label: String, r: RegressionResult): String {
        return "$label," +
            "${"%.6f".format(r.slope)}," +
            "${"%.6f".format(r.intercept)}," +
            "${"%.6f".format(r.r)}," +
            "${"%.6f".format(r.rSquared)}," +
            r.n
    }

    /**
     * Format a single ANOVA source row.  NaN values are emitted as empty cells.
     */
    private fun formatAnovaRow(
        source: String,
        ss: Double,
        df: Int,
        ms: Double,
        f: Double,
        p: Double
    ): String {
        val msStr = if (ms.isNaN()) "" else "%.6f".format(ms)
        val fStr = if (f.isNaN()) "" else "%.6f".format(f)
        val pStr = if (p.isNaN()) "" else "%.6f".format(p)
        return "$source,${"%.6f".format(ss)},$df,$msStr,$fStr,$pStr"
    }

    /**
     * Escape a string value for safe inclusion in a CSV cell.
     * Wraps the value in double-quotes if it contains commas, quotes, or newlines.
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
