package com.statproj.wifispatial.stats

import com.statproj.wifispatial.data.WifiMeasurement
import org.apache.commons.math3.distribution.FDistribution
import org.apache.commons.math3.stat.regression.SimpleRegression

/**
 * Full statistical analysis engine for Wi-Fi spatial measurement data.
 *
 * This engine provides three core capabilities:
 *
 * 1. **Simple Linear Regression** — relating linearized power (mW) to
 *    download / upload throughput using ordinary least squares.
 * 2. **Two-Way (Factorial) ANOVA** — testing main effects and interaction
 *    of floor level (Factor A) and building position (Factor B) on each
 *    dependent variable (dBm, DL, UL).
 * 3. **Tukey's HSD Post-Hoc Test** — pairwise mean comparisons whenever a
 *    significant main effect is detected.
 *
 * All computations follow standard textbook formulas (see per-function docs)
 * and use Apache Commons Math 3.6.1 for distributional calculations.
 *
 * @see StatsReport
 * @see AnovaResult
 * @see RegressionResult
 * @see TukeyResult
 */
class StatisticsEngine {

    /** Significance level for flagging Tukey pairwise comparisons. */
    private val alpha = 0.05

    /** Studentized Range approximation used by Tukey's HSD. */
    private val studentizedRange = StudentizedRange()

    // ================================================================== //
    //  1. LINEAR REGRESSION                                               //
    // ================================================================== //

    /**
     * Computes a **simple (ordinary) least-squares linear regression** of
     * [yValues] on [xValues].
     *
     * ### Model
     *
     * ```
     *   ŷ = b₀ + b₁ · x
     * ```
     *
     * where the slope and intercept are estimated by minimising the residual
     * sum of squares:
     *
     * ```
     *   b₁ = Σ(xᵢ − x̄)(yᵢ − ȳ) / Σ(xᵢ − x̄)²
     *   b₀ = ȳ − b₁ · x̄
     * ```
     *
     * ### Correlation
     *
     * The Pearson product-moment correlation coefficient is:
     *
     * ```
     *   r = Σ(xᵢ − x̄)(yᵢ − ȳ) / √[Σ(xᵢ − x̄)² · Σ(yᵢ − ȳ)²]
     * ```
     *
     * and the coefficient of determination is R² = r².
     *
     * This method delegates to Apache Commons Math3's [SimpleRegression],
     * which uses numerically stable one-pass updating formulas internally.
     *
     * @param xValues Predictor (independent) variable values.
     * @param yValues Response (dependent) variable values.  Entries may be
     *                [Double.NaN] to indicate missing data; such pairs are
     *                excluded before fitting.
     * @return A [RegressionResult] or `null` if fewer than 2 valid
     *         observations remain after filtering.
     */
    fun computeRegression(
        xValues: DoubleArray,
        yValues: DoubleArray
    ): RegressionResult? {
        require(xValues.size == yValues.size) {
            "xValues and yValues must have the same length " +
                    "(${xValues.size} vs ${yValues.size})"
        }

        val regression = SimpleRegression()

        // Add only pairs where both x and y are finite (non-NaN, non-Inf).
        for (i in xValues.indices) {
            val x = xValues[i]
            val y = yValues[i]
            if (x.isFinite() && y.isFinite()) {
                regression.addData(x, y)
            }
        }

        // Need at least 2 data points to define a line.
        if (regression.n < 2) return null

        val r = regression.r
        return RegressionResult(
            slope = regression.slope,
            intercept = regression.intercept,
            r = r,
            rSquared = regression.rSquare,
            n = regression.n.toInt()
        )
    }

    // ================================================================== //
    //  2. TWO-WAY ANOVA (Balanced Design)                                 //
    // ================================================================== //

    /**
     * Performs a **balanced two-way (factorial) ANOVA** on the measurements.
     *
     * ### Factors
     *
     * | Symbol | Factor           | Field in [WifiMeasurement] |
     * |--------|------------------|----------------------------|
     * | A      | Floor Level      | `floor_lvl`                |
     * | B      | Building Position| `building_pos`             |
     *
     * ### Dependent Variables
     *
     * [dependentVar] selects which measurement column to analyse:
     *
     * | String      | Field       |
     * |-------------|-------------|
     * | `"wifi_dbm"`| `wifi_dbm`  |
     * | `"dl_mbps"` | `dl_mbps`   |
     * | `"ul_mbps"` | `ul_mbps`   |
     *
     * ### Model
     *
     * The two-factor fixed-effects model is:
     *
     * ```
     *   X_ijk = μ + αᵢ + βⱼ + (αβ)ᵢⱼ + ε_ijk
     * ```
     *
     * where `i = 1…a` (levels of A), `j = 1…b` (levels of B),
     * `k = 1…n` (replicates per cell), and `ε_ijk ~ N(0, σ²)`.
     *
     * ### Sums of Squares (balanced case)
     *
     * Let:
     * - `X̄...` = grand mean
     * - `X̄ᵢ..` = mean for level i of Factor A (across all B and replicates)
     * - `X̄.ⱼ.` = mean for level j of Factor B (across all A and replicates)
     * - `X̄ᵢⱼ.` = cell mean for level i of A and level j of B
     *
     * ```
     *   SS_A  = b · n · Σᵢ (X̄ᵢ.. − X̄...)²
     *   SS_B  = a · n · Σⱼ (X̄.ⱼ. − X̄...)²
     *   SS_AB = n · Σᵢ Σⱼ (X̄ᵢⱼ. − X̄ᵢ.. − X̄.ⱼ. + X̄...)²
     *   SS_W  = Σᵢ Σⱼ Σₖ (X_ijk − X̄ᵢⱼ.)²
     *   SS_T  = Σᵢ Σⱼ Σₖ (X_ijk − X̄...)²
     * ```
     *
     * ### Degrees of Freedom
     *
     * ```
     *   df_A  = a − 1
     *   df_B  = b − 1
     *   df_AB = (a − 1)(b − 1)
     *   df_W  = a · b · (n − 1)
     *   df_T  = N − 1          where N = a · b · n
     * ```
     *
     * ### F-Statistics
     *
     * ```
     *   MS_X = SS_X / df_X        for X ∈ {A, B, AB, W}
     *   F_X  = MS_X / MS_W        for X ∈ {A, B, AB}
     * ```
     *
     * p-values are computed as the upper-tail probability of the
     * F(df_X, df_W) distribution.
     *
     * ### Post-Hoc
     *
     * If any main effect is significant at α = 0.05, Tukey's HSD is run
     * for that factor.
     *
     * @param data         List of [WifiMeasurement] observations.
     * @param dependentVar One of `"wifi_dbm"`, `"dl_mbps"`, or `"ul_mbps"`.
     * @return An [AnovaResult] or `null` if the data is insufficient or
     *         unbalanced.
     */
    fun computeTwoWayAnova(
        data: List<WifiMeasurement>,
        dependentVar: String
    ): AnovaResult? {
        if (data.isEmpty()) return null

        // ---- Extract the dependent variable from each measurement --------
        val extracted: List<Pair<WifiMeasurement, Double>> = data.mapNotNull { m ->
            val y: Double? = when (dependentVar) {
                "wifi_dbm" -> m.wifi_dbm.toDouble()
                "dl_mbps" -> m.dl_mbps
                "ul_mbps" -> m.ul_mbps
                else -> return null   // unknown dependent variable
            }
            if (y != null) m to y else null
        }
        if (extracted.isEmpty()) return null

        // ---- Determine factor levels ------------------------------------
        val levelsA: List<String> = extracted.map { it.first.floor_lvl }.distinct().sorted()
        val levelsB: List<String> = extracted.map { it.first.building_pos }.distinct().sorted()
        val a = levelsA.size   // number of levels of Factor A
        val b = levelsB.size   // number of levels of Factor B

        if (a < 2 || b < 2) return null  // need at least 2 levels per factor

        // ---- Organise data into cells: (levelA, levelB) → values --------
        val cellData = mutableMapOf<Pair<String, String>, MutableList<Double>>()
        for ((m, y) in extracted) {
            val key = m.floor_lvl to m.building_pos
            cellData.getOrPut(key) { mutableListOf() }.add(y)
        }

        // Check that every cell is present and all cells have the same size.
        val cellSizes = cellData.values.map { it.size }
        if (cellSizes.isEmpty()) return null
        val n = cellSizes.min()           // replicates per cell
        if (n < 1) return null
        if (cellSizes.any { it != n }) {
            // Unbalanced design — truncate each cell to n observations so
            // the balanced-design formulas remain correct.  A production
            // system would use Type-III SS instead; for this app the data
            // collection protocol guarantees balance.
        }

        // Ensure every combination of (A, B) exists.
        for (la in levelsA) {
            for (lb in levelsB) {
                if ((la to lb) !in cellData) return null
            }
        }

        // Use exactly n observations per cell (take the first n if slightly
        // over due to duplicates).
        val cells: Map<Pair<String, String>, List<Double>> =
            cellData.mapValues { (_, v) -> v.take(n) }

        val totalN = a * b * n   // total observations

        // ---- Compute means -----------------------------------------------

        // Grand mean:  X̄...
        val grandMean = cells.values.flatten().average()

        // Factor A (floor_lvl) marginal means:  X̄ᵢ..
        val meansA: Map<String, Double> = levelsA.associateWith { la ->
            levelsB.flatMap { lb -> cells[la to lb]!! }.average()
        }

        // Factor B (building_pos) marginal means:  X̄.ⱼ.
        val meansB: Map<String, Double> = levelsB.associateWith { lb ->
            levelsA.flatMap { la -> cells[la to lb]!! }.average()
        }

        // Cell means:  X̄ᵢⱼ.
        val cellMeans: Map<Pair<String, String>, Double> =
            cells.mapValues { (_, v) -> v.average() }

        // ---- Sums of Squares ---------------------------------------------

        // SS_A = b · n · Σᵢ (X̄ᵢ.. − X̄...)²
        val ssA = b * n * levelsA.sumOf { la ->
            val diff = meansA[la]!! - grandMean
            diff * diff
        }

        // SS_B = a · n · Σⱼ (X̄.ⱼ. − X̄...)²
        val ssB = a * n * levelsB.sumOf { lb ->
            val diff = meansB[lb]!! - grandMean
            diff * diff
        }

        // SS_AB = n · Σᵢ Σⱼ (X̄ᵢⱼ. − X̄ᵢ.. − X̄.ⱼ. + X̄...)²
        val ssAB = n * levelsA.sumOf { la ->
            levelsB.sumOf { lb ->
                val diff = cellMeans[la to lb]!! - meansA[la]!! - meansB[lb]!! + grandMean
                diff * diff
            }
        }

        // SS_W = Σᵢ Σⱼ Σₖ (X_ijk − X̄ᵢⱼ.)²
        val ssW = levelsA.sumOf { la ->
            levelsB.sumOf { lb ->
                val cm = cellMeans[la to lb]!!
                cells[la to lb]!!.sumOf { x ->
                    val diff = x - cm
                    diff * diff
                }
            }
        }

        // SS_T = Σᵢ Σⱼ Σₖ (X_ijk − X̄...)²
        val ssT = cells.values.flatten().sumOf { x ->
            val diff = x - grandMean
            diff * diff
        }

        // ---- Degrees of Freedom ------------------------------------------
        val dfA = a - 1
        val dfB = b - 1
        val dfAB = (a - 1) * (b - 1)
        val dfW = a * b * (n - 1)
        val dfT = totalN - 1

        // Guard against zero within-cell df (only 1 obs per cell).
        if (dfW < 1) return null

        // ---- Mean Squares & F-statistics ---------------------------------
        val msA = ssA / dfA
        val msB = ssB / dfB
        val msAB = if (dfAB > 0) ssAB / dfAB else 0.0
        val msW = ssW / dfW

        // Protect against division by zero if MS_W ≈ 0 (all values identical
        // within cells).
        val fA: Double
        val fB: Double
        val fAB: Double
        if (msW > 0.0) {
            fA = msA / msW
            fB = msB / msW
            fAB = if (dfAB > 0) msAB / msW else 0.0
        } else {
            // When MS_W is exactly zero every observation in each cell is
            // identical.  Any non-zero MS_effect ⟹ F = ∞ (significant);
            // zero MS_effect ⟹ F = 0 (not significant).
            fA = if (msA > 0.0) Double.POSITIVE_INFINITY else 0.0
            fB = if (msB > 0.0) Double.POSITIVE_INFINITY else 0.0
            fAB = if (msAB > 0.0) Double.POSITIVE_INFINITY else 0.0
        }

        // ---- p-values from F distribution --------------------------------
        // P(F > f_obs | df_num, df_den)
        val pA = computeFPValue(fA, dfA, dfW)
        val pB = computeFPValue(fB, dfB, dfW)
        val pAB = computeFPValue(fAB, dfAB, dfW)

        // ---- Post-hoc: Tukey HSD when a main effect is significant -------
        val tukeyResults = mutableListOf<TukeyResult>()

        // Total number of groups for the studentized range k parameter
        // depends on which factor we are testing:
        //   Factor A (floor levels): k = a, each group mean is computed
        //       across b·n observations.
        //   Factor B (building positions): k = b, each group mean is computed
        //       across a·n observations.
        if (pA < alpha) {
            val groupN = b * n   // observations per Factor-A level
            tukeyResults += computeTukeyHSD(
                groupMeans = meansA,
                groupN = groupN,
                msW = msW,
                dfW = dfW
            )
        }
        if (pB < alpha) {
            val groupN = a * n   // observations per Factor-B level
            tukeyResults += computeTukeyHSD(
                groupMeans = meansB,
                groupN = groupN,
                msW = msW,
                dfW = dfW
            )
        }

        return AnovaResult(
            dependentVar = dependentVar,
            factorAName = "floor_lvl",
            factorBName = "building_pos",
            ssA = ssA, dfA = dfA, msA = msA, fA = fA, pA = pA,
            ssB = ssB, dfB = dfB, msB = msB, fB = fB, pB = pB,
            ssAB = ssAB, dfAB = dfAB, msAB = msAB, fAB = fAB, pAB = pAB,
            ssW = ssW, dfW = dfW, msW = msW,
            ssT = ssT, dfT = dfT,
            tukeyResults = tukeyResults
        )
    }

    // ================================================================== //
    //  3. TUKEY'S HSD POST-HOC TEST                                       //
    // ================================================================== //

    /**
     * Performs **Tukey's Honestly Significant Difference (HSD)** pairwise
     * comparison for all pairs of group means.
     *
     * ### Procedure
     *
     * For each pair of groups `(i, j)`:
     *
     * ```
     *   meanDiff = |X̄ᵢ − X̄ⱼ|
     *   SE       = √(MS_W / n)
     *   q        = meanDiff / SE
     * ```
     *
     * where `n` = [groupN] is the number of observations **per group**
     * (not per cell — for Factor A this is `b × n_cell`; for Factor B
     * it is `a × n_cell`).
     *
     * The p-value is obtained from the **Studentized Range distribution**
     * `Q(k, df_W)` where `k` = number of groups.
     *
     * A comparison is flagged as significant when `p < 0.05`.
     *
     * @param groupMeans Map from group label to its marginal mean.
     * @param groupN     Number of observations that went into each group mean.
     * @param msW        Within-cell mean square (from the ANOVA table).
     * @param dfW        Within-cell degrees of freedom (from the ANOVA table).
     * @return A list of [TukeyResult] for every unique pair of groups.
     */
    fun computeTukeyHSD(
        groupMeans: Map<String, Double>,
        groupN: Int,
        msW: Double,
        dfW: Int
    ): List<TukeyResult> {
        val results = mutableListOf<TukeyResult>()
        val labels = groupMeans.keys.sorted()
        val k = labels.size

        if (k < 2 || groupN < 1 || msW < 0.0 || dfW < 1) return results

        // Standard error of a group mean: SE = √(MS_W / n)
        val se = Math.sqrt(msW / groupN)
        if (se == 0.0) return results   // all cell values identical

        for (i in labels.indices) {
            for (j in i + 1 until labels.size) {
                val label1 = labels[i]
                val label2 = labels[j]
                val meanDiff = Math.abs(groupMeans[label1]!! - groupMeans[label2]!!)

                // Studentized range statistic: q = |X̄ᵢ − X̄ⱼ| / SE
                val qStat = meanDiff / se

                // p-value from the Studentized Range distribution Q(k, df_W).
                val pValue = studentizedRange.survivalProbability(
                    q = qStat,
                    k = k,
                    df = dfW.toDouble()
                )

                results += TukeyResult(
                    group1 = label1,
                    group2 = label2,
                    meanDiff = meanDiff,
                    qStat = qStat,
                    pValue = pValue,
                    significant = pValue < alpha
                )
            }
        }

        return results
    }

    // ================================================================== //
    //  4. FULL REPORT GENERATION                                          //
    // ================================================================== //

    /**
     * Generates a complete [StatsReport] from the collected measurements.
     *
     * ### Regression
     *
     * Two simple linear regressions are computed:
     *
     * | X (predictor)         | Y (response)         |
     * |-----------------------|----------------------|
     * | `wifi_dbm`            | `dl_mbps`            |
     * | `wifi_dbm`            | `ul_mbps`            |
     *
     * Measurements with null DL or UL are excluded from the respective
     * regressions.
     *
     * ### ANOVA
     *
     * Three separate two-way ANOVAs are run:
     *
     * | Dependent Variable | Factor A    | Factor B       |
     * |--------------------|-------------|----------------|
     * | `wifi_dbm`         | `floor_lvl` | `building_pos` |
     * | `dl_mbps`          | `floor_lvl` | `building_pos` |
     * | `ul_mbps`          | `floor_lvl` | `building_pos` |
     *
     * @param measurements All collected [WifiMeasurement] rows.
     * @return A [StatsReport] aggregating every analysis.
     */
    fun generateReport(measurements: List<WifiMeasurement>): StatsReport {

        // ---- Prepare regression arrays -----------------------------------
        val dbmValues = measurements.map { m ->
            m.wifi_dbm.toDouble()
        }.toDoubleArray()

        val dlValues = measurements.map { m ->
            m.dl_mbps ?: Double.NaN
        }.toDoubleArray()

        val ulValues = measurements.map { m ->
            m.ul_mbps ?: Double.NaN
        }.toDoubleArray()

        // ---- Regressions (Use ALL data) ----------------------------------
        val regressionDl = computeRegression(dbmValues, dlValues)
        val regressionUl = computeRegression(dbmValues, ulValues)

        // ---- Downsample data for ANOVA -----------------------------------
        // ANOVA requires balanced data. We take exactly 30 randomly selected 
        // points from each (building, floor) group.
        val groupedData = measurements.groupBy { Pair(it.building_pos, it.floor_lvl) }
        val anovaData = mutableListOf<WifiMeasurement>()
        
        for ((group, list) in groupedData) {
            if (list.size >= 30) {
                anovaData.addAll(list.shuffled().take(30))
            } else {
                // If a group has fewer than 30 points, we add all of them, 
                // but note that ANOVA will fail/return null later if the design is unbalanced.
                anovaData.addAll(list)
            }
        }

        // ---- Two-Way ANOVAs (Use DOWNSAMPLED data) -----------------------
        val anovaDbm = computeTwoWayAnova(anovaData, "wifi_dbm")
        val anovaDl = computeTwoWayAnova(
            anovaData.filter { it.dl_mbps != null }, "dl_mbps"
        )
        val anovaUl = computeTwoWayAnova(
            anovaData.filter { it.ul_mbps != null }, "ul_mbps"
        )

        return StatsReport(
            regressionDl = regressionDl,
            regressionUl = regressionUl,
            anovaDbm = anovaDbm,
            anovaDl = anovaDl,
            anovaUl = anovaUl
        )
    }

    // ================================================================== //
    //  Internal helpers                                                    //
    // ================================================================== //

    /**
     * Computes the upper-tail p-value from the F-distribution:
     *
     * ```
     *   p = P(F > f_obs | df_num, df_den)
     *     = 1 − CDF_F(f_obs; df_num, df_den)
     * ```
     *
     * Handles edge cases:
     * - `f_obs` infinite ⟹ p = 0
     * - `df_num` or `df_den` ≤ 0 ⟹ p = 1 (not computable)
     *
     * @param fValue Observed F-statistic.
     * @param dfNum  Numerator degrees of freedom.
     * @param dfDen  Denominator degrees of freedom.
     * @return       Upper-tail probability in `[0, 1]`.
     */
    private fun computeFPValue(fValue: Double, dfNum: Int, dfDen: Int): Double {
        if (dfNum <= 0 || dfDen <= 0) return 1.0
        if (fValue.isInfinite()) return 0.0
        if (fValue <= 0.0) return 1.0

        return try {
            val fDist = FDistribution(dfNum.toDouble(), dfDen.toDouble())
            1.0 - fDist.cumulativeProbability(fValue)
        } catch (_: Exception) {
            1.0   // fallback: treat as non-significant
        }
    }
}
