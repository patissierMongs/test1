package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PositionAdvice {
    const val MIN_TAPS = 150
    const val MIN_MOVE_MM = 0.3f

    data class Thumb(val taps: Int, val xMm: Float, val yMm: Float, val seXMm: Float, val seYMm: Float)

    data class Advice(val left: Thumb?, val right: Thumb?, val leftMm: Float, val rightMm: Float, val raiseMm: Float) {
        val moves: Boolean get() = leftMm != 0f || rightMm != 0f || raiseMm != 0f
    }

    private class Sums(var n: Int = 0, var sx: Double = 0.0, var sy: Double = 0.0, var sxx: Double = 0.0, var syy: Double = 0.0) {
        operator fun plus(o: Sums) = Sums(n + o.n, sx + o.sx, sy + o.sy, sxx + o.sxx, syy + o.syy)

        fun thumb(): Thumb? {
            if (n == 0) return null
            val mx = sx / n
            val my = sy / n
            val vx = max(0.0, sxx / n - mx * mx)
            val vy = max(0.0, syy / n - my * my)
            return Thumb(n, mx.toFloat(), my.toFloat(), sqrt(vx / n).toFloat(), sqrt(vy / n).toFloat())
        }
    }

    private fun sums(heat: TapHeat, keys: Collection<Char>): Sums {
        val s = Sums()
        for (c in keys) {
            val k = heat.cloud(c) ?: continue
            s.n += k.n
            s.sx += k.sx
            s.sy += k.sy
            s.sxx += k.sxx
            s.syy += k.syy
        }
        return s
    }

    private fun move(mean: Float, se: Float): Float {
        if (abs(mean) < max(MIN_MOVE_MM, 2f * se)) return 0f
        return (mean * 10f).roundToInt() / 10f
    }

    fun advise(heat: TapHeat, leftKeys: Set<Char>): Advice {
        val leftSums = sums(heat, heat.keys.filter { it in leftKeys })
        val rightSums = sums(heat, heat.keys.filter { it !in leftKeys })
        val left = leftSums.thumb()
        val right = rightSums.thumb()
        val both = (leftSums + rightSums).thumb()
        return Advice(
            left,
            right,
            leftMm = if (left == null || left.taps < MIN_TAPS) 0f else move(left.xMm, left.seXMm),
            rightMm = if (right == null || right.taps < MIN_TAPS) 0f else move(right.xMm, right.seXMm),
            raiseMm = if (both == null || both.taps < MIN_TAPS) 0f else -move(both.yMm, both.seYMm),
        )
    }

    fun shiftOffsets(model: OffsetModel, before: KeyboardGeometry, after: KeyboardGeometry, pxPerMmX: Float, pxPerMmY: Float, raiseMm: Float) {
        val moved = HashMap<Int, MutableList<Pair<Float, Float>>>()
        for (old in before.keys) {
            if (old.ghost || old.pad) continue
            val new = after.keys.firstOrNull { !it.ghost && !it.pad && it.def === old.def } ?: continue
            val dx = (new.face.centerX - old.face.centerX) / pxPerMmX
            val dy = (new.face.centerY - old.face.centerY) / pxPerMmY - raiseMm
            moved.getOrPut(old.zone) { ArrayList() }.add(dx to dy)
        }
        for ((zone, list) in moved) {
            model.move(zone, -list.map { it.first }.average().toFloat(), -list.map { it.second }.average().toFloat())
        }
    }
}
