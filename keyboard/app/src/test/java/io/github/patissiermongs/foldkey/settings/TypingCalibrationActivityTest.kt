package io.github.patissiermongs.foldkey.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.TypingPrompts
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.TypingSession
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w945dp-h857dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypingCalibrationActivityTest {
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun render(root: View, name: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(board: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(t, t, action, x, y, 0)
            board.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    private fun typeUntil(board: View, session: () -> TypingSession, random: Random, stop: () -> Boolean) {
        val px = Dpi.physical(board.resources.displayMetrics, true) / 25.4f
        val py = Dpi.physical(board.resources.displayMetrics, false) / 25.4f
        var guard = 0
        while (!stop()) {
            val s = session()
            val c = s.expected!!
            val key = s.geometry.keys.first { k ->
                !k.ghost && if (c == ' ') k.def.action == KeyAction.Space else (k.def.action as? KeyAction.Char)?.base == c
            }
            val p = s.round.placement
            val top = board.height - (p.liftMm + Layouts.rows(LayoutKind.SPLIT, s.layer) * p.rowMm) * py
            tap(board, key.face.centerX + (0.6f + 1.4f * random.nextGaussian().toFloat()) * px, top + key.face.centerY + (1.0f + 1.6f * random.nextGaussian().toFloat()) * py)
            check(++guard < 4000)
        }
    }

    @Test
    fun typingBothLayersGrowsSmallKeysAndSeedsTheCorrections() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx)
        prefs.sp.edit().clear().commit()
        val intent = Intent(ctx, TypingCalibrationActivity::class.java)
            .putExtra(TypingCalibrationActivity.EXTRA_ZONES, floatArrayOf(13.7f, 47.5f, 6.2f, 50.3f, 17.4f, 52.2f, 6.2f, 50.3f))
            .putExtra(TypingCalibrationActivity.EXTRA_START, floatArrayOf(4.3f, 6.1f, 8.8f))
        val activity = Robolectric.buildActivity(TypingCalibrationActivity::class.java, intent).setup().get()
        val root = activity.window.decorView
        val views = all(root)
        val board = views.first { it.javaClass.simpleName == "TypingView" }
        val apply = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.typing_apply) }
        assertFalse(apply.isEnabled)
        assertEquals(4.3f, activity.session.round.unitMm, 0.05f)
        val random = Random(21)
        typeUntil(board, { activity.session }, random) { activity.session.progress >= 20 }
        render(root, "typing_calibration_code.png")
        typeUntil(board, { activity.session }, random) { activity.session.layer == Layer.GENERAL }
        val code = activity.session.results.single()
        assertTrue("${code.rounds.size}", code.rounds.size >= 2)
        typeUntil(board, { activity.session }, random) { activity.session.finished }
        assertTrue(apply.isEnabled)
        val status = views.filterIsInstance<TextView>().filter { it !is Button }.joinToString("\n") { it.text.toString() }
        assertTrue(status, status.contains("Code layer: keys 4.3 →"))
        assertTrue(status, status.contains("Text layer: keys 6.1 →"))
        render(root, "typing_calibration_result.png")
        val epoch = prefs.offsetsEpoch
        apply.performClick()
        val unit = prefs.sp.getInt(Prefs.SPLIT_UNIT, 0)
        assertTrue("$unit", unit in 52..66)
        assertTrue(prefs.sp.getInt(Prefs.GENERAL_UNIT, 0) >= 61)
        assertEquals(88, prefs.sp.getInt(Prefs.ROW_HEIGHT, 0))
        val left = prefs.sp.getInt(Prefs.CODE_SIDE_LEFT, -1) / 10f
        assertEquals(30.6f - 7.25f * unit / 20f, left, 0.11f)
        assertEquals(epoch + 1, prefs.offsetsEpoch)
        val width = activity.resources.displayMetrics.widthPixels.toFloat()
        val px = Dpi.physical(activity.resources.displayMetrics, true) / 25.4f
        for (layer in Layer.entries) {
            val slot = KeyboardView.offsetSlot(LayoutKind.SPLIT, layer, width, px)
            val model = OffsetModel(20).also { it.load(prefs.offsets(slot)) }
            val seeded = (0 until model.zones).filter { model.samples(it) == 20 }
            assertTrue("$slot $seeded", seeded.size >= 10)
            assertEquals(1.0f, seeded.map { model.meanMm(it).second }.average().toFloat(), 0.5f)
        }
        assertTrue(activity.isFinishing)
    }

    @Test
    fun koreanPromptsMarkWholeSyllablesAsTyped() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs(ctx).sp.edit().clear().commit()
        val activity = Robolectric.buildActivity(TypingCalibrationActivity::class.java).setup().get()
        val views = all(activity.window.decorView)
        val board = views.first { it.javaClass.simpleName == "TypingView" }
        views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.typing_skip) }.performClick()
        val s = activity.session
        assertEquals(Layer.GENERAL, s.layer)
        val line = s.line
        assertTrue(line, line.first().code >= 0xAC00)
        val texts = views.filterIsInstance<TextView>().filter { it !is Button }
        val prompt = texts.first { it.text.toString() == line }
        fun marked(): Int {
            val text = prompt.text as Spanned
            return text.getSpans(0, text.length, ForegroundColorSpan::class.java).maxOfOrNull { text.getSpanEnd(it) } ?: 0
        }
        val py = Dpi.physical(board.resources.displayMetrics, false) / 25.4f
        val keys = TypingPrompts.keys(line.substring(0, 2))!!
        val first = TypingPrompts.keys(line.substring(0, 1))!!.size
        for ((i, c) in keys.withIndex()) {
            val key = s.geometry.keys.first { k -> !k.ghost && (k.def.action as? KeyAction.Char)?.base == c }
            val p = s.round.placement
            val top = board.height - (p.liftMm + Layouts.rows(LayoutKind.SPLIT, s.layer) * p.rowMm) * py
            tap(board, key.face.centerX, top + key.face.centerY)
            assertEquals("after ${i + 1} keys", if (i + 1 < first) 0 else if (i + 1 < keys.size) 1 else 2, marked())
        }
        assertTrue(texts.any { it.text.toString() == line.substring(0, 2) })
    }

    @Test
    fun skippingBothLayersLeavesSettingsAlone() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = Prefs(ctx)
        prefs.sp.edit().clear().commit()
        val activity = Robolectric.buildActivity(TypingCalibrationActivity::class.java).setup().get()
        val views = all(activity.window.decorView)
        val skip = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.typing_skip) }
        val apply = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.typing_apply) }
        assertEquals(prefs.splitUnitMm, activity.session.round.unitMm, 0.05f)
        skip.performClick()
        skip.performClick()
        assertTrue(activity.session.finished)
        assertFalse(apply.isEnabled)
        assertFalse(prefs.sp.contains(Prefs.SPLIT_UNIT))
    }
}
