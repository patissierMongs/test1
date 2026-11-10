package io.github.patissiermongs.foldkey.layout

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.UsKeyMap

enum class LayoutKind { FULL, SPLIT, COMPACT }

object Layouts {
    private fun code(keyCode: Int) = KeyAction.Code(keyCode)

    private fun ch(c: Char, width: Float = 1f) = KeyDef(KeyAction.Char(c, UsKeyMap.shiftedOf(c)), width = width)

    const val PUNCT_UNITS = 0.75f

    const val WIDE_VOWEL_UNITS = 1.25f

    private fun punct(c: Char) = ch(c, PUNCT_UNITS)

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

    private fun lang(width: Float) =
        KeyDef(KeyAction.Lang, width = width, label = "한/A", long = KeyAction.RemoteLang, style = KeyStyle.MOD)

    private fun arrow(keyCode: Int, label: String, alt: Int, altLabel: String) =
        KeyDef(code(keyCode), label = label, up = code(alt), upLabel = altLabel, repeat = true, style = KeyStyle.NAV)

    private fun arrows() = listOf(
        arrow(KeyEvent.KEYCODE_DPAD_LEFT, "←", KeyEvent.KEYCODE_MOVE_HOME, "Home"),
        arrow(KeyEvent.KEYCODE_DPAD_DOWN, "↓", KeyEvent.KEYCODE_PAGE_DOWN, "PgDn"),
        arrow(KeyEvent.KEYCODE_DPAD_UP, "↑", KeyEvent.KEYCODE_PAGE_UP, "PgUp"),
        arrow(KeyEvent.KEYCODE_DPAD_RIGHT, "→", KeyEvent.KEYCODE_MOVE_END, "End"),
    )

    val full: List<RowDef> = listOf(
        RowDef(listOf(ch('`')) + digits(1..10) + listOf(fkey('-', 11), fkey('=', 12))),
        RowDef(listOf(tab(1.5f)) + letters("qwertyuiop[]") + ch('\\', 1.5f)),
        RowDef(listOf(escCtrl(1.75f)) + letters("asdfghjkl;'") + enter(2.25f)),
        RowDef(listOf(mod(Modifier.SHIFT, "⇧", 2.25f)) + letters("zxcvbnm,./") + backspace(2.75f)),
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
            RowDef(listOf(punct('`')) + digits(1..6)),
            RowDef(digits(7..10) + listOf(fkey('-', 11), fkey('=', 12), punct('\\'))),
        ),
        SplitRow(
            RowDef(listOf(tab(1f)) + letters("qwert")),
            RowDef(letters("yuio") + listOf(ch('p', WIDE_VOWEL_UNITS), punct('['), punct(']'))),
        ),
        SplitRow(
            RowDef(listOf(escCtrl(1.25f)) + letters("asdfg")),
            RowDef(letters("hjk") + listOf(ch('l', WIDE_VOWEL_UNITS), punct(';'), punct('\''), enter(1.25f))),
        ),
        SplitRow(
            RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.75f)) + letters("zxcv")),
            RowDef(letters("bn") + listOf(ch('m', WIDE_VOWEL_UNITS), punct(','), punct('.'), punct('/'), backspace(1.25f))),
        ),
        SplitRow(
            RowDef(
                listOf(mod(Modifier.CTRL, "Ctrl", 1f), mod(Modifier.ALT, "Alt", 1f), lang(SPLIT_LANG_UNITS), space(3f + SPACE_INWARD_LEFT)),
                inward = SPACE_INWARD_LEFT,
            ),
            RowDef(listOf(mod(Modifier.FN, "Fn", 1f), space(3f)) + arrows(), inward = SPACE_INWARD_RIGHT),
        ),
    )

    const val SPLIT_LANG_UNITS = 1.25f

    const val SPACE_INWARD_LEFT = 0.75f

    const val SPACE_INWARD_RIGHT = 1.75f

    val splitUnits: Float = split.maxOf { it.left.span + it.right.span }

    val splitLeftUnits: Float = split.maxOf { it.left.span }

    val splitRightUnits: Float = splitUnits - split.minOf { it.left.span }

    val compact: List<RowDef> = listOf(
        RowDef(listOf(escCtrl(1f), tab(1f), ch('`'), fkey('-', 11), fkey('=', 12)) + letters("[]\\;'")),
        RowDef(digits(1..10)),
        RowDef(letters("qwertyuiop")),
        RowDef(letters("asdfghjkl") + enter(1f)),
        RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.5f)) + letters("zxcvbnm") + backspace(1.5f)),
        RowDef(
            listOf(mod(Modifier.CTRL, "Ctrl", 1f), mod(Modifier.ALT, "Alt", 1f), mod(Modifier.FN, "Fn", 1f), lang(1f), space(3f)) +
                letters(",./")
        ),
    )

    const val PROSE_SYMBOLS = "~-_=+;:'\"/"

    private fun proseDigit(d: Int): KeyDef {
        val c = '0' + d % 10
        val symbol = PROSE_SYMBOLS[d - 1]
        return KeyDef(KeyAction.Char(c, UsKeyMap.shiftedOf(c)), down = KeyAction.Char(symbol, symbol), downLabel = symbol.toString())
    }

    private fun proseDigits(range: IntRange) = range.map { proseDigit(it) }

    private fun prose(c: Char, shifted: Char): KeyDef {
        val action = KeyAction.Char(c, shifted)
        return KeyDef(action, down = action)
    }

    private fun proseComma() = prose(',', '!')

    val GENERAL_DOWN_SYMBOLS: Map<Char, Char> = mapOf(
        'q' to '`', 'w' to '\\', 'e' to '|', 't' to '[', 'y' to ']', 'u' to '<', 'i' to '>', 'o' to '{', 'p' to '}',
    )

    private fun proseLetters(s: String) = s.map { c ->
        val symbol = GENERAL_DOWN_SYMBOLS[c] ?: return@map ch(c)
        KeyDef(KeyAction.Char(c, UsKeyMap.shiftedOf(c)), down = KeyAction.Char(symbol, symbol), downLabel = symbol.toString())
    }

    private fun prosePeriod() = prose('.', '?')

    val general: List<RowDef> = listOf(
        RowDef(proseDigits(1..10)),
        RowDef(proseLetters("qwertyuiop")),
        RowDef(letters("asdfghjkl"), indent = 0.5f),
        RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.5f)) + letters("zxcvbnm") + backspace(1.5f)),
        RowDef(listOf(lang(1.5f), space(5f), proseComma(), prosePeriod(), enter(1.5f))),
    )

    val generalSplit: List<SplitRow> = listOf(
        SplitRow(RowDef(proseDigits(1..5)), RowDef(proseDigits(6..10))),
        SplitRow(RowDef(proseLetters("qwert"), indent = 0.5f), RowDef(proseLetters("yuiop"))),
        SplitRow(RowDef(letters("asdfg"), indent = 0.75f), RowDef(letters("hjkl"))),
        SplitRow(RowDef(listOf(mod(Modifier.SHIFT, "⇧", 1.25f)) + letters("zxcv")), RowDef(letters("bnm") + backspace(1.5f))),
        SplitRow(RowDef(listOf(lang(1f), space(4f))), RowDef(listOf(space(2f), proseComma(), prosePeriod(), enter(1f)))),
    )

    val generalSplitUnits: Float = generalSplit.maxOf { it.left.span + it.right.span }

    val generalSplitLeftUnits: Float = generalSplit.maxOf { it.left.span }

    val generalSplitRightUnits: Float = generalSplitUnits - generalSplit.minOf { it.left.span }

    const val GENERAL_KEY_MM = 9.6f

    fun generalSplitUnitsMm(codeLeftMm: Float, codeRightMm: Float, insetLeftMm: Float, insetRightMm: Float): Pair<Float, Float> {
        val left = (codeLeftMm * splitLeftUnits - insetLeftMm) / generalSplitLeftUnits
        val right = (codeRightMm * splitRightUnits - insetRightMm) / generalSplitRightUnits
        return maxOf(codeLeftMm, minOf(GENERAL_KEY_MM, left)) to maxOf(codeRightMm, minOf(GENERAL_KEY_MM, right))
    }

    fun generalSplitUnitMm(codeUnitMm: Float, insetLeftMm: Float = 0f, insetRightMm: Float = insetLeftMm): Float {
        val sameReach = minOf(
            (codeUnitMm * splitLeftUnits - insetLeftMm) / generalSplitLeftUnits,
            (codeUnitMm * splitRightUnits - insetRightMm) / generalSplitRightUnits,
        )
        return maxOf(codeUnitMm, minOf(GENERAL_KEY_MM, sameReach))
    }

    private fun padKey(c: Char) = KeyDef(KeyAction.Text(c.toString()), label = c.toString())

    private val padDot = KeyDef(KeyAction.Text("."), label = ".", up = code(KeyEvent.KEYCODE_FORWARD_DEL), upLabel = "Del")

    const val PAD_ANCHOR = 'h'

    val pad: List<PadCell> = listOf(
        PadCell(padKey('/'), 0, 1), PadCell(padKey('*'), 0, 2), PadCell(padKey('-'), 0, 3),
        PadCell(padKey('7'), 1, 0), PadCell(padKey('8'), 1, 1), PadCell(padKey('9'), 1, 2), PadCell(padKey('+'), 1, 3, rowSpan = 2),
        PadCell(padKey('4'), 2, 0), PadCell(padKey('5'), 2, 1), PadCell(padKey('6'), 2, 2),
        PadCell(padKey('1'), 3, 0), PadCell(padKey('2'), 3, 1), PadCell(padKey('3'), 3, 2), PadCell(enter(1f), 3, 3, rowSpan = 2),
        PadCell(padKey('0'), 4, 0, colSpan = 2), PadCell(padDot, 4, 2),
    )

    fun codeUnits(kind: LayoutKind): Float = when (kind) {
        LayoutKind.FULL -> full.maxOf { it.span }
        LayoutKind.SPLIT -> splitUnits
        LayoutKind.COMPACT -> compact.maxOf { it.span }
    }

    fun rows(kind: LayoutKind, layer: Layer = Layer.CODE): Int = when {
        layer == Layer.GENERAL && kind == LayoutKind.SPLIT -> generalSplit.size
        layer == Layer.GENERAL -> general.size
        kind == LayoutKind.FULL -> full.size
        kind == LayoutKind.SPLIT -> split.size
        else -> compact.size
    }

    val stripCommands = listOf(
        Command.SELECT_ALL,
        Command.COPY,
        Command.CUT,
        Command.PASTE,
        Command.TOGGLE_LAYER,
        Command.SETTINGS,
        Command.HIDE,
        Command.FLOAT,
        Command.BEHIND,
    )
}
