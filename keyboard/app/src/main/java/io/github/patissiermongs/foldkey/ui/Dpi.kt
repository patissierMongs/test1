package io.github.patissiermongs.foldkey.ui

import android.util.DisplayMetrics
import kotlin.math.abs
import kotlin.math.max

object Dpi {
    const val MIN_PANEL_DPI = 100f
    const val MAX_PANEL_DPI = 800f
    const val MAX_AXIS_SKEW = 0.1f

    fun physical(dm: DisplayMetrics, horizontal: Boolean): Float {
        val x = dm.xdpi
        val y = dm.ydpi
        return if (isPanelDpi(x, y)) (if (horizontal) x else y) else dm.densityDpi.toFloat()
    }

    fun isPanelDpi(x: Float, y: Float): Boolean =
        x in MIN_PANEL_DPI..MAX_PANEL_DPI && y in MIN_PANEL_DPI..MAX_PANEL_DPI && abs(x - y) / max(x, y) <= MAX_AXIS_SKEW
}
