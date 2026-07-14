package com.statproj.wifispatial.stats

import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.export.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Unit tests for [CsvExporter].
 *
 * Validates that raw CSV and full report CSV are correctly formatted
 * with the expected headers, data rows, and statistical summary sections.
 */
class CsvExporterTest {

    private fun buildTestMeasurements(): List<WifiMeasurement> {
        return listOf(
            WifiMeasurement(
                id = 1, timestamp = 1000000L,
                building_pos = "North", floor_lvl = "Floor 1",
                wifi_dbm = -50, dl_mbps = 150.5, ul_mbps = 45.2,
                x_coord = 1.5, y_coord = 2.3
            ),
            WifiMeasurement(
                id = 2, timestamp = 1000001L,
                building_pos = "South", floor_lvl = "Floor 2",
                wifi_dbm = -65, dl_mbps = null, ul_mbps = 30.0,
                x_coord = 4.5, y_coord = 7.8
            )
        )
    }

    @Test
    fun `raw CSV has correct header`() {
        val csv = CsvExporter.generateRawCsv(buildTestMeasurements())
        val firstLine = csv.lines().first()
        assertEquals(
            "id,timestamp,building_pos,floor_lvl,wifi_dbm,dl_mbps,ul_mbps,x_coord,y_coord",
            firstLine
        )
    }

    @Test
    fun `raw CSV has correct number of data rows`() {
        val csv = CsvExporter.generateRawCsv(buildTestMeasurements())
        val nonEmptyLines = csv.lines().filter { it.isNotBlank() }
        assertEquals("Header + 2 data rows = 3 lines", 3, nonEmptyLines.size)
    }

    @Test
    fun `null speed values exported as empty cells`() {
        val csv = CsvExporter.generateRawCsv(buildTestMeasurements())
        val line2 = csv.lines()[2] // second data row (id=2 has null dl_mbps)
        // dl_mbps should be empty: "2,...,-65,,30.0,..."
        assertTrue("Null dl_mbps should be empty cell: $line2",
            line2.contains("-65,,30.0"))
    }

    @Test
    fun `full report CSV includes statistical summary section`() {
        val measurements = buildTestMeasurements()
        val report = StatsReport(
            regressionDl = RegressionResult(
                slope = 1.5, intercept = 10.0, r = 0.85, rSquared = 0.7225, n = 2
            ),
            regressionUl = null,
            anovaDbm = null,
            anovaDl = null,
            anovaUl = null
        )

        val csv = CsvExporter.generateFullReportCsv(measurements, report)

        assertTrue("Should contain STATISTICAL SUMMARY header",
            csv.contains("STATISTICAL SUMMARY"))
        assertTrue("Should contain LINEAR REGRESSION RESULTS",
            csv.contains("LINEAR REGRESSION RESULTS"))
        assertTrue("Should contain regression data",
            csv.contains("dl_mbps"))
    }

    @Test
    fun `full report CSV without stats report only has raw data`() {
        val csv = CsvExporter.generateFullReportCsv(buildTestMeasurements(), null)
        assertNotNull(csv)
        assertTrue("Should not contain STATISTICAL SUMMARY",
            !csv.contains("STATISTICAL SUMMARY"))
    }

    @Test
    fun `CSV with ANOVA includes all source rows`() {
        val report = StatsReport(
            regressionDl = null,
            regressionUl = null,
            anovaDbm = AnovaResult(
                dependentVar = "wifi_dbm",
                factorAName = "floor_lvl", factorBName = "building_pos",
                ssA = 100.0, dfA = 3, msA = 33.33, fA = 5.0, pA = 0.003,
                ssB = 200.0, dfB = 4, msB = 50.0, fB = 7.5, pB = 0.0001,
                ssAB = 50.0, dfAB = 12, msAB = 4.17, fAB = 0.63, pAB = 0.81,
                ssW = 1200.0, dfW = 580, msW = 6.67,
                ssT = 1550.0, dfT = 599,
                tukeyResults = listOf(
                    TukeyResult("Floor 1", "Floor 2", 3.5, 4.2, 0.01, true),
                    TukeyResult("Floor 1", "Floor 3", 1.0, 1.2, 0.65, false)
                )
            ),
            anovaDl = null,
            anovaUl = null
        )

        val csv = CsvExporter.generateFullReportCsv(buildTestMeasurements(), report)

        assertTrue("Should contain TWO-WAY ANOVA section", csv.contains("TWO-WAY ANOVA"))
        assertTrue("Should contain floor_lvl source row", csv.contains("floor_lvl"))
        assertTrue("Should contain building_pos source row", csv.contains("building_pos"))
        assertTrue("Should contain Within source row", csv.contains("Within"))
        assertTrue("Should contain Total source row", csv.contains("Total"))
        assertTrue("Should contain TUKEY HSD section", csv.contains("TUKEY HSD"))
        assertTrue("Should contain pairwise comparison", csv.contains("Floor 1 vs Floor 2"))
    }

    @Test
    fun `empty measurement list produces header-only CSV`() {
        val csv = CsvExporter.generateRawCsv(emptyList())
        val nonEmptyLines = csv.lines().filter { it.isNotBlank() }
        assertEquals("Should only have header line", 1, nonEmptyLines.size)
    }
}
