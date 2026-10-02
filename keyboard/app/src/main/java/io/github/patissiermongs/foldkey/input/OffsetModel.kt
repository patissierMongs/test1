package io.github.patissiermongs.foldkey.input

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OffsetModel(val zones: Int) {
    private val dx = FloatArray(zones)
    private val dy = FloatArray(zones)
    private val count = IntArray(zones)

    var warmup = 20
    var minRate = 0.03f
    var maxShare = 0.35f
    var outlierShare = 0.5f

    fun samples(zone: Int): Int = if (zone in 0 until zones) count[zone] else 0

    fun meanMm(zone: Int): Pair<Float, Float> = if (zone in 0 until zones) dx[zone] to dy[zone] else 0f to 0f

    fun correctionMm(zone: Int, keyWidthMm: Float, keyHeightMm: Float): Pair<Float, Float> {
        if (zone !in 0 until zones || count[zone] == 0) return 0f to 0f
        val weight = min(1f, count[zone].toFloat() / warmup)
        val limX = keyWidthMm * maxShare
        val limY = keyHeightMm * maxShare
        return (dx[zone] * weight).coerceIn(-limX, limX) to (dy[zone] * weight).coerceIn(-limY, limY)
    }

    fun add(zone: Int, offsetXMm: Float, offsetYMm: Float, keyWidthMm: Float, keyHeightMm: Float): Boolean {
        if (zone !in 0 until zones) return false
        if (abs(offsetXMm) > keyWidthMm * outlierShare || abs(offsetYMm) > keyHeightMm * outlierShare) return false
        val n = count[zone] + 1
        val rate = max(1f / n, minRate)
        dx[zone] += (offsetXMm - dx[zone]) * rate
        dy[zone] += (offsetYMm - dy[zone]) * rate
        count[zone] = min(n, 1_000_000)
        return true
    }

    fun seed(zone: Int, offsetXMm: Float, offsetYMm: Float, n: Int) {
        if (zone !in 0 until zones) return
        dx[zone] = offsetXMm
        dy[zone] = offsetYMm
        count[zone] = n
    }

    fun clear() {
        dx.fill(0f)
        dy.fill(0f)
        count.fill(0)
    }

    fun serialize(): String = (0 until zones).joinToString(";") { "${dx[it]},${dy[it]},${count[it]}" }

    fun load(data: String?) {
        clear()
        if (data.isNullOrEmpty()) return
        val parts = data.split(";")
        if (parts.size != zones) return
        parts.forEachIndexed { i, p ->
            val f = p.split(",")
            if (f.size == 3) {
                dx[i] = f[0].toFloatOrNull() ?: 0f
                dy[i] = f[1].toFloatOrNull() ?: 0f
                count[i] = f[2].toIntOrNull() ?: 0
            }
        }
    }
}
