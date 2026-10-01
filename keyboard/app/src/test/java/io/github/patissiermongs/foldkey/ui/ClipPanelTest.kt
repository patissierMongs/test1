package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
class ClipPanelTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private val clips = mutableListOf(
        Clip("ssh fold@192.168.0.7", pinned = true),
        Clip("git status --short"),
        Clip("make test"),
        Clip("go build ./..."),
        Clip("python3 -m venv .venv"),
        Clip("tmux attach"),
    )
    private val edits = ArrayList<Pair<ClipEdit, String>>()
    private lateinit var editor: FakeEditor
    private lateinit var view: KeyboardView

    private fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).putBoolean(Prefs.SPLIT_LANDSCAPE, true).commit()
        editor = FakeEditor()
        val engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext())
        view = KeyboardView(activity, engine, prefs, Silent)
        view.clipSource = { clips.toList() }
        view.onClipEdit = { edit, text -> edits.add(edit to text) }
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.layoutKeys.isNotEmpty())
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun tap(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun tap(box: Box) = tap(box.centerX, box.centerY)

    private fun longPress(box: Box) {
        touch(MotionEvent.ACTION_DOWN, box.centerX, box.centerY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        touch(MotionEvent.ACTION_UP, box.centerX, box.centerY)
    }

    @Test
    fun editButtonsSitLeftOfPasteAndDisappearInTerminals() {
        setup()
        val selectAll = view.stripBox(Command.SELECT_ALL)!!
        val copy = view.stripBox(Command.COPY)!!
        val paste = view.stripBox(Command.PASTE)!!
        assertTrue(selectAll.right <= copy.left + 1f && copy.right <= paste.left + 1f)
        tap(selectAll)
        tap(copy)
        assertEquals(listOf("selectAll", "copy"), editor.log.filter { it == "selectAll" || it == "copy" })
        view.showEditCommands = false
        assertNull(view.stripBox(Command.SELECT_ALL))
        assertNull(view.stripBox(Command.COPY))
        assertNotNull(view.stripBox(Command.PASTE))
    }

    @Test
    fun tapPastesAndDragScrollsWithoutPasting() {
        setup()
        val slots = view.clipSlots
        assertEquals(2, slots.size)
        tap(slots[1])
        assertEquals("git status --short", editor.text.toString())
        touch(MotionEvent.ACTION_DOWN, slots[1].centerX, slots[1].centerY)
        touch(MotionEvent.ACTION_MOVE, slots[1].centerX, slots[1].centerY - 2.2f * slots[1].height)
        touch(MotionEvent.ACTION_UP, slots[1].centerX, slots[1].centerY - 2.2f * slots[1].height)
        assertEquals(2, view.clipOffset)
        assertEquals("git status --short", editor.text.toString())
        tap(slots[0])
        assertEquals("git status --shortmake test", editor.text.toString())
        touch(MotionEvent.ACTION_DOWN, slots[0].centerX, slots[0].centerY)
        touch(MotionEvent.ACTION_MOVE, slots[0].centerX, slots[0].centerY - 10f * slots[0].height)
        touch(MotionEvent.ACTION_UP, slots[0].centerX, slots[0].centerY - 10f * slots[0].height)
        assertEquals(clips.size - slots.size, view.clipOffset)
    }

    @Test
    fun longPressOpensPinAndDeleteChoices() {
        setup()
        val slots = view.clipSlots
        longPress(slots[1])
        assertEquals("git status --short", view.openClipMenu)
        assertEquals("", editor.text.toString())
        tap(slots[1].left + slots[1].width * 0.25f, slots[1].centerY)
        assertNull(view.openClipMenu)
        longPress(slots[0])
        tap(slots[0].left + slots[0].width * 0.25f, slots[0].centerY)
        longPress(slots[1])
        tap(slots[1].left + slots[1].width * 0.75f, slots[1].centerY)
        assertEquals(
            listOf(
                ClipEdit.PIN to "git status --short",
                ClipEdit.UNPIN to "ssh fold@192.168.0.7",
                ClipEdit.DELETE to "git status --short",
            ),
            edits,
        )
        assertEquals("", editor.text.toString())
    }

    @Test
    fun openMenuClosesOnOtherTapsWithoutActing() {
        setup()
        val slots = view.clipSlots
        longPress(slots[0])
        tap(slots[1])
        assertNull(view.openClipMenu)
        assertEquals("", editor.text.toString())
        view.pinsFull = { true }
        longPress(slots[1])
        tap(slots[1].left + slots[1].width * 0.25f, slots[1].centerY)
        assertTrue(edits.isEmpty())
    }
}
