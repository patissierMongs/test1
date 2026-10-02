package io.github.patissiermongs.foldkey.input

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ThumbZones {
    enum class Side { LEFT, RIGHT }

    data class Sample(val xMm: Float, val yMm: Float)

    data class Zone(val nearMm: Float, val farMm: Float, val bottomMm: Float, val topMm: Float) {
        val widthMm: Float get() = farMm - nearMm
        val heightMm: Float get() = topMm - bottomMm
    }

    data class Summary(val zone: Zone, val spreadMm: Float, val consistent: Boolean)

    data class Fit(
        val leftMm: Float,
        val rightMm: Float,
        val codeUnitMm: Float,
        val generalUnitMm: Float,
        val liftMm: Float,
        val rowHeightMm: Float,
    )

    fun strokeZone(samples: List<Sample>, side: Side, widthMm: Float): Zone? {
        if (samples.isEmpty()) return null
        val rows = HashMap<Int, FloatArray>()
        val columns = HashMap<Int, FloatArray>()
        fun widen(map: HashMap<Int, FloatArray>, key: Int, value: Float) {
            val range = map.getOrPut(key) { floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE) }
            range[0] = min(range[0], value)
            range[1] = max(range[1], value)
        }
        fun mark(s: Sample) {
            val d = if (side == Side.LEFT) s.xMm else widthMm - s.xMm
            widen(rows, floor(s.yMm / CELL_MM).toInt(), d)
            widen(columns, floor(d / CELL_MM).toInt(), s.yMm)
        }
        mark(samples[0])
        for (k in 1 until samples.size) {
            val a = samples[k - 1]
            val b = samples[k]
            val steps = max(1, ceil(max(abs(b.xMm - a.xMm), abs(b.yMm - a.yMm)) / (CELL_MM / 2f)).toInt())
            for (i in 1..steps) {
                val f = i.toFloat() / steps
                mark(Sample(a.xMm + (b.xMm - a.xMm) * f, a.yMm + (b.yMm - a.yMm) * f))
            }
        }
        if (rows.size < MIN_BANDS || columns.size < MIN_BANDS) return null
        val zone = Zone(
            nearMm = median(rows.values.map { it[0] }),
            farMm = median(rows.values.map { it[1] }),
            bottomMm = median(columns.values.map { it[0] }),
            topMm = median(columns.values.map { it[1] }),
        )
        if (zone.widthMm < MIN_WIDTH_MM || zone.heightMm < MIN_HEIGHT_MM) return null
        return zone
    }

    fun summarize(zones: List<Zone>): Summary? {
        if (zones.isEmpty()) return null
        val fields = listOf<(Zone) -> Float>({ it.nearMm }, { it.farMm }, { it.bottomMm }, { it.topMm })
        var spread = 0f
        var consistent = true
        val medians = fields.map { field ->
            val values = zones.map(field)
            val m = median(values)
            val s = values.max() - values.min()
            spread = max(spread, s)
            if (s > max(TOLERANCE_MM, TOLERANCE_SHARE * abs(m))) consistent = false
            m
        }
        return Summary(Zone(medians[0], medians[1], medians[2], medians[3]), spread, consistent)
    }

    fun unitMm(left: Zone, right: Zone, leftUnits: Float, rightUnits: Float): Float {
        val u = min(left.widthMm / leftUnits, right.widthMm / rightUnits)
        return (floor(u * 10f) / 10f).coerceIn(UNIT_MIN_MM, UNIT_MAX_MM)
    }

    fun fit(left: Zone, right: Zone, rows: Int, code: Pair<Float, Float>, general: Pair<Float, Float>): Fit {
        val bottom = max(left.bottomMm, right.bottomMm)
        val top = min(left.topMm, right.topMm)
        return Fit(
            leftMm = tenth(left.nearMm).coerceIn(0f, SIDE_MAX_MM),
            rightMm = tenth(right.nearMm).coerceIn(0f, SIDE_MAX_MM),
            codeUnitMm = unitMm(left, right, code.first, code.second),
            generalUnitMm = unitMm(left, right, general.first, general.second),
            liftMm = tenth(bottom).coerceIn(0f, LIFT_MAX_MM),
            rowHeightMm = (floor((top - bottom) / rows * 10f) / 10f).coerceIn(ROW_MIN_MM, ROW_MAX_MM),
        )
    }

    private fun tenth(v: Float): Float = Math.round(v * 10f) / 10f

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    const val CELL_MM = 0.5f
    const val STROKES = 3
    const val MIN_BANDS = 16
    const val MIN_WIDTH_MM = 12f
    const val MIN_HEIGHT_MM = 15f
    const val TOLERANCE_MM = 6f
    const val TOLERANCE_SHARE = 0.1f
    const val UNIT_MIN_MM = 4.0f
    const val UNIT_MAX_MM = 11.0f
    const val ROW_MIN_MM = 5.0f
    const val ROW_MAX_MM = 15.0f
    const val LIFT_MAX_MM = 30f
    const val SIDE_MAX_MM = 40f
}
