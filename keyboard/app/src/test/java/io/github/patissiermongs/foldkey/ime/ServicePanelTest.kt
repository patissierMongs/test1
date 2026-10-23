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
    fun clipsSurviveARestartAndDeletedClipsStayDeleted() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val view = service.onCreateInputView() as KeyboardView
        val cm = service.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("a", "ssh fold@host"))
        cm.setPrimaryClip(ClipData.newPlainText("b", "rm -rf build"))
        cm.setPrimaryClip(ClipData.newPlainText("c", "git log --oneline"))
        idle()
        view.onClipEdit(ClipEdit.PIN, "ssh fold@host")
        view.onClipEdit(ClipEdit.DELETE, "rm -rf build")
        service.onStartInputView(EditorInfo(), false)
        assertEquals(
            listOf("ssh fold@host" to true, "git log --oneline" to false),
            view.clipSource().map { it.text to it.pinned },
        )
        service.onDestroy()
        val restarted = Robolectric.setupService(FoldKeyService::class.java)
        val view2 = restarted.onCreateInputView() as KeyboardView
        restarted.onStartInputView(EditorInfo(), false)
        assertEquals(
            listOf("ssh fold@host" to true, "git log --oneline" to false),
            view2.clipSource().map { it.text to it.pinned },
        )
        view2.onClipEdit(ClipEdit.UNPIN, "ssh fold@host")
        view2.onClipEdit(ClipEdit.DELETE, "git log --oneline")
        assertFalse(cm.hasPrimaryClip())
        assertEquals(listOf("ssh fold@host" to false), view2.clipSource().map { it.text to it.pinned })
        restarted.onDestroy()
        val third = Robolectric.setupService(FoldKeyService::class.java)
        val view3 = third.onCreateInputView() as KeyboardView
        assertEquals(listOf("ssh fold@host" to false), view3.clipSource().map { it.text to it.pinned })
        Prefs(third).sp.edit().putBoolean(Prefs.CENTER_CLIPBOARD, false).commit()
        idle()
        third.onDestroy()
        val fourth = Robolectric.setupService(FoldKeyService::class.java)
        val view4 = fourth.onCreateInputView() as KeyboardView
        Prefs(fourth).sp.edit().putBoolean(Prefs.CENTER_CLIPBOARD, true).commit()
        idle()
        assertEquals(emptyList<String>(), view4.clipSource().map { it.text })
        fourth.onDestroy()
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

