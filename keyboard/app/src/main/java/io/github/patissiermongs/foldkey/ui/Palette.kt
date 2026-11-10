package io.github.patissiermongs.foldkey.ui

import android.content.res.Configuration

data class Palette(
    val background: Int,
    val strip: Int,
    val key: Int,
    val modKey: Int,
    val pressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentText: Int,
    val locked: Int,
    val popup: Int,
    val popupText: Int,
    val shadow: Int,
) {
    companion object {
        val DARK = Palette(
            background = 0xFF141518.toInt(),
            strip = 0xFF1C1E22.toInt(),
            key = 0xFF33363C.toInt(),
            modKey = 0xFF26282D.toInt(),
            pressed = 0xFF5A5F69.toInt(),
            text = 0xFFECEEF1.toInt(),
            hint = 0xFF9EA4AD.toInt(),
            accent = 0xFF7FB2FF.toInt(),
            accentText = 0xFF0B1A2E.toInt(),
            locked = 0xFFFFC857.toInt(),
            popup = 0xFF4A4F58.toInt(),
            popupText = 0xFFFFFFFF.toInt(),
            shadow = 0xFF0A0A0C.toInt(),
        )

        val LIGHT = Palette(
            background = 0xFFD5D8DE.toInt(),
            strip = 0xFFE3E5E9.toInt(),
            key = 0xFFFFFFFF.toInt(),
            modKey = 0xFFB9BEC7.toInt(),
            pressed = 0xFF9AA1AD.toInt(),
            text = 0xFF1B1D21.toInt(),
            hint = 0xFF5B616B.toInt(),
            accent = 0xFF1F6FEB.toInt(),
            accentText = 0xFFFFFFFF.toInt(),
            locked = 0xFFC77700.toInt(),
            popup = 0xFFFFFFFF.toInt(),
            popupText = 0xFF1B1D21.toInt(),
            shadow = 0xFF9EA3AB.toInt(),
        )

        fun of(configuration: Configuration): Palette =
            if ((configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }
}
