package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyNames
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.hangul.Dubeolsik

data class LabelState(
    val lang: Lang = Lang.LATIN,
    val shiftLetters: Boolean = false,
    val shiftSymbols: Boolean = false,
    val fn: Boolean = false,
    val latinHints: Boolean = true,
    val swipeDownCtrl: Boolean = true,
)

object KeyLabels {
    fun main(def: KeyDef, s: LabelState): String {
        val fnLabel = def.fnLabel
        if (s.fn && fnLabel != null) return fnLabel
        val a = def.action
        if (a is KeyAction.Char) return charLabel(a, s.shiftLetters, s.shiftSymbols, s)
        return def.label ?: ""
    }

    fun top(def: KeyDef, s: LabelState): String? {
        if (s.fn) {
            def.fnUpLabel?.let { return it }
            if (def.fnLabel != null) return null
        }
        def.upLabel?.let { return it }
        val a = def.action as? KeyAction.Char ?: return null
        if (a.isLetter) {
            if (s.lang == Lang.HANGUL && !s.shiftSymbols && Dubeolsik.hasShiftVariant(a.base)) {
                return Dubeolsik.jamo(a.base, true).toString()
            }
            return null
        }
        return (if (s.shiftSymbols) a.base else a.shifted).toString()
    }

    fun bottom(def: KeyDef, s: LabelState): String? {
        if (s.fn && (def.fnLabel != null || def.fnUpLabel != null)) return null
        def.downLabel?.let { return it }
        val a = def.action as? KeyAction.Char ?: return null
        if (a.isLetter && s.lang == Lang.HANGUL && s.latinHints) return a.base.toString()
        return null
    }

    fun popup(def: KeyDef, gesture: Gesture, s: LabelState): String? {
        val r = ActionResolver.resolve(def, gesture, s.fn, s.swipeDownCtrl)
        return when (val a = r.action) {
            is KeyAction.Char -> when {
                r.forceCtrl -> "^" + a.base.uppercaseChar()
                r.forceShift -> charLabel(a, shiftLetters = true, shiftSymbols = true, s = s)
                else -> charLabel(a, s.shiftLetters, s.shiftSymbols, s)
            }
            is KeyAction.Code -> codeLabel(a.keyCode)
            is KeyAction.Text -> a.text
            else -> null
        }
    }

    fun codeLabel(keyCode: Int): String? = KeyNames.code(keyCode)

    private fun charLabel(a: KeyAction.Char, shiftLetters: Boolean, shiftSymbols: Boolean, s: LabelState): String {
        if (a.isLetter) {
            if (s.lang == Lang.HANGUL) return Dubeolsik.jamo(a.base, shiftSymbols).toString()
            return (if (shiftLetters) a.shifted else a.base).toString()
        }
        return (if (shiftSymbols) a.shifted else a.base).toString()
    }
}
