package io.github.patissiermongs.foldkey.input

import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.max

class TapHeat(val signature: String = "") {
    class Cloud internal constructor(
        n: Int = 0,
        sx: Double = 0.0,
        sy: Double = 0.0,
        sxx: Double = 0.0,
        syy: Double = 0.0,
        sxy: Double = 0.0,
    ) {
        var n = n
            private set
        var sx = sx
            private set
        var sy = sy
            private set
        var sxx = sxx
            private set
        var syy = syy
            private set
        var sxy = sxy
            private set

        val meanXMm: Float get() = if (n == 0) 0f else (sx / n).toFloat()
        val meanYMm: Float get() = if (n == 0) 0f else (sy / n).toFloat()
        val varXMm2: Float get() = if (n == 0) 0f else max(0.0, sxx / n - (sx / n) * (sx / n)).toFloat()
        val varYMm2: Float get() = if (n == 0) 0f else max(0.0, syy / n - (sy / n) * (sy / n)).toFloat()
        val covMm2: Float get() = if (n == 0) 0f else (sxy / n - (sx / n) * (sy / n)).toFloat()

        internal fun add(dx: Double, dy: Double) {
            n++
            sx += dx
            sy += dy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
    }

    private val clouds = TreeMap<Char, Cloud>()

    val keys: Set<Char> get() = clouds.keys

    val taps: Int get() = clouds.values.sumOf { it.n }

    fun cloud(c: Char): Cloud? = clouds[c]

    fun add(c: Char, dxMm: Float, dyMm: Float, halfWidthMm: Float, halfHeightMm: Float): Boolean {
        if (abs(dxMm) > halfWidthMm || abs(dyMm) > halfHeightMm) return false
        clouds.getOrPut(c) { Cloud() }.add(dxMm.toDouble(), dyMm.toDouble())
        return true
    }

    fun serialize(): String = signature + SEPARATOR + clouds.entries.joinToString(";") { (c, k) ->
        "${c.code}:${k.n},${k.sx},${k.sy},${k.sxx},${k.syy},${k.sxy}"
    }

    companion object {
        private const val SEPARATOR = '|'

        fun parse(data: String?, signature: String): TapHeat {
            val heat = TapHeat(signature)
            if (data == null) return heat
            val bar = data.indexOf(SEPARATOR)
            if (bar < 0 || data.substring(0, bar) != signature) return heat
            for (part in data.substring(bar + 1).split(';')) {
                val colon = part.indexOf(':')
                if (colon < 0) continue
                val code = part.substring(0, colon).toIntOrNull() ?: continue
                val f = part.substring(colon + 1).split(',')
                if (f.size != 6 || code !in 0..0xFFFF) continue
                val n = f[0].toIntOrNull() ?: continue
                val sums = f.drop(1).map { it.toDoubleOrNull() ?: Double.NaN }
                if (n <= 0 || sums.any { !it.isFinite() }) continue
                heat.clouds[code.toChar()] = Cloud(n, sums[0], sums[1], sums[2], sums[3], sums[4])
            }
            return heat
        }
    }
}
