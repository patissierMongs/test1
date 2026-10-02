package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TypingCalibration {
    data class Sample(val side: Side, val zone: Int, val dxMm: Float, val dyMm: Float)

    data class Placement(val unitMm: Float, val rowMm: Float, val leftMm: Float, val rightMm: Float, val liftMm: Float)

    data class Anchors(val leftCenterMm: Float, val rightCenterMm: Float, val middleMm: Float)

    data class Frame(val widthMm: Float, val maxHeightMm: Float, val edgeMm: Float, val ghostUnits: Float)

    data class Spread(val xMm: Float, val yMm: Float, val count: Int)

    data class Analysis(
        val zoneOffsets: Map<Int, Pair<Float, Float>>,
        val sideOffsets: Map<Side, Pair<Float, Float>>,
        val spread: Map<Side, Spread>,
        val kept: Int,
        val dropped: Int,
    ) {
        val pitchMm: Float get() = 2f * Z95 * spread.values.maxOf { it.xMm }
        val rowMm: Float get() = 2f * Z95 * spread.values.maxOf { it.yMm }

        fun offset(zone: Int, side: Side): Pair<Float, Float> = zoneOffsets[zone] ?: sideOffsets[side] ?: (0f to 0f)
    }

    fun analyze(samples: List<Sample>): Analysis? {
        val kept = samples.groupBy { it.side }.values.flatMap { trim(it) }
        val bySide = kept.groupBy { it.side }.filterValues { it.size >= MIN_SIDE_SAMPLES }
        if (bySide.isEmpty()) return null
        val used = kept.filter { it.side in bySide }
        val sideOffsets = bySide.mapValues { (_, list) -> mean(list.map { it.dxMm }) to mean(list.map { it.dyMm }) }
        val zoneOffsets = HashMap<Int, Pair<Float, Float>>()
        val zoneSizes = used.groupingBy { it.zone }.eachCount()
        for ((zone, list) in used.groupBy { it.zone }) {
            val side = list.groupingBy { it.side }.eachCount().maxBy { it.value }.key
            val (mx, my) = sideOffsets.getValue(side)
            val n = list.size
            zoneOffsets[zone] = (list.sumOf { it.dxMm.toDouble() }.toFloat() + SHRINK * mx) / (n + SHRINK) to
                (list.sumOf { it.dyMm.toDouble() }.toFloat() + SHRINK * my) / (n + SHRINK)
        }
        val spread = bySide.mapValues { (side, list) ->
            val rx = list.map { it.dxMm - (zoneOffsets[it.zone] ?: sideOffsets.getValue(side)).first }
            val ry = list.map { it.dyMm - (zoneOffsets[it.zone] ?: sideOffsets.getValue(side)).second }
            val fitted = list.sumOf { s ->
                val n = zoneSizes.getValue(s.zone) + SHRINK
                ((n + SHRINK) / (n * n)).toDouble()
            }.toFloat()
            val df = list.size - 1f - fitted
            Spread(rms(rx, df), rms(ry, df), list.size)
        }
        return Analysis(zoneOffsets, sideOffsets, spread, used.size, samples.size - used.size)
    }

    fun hitRate(samples: List<Sample>, analysis: Analysis, pitchMm: Float, rowMm: Float): Float {
        if (samples.isEmpty()) return 0f
        val inside = samples.count {
            val (ox, oy) = analysis.offset(it.zone, it.side)
            abs(it.dxMm - ox) < pitchMm / 2f && abs(it.dyMm - oy) < rowMm / 2f
        }
        return inside.toFloat() / samples.size
    }

    fun anchors(left: Zone, right: Zone): Anchors = Anchors(
        (left.nearMm + left.farMm) / 2f,
        (right.nearMm + right.farMm) / 2f,
        ((left.bottomMm + left.topMm) / 2f + (right.bottomMm + right.topMm) / 2f) / 2f,
    )

    fun place(anchors: Anchors, frame: Frame, layer: Layer, unitMm: Float, rowMm: Float): Placement {
        val rows = Layouts.rows(LayoutKind.SPLIT, layer)
        val m = frame.maxHeightMm
        val cap = if (anchors.middleMm < m / 2f) m / rows else 2f * (m - anchors.middleMm) / rows
        val row = min(rowMm, cap).coerceIn(ThumbZones.ROW_MIN_MM, ThumbZones.ROW_MAX_MM)
        val lift = (anchors.middleMm - rows * row / 2f).coerceIn(0f, ThumbZones.LIFT_MAX_MM)
        val general = layer == Layer.GENERAL
        val leftUnits = if (general) Layouts.generalSplitLeftUnits else Layouts.splitLeftUnits
        val rightUnits = if (general) Layouts.generalSplitRightUnits else Layouts.splitRightUnits
        val total = if (general) Layouts.generalSplitUnits else Layouts.splitUnits
        var left = max(frame.edgeMm, (anchors.leftCenterMm - leftUnits * unitMm / 2f).coerceAtMost(ThumbZones.SIDE_MAX_MM))
        var right = max(frame.edgeMm, (anchors.rightCenterMm - rightUnits * unitMm / 2f).coerceAtMost(ThumbZones.SIDE_MAX_MM))
        val excess = left + right + (total + 2f * frame.ghostUnits) * unitMm - frame.widthMm
        val room = left + right - 2f * frame.edgeMm
        if (excess > 0f && room > 0f) {
            val keep = ((room - excess) / room).coerceAtLeast(0f)
            left = frame.edgeMm + (left - frame.edgeMm) * keep
            right = frame.edgeMm + (right - frame.edgeMm) * keep
        }
        return Placement(unitMm, row, tenth(left), tenth(right), tenth(lift))
    }

    private fun tenth(mm: Float): Float = Math.round(mm * 10f) / 10f

    fun grow(currentMm: Float, requiredMm: Float, minMm: Float, maxMm: Float): Float =
        max(currentMm, ceil(requiredMm * 10f - 1e-3f) / 10f).coerceIn(minMm, maxMm)

    private fun trim(list: List<Sample>): List<Sample> {
        if (list.size < MIN_SIDE_SAMPLES) return list
        val mx = median(list.map { it.dxMm })
        val my = median(list.map { it.dyMm })
        val sx = max(MAD_SCALE * median(list.map { abs(it.dxMm - mx) }), MIN_SCALE_MM)
        val sy = max(MAD_SCALE * median(list.map { abs(it.dyMm - my) }), MIN_SCALE_MM)
        return list.filter { abs(it.dxMm - mx) <= TRIM * sx && abs(it.dyMm - my) <= TRIM * sy }
    }

    private fun mean(values: List<Float>): Float = values.sum() / values.size

    private fun rms(values: List<Float>, df: Float): Float =
        sqrt(values.sumOf { (it * it).toDouble() } / max(df, 1f)).toFloat()

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    const val Z95 = 1.959964f
    const val SHRINK = 5f
    const val TRIM = 4f
    const val MAD_SCALE = 1.4826f
    const val MIN_SCALE_MM = 0.3f
    const val MIN_SIDE_SAMPLES = 8
}
