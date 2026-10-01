package io.github.patissiermongs.foldkey.ime

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isSplit(landscape: Boolean): Boolean =
        if (landscape) sp.getBoolean(SPLIT_LANDSCAPE, true) else sp.getBoolean(SPLIT_PORTRAIT, false)

    fun toggleSplit(landscape: Boolean) {
        sp.edit().putBoolean(if (landscape) SPLIT_LANDSCAPE else SPLIT_PORTRAIT, !isSplit(landscape)).apply()
    }

    val rowHeightMm: Float get() = sp.getInt(ROW_HEIGHT, 95) / 10f
    val splitUnitMm: Float get() = sp.getInt(SPLIT_UNIT, 85) / 10f
    val splitLiftMm: Float get() = sp.getInt(SPLIT_LIFT, 0) / 10f
    val centerEcho: Boolean get() = sp.getBoolean(CENTER_ECHO, true)
    val centerClipboard: Boolean get() = sp.getBoolean(CENTER_CLIPBOARD, true)
    val terminalEcho: Boolean get() = sp.getBoolean(TERMINAL_ECHO, false)
    val clipCount: Int get() = sp.getInt(CLIP_COUNT, ClipboardHistory.DEFAULT_CAPACITY).coerceIn(CLIP_COUNT_MIN, CLIP_COUNT_MAX)
    val clipHours: Int get() = sp.getInt(CLIP_HOURS, DEFAULT_CLIP_HOURS).coerceIn(CLIP_HOURS_MIN, CLIP_HOURS_MAX)
    val haptic: Boolean get() = sp.getBoolean(HAPTIC, true)
    val hapticLevel: Int get() = sp.getInt(HAPTIC_LEVEL, 0)
    val sound: Boolean get() = sp.getBoolean(SOUND, false)
    val popupMode: Int get() = sp.getInt(POPUP_MODE, POPUP_AUTO)
    val longPressMs: Long get() = sp.getInt(LONG_PRESS, 400).toLong()
    val longPressAction: Int get() = sp.getInt(LONG_PRESS_ACTION, LONG_PRESS_SHIFT)
    val swipeDownCtrl: Boolean get() = sp.getBoolean(SWIPE_DOWN_CTRL, true)
    val escToLatin: Boolean get() = sp.getBoolean(ESC_TO_LATIN, true)
    val adaptive: Boolean get() = sp.getBoolean(ADAPTIVE, true)
    val latinHints: Boolean get() = sp.getBoolean(LATIN_HINTS, true)
    val rawPackages: Set<String>
        get() = (sp.getString(RAW_PACKAGES, DEFAULT_RAW_PACKAGES) ?: "")
            .split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun offsets(slot: String): String? = sp.getString(OFFSETS_PREFIX + slot, null)

    fun saveOffsets(slot: String, data: String) {
        sp.edit().putString(OFFSETS_PREFIX + slot, data).apply()
    }

    fun clearOffsets() {
        val editor = sp.edit()
        for (k in sp.all.keys) if (k.startsWith(OFFSETS_PREFIX)) editor.remove(k)
        editor.apply()
    }

    companion object {
        const val NAME = "foldkey"
        const val SPLIT_PORTRAIT = "split_portrait"
        const val SPLIT_LANDSCAPE = "split_landscape"
        const val ROW_HEIGHT = "row_height_tenth_mm"
        const val SPLIT_UNIT = "split_unit_tenth_mm"
        const val SPLIT_LIFT = "split_lift_tenth_mm"
        const val CENTER_ECHO = "center_echo"
        const val CENTER_CLIPBOARD = "center_clipboard"
        const val TERMINAL_ECHO = "terminal_echo"
        const val CLIP_COUNT = "clip_history_count"
        const val CLIP_COUNT_MIN = 5
        const val CLIP_COUNT_MAX = 50
        const val CLIP_HOURS = "clip_history_hours"
        const val DEFAULT_CLIP_HOURS = 24
        const val CLIP_HOURS_MIN = 1
        const val CLIP_HOURS_MAX = 72
        const val HAPTIC = "haptic"
        const val HAPTIC_LEVEL = "haptic_level"
        const val SOUND = "sound"
        const val POPUP_MODE = "popup_mode"
        const val POPUP_OFF = 0
        const val POPUP_AUTO = 1
        const val POPUP_ON = 2
        const val LONG_PRESS = "long_press_ms"
        const val LONG_PRESS_ACTION = "long_press_action"
        const val LONG_PRESS_SHIFT = 0
        const val LONG_PRESS_REPEAT = 1
        const val SWIPE_DOWN_CTRL = "swipe_down_ctrl"
        const val ESC_TO_LATIN = "esc_to_latin"
        const val ADAPTIVE = "adaptive"
        const val LATIN_HINTS = "latin_hints"
        const val RAW_PACKAGES = "raw_packages"
        const val OFFSETS_PREFIX = "offsets_"
        const val DEFAULT_RAW_PACKAGES =
            "com.termux, org.connectbot, com.sonelli.juicessh, jackpal.androidterm"
    }
}
