package io.github.patissiermongs.foldkey.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ui.Dpi
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertArrayEquals
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
@Config(sdk = [36], qualifiers = "w900dp-h480dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReachCalibrationActivityTest {
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun scribble(view: View, side: Int, near: Float, far: Float, bottom: Float, top: Float) {
        val dm = view.resources.displayMetrics
        val px = Dpi.physical(dm, true) / 25.4f
        val py = Dpi.physical(dm, false) / 25.4f
        val base = view.height.toFloat()
        val sweeps = ((top - bottom) / 0.4f).toInt()
        val points = (0..sweeps).flatMap { k ->
            val y = bottom + (top - bottom) * k / sweeps
            val ends = if (k % 2 == 0) listOf(near, far) else listOf(far, near)
            ends.map { d -> (if (side < 0) d * px else view.width - d * px) to base - y * py }
        }
        val t = SystemClock.uptimeMillis()
        points.forEachIndexed { i, (x, y) ->
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                points.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val e = MotionEvent.obtain(t, t + i * 8L, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    @Test
    fun threeScribblesPerThumbLeadToTheTypingStep() {
        val controller = Robolectric.buildActivity(ReachCalibrationActivity::class.java).setup()
        val activity = controller.get()
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().commit()
        val views = all(activity.window.decorView)
        val zones = views.first { it.javaClass.simpleName == "ZoneView" }
        val next = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.reach_next) }
        assertTrue(zones.width > 0)
        assertFalse(next.isEnabled)
        for (shift in listOf(0f, 0.6f, -0.5f)) scribble(zones, -1, 13.7f + shift, 47.5f + shift, 6.2f, 50.5f + shift)
        for (shift in listOf(0f, -0.4f, 0.7f)) scribble(zones, 1, 17.4f + shift, 52.2f + shift, 6.2f + shift, 50.5f)
        val status = views.filterIsInstance<TextView>().filter { it !is Button }.joinToString("\n") { it.text.toString() }
        assertTrue(status, status.contains("code keys 4.3 mm"))
        assertTrue(status, status.contains("text keys 6.1 mm"))
        assertTrue(next.isEnabled)
        val preview = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.reach_preview_code) }
        val dir = File("build/render").apply { mkdirs() }
        for (name in listOf("reach_calibration_code.png", "reach_calibration_general.png")) {
            shadowOf(Looper.getMainLooper()).idle()
            val bmp = Bitmap.createBitmap(zones.rootView.width, zones.rootView.height, Bitmap.Config.ARGB_8888)
            zones.rootView.draw(Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            preview.performClick()
        }
        next.performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(TypingCalibrationActivity::class.java.name, started.component?.className)
        val z = started.getFloatArrayExtra(TypingCalibrationActivity.EXTRA_ZONES)!!
        assertArrayEquals(floatArrayOf(13.7f, 47.5f, 17.4f, 52.2f), floatArrayOf(z[0], z[1], z[4], z[5]), 0.3f)
        assertEquals(6.2f, z[2], 0.3f)
        assertArrayEquals(floatArrayOf(4.3f, 6.1f, 8.8f), started.getFloatArrayExtra(TypingCalibrationActivity.EXTRA_START)!!, 0.11f)
        assertFalse(prefs.sp.contains(Prefs.SPLIT_UNIT))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun scribblesThatDisagreeKeepNextDisabled() {
        val activity = Robolectric.buildActivity(ReachCalibrationActivity::class.java).setup().get()
        val views = all(activity.window.decorView)
        val zones = views.first { it.javaClass.simpleName == "ZoneView" }
        val next = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.reach_next) }
        for (far in listOf(58f, 47f, 48f)) scribble(zones, -1, 14f, far, 6f, 50f)
        for (far in listOf(52f, 52f, 53f)) scribble(zones, 1, 17f, far, 6f, 50f)
        assertFalse(next.isEnabled)
        scribble(zones, -1, 14f, 47f, 6f, 50f)
        assertTrue(next.isEnabled)
        scribble(zones, 1, 30f, 31f, 6f, 50f)
        val status = views.filterIsInstance<TextView>().filter { it !is Button }.joinToString("\n") { it.text.toString() }
        assertTrue(status, status.contains(activity.getString(R.string.reach_invalid)))
        assertTrue(next.isEnabled)
    }
}
