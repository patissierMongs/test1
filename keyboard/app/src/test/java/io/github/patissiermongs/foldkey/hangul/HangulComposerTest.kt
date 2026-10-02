package io.github.patissiermongs.foldkey.hangul

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulComposerTest {

    private fun type(seq: String, composer: HangulComposer = HangulComposer()): Pair<String, String> {
        val out = StringBuilder()
        for (c in seq) out.append(composer.input(c))
        return out.toString() to composer.composing
    }

    private fun full(seq: String): String {
        val c = HangulComposer()
        val (committed, _) = type(seq, c)
        return committed + c.flush()
    }

    @Test
    fun tableOrderMatchesUnicodeNames() {
        for (i in Jamo.CHO.indices) {
            val compat = Character.getName(Jamo.CHO[i].code)!!.removePrefix("HANGUL LETTER ")
            val conj = Character.getName(0x1100 + i)!!.removePrefix("HANGUL CHOSEONG ")
            assertEquals("cho $i", conj, compat)
        }
        for (i in Jamo.JUNG.indices) {
            val compat = Character.getName(Jamo.JUNG[i].code)!!.removePrefix("HANGUL LETTER ")
            val conj = Character.getName(0x1161 + i)!!.removePrefix("HANGUL JUNGSEONG ")
            assertEquals("jung $i", conj, compat)
        }
        for (i in 1 until Jamo.JONG.length) {
            val compat = Character.getName(Jamo.JONG[i].code)!!.removePrefix("HANGUL LETTER ")
            val conj = Character.getName(0x11A7 + i)!!.removePrefix("HANGUL JONGSEONG ")
            assertEquals("jong $i", conj, compat)
        }
        assertEquals(19, Jamo.CHO.length)
        assertEquals(21, Jamo.JUNG.length)
        assertEquals(28, Jamo.JONG.length)
    }

    @Test
    fun syllableFormulaMatchesNfcForEveryCombination() {
        var count = 0
        for (l in 0 until 19) for (v in 0 until 21) for (t in 0 until 28) {
            val seq = StringBuilder().appendCodePoint(0x1100 + l).appendCodePoint(0x1161 + v)
            if (t > 0) seq.appendCodePoint(0x11A7 + t)
            val nfc = Normalizer.normalize(seq, Normalizer.Form.NFC)
            assertEquals(nfc, Syllable(l, v, t).text())
            count++
        }
        assertEquals(11172, count)
    }

    @Test
    fun compoundTablesAreConsistentWithUnicodeDecomposition() {
        for ((pair, combined) in Jamo.JONG_COMBINE) {
            val idx = Jamo.JONG.indexOf(combined)
            assertTrue(idx > 0)
            val name = Character.getName(combined.code)!!.removePrefix("HANGUL LETTER ")
            val a = Character.getName(pair.first.code)!!.removePrefix("HANGUL LETTER ")
            val b = Character.getName(pair.second.code)!!.removePrefix("HANGUL LETTER ")
            assertEquals("$a-$b", name)
        }
        assertEquals(11, Jamo.JONG_COMBINE.size)
        assertEquals(7, Jamo.JUNG_COMBINE.size)
        for ((pair, combined) in Jamo.JUNG_COMBINE) {
            val s = Syllable(Jamo.CHO.indexOf('ㅇ'), Jamo.JUNG.indexOf(combined)).text()
            val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
            assertEquals(2, nfd.length)
            assertTrue(Jamo.isVowel(pair.first) && Jamo.isVowel(pair.second))
        }
    }

    @Test
    fun basicWords() {
        assertEquals("한글", full("ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", full("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("닭", full("ㄷㅏㄹㄱ"))
        assertEquals("달가", full("ㄷㅏㄹㄱㅏ"))
        assertEquals("이써", full("ㅇㅣㅆㅓ"))
        assertEquals("과", full("ㄱㅗㅏ"))
        assertEquals("관", full("ㄱㅗㅏㄴ"))
        assertEquals("웨", full("ㅇㅜㅔ"))
        assertEquals("의사", full("ㅇㅡㅣㅅㅏ"))
        assertEquals("밦", full("ㅂㅏㅂㅅ"))
        assertEquals("밥사", full("ㅂㅏㅂㅅㅏ"))
        assertEquals("값어치", full("ㄱㅏㅂㅅㅇㅓㅊㅣ"))
        assertEquals("읽어", full("ㅇㅣㄹㄱㅇㅓ"))
        assertEquals("일거", full("ㅇㅣㄹㄱㅓ"))
        assertEquals("빠른", full("ㅃㅏㄹㅡㄴ"))
        assertEquals("밖에", full("ㅂㅏㄲㅇㅔ"))
        assertEquals("바께", full("ㅂㅏㄲㅔ"))
    }

    @Test
    fun consonantsThatCannotBeFinalStartNewSyllable() {
        assertEquals("가ㅃ", full("ㄱㅏㅃ"))
        assertEquals("가따", full("ㄱㅏㄸㅏ"))
        assertEquals("가짜", full("ㄱㅏㅉㅏ"))
    }

    @Test
    fun standaloneJamo() {
        assertEquals("ㄱㄱ", full("ㄱㄱ"))
        assertEquals("ㅋㅋㅋ", full("ㅋㅋㅋ"))
        assertEquals("ㅏ", full("ㅏ"))
        assertEquals("ㅘ", full("ㅗㅏ"))
        assertEquals("ㅏㅏ", full("ㅏㅏ"))
        assertEquals("ㅏ가", full("ㅏㄱㅏ"))
        assertEquals("ㅠㅠ", full("ㅠㅠ"))
    }

    @Test
    fun consonantClustersWithoutVowelFollowLibhangul() {
        assertEquals("ㄳ", full("ㄱㅅ"))
        assertEquals("ㄱ사", full("ㄱㅅㅏ"))
        assertEquals("ㄺ", full("ㄹㄱ"))
        assertEquals("ㄹ가", full("ㄹㄱㅏ"))
        assertEquals("ㄳㄱ", full("ㄱㅅㄱ"))
        assertEquals("ㄱㄱ", full("ㄱㄱ"))
        assertEquals("ㄲㅅ", full("ㄲㅅ"))
        val c = HangulComposer()
        type("ㄴㅎ", c)
        assertEquals("ㄶ", c.composing)
        assertTrue(c.backspace())
        assertEquals("ㄴ", c.composing)
    }

    @Test
    fun libhangulReferenceSequences() {
        assertEquals("맑", full("ㅁㅏㄹㄱ"))
        assertEquals("말고", full("ㅁㅏㄹㄱㅗ"))
        assertEquals("버쓰", full("ㅂㅓㅆㅡ"))
        assertEquals("가ㅣ", full("ㄱㅏㅣ"))
        assertEquals("가ㅉ", full("ㄱㅏㅉ"))
        assertEquals("가따", full("ㄱㅏㄸㅏ"))
        assertEquals("버ㅃ", full("ㅂㅓㅃ"))
    }

    @Test
    fun composingStatesDuringTyping() {
        val c = HangulComposer()
        assertEquals("", c.input('ㄱ')); assertEquals("ㄱ", c.composing)
        assertEquals("", c.input('ㅏ')); assertEquals("가", c.composing)
        assertEquals("", c.input('ㅂ')); assertEquals("갑", c.composing)
        assertEquals("", c.input('ㅅ')); assertEquals("값", c.composing)
        assertEquals("갑", c.input('ㅏ')); assertEquals("사", c.composing)
    }

    @Test
    fun backspaceUndoesOneJamoAtATime() {
        val c = HangulComposer()
        type("ㄱㅗㅏㄴ", c)
        assertEquals("관", c.composing)
        assertTrue(c.backspace()); assertEquals("과", c.composing)
        assertTrue(c.backspace()); assertEquals("고", c.composing)
        assertTrue(c.backspace()); assertEquals("ㄱ", c.composing)
        assertTrue(c.backspace()); assertEquals("", c.composing)
        assertFalse(c.isComposing)
        assertFalse(c.backspace())
    }

    @Test
    fun backspaceAfterFinalMovedToNextSyllable() {
        val c = HangulComposer()
        val (committed, composing) = type("ㄱㅏㅂㅅㅏ", c)
        assertEquals("갑", committed)
        assertEquals("사", composing)
        assertTrue(c.backspace()); assertEquals("ㅅ", c.composing)
        assertTrue(c.backspace()); assertEquals("", c.composing)
        assertFalse(c.backspace())
    }

    @Test
    fun backspaceSplitsCompoundFinal() {
        val c = HangulComposer()
        type("ㄷㅏㄹㄱ", c)
        assertEquals("닭", c.composing)
        assertTrue(c.backspace()); assertEquals("달", c.composing)
        assertTrue(c.backspace()); assertEquals("다", c.composing)
    }

    @Test
    fun nonJamoFlushesComposition() {
        val c = HangulComposer()
        type("ㅎㅏㄴ", c)
        assertEquals("한 ", c.input(' '))
        assertFalse(c.isComposing)
        assertEquals("1", c.input('1'))
    }

    @Test
    fun everyCompoundFinalMovesItsSecondPart() {
        for ((pair, combined) in Jamo.JONG_COMBINE) {
            val c = HangulComposer()
            val seq = "ㄱㅏ${pair.first}${pair.second}ㅏ"
            val (committed, composing) = type(seq, c)
            assertEquals(Syllable(0, 0, Jamo.JONG.indexOf(pair.first)).text(), committed)
            assertEquals(Syllable(Jamo.CHO.indexOf(pair.second), 0).text(), composing)
            assertEquals(Syllable(0, 0, Jamo.JONG.indexOf(combined)).text(), type("ㄱㅏ${pair.first}${pair.second}").second)
        }
    }
}
