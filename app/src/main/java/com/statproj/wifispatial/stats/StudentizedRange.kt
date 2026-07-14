package com.statproj.wifispatial.stats

import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.distribution.TDistribution

/**
 * Approximation of the **Studentized Range (q) distribution** using the
 * Gleason (1999) method.
 *
 * ## Background
 *
 * The Studentized Range distribution arises in Tukey's Honestly Significant
 * Difference (HSD) test.  Given `k` groups each of size `n`, the test
 * statistic is:
 *
 * ```
 *   q = (max(X̄) − min(X̄)) / SE,    where SE = √(MS_W / n)
 * ```
 *
 * Exact critical values require numerical integration of the joint order
 * statistics, which is computationally expensive.  The Gleason (1999)
 * approximation instead relates `q` to pairwise **t-statistics** with a
 * **Šidák correction** for multiplicity.
 *
 * ## Mathematical Approach
 *
 * ### Step 1 — Convert q to t
 *
 * The relationship between a studentized range value and the corresponding
 * two-sample t-statistic (assuming equal variances and equal group sizes) is:
 *
 * ```
 *   t = q / √2
 * ```
 *
 * This follows from the fact that the standard error of the difference of two
 * group means is SE_diff = SE · √2, so dividing `q · SE` by `SE · √2` gives
 * `t = q / √2`.
 *
 * ### Step 2 — Single-comparison two-tailed p-value
 *
 * For **finite** error degrees of freedom (`df`), the single pairwise
 * comparison p-value is obtained from the Student-t distribution:
 *
 * ```
 *   p_single = 2 · P(T > |t|)   where T ~ t(df)
 * ```
 *
 * For very large `df` (≥ 100 000), the standard normal is used instead:
 *
 * ```
 *   p_single = 2 · Φ(−|t|)      where Φ is the standard normal CDF
 * ```
 *
 * ### Step 3 — Šidák correction for C(k, 2) comparisons
 *
 * With `k` groups there are:
 *
 * ```
 *   m = C(k, 2) = k(k − 1) / 2
 * ```
 *
 * pairwise comparisons.  The **Šidák** family-wise adjustment is:
 *
 * ```
 *   P(Q ≤ q) ≈ (1 − p_single)^m
 * ```
 *
 * so the survival probability (the p-value used for significance testing) is:
 *
 * ```
 *   P(Q > q) = 1 − (1 − p_single)^m
 * ```
 *
 * ## Accuracy
 *
 * This approximation is conservative for small `k` and becomes increasingly
 * accurate as `df` grows.  It is suitable for applied work when exact tables
 * are unavailable and a closed-form solution is needed (e.g., on a mobile
 * device without access to full numerical integration libraries).
 *
 * ## References
 *
 * - Gleason, J. R. (1999). *An accurate, non-iterative approximation for
 *   studentized range quantiles.* Computational Statistics & Data Analysis,
 *   31(2), 147–158.
 * - Šidák, Z. (1967). *Rectangular confidence regions for the means of
 *   multivariate normal distributions.* JASA, 62(318), 626–633.
 *
 * @see org.apache.commons.math3.distribution.TDistribution
 * @see org.apache.commons.math3.distribution.NormalDistribution
 */
class StudentizedRange {

    /**
     * Threshold above which the normal distribution is used in place of the
     * t-distribution.  At 100 000 degrees of freedom the two distributions
     * are virtually indistinguishable.
     */
    private val normalDfThreshold = 100_000.0

    /**
     * Standard normal distribution instance (mean = 0, sd = 1).
     * Thread-safe and reusable.
     */
    private val normalDist = NormalDistribution(0.0, 1.0)

    // --------------------------------------------------------------------- //
    //  Public API                                                             //
    // --------------------------------------------------------------------- //

    /**
     * Computes the **cumulative probability** P(Q ≤ [q]) of the Studentized
     * Range distribution with [k] groups and [df] error degrees of freedom.
     *
     * ### Algorithm
     *
     * 1. Convert `q` to a t-statistic: `t = q / √2`
     * 2. Compute the single-comparison two-tailed p-value `p₁` from
     *    `t(df)` (or `N(0,1)` when `df` is very large).
     * 3. Number of pairwise comparisons: `m = k(k−1)/2`
     * 4. Return `(1 − p₁)^m`  (Šidák correction).
     *
     * @param q  The studentized range statistic (must be ≥ 0).
     * @param k  Number of groups being compared (must be ≥ 2).
     * @param df Degrees of freedom for the error term (must be > 0).
     * @return   Cumulative probability in `[0, 1]`.
     * @throws IllegalArgumentException if any argument is out of range.
     */
    fun cumulativeProbability(q: Double, k: Int, df: Double): Double {
        require(q >= 0.0) { "q must be non-negative, got $q" }
        require(k >= 2) { "k must be at least 2, got $k" }
        require(df > 0.0) { "df must be positive, got $df" }

        // Edge case: q == 0 ⟹ all group means are identical ⟹ P = 0
        if (q == 0.0) return 0.0

        // Step 1 — Convert q to the equivalent two-sample t-statistic.
        // The studentized range uses SE = √(MS_W / n), while the two-sample
        // t-test uses SE_diff = √(2 · MS_W / n).  Therefore t = q / √2.
        val tStat = q / Math.sqrt(2.0)

        // Step 2 — Single-comparison two-tailed p-value.
        val pSingle = computeSinglePairPValue(tStat, df)

        // Step 3 — Number of pairwise comparisons: C(k, 2).
        val m = k.toLong() * (k.toLong() - 1L) / 2L

        // Step 4 — Šidák family-wise cumulative probability.
        // P(Q ≤ q) ≈ (1 − p_single)^m
        //
        // For numerical stability when p_single is very small we use
        // Math.pow directly; (1 − ε)^m is well-behaved for small ε.
        val cdf = Math.pow(1.0 - pSingle, m.toDouble())

        return cdf.coerceIn(0.0, 1.0)
    }

    /**
     * Computes the **survival probability** P(Q > [q]), i.e., the p-value
     * for a Tukey HSD test.
     *
     * ```
     *   P(Q > q) = 1 − P(Q ≤ q)
     * ```
     *
     * @param q  The studentized range statistic (must be ≥ 0).
     * @param k  Number of groups being compared (must be ≥ 2).
     * @param df Degrees of freedom for the error term (must be > 0).
     * @return   Survival (upper-tail) probability in `[0, 1]`.
     */
    fun survivalProbability(q: Double, k: Int, df: Double): Double {
        return (1.0 - cumulativeProbability(q, k, df)).coerceIn(0.0, 1.0)
    }

    // --------------------------------------------------------------------- //
    //  Internal helpers                                                       //
    // --------------------------------------------------------------------- //

    /**
     * Computes the **two-tailed p-value** for a single pairwise comparison
     * using either the Student-t or normal distribution.
     *
     * ```
     *   p = 2 · P(T > |t|)
     * ```
     *
     * @param tStat Absolute value of the t-statistic.
     * @param df    Error degrees of freedom.
     * @return      Two-tailed p-value in `[0, 1]`.
     */
    private fun computeSinglePairPValue(tStat: Double, df: Double): Double {
        val absTStat = Math.abs(tStat)

        return if (df >= normalDfThreshold) {
            // Use the standard normal as a limiting case of t(df → ∞).
            // Survival function: P(Z > z) = 1 − Φ(z)
            2.0 * normalDist.cumulativeProbability(-absTStat)
        } else {
            // Use the exact Student-t distribution.
            // Commons Math3's cumulativeProbability gives the lower tail,
            // so P(T > |t|) = 1 − P(T ≤ |t|).
            val tDist = TDistribution(df)
            2.0 * (1.0 - tDist.cumulativeProbability(absTStat))
        }.coerceIn(0.0, 1.0)
    }
}
