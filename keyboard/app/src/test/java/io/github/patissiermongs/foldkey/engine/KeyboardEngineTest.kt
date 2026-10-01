package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardEngineTest {
    private lateinit var editor: FakeEditor
    private lateinit var listener: RecordingListener
    private lateinit var engine: KeyboardEngine
    private var now = 1_000L

    private fun ch(c: Char) = KeyAction.Char(c, io.github.patissiermongs.foldkey.engine.UsKeyMap.shiftedOf(c))

    private fun tap(c: Char) = engine.perform(ch(c))

    private fun type(s: String) = s.forEach { if (it == ' ') engine.perform(KeyAction.Space) else tap(it) }

    private fun tapMod(m: Modifier) {
        engine.press(KeyAction.Mod(m), now)
        now += 40
        engine.release(KeyAction.Mod(m), now)
        now += 60
    }

    @Before
    fun setUp() {
        editor = FakeEditor()
        listener = RecordingListener()
        engine = KeyboardEngine(editor, listener)
        engine.startInput(EditorContext())
    }

    @Test
    fun latinTyping() {
        type("ls -la")
        assertEquals("ls -la", editor.text.toString())
    }

    @Test
    fun oneShotShiftAppliesToNextKeyOnly() {
        tapMod(Modifier.SHIFT)
        type("ab")
        assertEquals("Ab", editor.text.toString())
    }

    @Test
    fun doubleTapShiftLocksLettersButNotDigits() {
        tapMod(Modifier.SHIFT)
        tapMod(Modifier.SHIFT)
        assertEquals(ModState.LOCKED, engine.modifiers.state(Modifier.SHIFT))
        type("ab1")
        assertEquals("AB1", editor.text.toString())
        tapMod(Modifier.SHIFT)
        type("c")
        assertEquals("AB1c", editor.text.toString())
    }

    @Test
    fun slowSecondShiftTapTurnsShiftOff() {
        tapMod(Modifier.SHIFT)
        now += 1_000
        tapMod(Modifier.SHIFT)
        assertEquals(ModState.OFF, engine.modifiers.state(Modifier.SHIFT))
    }

    @Test
    fun swipeUpGivesShiftedSymbol() {
        engine.perform(ch('9'), forceShift = true)
        engine.perform(ch('0'), forceShift = true)
        engine.perform(ch('-'), forceShift = true)
        engine.perform(ch('\''), forceShift = true)
        assertEquals("()_\"", editor.text.toString())
    }

    @Test
    fun oneShotCtrlSendsKeyEventThenReleases() {
        tapMod(Modifier.CTRL)
        tap('c')
        tap('c')
        assertEquals(listOf(KeyEvent.KEYCODE_C to Modifiers.CTRL_META), editor.keys)
        assertEquals("c", editor.text.toString())
    }

    @Test
    fun swipeDownIsCtrlChord() {
        engine.perform(ch('d'), forceCtrl = true)
        assertEquals(listOf(KeyEvent.KEYCODE_D to Modifiers.CTRL_META), editor.keys)
        assertEquals(ModState.OFF, engine.modifiers.state(Modifier.CTRL))
    }

    @Test
    fun heldCtrlChordDoesNotLeaveOneShot() {
        engine.press(KeyAction.Mod(Modifier.CTRL), now)
        tap('r')
        now += 100
        engine.release(KeyAction.Mod(Modifier.CTRL), now)
        assertEquals(ModState.OFF, engine.modifiers.state(Modifier.CTRL))
        assertFalse(engine.modifiers.isActive(Modifier.CTRL))
        assertEquals(listOf(KeyEvent.KEYCODE_R to Modifiers.CTRL_META), editor.keys)
    }

    @Test
    fun dualRoleKeyTapIsEscape() {
        engine.press(KeyAction.EscCtrl, now)
        engine.release(KeyAction.EscCtrl, now + 120)
        assertEquals(listOf(KeyEvent.KEYCODE_ESCAPE to 0), editor.keys)
        assertFalse(engine.modifiers.isActive(Modifier.CTRL))
    }

    @Test
    fun dualRoleKeyHoldIsCtrl() {
        engine.press(KeyAction.EscCtrl, now)
        tap('w')
        engine.release(KeyAction.EscCtrl, now + 150)
        assertEquals(listOf(KeyEvent.KEYCODE_W to Modifiers.CTRL_META), editor.keys)
    }

    @Test
    fun dualRoleKeyLongHoldAloneDoesNothing() {
        engine.press(KeyAction.EscCtrl, now)
        engine.release(KeyAction.EscCtrl, now + 900)
        assertTrue(editor.keys.isEmpty())
    }

    @Test
    fun ctrlWithPunctuationUsesUsKeyCodes() {
        tapMod(Modifier.CTRL)
        tap('[')
        tapMod(Modifier.CTRL)
        engine.perform(ch('-'), forceShift = true)
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_LEFT_BRACKET to Modifiers.CTRL_META,
                KeyEvent.KEYCODE_MINUS to (Modifiers.CTRL_META or Modifiers.SHIFT_META),
            ),
            editor.keys,
        )
    }

    @Test
    fun altSendsMetaAlt() {
        tapMod(Modifier.ALT)
        tap('b')
        assertEquals(listOf(KeyEvent.KEYCODE_B to Modifiers.ALT_META), editor.keys)
    }

    @Test
    fun hangulComposesInNormalEditor() {
        engine.perform(KeyAction.Lang)
        type("gksrmf")
        assertEquals("한글", editor.text.toString())
        assertEquals("글", editor.composing)
        engine.perform(KeyAction.Space)
        assertEquals("한글 ", editor.text.toString())
        assertEquals("", editor.composing)
    }

    @Test
    fun hangulShiftAndSwipeUpGiveDoubleConsonants() {
        engine.perform(KeyAction.Lang)
        engine.perform(ch('q'), forceShift = true)
        tap('k')
        tapMod(Modifier.SHIFT)
        tap('r')
        tap('k')
        engine.perform(KeyAction.Space)
        assertEquals("빠까 ", editor.text.toString())
    }

    @Test
    fun hangulShiftLockDoesNotProduceDoubleConsonants() {
        engine.perform(KeyAction.Lang)
        tapMod(Modifier.SHIFT)
        tapMod(Modifier.SHIFT)
        type("rk")
        engine.perform(KeyAction.Space)
        assertEquals("가 ", editor.text.toString())
    }

    @Test
    fun hangulBackspaceWalksBackThroughJamo() {
        engine.perform(KeyAction.Lang)
        type("rhks")
        assertEquals("관", editor.text.toString())
        engine.perform(KeyAction.Backspace)
        assertEquals("과", editor.text.toString())
        engine.perform(KeyAction.Backspace)
        engine.perform(KeyAction.Backspace)
        assertEquals("ㄱ", editor.text.toString())
        engine.perform(KeyAction.Backspace)
        assertEquals("", editor.text.toString())
        assertTrue(editor.keys.isEmpty())
        engine.perform(KeyAction.Backspace)
        assertEquals(listOf(KeyEvent.KEYCODE_DEL to 0), editor.keys)
    }

    @Test
    fun hangulInTerminalUsesPreeditStripAndCommitsFinishedSyllables() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("dkssud")
        assertEquals("안", editor.text.toString())
        assertEquals("녕", listener.preedit)
        assertEquals("", editor.composing)
        engine.perform(KeyAction.Enter)
        assertEquals("안녕", editor.text.toString())
        assertEquals("", listener.preedit)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER to 0), editor.keys)
    }

    @Test
    fun escapeFlushesHangulAndSwitchesToLatin() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("rk")
        engine.perform(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE))
        assertEquals("가", editor.text.toString())
        assertEquals(Lang.LATIN, engine.lang)
        tap('j')
        assertEquals("가j", editor.text.toString())
        assertEquals(listOf(KeyEvent.KEYCODE_ESCAPE to 0), editor.keys)
    }

    @Test
    fun ctrlInHangulModeSendsLatinKey() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("rk")
        engine.perform(ch('c'), forceCtrl = true)
        assertEquals("가", editor.text.toString())
        assertEquals(listOf(KeyEvent.KEYCODE_C to Modifiers.CTRL_META), editor.keys)
        assertEquals(Lang.HANGUL, engine.lang)
    }

    @Test
    fun enterUsesEditorActionOnlyWhenAvailable() {
        engine.startInput(EditorContext(enterAction = EditorInfo.IME_ACTION_SEARCH))
        engine.perform(KeyAction.Enter)
        assertEquals(listOf(EditorInfo.IME_ACTION_SEARCH), editor.actions)
        engine.startInput(EditorContext(enterAction = null))
        engine.perform(KeyAction.Enter)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER to 0), editor.keys)
        tapMod(Modifier.SHIFT)
        engine.startInput(EditorContext(enterAction = EditorInfo.IME_ACTION_SEND))
        engine.press(KeyAction.Mod(Modifier.SHIFT), now)
        engine.perform(KeyAction.Enter)
        assertEquals(KeyEvent.KEYCODE_ENTER to Modifiers.SHIFT_META, editor.keys.last())
    }

    @Test
    fun languageIsRememberedForTextFieldsButTerminalsStartLatin() {
        engine.perform(KeyAction.Lang)
        assertEquals(Lang.HANGUL, engine.lang)
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        assertEquals(Lang.LATIN, engine.lang)
        engine.startInput(EditorContext())
        assertEquals(Lang.HANGUL, engine.lang)
    }

    @Test
    fun selectionMoveAbandonsComposition() {
        engine.perform(KeyAction.Lang)
        type("rk")
        engine.selectionChanged(0, 0, 0, 1)
        tap('k')
        assertEquals("가ㅏ", editor.text.toString())
    }

    @Test
    fun cursorMoveUsesArrowKeysWithShiftSelection() {
        engine.moveCursor(-3)
        assertEquals(List(3) { KeyEvent.KEYCODE_DPAD_LEFT to 0 }, editor.keys)
        editor.keys.clear()
        tapMod(Modifier.SHIFT)
        engine.moveCursor(2)
        engine.endCursorMove()
        assertEquals(List(2) { KeyEvent.KEYCODE_DPAD_RIGHT to Modifiers.SHIFT_META }, editor.keys)
        assertFalse(engine.modifiers.isActive(Modifier.SHIFT))
    }

    @Test
    fun rowMoveUsesUpDownArrowsAndFinishesHangulFirst() {
        engine.perform(KeyAction.Lang)
        type("gks")
        engine.moveCursorRows(-2)
        engine.moveCursorRows(1)
        engine.endCursorMove()
        assertEquals("한", editor.text.toString())
        assertEquals("", editor.composing)
        assertEquals(
            listOf(KeyEvent.KEYCODE_DPAD_UP to 0, KeyEvent.KEYCODE_DPAD_UP to 0, KeyEvent.KEYCODE_DPAD_DOWN to 0),
            editor.keys,
        )
    }

    @Test
    fun specialKeysAreEchoedForTheStrip() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        type("ls")
        engine.perform(ch('c'), forceCtrl = true)
        engine.perform(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE))
        tapMod(Modifier.ALT)
        tap('b')
        engine.perform(KeyAction.Code(KeyEvent.KEYCODE_TAB))
        engine.perform(KeyAction.Enter)
        assertEquals(listOf("^C", "Esc", "M-b", "Tab", "⏎"), listener.echoes)
    }

    @Test
    fun ctrlAltInTerminalIsCommittedAsEscapePrefixedControlCode() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.press(KeyAction.Mod(Modifier.ALT), now)
        engine.perform(ch('x'), forceCtrl = true)
        engine.release(KeyAction.Mod(Modifier.ALT), now + 50)
        assertEquals("\u001b\u0018", editor.text.toString())
        assertTrue(editor.keys.isEmpty())
        engine.startInput(EditorContext())
        engine.press(KeyAction.Mod(Modifier.ALT), now)
        engine.perform(ch('x'), forceCtrl = true)
        engine.release(KeyAction.Mod(Modifier.ALT), now + 50)
        assertEquals(listOf(KeyEvent.KEYCODE_X to (Modifiers.CTRL_META or Modifiers.ALT_META)), editor.keys)
    }

    @Test
    fun ctrlAltDigitsSpaceAndSlashFollowTermuxControlMapping() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.press(KeyAction.Mod(Modifier.ALT), now)
        for (c in "2345678/") engine.perform(ch(c), forceCtrl = true)
        engine.press(KeyAction.Mod(Modifier.CTRL), now)
        engine.perform(KeyAction.Space)
        engine.release(KeyAction.Mod(Modifier.CTRL), now + 50)
        engine.release(KeyAction.Mod(Modifier.ALT), now + 50)
        val expected = listOf(0, 27, 28, 29, 30, 31, 127, 31, 0).joinToString("") { "\u001b" + it.toChar() }
        assertEquals(expected, editor.text.toString())
        assertTrue(editor.keys.isEmpty())
    }

    @Test
    fun fnSymbolCommitsTextAfterFinishingHangul() {
        engine.perform(KeyAction.Lang)
        type("gk")
        engine.perform(KeyAction.Text("·"))
        type("rk")
        engine.perform(KeyAction.Text("…"))
        assertEquals("하·가…", editor.text.toString())
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("gk")
        engine.perform(KeyAction.Text("₩"))
        assertEquals("하·가…하₩", editor.text.toString())
        assertEquals("", listener.preedit)
    }

    @Test
    fun fnWithEscKeySendsInsertAndShiftInsert() {
        tapMod(Modifier.FN)
        engine.press(KeyAction.EscCtrl, now)
        engine.release(KeyAction.EscCtrl, now + 100)
        now += 200
        assertFalse(engine.modifiers.isActive(Modifier.FN))
        tapMod(Modifier.FN)
        tapMod(Modifier.SHIFT)
        engine.press(KeyAction.EscCtrl, now)
        engine.release(KeyAction.EscCtrl, now + 100)
        now += 200
        engine.press(KeyAction.EscCtrl, now)
        engine.release(KeyAction.EscCtrl, now + 100)
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_INSERT to 0,
                KeyEvent.KEYCODE_INSERT to Modifiers.SHIFT_META,
                KeyEvent.KEYCODE_ESCAPE to 0,
            ),
            editor.keys,
        )
        assertTrue("Ins" in listener.echoes && "⇧Ins" in listener.echoes)
    }

    private fun shownTyped() = engine.typedSegments.joinToString("|") { if (it.token) "<${it.display}>" else it.display }

    @Test
    fun terminalEchoRecordsWhatWasSentOnlyWhenEnabled() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        type("ls")
        assertEquals("", shownTyped())
        engine.settings = EngineSettings(terminalEcho = true)
        type("git co")
        engine.perform(KeyAction.Code(KeyEvent.KEYCODE_TAB))
        type("x")
        engine.perform(KeyAction.Backspace)
        engine.perform(KeyAction.Lang)
        type("gks")
        assertEquals("git co|<Tab>", shownTyped())
        assertEquals("한", engine.preedit)
        engine.perform(KeyAction.Space)
        assertEquals("git co|<Tab>|한 ", shownTyped())
        engine.perform(KeyAction.Enter)
        assertEquals("", shownTyped())
        engine.pasteText("echo a\nmake")
        assertEquals("make", shownTyped())
        assertTrue(editor.text.endsWith("echo a\nmake"))
        engine.settings = EngineSettings(terminalEcho = false)
        assertEquals("", shownTyped())
    }

    @Test
    fun terminalEchoIsNotRecordedForNormalEditors() {
        engine.settings = EngineSettings(terminalEcho = true)
        type("abc")
        engine.pasteText("x")
        assertEquals("", shownTyped())
        assertEquals("abcx", editor.text.toString())
    }

    @Test
    fun ctrlBracketActsAsEscapeForHangulMode() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("rk")
        engine.perform(ch('['), forceCtrl = true)
        assertEquals(Lang.LATIN, engine.lang)
        assertEquals("가", editor.text.toString())
    }

    @Test
    fun everyActionRunsInsideOneBalancedBatchEdit() {
        engine.perform(KeyAction.Lang)
        type("rkqt")
        engine.perform(KeyAction.Enter)
        engine.moveCursor(-2)
        assertEquals(0, editor.depth)
        assertTrue(editor.batches >= 7)
    }

    @Test
    fun restartDuringCompositionKeepsTypedJamo() {
        engine.perform(KeyAction.Lang)
        type("gksr")
        assertEquals("한ㄱ", editor.text.toString())
        assertEquals("ㄱ", editor.composing)
        engine.startInput(EditorContext(), restarting = true)
        assertEquals("", editor.composing)
        type("mf")
        engine.perform(KeyAction.Space)
        assertEquals("한ㄱㅡㄹ ", editor.text.toString())
    }

    @Test
    fun restartInTerminalCommitsPendingSyllable() {
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        type("rk")
        engine.startInput(EditorContext(raw = true, preferLatin = true), restarting = true)
        assertEquals("가", editor.text.toString())
        assertEquals("", listener.preedit)
    }

    @Test
    fun pasteUsesRawFlag() {
        engine.startInput(EditorContext(raw = true))
        engine.perform(KeyAction.Cmd(Command.PASTE))
        assertEquals(listOf("paste:true"), editor.log.filter { it.startsWith("paste") })
    }
}
