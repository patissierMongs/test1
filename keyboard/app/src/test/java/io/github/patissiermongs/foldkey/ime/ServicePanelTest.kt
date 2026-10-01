package io.github.patissiermongs.foldkey.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.patissiermongs.foldkey.ui.KeyboardView
import org.junit.Assert.assertEquals
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
        assertEquals(listOf("make test", "git status"), view.clipSource())
        val secret = ClipData.newPlainText("p", "hunter2")
        secret.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(secret)
        idle()
        assertEquals(listOf("make test", "git status"), view.clipSource())
        Prefs(service).sp.edit().putBoolean(Prefs.CENTER_CLIPBOARD, false).commit()
        idle()
        assertEquals(emptyList<String>(), view.clipSource())
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
}
