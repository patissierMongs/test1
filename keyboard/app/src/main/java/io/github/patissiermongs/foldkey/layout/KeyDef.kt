package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.KeyAction

enum class KeyStyle { NORMAL, MOD, ACTION, SPACE, NAV }

data class KeyDef(
    val action: KeyAction,
    val width: Float = 1f,
    val label: String? = null,
    val up: KeyAction? = null,
    val upLabel: String? = null,
    val down: KeyAction? = null,
    val downLabel: String? = null,
    val fnLabel: String? = null,
    val repeat: Boolean = false,
    val style: KeyStyle = KeyStyle.NORMAL,
) {
    val isModifier: Boolean get() = action is KeyAction.Mod || action == KeyAction.EscCtrl
    val isSpace: Boolean get() = action == KeyAction.Space
    val hasVariants: Boolean get() = action is KeyAction.Char || up != null || down != null
}

data class RowDef(val keys: List<KeyDef>, val indent: Float = 0f) {
    val units: Float get() = keys.sumOf { it.width.toDouble() }.toFloat()

    val span: Float get() = indent + units
}

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

class Key(
    val def: KeyDef,
    val face: Box,
    val touch: Box,
    val row: Int,
    val zone: Int,
    val ghost: Boolean = false,
    val pad: Boolean = false,
)
