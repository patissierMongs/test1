package io.github.patissiermongs.foldkey.input

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ReachCalibration {
    enum class Side { LEFT, RIGHT }

    data class Sample(val xMm: Float, val yMm: Float)

    data class Bands(val rows: Int, val rowHeightMm: Float, val liftMm: Float) {
        fun row(yMm: Float): Int? {
            val fromBottom = yMm - liftMm
            if (fromBottom < 0f) return null
            val r = rows - 1 - floor(fromBottom / rowHeightMm).toInt()
            return if (r in 0 until rows) r else null
        }
    }

    data class Summary(val medianMm: Float, val spreadMm: Float, val consistent: Boolean)

    fun strokeReach(samples: List<Sample>, side: Side, widthMm: Float, bands: Bands): Float? {
        val best = HashMap<Int, Float>()
        for (s in samples) {
            val r = bands.row(s.yMm) ?: continue
            val reach = if (side == Side.LEFT) s.xMm else widthMm - s.xMm
            best[r] = max(best[r] ?: Float.NEGATIVE_INFINITY, reach)
        }
        if (best.size < bands.rows) return null
        return best.values.min()
    }

    fun summarize(reaches: List<Float>): Summary? {
        if (reaches.isEmpty()) return null
        val sorted = reaches.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
        val spread = sorted.last() - sorted.first()
        return Summary(median, spread, spread <= max(TOLERANCE_MM, TOLERANCE_SHARE * median))
    }

    fun unitMm(
        leftReachMm: Float,
        rightReachMm: Float,
        sidePaddingMm: Float,
        leftUnits: Float,
        rightUnits: Float,
        minMm: Float,
        maxMm: Float,
    ): Float {
        val u = min((leftReachMm - sidePaddingMm) / leftUnits, (rightReachMm - sidePaddingMm) / rightUnits)
        return (floor(u * 10f) / 10f).coerceIn(minMm, maxMm)
    }

    const val STROKES = 3
    const val TOLERANCE_MM = 6f
    const val TOLERANCE_SHARE = 0.1f
}
