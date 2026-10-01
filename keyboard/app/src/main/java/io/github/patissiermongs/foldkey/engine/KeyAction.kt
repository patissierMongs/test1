package io.github.patissiermongs.foldkey.engine

enum class Modifier(val latches: Boolean = false) { SHIFT, CTRL, ALT, FN(latches = true) }

enum class Command { SETTINGS, SWITCH_IME, HIDE, PASTE, TOGGLE_SPLIT, SELECT_ALL, COPY }

enum class Gesture { TAP, UP, DOWN, LONG, REPEAT }

sealed interface KeyAction {
    data class Char(val base: kotlin.Char, val shifted: kotlin.Char) : KeyAction {
        val isLetter: Boolean get() = base in 'a'..'z'
    }

    data class Code(val keyCode: Int) : KeyAction

    data class Text(val text: String) : KeyAction

    data class Mod(val modifier: Modifier) : KeyAction

    data class Cmd(val command: Command) : KeyAction

    data object EscCtrl : KeyAction

    data object Lang : KeyAction

    data object Enter : KeyAction

    data object Space : KeyAction

    data object Backspace : KeyAction
}
