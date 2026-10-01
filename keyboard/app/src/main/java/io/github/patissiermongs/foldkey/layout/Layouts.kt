package io.github.patissiermongs.foldkey.layout

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.UsKeyMap

enum class LayoutKind { FULL, SPLIT, COMPACT }

object Layouts {
    private fun code(keyCode: Int) = KeyAction.Code(keyCode)

    private fun ch(c: Char, width: Float = 1f) = KeyDef(KeyAction.Char(c, UsKeyMap.shiftedOf(c)), width = width)

    private fun fkey(c: Char, f: Int) = KeyDef(
        KeyAction.Char(c, UsKeyMap.shiftedOf(c)),
        down = code(KeyEvent.KEYCODE_F1 + f - 1),
        downLabel = "F$f",
    )

    private fun digits(range: IntRange) = range.map { if (it == 10) fkey('0', 10) else fkey('0' + it, it) }

    private fun letters(s: String) = s.map { ch(it) }

    private fun mod(m: Modifier, label: String, width: Float) =
        KeyDef(KeyAction.Mod(m), width = width, label = label, style = KeyStyle.MOD)

    private fun backspace(width: Float) =
        KeyDef(KeyAction.Backspace, width = width, label = "⌫", repeat = true, style = KeyStyle.MOD)

    private fun tab(width: Float) = KeyDef(code(KeyEvent.KEYCODE_TAB), width = width, label = "Tab", style = KeyStyle.MOD)

    private fun escCtrl(width: Float) = KeyDef(
        KeyAction.EscCtrl, width = width, label = "Esc", upLabel = "Ctrl", fnLabel = "Ins", style = KeyStyle.MOD,
    )

    private fun enter(width: Float) = KeyDef(KeyAction.Enter, width = width, label = "⏎", style = KeyStyle.ACTION)

    private fun space(width: Float) = KeyDef(KeyAction.Space, width = width, label = "", style = KeyStyle.SPACE)

    private fun lang(width: Float) = KeyDef(KeyAction.Lang, width = width, label = "한/A", style = KeyStyle.MOD)

    private fun arrow(keyCode: Int, label: String, alt: Int, altLabel: String) =
        KeyDef(code(keyCode), label = label, up = code(alt), upLabel = altLabel, repeat = true, style = KeyStyle.NAV)

    private fun arrows() = listOf(
        arrow(KeyEvent.KEYCODE_DPAD_LEFT, "←", KeyEvent.KEYCODE_MOVE_HOME, "Home"),
        arrow(KeyEvent.KEYCODE_DPAD_DOWN, "↓", KeyEvent.KEYCODE_PAGE_DOWN, "PgDn"),
        arrow(KeyEvent.KEYCODE_DPAD_UP, "↑", KeyEvent.KEYCODE_PAGE_UP, "PgUp"),
        arrow(KeyEvent.KEYCODE_DPAD_RIGHT, "→", KeyEvent.KEYCODE_MOVE_END, "End"),
    )

    val full: List<RowDef> = listOf(
        RowDef(listOf(ch('`')) + digits(1..10) + listOf(fkey('-', 11), fkey('=', 12), backspace(2f))),
        RowDef(listOf(tab(1.5f)) + letters("qwertyuiop[]") + ch('\\', 1.5f)),
        RowDef(listOf(escCtrl(1.75f)) + letters("asdfghjkl;'") + enter(2.25f)),
        RowDef(listOf(mod(Modifier.SHIFT, "⇧", 2.25f)) + letters("zxcvbnm,./") + mod(Modifier.SHIFT, "⇧", 2.75f)),
        RowDef(
            listOf(
                mod(Modifier.CTRL, "Ctrl", 1.5f),
                mod(Modifier.ALT, "Alt", 1.25f),
                mod(Modifier.FN, "Fn", 1.25f),
                lang(1.25f),
                space(5.75f),
            ) + arrows()
        ),
    )

    val split: List<SplitRow> = listOf(
        SplitRow(
            RowDef(listOf(ch('`')) + digits(1..5)),
            RowDef(digits(6..10) + listOf(fkey('-', 11), fkey('=', 12))),
        ),
        SplitRow(
            RowDef(listOf(tab(1.5f)) + letters("qwert")),
            RowDef(letters("yuiop[]") + backspace(1.5f)),
        ),
        SplitRow(
            RowDef(listOf(escCtrl(1.75f)) + letters("asdfg")),
            RowDef(letters("hjkl;'") + enter(2.25f)),
        ),
        SplitRow(
            RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.25f), ch('\\')) + letters("zxcv")),
            RowDef(letters("bnm,./") + mod(Modifier.SHIFT, "⇧", 2.75f)),
        ),
        SplitRow(
            RowDef(listOf(mod(Modifier.CTRL, "Ctrl", 1.25f), mod(Modifier.ALT, "Alt", 1.25f), mod(Modifier.FN, "Fn", 1f), space(3f))),
            RowDef(listOf(space(3.5f), lang(1f)) + arrows()),
        ),
    )

    val splitUnits: Float = split.maxOf { it.left.units + it.right.units }

    val splitLeftUnits: Float = split.maxOf { it.left.units }

    val splitRightUnits: Float = splitUnits - split.minOf { it.left.units }

    val compact: List<RowDef> = listOf(
        RowDef(listOf(escCtrl(1f), tab(1f), ch('`'), fkey('-', 11), fkey('=', 12)) + letters("[]\\;'")),
        RowDef(digits(1..10)),
        RowDef(letters("qwertyuiop")),
        RowDef(letters("asdfghjkl") + enter(1f)),
        RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.5f)) + letters("zxcvbnm") + backspace(1.5f)),
        RowDef(
            listOf(mod(Modifier.CTRL, "Ctrl", 1.5f), mod(Modifier.ALT, "Alt", 1f), mod(Modifier.FN, "Fn", 1f), lang(1f), space(2.5f)) +
                letters(",./")
        ),
    )

    private fun padKey(c: Char) = KeyDef(KeyAction.Text(c.toString()), label = c.toString())

    private val padBackspace = KeyDef(
        KeyAction.Backspace, label = "⌫", up = code(KeyEvent.KEYCODE_FORWARD_DEL), upLabel = "Del",
        repeat = true, style = KeyStyle.MOD,
    )

    const val PAD_ANCHOR = 'h'

    val pad: List<List<KeyDef>> = listOf(
        listOf(padKey('7'), padKey('8'), padKey('9'), padKey('/'), padBackspace),
        listOf(padKey('4'), padKey('5'), padKey('6'), padKey('*'), padKey('(')),
        listOf(padKey('1'), padKey('2'), padKey('3'), padKey('-'), padKey(')')),
        listOf(padKey('0'), padKey('.'), padKey('='), padKey('+'), enter(1f)),
    )

    fun rows(kind: LayoutKind): Int = when (kind) {
        LayoutKind.FULL -> full.size
        LayoutKind.SPLIT -> split.size
        LayoutKind.COMPACT -> compact.size
    }

    val stripCommands = listOf(
        Command.SELECT_ALL,
        Command.COPY,
        Command.PASTE,
        Command.TOGGLE_SPLIT,
        Command.SWITCH_IME,
        Command.SETTINGS,
        Command.HIDE,
    )
}
