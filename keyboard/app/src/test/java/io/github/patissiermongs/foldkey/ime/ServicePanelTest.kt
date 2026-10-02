package io.github.patissiermongs.foldkey.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.patissiermongs.foldkey.ui.ClipEdit
import io.github.patissiermongs.foldkey.ui.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServicePanelTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun copiedTextIsListedNewestFirstAndSensitiveClipsAreSkipped() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        val cm = service.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("a", "git status"))
        cm.setPrimaryClip(ClipData.newPlainText("b", "make test"))
        idle()
        assertEquals(listOf("make test", "git status"), view.clipSource().map { it.text })
        val secret = ClipData.newPlainText("p", "hunter2")
        secret.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(secret)
        idle()
        assertEquals(listOf("make test", "git status"), view.clipSource().map { it.text })
        Prefs(service).sp.edit().putBoolean(Prefs.CENTER_CLIPBOARD, false).commit()
        idle()
        assertEquals(emptyList<String>(), view.clipSource().map { it.text })
        service.onDestroy()
    }

    @Test
    fun editorLineComesFromInitialTextAndIsHiddenForPasswords() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        val text = "first line\nprint(x"
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            initialSelStart = text.length
            initialSelEnd = text.length
            setInitialSurroundingText(text)
        }
        service.onStartInputView(info, false)
        assertEquals("print(x", view.shownEditorLine)
        val password = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            initialSelStart = 7
            initialSelEnd = 7
            setInitialSurroundingText("hunter2")
        }
        service.onStartInputView(password, false)
        assertEquals("", view.shownEditorLine)
        service.onDestroy()
    }

    @Test
    fun pinnedClipsSurviveARestartAndDeletedClipsStayDeleted() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        val cm = service.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("a", "ssh fold@host"))
        cm.setPrimaryClip(ClipData.newPlainText("b", "rm -rf build"))
        idle()
        view.onClipEdit(ClipEdit.PIN, "ssh fold@host")
        view.onClipEdit(ClipEdit.DELETE, "rm -rf build")
        assertFalse(cm.hasPrimaryClip())
        service.onStartInputView(EditorInfo(), false)
        assertEquals(listOf("ssh fold@host" to true), view.clipSource().map { it.text to it.pinned })
        service.onDestroy()
        val restarted = Robolectric.setupService(FoldKeyService::class.java)
        val view2 = restarted.onCreateInputView() as KeyboardView
        restarted.onStartInputView(EditorInfo(), false)
        assertEquals(listOf("ssh fold@host" to true), view2.clipSource().map { it.text to it.pinned })
        view2.onClipEdit(ClipEdit.UNPIN, "ssh fold@host")
        assertEquals(listOf("ssh fold@host" to false), view2.clipSource().map { it.text to it.pinned })
        restarted.onDestroy()
        val third = Robolectric.setupService(FoldKeyService::class.java)
        val view3 = third.onCreateInputView() as KeyboardView
        assertEquals(emptyList<String>(), view3.clipSource().map { it.text })
        third.onDestroy()
    }

    @Test
    fun historySizeFollowsSettings() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        Prefs(service).sp.edit().putInt(Prefs.CLIP_COUNT, 5).commit()
        idle()
        val cm = service.getSystemService(ClipboardManager::class.java)
        for (i in 1..7) cm.setPrimaryClip(ClipData.newPlainText("c", "cmd $i"))
        idle()
        assertEquals(listOf("cmd 7", "cmd 6", "cmd 5", "cmd 4", "cmd 3"), view.clipSource().map { it.text })
        service.onDestroy()
    }

    @Test
    fun editButtonsAreHiddenInTerminals() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        assertTrue(view.showEditCommands)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_NULL }, false)
        assertFalse(view.showEditCommands)
        service.onDestroy()
    }
}

