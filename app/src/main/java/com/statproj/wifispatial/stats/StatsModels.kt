package com.statproj.wifispatial.stats

/**
 * Aggregated statistical report containing regression and ANOVA results
 * for all dependent variables in the study.
 */
data class StatsReport(
    val regressionDl: RegressionResult?,
    val regressionUl: RegressionResult?,
    val anovaDbm: AnovaResult?,
    val anovaDl: AnovaResult?,
    val anovaUl: AnovaResult?
)

/**
 * Result of a simple linear regression analysis.
 *
 * @property slope     Slope of the regression line.
 * @property intercept Y-intercept of the regression line.
 * @property r         Pearson correlation coefficient.
 * @property rSquared  Coefficient of determination (R²).
 * @property n         Number of observations.
 */
data class RegressionResult(
    val slope: Double,
    val intercept: Double,
    val r: Double,
    val rSquared: Double,
    val n: Int
)

/**
 * Result of a two-way ANOVA (Factor A × Factor B) on a single dependent variable.
 *
 * The model decomposes total variance into:
 * - Factor A main effect
 * - Factor B main effect
 * - A × B interaction effect
 * - Within-group (residual) error
 *
 * @property dependentVar  Name of the dependent variable being analysed.
 * @property factorAName   Name of the first factor (e.g. "building_pos").
 * @property factorBName   Name of the second factor (e.g. "floor_lvl").
 */
data class AnovaResult(
    val dependentVar: String,
    val factorAName: String,
    val factorBName: String,
    val ssA: Double,
    val dfA: Int,
    val msA: Double,
    val fA: Double,
    val pA: Double,
    val ssB: Double,
    val dfB: Int,
    val msB: Double,
    val fB: Double,
    val pB: Double,
    val ssAB: Double,
    val dfAB: Int,
    val msAB: Double,
    val fAB: Double,
    val pAB: Double,
    val ssW: Double,
    val dfW: Int,
    val msW: Double,
    val ssT: Double,
    val dfT: Int,
    val tukeyResults: List<TukeyResult>
)

/**
 * Result of a single Tukey HSD pairwise comparison.
 *
 * @property group1      Label of the first group.
 * @property group2      Label of the second group.
 * @property meanDiff    Difference in group means (group1 − group2).
 * @property qStat       Studentized range statistic (q).
 * @property pValue      Approximate p-value for the comparison.
 * @property significant Whether the comparison is significant at α = 0.05.
 */
data class TukeyResult(
    val group1: String,
    val group2: String,
    val meanDiff: Double,
    val qStat: Double,
    val pValue: Double,
    val significant: Boolean
)
