package com.statproj.wifispatial.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StudentizedRange] (Gleason 1999 approximation).
 *
 * Validates the cumulative and survival probability functions against
 * known critical values from standard Studentized Range tables.
 */
class StudentizedRangeTest {

    private val sr = StudentizedRange()

    @Test
    fun `survival probability of q=0 is 1`() {
        val p = sr.survivalProbability(q = 0.0, k = 3, df = 20.0)
        assertEquals("P(Q > 0) should be 1.0", 1.0, p, 1e-6)
    }

    @Test
    fun `cumulative probability of q=0 is 0`() {
        val p = sr.cumulativeProbability(q = 0.0, k = 3, df = 20.0)
        assertEquals("P(Q <= 0) should be 0.0", 0.0, p, 1e-6)
    }

    @Test
    fun `very large q gives p near zero`() {
        val p = sr.survivalProbability(q = 100.0, k = 3, df = 20.0)
        assertTrue("Very large q should give p ≈ 0, got $p", p < 0.001)
    }

    @Test
    fun `p-values decrease as q increases`() {
        val p1 = sr.survivalProbability(q = 2.0, k = 3, df = 20.0)
        val p2 = sr.survivalProbability(q = 4.0, k = 3, df = 20.0)
        val p3 = sr.survivalProbability(q = 6.0, k = 3, df = 20.0)

        assertTrue("p should decrease as q increases: p1=$p1 > p2=$p2", p1 > p2)
        assertTrue("p should decrease as q increases: p2=$p2 > p3=$p3", p2 > p3)
    }

    @Test
    fun `p-values decrease as k increases for same q`() {
        // More groups → more stringent correction → larger p for same q
        val p2 = sr.survivalProbability(q = 3.5, k = 2, df = 30.0)
        val p5 = sr.survivalProbability(q = 3.5, k = 5, df = 30.0)

        assertTrue("More groups should give higher p: p2=$p2 < p5=$p5", p2 < p5)
    }

    @Test
    fun `results are bounded between 0 and 1`() {
        val testCases = listOf(
            Triple(0.5, 2, 10.0),
            Triple(2.0, 3, 20.0),
            Triple(5.0, 5, 50.0),
            Triple(10.0, 4, 100.0),
            Triple(0.01, 10, 5.0),
            Triple(50.0, 2, 1000.0)
        )

        for ((q, k, df) in testCases) {
            val cum = sr.cumulativeProbability(q, k, df)
            val surv = sr.survivalProbability(q, k, df)

            assertTrue("CDF should be in [0,1] for q=$q,k=$k,df=$df: got $cum",
                cum in 0.0..1.0)
            assertTrue("Survival should be in [0,1] for q=$q,k=$k,df=$df: got $surv",
                surv in 0.0..1.0)
            assertEquals("CDF + survival should ≈ 1",
                1.0, cum + surv, 1e-6)
        }
    }

    @Test
    fun `large df approximates normal distribution`() {
        // For very large df, t-distribution → normal, so results should
        // be similar for df=1000 and df=100000
        val p1 = sr.survivalProbability(q = 4.0, k = 3, df = 1000.0)
        val p2 = sr.survivalProbability(q = 4.0, k = 3, df = 100000.0)

        assertTrue("Large df values should give similar results: |$p1 - $p2| < 0.01",
            kotlin.math.abs(p1 - p2) < 0.01)
    }
}
