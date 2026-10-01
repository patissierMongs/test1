package io.github.patissiermongs.foldkey.ime

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.engine.UsKeyMap
import io.github.patissiermongs.foldkey.settings.KeyEchoView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorIntegrationTest {
    private fun ch(c: Char) = KeyAction.Char(c, UsKeyMap.shiftedOf(c))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun focusWindow(view: View) {
        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(true)
        idle()
    }

    private fun editTextSetup(multiLine: Boolean): Triple<EditText, KeyboardEngine, EditorInfo> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        if (multiLine) edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        activity.setContentView(edit)
        edit.requestFocus()
        focusWindow(edit)
        val info = EditorInfo()
        val ic = edit.onCreateInputConnection(info)!!
        val engine = KeyboardEngine(InputConnectionEditor(activity) { ic }, RecordingListener())
        return Triple(edit, engine, info)
    }

    @Test
    fun hangulCompositionInEditText() {
        val (edit, engine, _) = editTextSetup(multiLine = true)
        engine.startInput(EditorContext())
        engine.perform(KeyAction.Lang)
        "gksrmf".forEach { engine.perform(ch(it)) }
        idle()
        assertEquals("한글", edit.text.toString())
        engine.perform(KeyAction.Backspace)
        engine.perform(KeyAction.Backspace)
        idle()
        assertEquals("한ㄱ", edit.text.toString())
        "qkqtk".forEach { engine.perform(ch(it)) }
        engine.perform(KeyAction.Space)
        idle()
        assertTrue(edit.text.toString().endsWith("밥사 "))
    }

    @Test
    fun enterFollowsTextViewEditorInfo() {
        val (edit, engine, info) = editTextSetup(multiLine = true)
        assertTrue((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)
        engine.startInput(EditorContext(enterAction = null))
        "ab".forEach { engine.perform(ch(it)) }
        idle()
        engine.perform(KeyAction.Enter)
        idle()
        engine.perform(ch('c'))
        idle()
        assertEquals("ab\nc", edit.text.toString())
    }

    @Test
    fun terminalStyleViewReceivesKeyEventsAndCommittedSyllables() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val echo = KeyEchoView(activity)
        activity.setContentView(echo)
        echo.requestFocus()
        focusWindow(echo)
        val info = EditorInfo()
        val ic = echo.onCreateInputConnection(info)
        assertEquals(InputType.TYPE_NULL, info.inputType)
        val listener = RecordingListener()
        val engine = KeyboardEngine(InputConnectionEditor(activity) { ic }, listener)
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        engine.perform(KeyAction.Lang)
        "dkssud".forEach { engine.perform(ch(it)) }
        assertEquals("녕", listener.preedit)
        engine.perform(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE))
        idle()
        engine.perform(ch('c'), forceCtrl = true)
        idle()
        engine.press(KeyAction.Mod(Modifier.ALT), 0)
        engine.perform(ch('b'))
        engine.release(KeyAction.Mod(Modifier.ALT), 50)
        idle()
        engine.perform(KeyAction.Enter)
        idle()
        val log = echo.text.toString().lines()
        fun name(code: Int) = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
        assertEquals(
            listOf(
                "text \"안\"",
                "text \"녕\"",
                "down " + name(KeyEvent.KEYCODE_ESCAPE),
                "down Ctrl+" + name(KeyEvent.KEYCODE_CTRL_LEFT),
                "down Ctrl+" + name(KeyEvent.KEYCODE_C),
                "down Alt+" + name(KeyEvent.KEYCODE_ALT_LEFT),
                "down Alt+" + name(KeyEvent.KEYCODE_B),
                "text \"\\n\"",
            ),
            log,
        )
    }

    @Test
    fun selectAllAndCopyUseTheEditorsOwnActions() {
        val (edit, engine, _) = editTextSetup(multiLine = false)
        engine.startInput(EditorContext())
        "git log".forEach { engine.perform(if (it == ' ') KeyAction.Space else ch(it)) }
        idle()
        engine.perform(KeyAction.Cmd(Command.SELECT_ALL))
        idle()
        assertEquals(0, edit.selectionStart)
        assertEquals(7, edit.selectionEnd)
        engine.perform(KeyAction.Cmd(Command.COPY))
        idle()
        val cm = edit.context.getSystemService(ClipboardManager::class.java)
        assertEquals("git log", cm.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun selectionDragExtendsTheEditTextSelection() {
        val (edit, engine, _) = editTextSetup(multiLine = false)
        engine.startInput(EditorContext())
        "git status".forEach { engine.perform(if (it == ' ') KeyAction.Space else ch(it)) }
        idle()
        assertEquals(10, edit.selectionEnd)
        engine.moveCursor(-6, select = true)
        engine.endCursorMove()
        idle()
        assertEquals(4, minOf(edit.selectionStart, edit.selectionEnd))
        assertEquals(10, maxOf(edit.selectionStart, edit.selectionEnd))
        engine.perform(KeyAction.Text("7"))
        idle()
        assertEquals("git 7", edit.text.toString())
    }

    @Test
    fun pasteRunsOnceWhenTheEditorHandlesItButReportsFalse() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        activity.setContentView(edit)
        edit.requestFocus()
        focusWindow(edit)
        val inner = edit.onCreateInputConnection(EditorInfo())!!
        val reportsFalse = object : InputConnectionWrapper(inner, false) {
            override fun performContextMenuAction(id: Int): Boolean {
                super.performContextMenuAction(id)
                return false
            }
        }
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("c", "make test"))
        val engine = KeyboardEngine(InputConnectionEditor(activity) { reportsFalse }, RecordingListener())
        engine.startInput(EditorContext())
        engine.perform(KeyAction.Cmd(Command.PASTE))
        idle()
        assertEquals("make test", edit.text.toString())
    }
}

