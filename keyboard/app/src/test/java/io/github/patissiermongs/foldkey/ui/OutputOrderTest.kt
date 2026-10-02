package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.InputConnectionEditor
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.settings.KeyEchoView
import org.junit.Assert.assertEquals
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
@Config(sdk = [36], qualifiers = "w750dp-h832dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OutputOrderTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var echo: KeyEchoView
    private lateinit var view: KeyboardView
    private val fingers = LinkedHashMap<Int, Pair<Float, Float>>()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).commit()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        echo = KeyEchoView(activity)
        root.addView(echo, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.setContentView(root)
        echo.requestFocus()
        Shadow.extract<ShadowViewRootImpl>(echo.rootView.parent).callWindowFocusChanged(true)
        idle()
        val ic = echo.onCreateInputConnection(EditorInfo())
        val engine = KeyboardEngine(InputConnectionEditor(activity) { ic }, RecordingListener())
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        view = KeyboardView(activity, engine, prefs, Silent)
        root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
    }

    private fun key(predicate: (Key) -> Boolean): Box = view.layoutKeys.first { !it.ghost && predicate(it) }.face

    private fun send(action: Int, index: Int) {
        val ids = fingers.keys.toList()
        val props = ids.map { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = ids.map { id ->
            val (x, y) = fingers.getValue(id)
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }
        }.toTypedArray()
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ids.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun down(id: Int, box: Box) {
        fingers[id] = box.centerX to box.centerY
        send(if (fingers.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN, fingers.keys.indexOf(id))
    }

    private fun up(id: Int) {
        send(if (fingers.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP, fingers.keys.indexOf(id))
        fingers.remove(id)
    }

    private fun rollOver(first: Box, second: Box) {
        down(0, first)
        down(1, second)
        up(1)
        up(0)
        idle()
    }

    private fun log() = echo.text.toString().lines()

    @Test
    fun terminalEnterRolledIntoALetterArrivesFirst() {
        setup()
        rollOver(key { it.def.action == KeyAction.Enter }, key { (it.def.action as? KeyAction.Char)?.base == 'c' })
        assertEquals(listOf("text \"\\n\"", "text \"c\""), log())
    }

    @Test
    fun terminalTabRolledIntoALetterArrivesFirst() {
        setup()
        rollOver(key { it.def.label == "Tab" }, key { (it.def.action as? KeyAction.Char)?.base == 'l' })
        assertEquals(listOf("text \"\\t\"", "text \"l\""), log())
    }
}
