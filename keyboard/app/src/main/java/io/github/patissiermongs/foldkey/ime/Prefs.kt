package io.github.patissiermongs.foldkey.ime

import android.content.Context
import android.content.SharedPreferences
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.input.TypingCalibration
import kotlin.math.roundToInt

class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isSplit(landscape: Boolean): Boolean =
        if (landscape) sp.getBoolean(SPLIT_LANDSCAPE, true) else sp.getBoolean(SPLIT_PORTRAIT, false)

    fun toggleSplit(landscape: Boolean) {
        sp.edit().putBoolean(if (landscape) SPLIT_LANDSCAPE else SPLIT_PORTRAIT, !isSplit(landscape)).apply()
    }

    val layer: Layer get() = if (sp.getBoolean(GENERAL_LAYER, false)) Layer.GENERAL else Layer.CODE

    fun setLayer(layer: Layer) {
        sp.edit().putBoolean(GENERAL_LAYER, layer == Layer.GENERAL).apply()
    }

    val rowHeightMm: Float get() = sp.getInt(ROW_HEIGHT, 95) / 10f
    val splitUnitMm: Float get() = sp.getInt(SPLIT_UNIT, 85) / 10f
    val splitLiftMm: Float get() = sp.getInt(SPLIT_LIFT, 0) / 10f
    val generalUnitMm: Float? get() = sp.getInt(GENERAL_UNIT, GENERAL_UNIT_AUTO).takeIf { it > GENERAL_UNIT_AUTO }?.let { it / 10f }
    val codeSideLeftMm: Float get() = sp.getInt(CODE_SIDE_LEFT, DEFAULT_CODE_SIDE) / 10f
    val codeSideRightMm: Float get() = sp.getInt(CODE_SIDE_RIGHT, DEFAULT_CODE_SIDE) / 10f
    val generalSideLeftMm: Float get() = sp.getInt(GENERAL_SIDE_LEFT, legacyGeneralSide) / 10f
    val generalSideRightMm: Float get() = sp.getInt(GENERAL_SIDE_RIGHT, legacyGeneralSide) / 10f
    private val legacyGeneralSide: Int get() = sp.getInt(GENERAL_MARGIN, DEFAULT_GENERAL_SIDE)
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

    fun applyCalibration(placements: Map<Layer, TypingCalibration.Placement>, offsets: Map<String, String>) {
        fun t(mm: Float) = (mm * 10f).roundToInt()
        val editor = sp.edit()
        placements[Layer.CODE]?.let {
            editor.putInt(SPLIT_UNIT, t(it.unitMm)).putInt(CODE_SIDE_LEFT, t(it.leftMm)).putInt(CODE_SIDE_RIGHT, t(it.rightMm))
        }
        placements[Layer.GENERAL]?.let {
            editor.putInt(GENERAL_UNIT, t(it.unitMm)).putInt(GENERAL_SIDE_LEFT, t(it.leftMm)).putInt(GENERAL_SIDE_RIGHT, t(it.rightMm))
        }
        placements.values.firstOrNull()?.let { editor.putInt(ROW_HEIGHT, t(it.rowMm)).putInt(SPLIT_LIFT, t(it.liftMm)) }
        for ((slot, data) in offsets) editor.putString(OFFSETS_PREFIX + slot, data)
        if (offsets.isNotEmpty()) editor.putInt(OFFSETS_EPOCH, offsetsEpoch + 1)
        editor.apply()
    }

    var setupShown: Boolean
        get() = sp.getBoolean(SETUP_SHOWN, false)
        set(value) {
            sp.edit().putBoolean(SETUP_SHOWN, value).apply()
        }

    fun offsets(slot: String): String? = sp.getString(OFFSETS_PREFIX + slot, null)

    fun offsetSlots(): List<String> = sp.all.keys.filter { it.startsWith(OFFSETS_PREFIX) }.map { it.removePrefix(OFFSETS_PREFIX) }.sorted()

    fun saveOffsets(slot: String, data: String) {
        sp.edit().putString(OFFSETS_PREFIX + slot, data).apply()
    }

    val offsetsEpoch: Int get() = sp.getInt(OFFSETS_EPOCH, 0)

    fun clearOffsets() {
        val editor = sp.edit()
        for (k in sp.all.keys) if (k.startsWith(OFFSETS_PREFIX)) editor.remove(k)
        editor.putInt(OFFSETS_EPOCH, offsetsEpoch + 1)
        editor.apply()
    }

    companion object {
        const val NAME = "foldkey"
        const val SPLIT_PORTRAIT = "split_portrait"
        const val SPLIT_LANDSCAPE = "split_landscape"
        const val GENERAL_LAYER = "general_layer"
        const val ROW_HEIGHT = "row_height_tenth_mm"
        const val SPLIT_UNIT = "split_unit_tenth_mm"
        const val SPLIT_LIFT = "split_lift_tenth_mm"
        const val GENERAL_MARGIN = "general_margin_tenth_mm"
        const val GENERAL_UNIT = "general_unit_tenth_mm"
        const val GENERAL_UNIT_AUTO = 39
        const val CODE_SIDE_LEFT = "code_side_left_tenth_mm"
        const val CODE_SIDE_RIGHT = "code_side_right_tenth_mm"
        const val GENERAL_SIDE_LEFT = "general_side_left_tenth_mm"
        const val GENERAL_SIDE_RIGHT = "general_side_right_tenth_mm"
        const val DEFAULT_CODE_SIDE = 5
        const val DEFAULT_GENERAL_SIDE = 140
        const val SIDE_MAX = 400
        const val UNIT_MIN = 40
        const val UNIT_MAX = 110
        const val ROW_MIN = 50
        const val ROW_MAX = 150
        const val LIFT_MAX = 300
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
        const val OFFSETS_EPOCH = "touch_offsets_epoch"
        const val SETUP_SHOWN = "setup_shown"
        const val DEFAULT_RAW_PACKAGES =
            "com.termux, org.connectbot, com.sonelli.juicessh, jackpal.androidterm"
    }
}
