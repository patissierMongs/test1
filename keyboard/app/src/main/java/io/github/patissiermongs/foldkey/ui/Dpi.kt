package io.github.patissiermongs.foldkey.ui

import android.util.DisplayMetrics
import kotlin.math.abs

object Dpi {
    fun physical(dm: DisplayMetrics, horizontal: Boolean): Float {
        val reported = if (horizontal) dm.xdpi else dm.ydpi
        val nominal = dm.densityDpi.toFloat()
        return if (reported > 0f && abs(reported - nominal) / nominal < 0.35f) reported else nominal
    }
}
