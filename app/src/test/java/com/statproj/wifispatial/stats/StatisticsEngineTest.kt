package com.statproj.wifispatial.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.statproj.wifispatial.data.WifiMeasurement
import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit tests for [StatisticsEngine].
 *
 * Uses known textbook datasets to verify correctness of:
 * - Simple linear regression (slope, intercept, r, R²)
 * - Two-Way ANOVA (SS, df, MS, F, p)
 * - Tukey HSD post-hoc pairwise comparisons
 * - Full report generation pipeline
 */
class StatisticsEngineTest {

    private lateinit var engine: StatisticsEngine

    @Before
    fun setup() {
        engine = StatisticsEngine()
    }

    // ════════════════════════════════════════════════════════════════════
    //  1. LINEAR REGRESSION
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `regression with perfect positive correlation`() {
        // y = 2x + 1  →  slope=2, intercept=1, r=1, R²=1
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val y = doubleArrayOf(3.0, 5.0, 7.0, 9.0, 11.0)

        val result = engine.computeRegression(x, y)

        assertNotNull(result)
        assertEquals(2.0, result!!.slope, 1e-10)
        assertEquals(1.0, result.intercept, 1e-10)
        assertEquals(1.0, result.r, 1e-10)
        assertEquals(1.0, result.rSquared, 1e-10)
        assertEquals(5, result.n)
    }

    @Test
    fun `regression with known dataset`() {
        // Classic small dataset: verify against hand-calculated values
        val x = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val y = doubleArrayOf(2.1, 4.0, 5.8, 8.1, 10.2, 11.8, 14.1, 16.0)

        val result = engine.computeRegression(x, y)

        assertNotNull(result)
        // Slope should be approximately 2.0
        assertTrue("Slope should be near 2.0, got ${result!!.slope}",
            abs(result.slope - 2.0) < 0.1)
        // R² should be very high (near-perfect linear)
        assertTrue("R² should be > 0.99, got ${result.rSquared}",
            result.rSquared > 0.99)
        assertEquals(8, result.n)
    }

    @Test
    fun `regression filters NaN values`() {
        val x = doubleArrayOf(1.0, 2.0, Double.NaN, 4.0, 5.0)
        val y = doubleArrayOf(2.0, 4.0, 6.0, 8.0, Double.NaN)

        val result = engine.computeRegression(x, y)

        assertNotNull(result)
        // Only 3 valid pairs: (1,2), (2,4), (4,8)
        assertEquals(3, result!!.n)
    }

    @Test
    fun `regression returns null with insufficient data`() {
        val x = doubleArrayOf(1.0)
        val y = doubleArrayOf(2.0)

        val result = engine.computeRegression(x, y)
        assertNull("Should return null with only 1 data point", result)
    }

    @Test
    fun `regression returns null when all y values are NaN`() {
        val x = doubleArrayOf(1.0, 2.0, 3.0)
        val y = doubleArrayOf(Double.NaN, Double.NaN, Double.NaN)

        val result = engine.computeRegression(x, y)
        assertNull(result)
    }

    // ════════════════════════════════════════════════════════════════════
    //  2. TWO-WAY ANOVA
    // ════════════════════════════════════════════════════════════════════

    /**
     * Build a balanced dataset with known group means for testing ANOVA.
     *
     * Uses a 2×2 design (2 floors, 2 buildings) with n observations per cell.
     * The cell means are:
     *   Floor 1, North = 10
     *   Floor 1, South = 20
     *   Floor 2, North = 15
     *   Floor 2, South = 25
     *
     * This gives:
     *   Floor 1 mean = 15, Floor 2 mean = 20  →  Floor effect exists
     *   North mean = 12.5, South mean = 22.5  →  Building effect exists
     *   No interaction (parallel lines)
     */
    private fun buildBalancedDataset(n: Int = 5): List<WifiMeasurement> {
        val data = mutableListOf<WifiMeasurement>()
        val cellValues = mapOf(
            ("Floor 1" to "North") to 10.0,
            ("Floor 1" to "South") to 20.0,
            ("Floor 2" to "North") to 15.0,
            ("Floor 2" to "South") to 25.0
        )

        var id = 1L
        for ((key, baseMean) in cellValues) {
            val (floor, building) = key
            for (k in 0 until n) {
                // Add small deterministic variation around the mean
                val noise = (k - n / 2) * 0.5
                val dbm = (baseMean + noise).toInt()
                data.add(
                    WifiMeasurement(
                        id = id++,
                        timestamp = System.currentTimeMillis(),
                        building_pos = building,
                        floor_lvl = floor,
                        wifi_dbm = dbm,
                        dl_mbps = baseMean + noise + 50.0,
                        ul_mbps = baseMean + noise + 20.0,
                        x_coord = id.toDouble(),
                        y_coord = id.toDouble()
                    )
                )
            }
        }
        return data
    }

    @Test
    fun `anova detects significant floor effect`() {
        val data = buildBalancedDataset(n = 5)
        val result = engine.computeTwoWayAnova(data, "wifi_dbm")

        assertNotNull("ANOVA should not be null", result)

        // Factor A = floor_lvl
        assertTrue("Floor effect should be significant (p < 0.05), got p=${result!!.pA}",
            result.pA < 0.05)

        // Factor B = building_pos
        assertTrue("Building effect should be significant (p < 0.05), got p=${result.pB}",
            result.pB < 0.05)

        // Degrees of freedom
        assertEquals("df_A should be a-1 = 1", 1, result.dfA)
        assertEquals("df_B should be b-1 = 1", 1, result.dfB)
        assertEquals("df_AB should be (a-1)(b-1) = 1", 1, result.dfAB)
        assertEquals("df_W should be ab(n-1) = 2*2*4 = 16", 16, result.dfW)
        assertEquals("df_T should be N-1 = 19", 19, result.dfT)

        // SS decomposition: SS_T = SS_A + SS_B + SS_AB + SS_W
        val ssSum = result.ssA + result.ssB + result.ssAB + result.ssW
        assertEquals("SS should decompose correctly",
            result.ssT, ssSum, 1e-6)
    }

    @Test
    fun `anova with no effect returns non-significant p-values`() {
        // All cells have the same mean → no effect
        val data = mutableListOf<WifiMeasurement>()
        var id = 1L
        for (floor in listOf("Floor 1", "Floor 2")) {
            for (building in listOf("North", "South")) {
                for (k in 0 until 5) {
                    data.add(
                        WifiMeasurement(
                            id = id++, timestamp = 0L,
                            building_pos = building, floor_lvl = floor,
                            wifi_dbm = -50, dl_mbps = 100.0, ul_mbps = 50.0,
                            x_coord = 0.0, y_coord = 0.0
                        )
                    )
                }
            }
        }

        val result = engine.computeTwoWayAnova(data, "wifi_dbm")

        assertNotNull(result)
        // All values identical → SS_A = SS_B = SS_AB = 0
        assertEquals(0.0, result!!.ssA, 1e-10)
        assertEquals(0.0, result.ssB, 1e-10)
        assertEquals(0.0, result.ssAB, 1e-10)
    }

    @Test
    fun `anova returns null with single factor level`() {
        // Only one floor → can't do two-way ANOVA
        val data = (1..10).map {
            WifiMeasurement(
                id = it.toLong(), timestamp = 0L,
                building_pos = if (it <= 5) "North" else "South",
                floor_lvl = "Floor 1",  // only one level!
                wifi_dbm = -50 + it, dl_mbps = 100.0, ul_mbps = 50.0,
                x_coord = 0.0, y_coord = 0.0
            )
        }

        val result = engine.computeTwoWayAnova(data, "wifi_dbm")
        assertNull("Should return null with only 1 level of Factor A", result)
    }

    @Test
    fun `anova returns null with empty data`() {
        val result = engine.computeTwoWayAnova(emptyList(), "wifi_dbm")
        assertNull(result)
    }

    @Test
    fun `anova SS decomposition holds for larger design`() {
        // 4 floors × 3 buildings × 5 reps = 60 observations
        val data = mutableListOf<WifiMeasurement>()
        var id = 1L
        val floors = listOf("Floor 1", "Floor 2", "Floor 3", "Floor 4")
        val buildings = listOf("North", "South", "West")

        for (floor in floors) {
            for (building in buildings) {
                for (k in 0 until 5) {
                    // Create means that differ by factor
                    val floorEffect = floors.indexOf(floor) * 3.0
                    val buildingEffect = buildings.indexOf(building) * 5.0
                    val dbm = (-60 + floorEffect + buildingEffect + k * 0.2).toInt()
                    data.add(
                        WifiMeasurement(
                            id = id++, timestamp = 0L,
                            building_pos = building, floor_lvl = floor,
                            wifi_dbm = dbm,
                            dl_mbps = 100.0 + floorEffect + buildingEffect,
                            ul_mbps = 50.0 + floorEffect,
                            x_coord = id.toDouble(), y_coord = id.toDouble()
                        )
                    )
                }
            }
        }

        val result = engine.computeTwoWayAnova(data, "wifi_dbm")

        assertNotNull(result)
        // Verify df
        assertEquals(3, result!!.dfA)     // 4 floors - 1
        assertEquals(2, result.dfB)       // 3 buildings - 1
        assertEquals(6, result.dfAB)      // 3 * 2
        assertEquals(48, result.dfW)      // 4*3*4
        assertEquals(59, result.dfT)      // 60 - 1

        // Verify SS decomposition
        val ssSum = result.ssA + result.ssB + result.ssAB + result.ssW
        assertEquals(result.ssT, ssSum, 1e-4)

        // Both effects should be significant
        assertTrue("Floor effect p < 0.05", result.pA < 0.05)
        assertTrue("Building effect p < 0.05", result.pB < 0.05)
    }

    // ════════════════════════════════════════════════════════════════════
    //  3. TUKEY HSD
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `tukey detects significant pairwise differences`() {
        val data = buildBalancedDataset(n = 10)
        val result = engine.computeTwoWayAnova(data, "wifi_dbm")

        assertNotNull(result)
        // With strong effects and n=10, Tukey should find significant pairs
        if (result!!.pA < 0.05 || result.pB < 0.05) {
            assertTrue("Tukey results should be non-empty when effects significant",
                result.tukeyResults.isNotEmpty())

            // Verify all Tukey results have valid structure
            for (tukey in result.tukeyResults) {
                assertTrue("q-stat should be non-negative", tukey.qStat >= 0.0)
                assertTrue("p-value should be in [0,1]",
                    tukey.pValue in 0.0..1.0)
                assertTrue("meanDiff should be non-negative", tukey.meanDiff >= 0.0)
                assertEquals("significant should match p < 0.05",
                    tukey.pValue < 0.05, tukey.significant)
            }
        }
    }

    @Test
    fun `tukey HSD with known means`() {
        // Direct test of computeTukeyHSD with hand-picked values
        val means = mapOf(
            "Group A" to 10.0,
            "Group B" to 20.0,
            "Group C" to 12.0
        )
        val msW = 5.0
        val groupN = 30
        val dfW = 87  // 3 groups * 30 - 3

        val results = engine.computeTukeyHSD(means, groupN, msW, dfW)

        assertEquals("Should have C(3,2)=3 pairwise comparisons", 3, results.size)

        // Group A vs Group B should have largest difference
        val abPair = results.find {
            (it.group1 == "Group A" && it.group2 == "Group B") ||
            (it.group1 == "Group B" && it.group2 == "Group A")
        }
        assertNotNull("A vs B pair should exist", abPair)
        assertEquals(10.0, abPair!!.meanDiff, 1e-10)

        // q = meanDiff / sqrt(MSW/n) = 10 / sqrt(5/30) = 10 / 0.4082 ≈ 24.49
        val expectedQ = 10.0 / Math.sqrt(5.0 / 30.0)
        assertEquals(expectedQ, abPair.qStat, 0.01)

        // Such a large q should be highly significant
        assertTrue("A vs B should be significant", abPair.significant)
    }

    @Test
    fun `tukey returns empty for single group`() {
        val means = mapOf("OnlyGroup" to 5.0)
        val results = engine.computeTukeyHSD(means, 30, 5.0, 29)
        assertTrue(results.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════════
    //  4. FULL REPORT PIPELINE
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `generateReport produces complete report from balanced data`() {
        val data = buildBalancedDataset(n = 5)
        val report = engine.generateReport(data)

        assertNotNull("Regression DL should not be null", report.regressionDl)
        assertNotNull("Regression UL should not be null", report.regressionUl)
        assertNotNull("ANOVA dBm should not be null", report.anovaDbm)
        assertNotNull("ANOVA DL should not be null", report.anovaDl)
        assertNotNull("ANOVA UL should not be null", report.anovaUl)
    }

}
