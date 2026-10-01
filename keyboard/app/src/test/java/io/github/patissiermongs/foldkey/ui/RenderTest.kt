package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.EngineSettings
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.engine.UsKeyMap
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Key
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w750dp-h832dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private fun setup(
        split: Boolean,
        widthPx: Int,
        ppi: Float = FOLD7_PPI,
        terminalEcho: Boolean = false,
    ): Triple<KeyboardView, KeyboardEngine, FakeEditor> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dm = ctx.resources.displayMetrics
        dm.xdpi = ppi
        dm.ydpi = ppi
        val prefs = Prefs(ctx)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, split).putBoolean(Prefs.SPLIT_LANDSCAPE, split)
            .putBoolean(Prefs.TERMINAL_ECHO, terminalEcho).commit()
        val editor = FakeEditor()
        val engine = KeyboardEngine(editor, RecordingListener())
        engine.settings = EngineSettings(terminalEcho = terminalEcho)
        val view = KeyboardView(ctx, engine, prefs, Silent)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return Triple(view, engine, editor)
    }

    private fun save(view: View, name: String): File {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    @Test
    fun renderFullPortraitLatin() {
        val (view, _, _) = setup(split = false, widthPx = 1968)
        assertTrue(save(view, "full_portrait_latin.png").length() > 0)
    }

    @Test
    fun renderFullPortraitHangulWithShift() {
        val (view, engine, _) = setup(split = false, widthPx = 1968)
        engine.perform(KeyAction.Lang)
        engine.press(KeyAction.Mod(Modifier.SHIFT), 0)
        engine.release(KeyAction.Mod(Modifier.SHIFT), 50)
        assertTrue(save(view, "full_portrait_hangul_shift.png").length() > 0)
    }

    @Test
    fun renderFullLandscapeWithPopup() {
        val (view, _, _) = setup(split = false, widthPx = 2184)
        val face = view.layoutKeys.first { !it.ghost && (it.def.action as? KeyAction.Char)?.base == '9' }.face
        touch(view, MotionEvent.ACTION_DOWN, face.centerX, face.centerY)
        touch(view, MotionEvent.ACTION_MOVE, face.centerX, face.centerY - 80f)
        assertTrue(save(view, "full_landscape_popup.png").length() > 0)
    }

    @Test
    fun renderSplitPortraitFn() {
        val (view, engine, _) = setup(split = true, widthPx = 1968)
        engine.press(KeyAction.Mod(Modifier.FN), 0)
        engine.release(KeyAction.Mod(Modifier.FN), 50)
        assertTrue(save(view, "split_portrait_fn.png").length() > 0)
    }

    @Test
    fun renderSplitPortrait() {
        val (view, _, _) = setup(split = true, widthPx = 1968)
        assertTrue(save(view, "split_portrait.png").length() > 0)
    }

    @Test
    fun renderFullPortraitFn() {
        val (view, engine, _) = setup(split = false, widthPx = 1968)
        engine.press(KeyAction.Mod(Modifier.FN), 0)
        engine.release(KeyAction.Mod(Modifier.FN), 50)
        assertTrue(save(view, "full_portrait_fn.png").length() > 0)
    }

    @Test
    fun renderCoverScreenCompactFn() {
        val (view, engine, _) = setup(split = false, widthPx = 1080, ppi = COVER_PPI)
        engine.press(KeyAction.Mod(Modifier.FN), 0)
        engine.release(KeyAction.Mod(Modifier.FN), 50)
        assertTrue(save(view, "cover_compact_fn.png").length() > 0)
    }

    @Test
    fun renderCoverScreenCompact() {
        val (view, _, _) = setup(split = false, widthPx = 1080, ppi = COVER_PPI)
        assertTrue(save(view, "cover_compact.png").length() > 0)
    }

    @Test
    fun renderSplitSwipeDownPreview() {
        val (view, _, _) = setup(split = true, widthPx = 2184)
        val face = view.layoutKeys.first { !it.ghost && (it.def.action as? KeyAction.Char)?.base == 'c' }.face
        touch(view, MotionEvent.ACTION_DOWN, face.centerX, face.centerY)
        touch(view, MotionEvent.ACTION_MOVE, face.centerX, face.centerY + 90f)
        assertTrue(save(view, "split_swipe_down_ctrl.png").length() > 0)
    }

    @Test
    fun renderSplitLandscape() {
        val (view, _, _) = setup(split = true, widthPx = 2184)
        assertTrue(save(view, "split_landscape.png").length() > 0)
    }

    private fun type(engine: KeyboardEngine, s: String) = s.forEach {
        engine.perform(if (it == ' ') KeyAction.Space else KeyAction.Char(it, UsKeyMap.shiftedOf(it)))
    }

    @Test
    fun renderSplitLandscapeCenterPanel() {
        val (view, engine, _) = setup(split = true, widthPx = 2184)
        view.clipSource = { listOf(Clip("git status --short"), Clip("ssh fold@192.168.0.7\nls -la")) }
        engine.startInput(EditorContext())
        engine.perform(KeyAction.Lang)
        type(engine, "gks")
        view.setEditorLine("        return render(x, y) # 한")
        assertTrue(save(view, "split_landscape_panel.png").length() > 0)
    }

    @Test
    fun renderSplitLandscapeFn() {
        val (view, engine, _) = setup(split = true, widthPx = 2184)
        engine.press(KeyAction.Mod(Modifier.FN), 0)
        engine.release(KeyAction.Mod(Modifier.FN), 50)
        assertTrue(save(view, "split_landscape_fn.png").length() > 0)
    }

    @Test
    fun renderTerminalEchoWithHangulPreedit() {
        val (view, engine, _) = setup(split = true, widthPx = 2184, terminalEcho = true)
        engine.startInput(EditorContext(raw = true, preferLatin = true))
        type(engine, "git commit -m ")
        engine.perform(KeyAction.Lang)
        type(engine, "gksrmf")
        assertTrue(save(view, "split_landscape_terminal_echo.png").length() > 0)
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi")
    fun renderSplitLandscapeClipMenu() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = FOLD7_PPI
        dm.ydpi = FOLD7_PPI
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_LANDSCAPE, true).commit()
        val view = KeyboardView(activity, KeyboardEngine(FakeEditor(), RecordingListener()), prefs, Silent)
        view.clipSource = {
            listOf(Clip("ssh fold@192.168.0.7", pinned = true), Clip("git status --short"), Clip("make test"), Clip("tmux attach"))
        }
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shadowOf(Looper.getMainLooper()).idle()
        val slot = view.clipSlots[1]
        touch(view, MotionEvent.ACTION_DOWN, slot.centerX, slot.centerY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        touch(view, MotionEvent.ACTION_UP, slot.centerX, slot.centerY)
        assertTrue(view.openClipMenu == "git status --short")
        assertTrue(save(view, "split_landscape_clip_menu.png").length() > 0)
    }

    companion object {
        const val FOLD7_PPI = 368f
        const val COVER_PPI = 422f
    }
}
