package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyNames
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.hangul.Dubeolsik

data class LabelState(
    val lang: Lang = Lang.LATIN,
    val shiftText: Boolean = false,
    val shiftJamo: Boolean = false,
    val fn: Boolean = false,
    val latinHints: Boolean = true,
    val swipeDownCtrl: Boolean = true,
)

object KeyLabels {
    fun main(def: KeyDef, s: LabelState): String {
        val fnLabel = def.fnLabel
        if (s.fn && fnLabel != null) return fnLabel
        val a = def.action
        if (a is KeyAction.Char) return charLabel(a, s.shiftText, s.shiftJamo, s)
        return def.label ?: ""
    }

    fun top(def: KeyDef, s: LabelState): String? {
        if (s.fn && def.fnLabel != null) return null
        def.upLabel?.let { return it }
        val a = def.action as? KeyAction.Char ?: return null
        if (a.isLetter) {
            if (s.lang == Lang.HANGUL && !s.shiftJamo && Dubeolsik.hasShiftVariant(a.base)) {
                return Dubeolsik.jamo(a.base, true).toString()
            }
            return null
        }
        return (if (s.shiftText) a.base else a.shifted).toString()
    }

    fun bottom(def: KeyDef, s: LabelState): String? {
        if (s.fn && def.fnLabel != null) return null
        return def.downLabel
    }

    fun latin(def: KeyDef, s: LabelState): String? {
        if (s.fn && def.fnLabel != null) return null
        val a = def.action as? KeyAction.Char ?: return null
        return if (a.isLetter && s.lang == Lang.HANGUL && s.latinHints) a.base.toString() else null
    }

    fun popup(def: KeyDef, gesture: Gesture, s: LabelState): String? {
        val r = ActionResolver.resolve(def, gesture, s.swipeDownCtrl)
        return when (val a = r.action) {
            is KeyAction.Char -> when {
                r.forceCtrl -> "^" + a.base.uppercaseChar()
                r.forceShift -> charLabel(a, shiftText = true, shiftJamo = true, s = s)
                else -> charLabel(a, s.shiftText, s.shiftJamo, s)
            }
            is KeyAction.Code -> codeLabel(a.keyCode)
            is KeyAction.Text -> a.text
            KeyAction.RemoteLang -> KeyNames.REMOTE_LANG
            else -> null
        }
    }

    fun codeLabel(keyCode: Int): String? = KeyNames.code(keyCode)

    private fun charLabel(a: KeyAction.Char, shiftText: Boolean, shiftJamo: Boolean, s: LabelState): String {
        if (a.isLetter && s.lang == Lang.HANGUL) return Dubeolsik.jamo(a.base, shiftJamo).toString()
        return (if (shiftText) a.shifted else a.base).toString()
    }
}
