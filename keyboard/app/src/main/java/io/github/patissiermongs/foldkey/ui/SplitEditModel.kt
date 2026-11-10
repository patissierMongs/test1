package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.SplitRow
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class SplitEditModel(
    private val rows: List<SplitRow>,
    val widthMm: Float,
    private val edgeMm: Float,
    private val unitMinMm: Float = UNIT_MIN_MM,
    private val unitMaxMm: Float = UNIT_MAX_MM,
    private val sideMaxMm: Float = SIDE_MAX_MM,
) {
    enum class Handle { LEFT_OUTER, LEFT_INNER, RIGHT_INNER, RIGHT_OUTER }

    data class Sizes(val leftSideMm: Float, val leftUnitMm: Float, val rightUnitMm: Float, val rightSideMm: Float)

    fun side(mm: Float): Float = max(mm, edgeMm)

    fun totalMm(s: Sizes): Float = rows.maxOf { it.left.span * s.leftUnitMm + it.right.span * s.rightUnitMm }

    fun gapMm(s: Sizes): Float = widthMm - side(s.leftSideMm) - side(s.rightSideMm) - totalMm(s)

    fun minGapMm(s: Sizes): Float = KeyboardGeometry.MIN_CENTER_UNITS * (s.leftUnitMm + s.rightUnitMm) / 2f

    fun fits(s: Sizes): Boolean = gapMm(s) >= minGapMm(s) - TOLERANCE_MM

    fun handleRow(s: Sizes): Int {
        var best = 0
        var bestTotal = -1f
        rows.forEachIndexed { i, row ->
            if (row.left.inward + row.right.inward > 0f) return@forEachIndexed
            val total = row.left.span * s.leftUnitMm + row.right.span * s.rightUnitMm
            if (total > bestTotal) {
                best = i
                bestTotal = total
            }
        }
        return best
    }

    fun handleMm(s: Sizes, handle: Handle, row: Int): Float = when (handle) {
        Handle.LEFT_OUTER -> side(s.leftSideMm)
        Handle.LEFT_INNER -> side(s.leftSideMm) + rows[row].left.span * s.leftUnitMm
        Handle.RIGHT_INNER -> widthMm - side(s.rightSideMm) - rows[row].right.span * s.rightUnitMm
        Handle.RIGHT_OUTER -> widthMm - side(s.rightSideMm)
    }

    fun value(s: Sizes, handle: Handle): Float = when (handle) {
        Handle.LEFT_OUTER -> s.leftSideMm
        Handle.LEFT_INNER -> s.leftUnitMm
        Handle.RIGHT_INNER -> s.rightUnitMm
        Handle.RIGHT_OUTER -> s.rightSideMm
    }

    fun drag(s: Sizes, handle: Handle, xMm: Float, row: Int): Sizes {
        val wanted = when (handle) {
            Handle.LEFT_OUTER -> xMm
            Handle.LEFT_INNER -> (xMm - side(s.leftSideMm)) / rows[row].left.span
            Handle.RIGHT_INNER -> (widthMm - side(s.rightSideMm) - xMm) / rows[row].right.span
            Handle.RIGHT_OUTER -> widthMm - xMm
        }
        return set(s, handle, wanted)
    }

    fun nudge(s: Sizes, handle: Handle, deltaMm: Float): Sizes = set(s, handle, value(s, handle) + deltaMm)

    fun set(s: Sizes, handle: Handle, wanted: Float): Sizes {
        val (low, high) = range(handle)
        val target = round1(wanted.coerceIn(low, high))
        val direct = with(s, handle, target)
        if (fits(direct)) return direct
        if (!fits(with(s, handle, low))) return s
        var good = low
        var bad = target
        repeat(BISECTIONS) {
            val mid = (good + bad) / 2f
            if (fits(with(s, handle, mid))) good = mid else bad = mid
        }
        return with(s, handle, floor1(good))
    }

    fun floorSizes(s: Sizes): Sizes = Sizes(round1(s.leftSideMm), floor1(s.leftUnitMm), floor1(s.rightUnitMm), round1(s.rightSideMm))

    private fun range(handle: Handle): Pair<Float, Float> = when (handle) {
        Handle.LEFT_OUTER, Handle.RIGHT_OUTER -> 0f to sideMaxMm
        Handle.LEFT_INNER, Handle.RIGHT_INNER -> unitMinMm to unitMaxMm
    }

    private fun with(s: Sizes, handle: Handle, v: Float): Sizes = when (handle) {
        Handle.LEFT_OUTER -> s.copy(leftSideMm = v)
        Handle.LEFT_INNER -> s.copy(leftUnitMm = v)
        Handle.RIGHT_INNER -> s.copy(rightUnitMm = v)
        Handle.RIGHT_OUTER -> s.copy(rightSideMm = v)
    }

    companion object {
        const val UNIT_MIN_MM = 4.0f
        const val UNIT_MAX_MM = 11.0f
        const val SIDE_MAX_MM = 40.0f
        const val STEP_MM = 0.1f
        private const val TOLERANCE_MM = 0.001f
        private const val BISECTIONS = 24

        fun round1(mm: Float): Float = (mm * 10f).roundToInt() / 10f

        fun floor1(mm: Float): Float = floor(mm * 10f + 0.0001f) / 10f

        fun tenths(mm: Float): Int = (mm * 10f).roundToInt()
    }
}
