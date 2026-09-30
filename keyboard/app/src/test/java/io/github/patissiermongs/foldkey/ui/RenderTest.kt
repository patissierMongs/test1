package io.github.patissiermongs.foldkey.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Key
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    private fun setup(split: Boolean, widthPx: Int, ppi: Float = FOLD7_PPI): Triple<KeyboardView, KeyboardEngine, FakeEditor> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dm = ctx.resources.displayMetrics
        dm.xdpi = ppi
        dm.ydpi = ppi
        val prefs = Prefs(ctx)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, split).putBoolean(Prefs.SPLIT_LANDSCAPE, split).commit()
        val editor = FakeEditor()
        val engine = KeyboardEngine(editor, RecordingListener())
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

    companion object {
        const val FOLD7_PPI = 368f
        const val COVER_PPI = 422f
    }
}
