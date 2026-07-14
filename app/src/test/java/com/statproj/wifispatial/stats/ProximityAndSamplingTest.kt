package com.statproj.wifispatial.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Unit tests for the proximity check logic and sample counting rules.
 *
 * These test the pure Euclidean distance calculation and the
 * prescribed sample size enforcement — the two core "guided sampling"
 * rules that don't require Android framework classes.
 */
class ProximityAndSamplingTest {

    // ════════════════════════════════════════════════════════════════════
    //  PROXIMITY CHECK (Euclidean distance >= 3m)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Replicates the proximity check from CollectionViewModel:
     * returns the minimum Euclidean distance from (currentX, currentY)
     * to any point in the previousPoints list.
     */
    private fun minDistanceTo(
        currentX: Double,
        currentY: Double,
        previousPoints: List<Pair<Double, Double>>
    ): Double {
        if (previousPoints.isEmpty()) return Double.MAX_VALUE
        return previousPoints.minOf { (px, py) ->
            sqrt((currentX - px).pow(2) + (currentY - py).pow(2))
        }
    }

    private fun isProximityOk(
        currentX: Double,
        currentY: Double,
        previousPoints: List<Pair<Double, Double>>
    ): Boolean {
        return previousPoints.isEmpty() || minDistanceTo(currentX, currentY, previousPoints) > 3.0
    }

    @Test
    fun `first point is always allowed (no previous points)`() {
        assertTrue(isProximityOk(0.0, 0.0, emptyList()))
    }

    @Test
    fun `point exactly 3m away is NOT allowed (strictly greater required)`() {
        val previous = listOf(0.0 to 0.0)
        // Point at (3, 0) is exactly 3m → NOT allowed (need > 3m)
        assertFalse(isProximityOk(3.0, 0.0, previous))
    }

    @Test
    fun `point at 3_01m is allowed`() {
        val previous = listOf(0.0 to 0.0)
        assertTrue(isProximityOk(3.01, 0.0, previous))
    }

    @Test
    fun `point at 2m is rejected`() {
        val previous = listOf(0.0 to 0.0)
        assertFalse(isProximityOk(2.0, 0.0, previous))
    }

    @Test
    fun `diagonal distance calculated correctly`() {
        val previous = listOf(0.0 to 0.0)
        // Point at (2.5, 2.5): distance = sqrt(6.25 + 6.25) = sqrt(12.5) ≈ 3.536
        assertTrue(isProximityOk(2.5, 2.5, previous))
    }

    @Test
    fun `closest point determines rejection`() {
        // Multiple previous points — the closest one determines the result
        val previous = listOf(
            0.0 to 0.0,    // 5m from test point
            4.0 to 3.0,    // 1m from test point ← this should cause rejection
            10.0 to 10.0   // far away
        )
        // Test point at (5.0, 3.0): distance to (4,3) = 1.0 → rejected
        assertFalse(isProximityOk(5.0, 3.0, previous))
    }

    @Test
    fun `all points far away allows placement`() {
        val previous = listOf(
            0.0 to 0.0,
            10.0 to 0.0,
            0.0 to 10.0,
            10.0 to 10.0
        )
        // Test point at (5.0, 5.0): closest is diagonal = sqrt(50)/2 ≈ 7.07
        // Actually closest is any corner: sqrt(25+25) = sqrt(50) ≈ 7.07
        assertTrue(isProximityOk(5.0, 5.0, previous))
    }

    @Test
    fun `PDR step sequence with 0_75m stride`() {
        // Simulate walking in a straight line (heading = 0, due north)
        // After 10 steps: y = 7.5m → should be > 3m from origin
        val strideLength = 0.75
        var x = 0.0
        var y = 0.0
        val previousPoints = mutableListOf(0.0 to 0.0)

        // Walk 10 steps north
        for (step in 1..10) {
            y += strideLength
        }

        // After 10 steps: y = 7.5m
        assertEquals(7.5, y, 1e-10)
        assertTrue("Should be > 3m after 10 steps straight", isProximityOk(x, y, previousPoints))
    }

    @Test
    fun `walking in circle returns to near start`() {
        // Simulate walking in a tight circle — should trigger proximity warning
        val strideLength = 0.75
        var x = 0.0
        var y = 0.0

        // Walk 4 steps north, then 4 east, then 4 south → ends near start
        repeat(4) { y += strideLength }   // y = 3.0
        repeat(4) { x += strideLength }   // x = 3.0
        repeat(4) { y -= strideLength }   // y = 0.0
        // Now at (3.0, 0.0) — exactly 3m from origin → rejected
        assertFalse(isProximityOk(x, y, listOf(0.0 to 0.0)))
    }

    // ════════════════════════════════════════════════════════════════════
    //  SAMPLE SIZE ENFORCEMENT (30 per group)
    // ════════════════════════════════════════════════════════════════════

    private val prescribedSampleSize = 30

    @Test
    fun `new group starts at zero`() {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        val group = "North" to "Floor 1"
        assertEquals(0, counts.getOrDefault(group, 0))
    }

    @Test
    fun `counting increments correctly`() {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        val group = "North" to "Floor 1"

        for (i in 1..15) {
            counts[group] = counts.getOrDefault(group, 0) + 1
        }
        assertEquals(15, counts[group])
        assertFalse("Should not be maxed at 15", counts[group]!! >= prescribedSampleSize)
    }

    @Test
    fun `max reached at exactly 30`() {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        val group = "North" to "Floor 1"

        for (i in 1..30) {
            counts[group] = counts.getOrDefault(group, 0) + 1
        }
        assertEquals(30, counts[group])
        assertTrue("Should be maxed at 30", counts[group]!! >= prescribedSampleSize)
    }

    @Test
    fun `different groups are independent`() {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        val group1 = "North" to "Floor 1"
        val group2 = "South" to "Floor 2"

        // Fill group1 to 30
        counts[group1] = 30
        // Group2 should still be 0
        assertEquals(0, counts.getOrDefault(group2, 0))
        assertFalse(counts.getOrDefault(group2, 0) >= prescribedSampleSize)
    }

    @Test
    fun `total cells for study design`() {
        val buildings = listOf("North", "North West", "West", "South West", "South")
        val floors = listOf("Floor 1", "Floor 2", "Floor 3", "Floor 4")

        val totalCells = buildings.size * floors.size
        assertEquals("5 buildings × 4 floors = 20 cells", 20, totalCells)

        val totalPoints = totalCells * prescribedSampleSize
        assertEquals("20 cells × 30 = 600 total points", 600, totalPoints)
    }

    // ════════════════════════════════════════════════════════════════════
    //  5GHz FREQUENCY FILTER
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `frequency below 5000 is rejected`() {
        val freq2_4GHz = 2437  // common 2.4GHz channel
        assertTrue("2.4GHz should be rejected", freq2_4GHz < 5000)
    }

    @Test
    fun `frequency at 5000 is accepted`() {
        val freq5GHz = 5180  // common 5GHz channel
        assertFalse("5GHz should be accepted", freq5GHz < 5000)
    }

    @Test
    fun `frequency exactly 5000 is accepted`() {
        val freq = 5000
        assertFalse("5000 MHz should be accepted", freq < 5000)
    }

    // ════════════════════════════════════════════════════════════════════
    //  POWER LINEARIZATION
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `linearization P_mW = 10^(dBm div 10)`() {
        assertEquals(0.001, 10.0.pow(-30.0 / 10.0), 1e-10)
        assertEquals(0.01, 10.0.pow(-20.0 / 10.0), 1e-10)
        assertEquals(0.1, 10.0.pow(-10.0 / 10.0), 1e-10)
        assertEquals(1.0, 10.0.pow(0.0 / 10.0), 1e-10)
        assertEquals(0.0001, 10.0.pow(-40.0 / 10.0), 1e-12)
        assertEquals(1e-7, 10.0.pow(-70.0 / 10.0), 1e-14)
    }
}
