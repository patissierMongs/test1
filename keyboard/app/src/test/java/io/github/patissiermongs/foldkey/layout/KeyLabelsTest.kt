package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyNames
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.engine.UsKeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyLabelsTest {
    private fun def(c: Char) = KeyDef(KeyAction.Char(c, UsKeyMap.shiftedOf(c)))

    @Test
    fun lockedShiftShowsShiftedDigitsAndSymbolsButPlainJamo() {
        val locked = LabelState(shiftText = true, shiftJamo = false)
        assertEquals("!", KeyLabels.main(def('1'), locked))
        assertEquals("1", KeyLabels.top(def('1'), locked))
        assertEquals("?", KeyLabels.main(def('/'), locked))
        assertEquals("A", KeyLabels.main(def('a'), locked))
        val hangul = locked.copy(lang = Lang.HANGUL)
        assertEquals("ㄱ", KeyLabels.main(def('r'), hangul))
        assertEquals("ㄲ", KeyLabels.top(def('r'), hangul))
        assertEquals("!", KeyLabels.main(def('1'), hangul))
        val once = LabelState(lang = Lang.HANGUL, shiftText = true, shiftJamo = true)
        assertEquals("ㄲ", KeyLabels.main(def('r'), once))
        assertNull(KeyLabels.top(def('r'), once))
        assertEquals("1", KeyLabels.main(def('1'), LabelState()))
        assertEquals("!", KeyLabels.top(def('1'), LabelState()))
    }

    @Test
    fun holdingTheLangKeyPreviewsTheRemoteToggle() {
        val lang = Layouts.split.last().left.keys.single { it.action == KeyAction.Lang }
        assertEquals("한/A", KeyLabels.main(lang, LabelState()))
        assertEquals(KeyNames.REMOTE_LANG, KeyLabels.popup(lang, Gesture.LONG, LabelState()))
        assertNull(KeyLabels.popup(lang, Gesture.TAP, LabelState()))
    }

    @Test
    fun textLayerTopRowShowsItsSymbolBesideTheLatinHint() {
        val row = Layouts.generalSplit[1].left.keys + Layouts.generalSplit[1].right.keys
        assertEquals(listOf("`", "\\", "|", "[", "]", "<", ">", "{", "}"), row.mapNotNull { it.downLabel })
        val q = row.first()
        val hangul = LabelState(lang = Lang.HANGUL)
        assertEquals("ㅂ", KeyLabels.main(q, hangul))
        assertEquals("`", KeyLabels.bottom(q, hangul))
        assertEquals("q", KeyLabels.latin(q, hangul))
        assertNull(KeyLabels.latin(q, hangul.copy(latinHints = false)))
        assertNull(KeyLabels.latin(q, LabelState()))
        assertEquals("`", KeyLabels.popup(q, Gesture.DOWN, hangul))
        val r = row.single { (it.action as KeyAction.Char).base == 'r' }
        assertNull(KeyLabels.bottom(r, hangul))
        assertEquals("^R", KeyLabels.popup(r, Gesture.DOWN, hangul))
        val digit = Layouts.split[0].left.keys[1]
        assertEquals("F1", KeyLabels.bottom(digit, LabelState()))
        assertNull(KeyLabels.latin(digit, hangul))
    }
}
