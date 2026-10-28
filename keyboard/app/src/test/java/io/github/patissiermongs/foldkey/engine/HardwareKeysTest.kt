package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HardwareKeysTest {
    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var keys: HardwareKeys

    private val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
    private val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

    private fun start(ctx: EditorContext = EditorContext()) {
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(ctx)
        keys = HardwareKeys(engine)
        keys.active = true
    }

    @Before
    fun setUp() = start()

    private fun code(c: Char): Int = KeyEvent.KEYCODE_A + (c - 'a')

    private fun press(keyCode: Int, meta: Int = 0, unicode: Int = 0): Pair<Boolean, Boolean> {
        val down = keys.down(keyCode, meta, 0, unicode)
        val up = keys.up(keyCode)
        return down to up
    }

    private fun letters(s: String) = s.forEach { assertEquals(true to true, press(code(it))) }

    private fun hangul() {
        assertEquals(true to true, press(KeyEvent.KEYCODE_KANA))
        assertEquals(Lang.HANGUL, engine.lang)
    }

    @Test
    fun hanEngKeyTurnsLetterKeysIntoTwoSetJamo() {
        hangul()
        letters("gksrmf")
        assertEquals("한글", editor.text.toString())
        assertEquals("글", editor.composing)
        assertEquals(false to false, press(KeyEvent.KEYCODE_SPACE, unicode = ' '.code))
        assertEquals("", editor.composing)
        assertEquals("finish", editor.log.last())
        assertEquals(true to true, press(KeyEvent.KEYCODE_KANA))
        assertEquals(Lang.LATIN, engine.lang)
        assertEquals(false to false, press(code('a'), unicode = 'a'.code))
        assertEquals("한글", editor.text.toString())
    }

    @Test
    fun shiftGivesTenseConsonantsAndCapsLockIsIgnored() {
        hangul()
        assertTrue(keys.down(code('r'), shift, 0, 'R'.code))
        keys.up(code('r'))
        assertTrue(keys.down(code('k'), shift or KeyEvent.META_CAPS_LOCK_ON, 0, 'k'.code))
        keys.up(code('k'))
        assertTrue(keys.down(code('t'), KeyEvent.META_CAPS_LOCK_ON, 0, 'T'.code))
        keys.up(code('t'))
        assertEquals("깟", editor.text.toString())
    }

    @Test
    fun backspaceUndoesJamoThenReachesTheApp() {
        hangul()
        letters("rkt")
        assertEquals("갓", editor.composing)
        assertEquals(true to true, press(KeyEvent.KEYCODE_DEL))
        assertEquals("가", editor.composing)
        assertEquals(true to true, press(KeyEvent.KEYCODE_DEL))
        assertEquals(true to true, press(KeyEvent.KEYCODE_DEL))
        assertEquals("", editor.text.toString())
        assertEquals(false to false, press(KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun commandChordsFinishTheSyllableAndPassThrough() {
        hangul()
        letters("dk")
        assertEquals(false to false, press(code('c'), ctrl))
        assertEquals("아", editor.text.toString())
        assertEquals("", editor.composing)
        assertEquals(false to false, press(code('v'), KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))
        assertEquals(false to false, press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals("아", editor.text.toString())
    }

    @Test
    fun modifierKeysAloneDoNotEndTheSyllable() {
        hangul()
        letters("dk")
        for (m in listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_CAPS_LOCK)) {
            assertEquals(false to false, press(m))
        }
        assertEquals("아", editor.composing)
    }

    @Test
    fun languageSwitchKeyAlsoTogglesAndRepeatsAreSwallowed() {
        assertTrue(keys.down(KeyEvent.KEYCODE_LANGUAGE_SWITCH, 0, 0, 0))
        assertTrue(keys.down(KeyEvent.KEYCODE_LANGUAGE_SWITCH, 0, 1, 0))
        assertTrue(keys.down(KeyEvent.KEYCODE_LANGUAGE_SWITCH, 0, 2, 0))
        assertTrue(keys.up(KeyEvent.KEYCODE_LANGUAGE_SWITCH))
        assertEquals(Lang.HANGUL, engine.lang)
        assertTrue(keys.down(KeyEvent.KEYCODE_KANA, 0, 0, 0))
        assertTrue(keys.down(KeyEvent.KEYCODE_KANA, 0, 0, 0))
        assertEquals(Lang.HANGUL, engine.lang)
        assertTrue(keys.up(KeyEvent.KEYCODE_KANA))
        assertFalse(keys.up(KeyEvent.KEYCODE_KANA))
    }

    @Test
    fun rightAltTappedAloneTogglesButChordsDoNot() {
        val alt = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        assertFalse(keys.down(KeyEvent.KEYCODE_ALT_RIGHT, alt, 0, 0))
        assertFalse(keys.up(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(Lang.HANGUL, engine.lang)
        assertFalse(keys.down(KeyEvent.KEYCODE_ALT_RIGHT, alt, 0, 0))
        assertFalse(keys.down(code('f'), alt, 0, 0))
        assertFalse(keys.up(code('f')))
        assertFalse(keys.up(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(Lang.HANGUL, engine.lang)
        assertFalse(keys.down(KeyEvent.KEYCODE_ALT_RIGHT, alt or ctrl, 0, 0))
        assertFalse(keys.up(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(Lang.HANGUL, engine.lang)
        assertFalse(keys.down(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON, 0, 0))
        assertFalse(keys.up(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(Lang.HANGUL, engine.lang)
    }

    @Test
    fun remoteAppsAndFieldlessFocusGetEveryKeyUntouched() {
        keys.passThrough = true
        assertEquals(false to false, press(KeyEvent.KEYCODE_KANA))
        assertFalse(keys.down(KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.META_ALT_ON, 0, 0))
        assertFalse(keys.up(KeyEvent.KEYCODE_ALT_RIGHT))
        assertEquals(Lang.LATIN, engine.lang)
        keys.passThrough = false
        hangul()
        keys.passThrough = true
        assertEquals(false to false, press(code('g'), unicode = 'g'.code))
        keys.passThrough = false
        keys.active = false
        assertEquals(false to false, press(code('g'), unicode = 'g'.code))
        assertEquals(false to false, press(KeyEvent.KEYCODE_KANA))
        assertEquals(Lang.HANGUL, engine.lang)
        assertEquals("", editor.text.toString())
    }

    @Test
    fun terminalGetsTheSyllableBeforeTheKeyThatEndedIt() {
        start(EditorContext(raw = true, preferLatin = true))
        hangul()
        letters("gks")
        assertEquals("", editor.text.toString())
        assertEquals("한", engine.preedit)
        assertEquals(true to true, press(KeyEvent.KEYCODE_ENTER, unicode = '\n'.code))
        assertEquals(listOf("commit:한", "commit:\n"), editor.log.filter { it.startsWith("commit:") })
        letters("dk")
        assertEquals(true to true, press(code('c'), ctrl, 'c'.code))
        assertEquals(KeyEvent.KEYCODE_C to Modifiers.CTRL_META, editor.keys.last())
        assertEquals("commit:아", editor.log.filter { it.startsWith("commit:") }.last())
        letters("dk")
        assertEquals(true to true, press(KeyEvent.KEYCODE_PERIOD, unicode = '.'.code))
        letters("dk")
        assertEquals(true to true, press(KeyEvent.KEYCODE_TAB, shift, '\t'.code))
        assertEquals(KeyEvent.KEYCODE_TAB to Modifiers.SHIFT_META, editor.keys.last())
        assertEquals("한\n아아.아", editor.text.toString())
    }

    @Test
    fun terminalLeavesSystemKeysToTheSystem() {
        start(EditorContext(raw = true, preferLatin = true))
        hangul()
        letters("gk")
        assertEquals(false to false, press(KeyEvent.KEYCODE_BACK))
        assertEquals("하", editor.text.toString())
        assertTrue(editor.keys.isEmpty())
        letters("gk")
        assertEquals(false to false, press(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals("하하", editor.text.toString())
    }

    @Test
    fun escapeLeavesHangulInTextFieldsAndTerminals() {
        hangul()
        letters("gk")
        assertEquals(false to false, press(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(Lang.LATIN, engine.lang)
        assertEquals("하", editor.text.toString())
        start(EditorContext(raw = true, preferLatin = true))
        hangul()
        letters("gk")
        assertEquals(true to true, press(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(KeyEvent.KEYCODE_ESCAPE to 0, editor.keys.last())
        assertEquals(Lang.LATIN, engine.lang)
        hangul()
        assertEquals(false to false, press(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl))
        assertEquals(Lang.LATIN, engine.lang)
        engine.settings = EngineSettings(escToLatin = false)
        hangul()
        assertEquals(false to false, press(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(Lang.HANGUL, engine.lang)
    }

    private fun typeOnSlowApp(keyText: String, lag: Int): String {
        val app = LaggingApp()
        val slow = KeyboardEngine(app, RecordingListener())
        slow.startInput(EditorContext(selStart = 0, selEnd = 0))
        val hw = HardwareKeys(slow).apply { active = true }
        fun key(keyCode: Int, meta: Int = 0, unicode: Int = 0) {
            app.deliver(slow, lag)
            if (!hw.down(keyCode, meta, 0, unicode)) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    app.appDelete()
                } else if (unicode >= ' '.code || unicode == '\n'.code) {
                    app.appType(unicode.toChar())
                }
            }
            hw.up(keyCode)
            app.nextKey()
        }
        var i = 0
        while (i < keyText.length) {
            val c = keyText[i]
            when {
                c == '<' -> {
                    val end = keyText.indexOf('>', i)
                    when (val token = keyText.substring(i + 1, end)) {
                        "DEL" -> key(KeyEvent.KEYCODE_DEL)
                        "LANG" -> key(KeyEvent.KEYCODE_KANA)
                        "ENT" -> key(KeyEvent.KEYCODE_ENTER, unicode = '\n'.code)
                        else -> {
                            app.deliver(slow, lag)
                            app.appMoveCursor(token.removePrefix("TAP").toInt())
                        }
                    }
                    i = end
                }
                c in 'a'..'z' -> key(code(c), unicode = c.code)
                c in 'A'..'Z' -> key(code(c.lowercaseChar()), shift, c.code)
                c == ' ' -> key(KeyEvent.KEYCODE_SPACE, unicode = c.code)
                c == '.' -> key(KeyEvent.KEYCODE_PERIOD, unicode = c.code)
                c in '0'..'9' -> key(KeyEvent.KEYCODE_0 + (c - '0'), unicode = c.code)
            }
            i++
        }
        app.deliver(slow)
        return app.text.toString()
    }

    @Test
    fun spacesTypedOnTheKeyboardKeepTheNextSyllableWhenTheAppReportsLate() {
        for (lag in 0..4) {
            assertEquals("lag $lag", "지금 저는 문제 진짜 좀", typeOnSlowApp("<LANG>wlrma wjsms answp wlsWk wha", lag))
        }
    }

    @Test
    fun enterTypedOnTheKeyboardKeepsTheNextSyllableWhenTheAppReportsLate() {
        for (lag in 0..4) {
            assertEquals("lag $lag", "문제\n저는\n진짜", typeOnSlowApp("<LANG>answp<ENT>wjsms<ENT>wlsWk", lag))
        }
    }

    @Test
    fun englishTypedBeforeTheHanEngKeyKeepsTheFirstSyllableWhenTheAppReportsLate() {
        for (lag in 0..4) {
            assertEquals("lag $lag", "abc 문제 저는", typeOnSlowApp("abc <LANG>answp wjsms", lag))
        }
    }

    @Test
    fun backspaceOnCommittedTextKeepsTheNextSyllableWhenTheAppReportsLate() {
        for (lag in 0..4) {
            assertEquals("lag $lag", "문제저는", typeOnSlowApp("<LANG>answp <DEL>wjsms", lag))
        }
    }

    @Test
    fun movingTheCursorStillEndsTheSyllable() {
        assertEquals("나가", typeOnSlowApp("<LANG>rk<TAP0>sk", lag = 0))
    }

    @Test
    fun heldKeysRepeatTheWayTheyStarted() {
        hangul()
        assertTrue(keys.down(code('r'), 0, 0, 'r'.code))
        assertTrue(keys.down(code('r'), 0, 1, 'r'.code))
        assertTrue(keys.down(code('r'), 0, 2, 'r'.code))
        assertTrue(keys.up(code('r')))
        assertEquals("ㄱㄱㄱ", editor.text.toString())
        assertTrue(keys.down(KeyEvent.KEYCODE_DEL, 0, 0, 0))
        assertTrue(keys.down(KeyEvent.KEYCODE_DEL, 0, 1, 0))
        assertTrue(keys.up(KeyEvent.KEYCODE_DEL))
        assertEquals("ㄱ", editor.text.toString())
        assertEquals(listOf(KeyEvent.KEYCODE_DEL to 0), editor.keys)
        assertTrue(keys.down(KeyEvent.KEYCODE_KANA, 0, 0, 0))
        assertTrue(keys.up(KeyEvent.KEYCODE_KANA))
        assertFalse(keys.down(code('r'), 0, 0, 'r'.code))
        hangul()
        assertFalse(keys.down(code('r'), 0, 1, 'r'.code))
        assertFalse(keys.up(code('r')))
        assertEquals("ㄱ", editor.text.toString())
    }
}
