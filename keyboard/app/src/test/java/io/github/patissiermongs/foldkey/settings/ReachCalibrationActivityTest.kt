package io.github.patissiermongs.foldkey.settings

import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w900dp-h480dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReachCalibrationActivityTest {
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun stroke(view: View, side: Int, reachMm: Float) {
        val dm = view.resources.displayMetrics
        val px = Dpi.physical(dm, true) / 25.4f
        val py = Dpi.physical(dm, false) / 25.4f
        val bottom = view.height.toFloat()
        val points = (0..4).flatMap { r ->
            val yMm = (4 - r) * 9.5f + 4.75f
            listOf(yMm - 3f, yMm, yMm + 3f).map { y ->
                val x = if (side < 0) reachMm * px else view.width - reachMm * px
                x to bottom - y * py
            }
        }
        val t = SystemClock.uptimeMillis()
        points.forEachIndexed { i, (x, y) ->
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                points.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val e = MotionEvent.obtain(t, t + i * 16L, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    @Test
    fun threeConsistentStrokesPerThumbSetTheSplitKeyWidth() {
        val controller = Robolectric.buildActivity(ReachCalibrationActivity::class.java).setup()
        val activity = controller.get()
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().commit()
        val views = all(activity.window.decorView)
        val reach = views.first { it.javaClass.simpleName == "ReachView" }
        val apply = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.reach_apply) }
        assertTrue(reach.width > 0)
        assertFalse(apply.isEnabled)
        for (mm in listOf(60f, 61f, 62f)) stroke(reach, -1, mm)
        for (mm in listOf(69f, 70f, 68f)) stroke(reach, 1, mm)
        val status = views.filterIsInstance<TextView>().filter { it !is Button }.map { it.text.toString() }
        assertTrue(status.joinToString("\n"), status.any { "8.3" in it })
        assertTrue(apply.isEnabled)
        val bmp = Bitmap.createBitmap(reach.rootView.width, reach.rootView.height, Bitmap.Config.ARGB_8888)
        reach.rootView.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        FileOutputStream(File(dir, "reach_calibration.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        apply.performClick()
        assertEquals(83, prefs.sp.getInt(Prefs.SPLIT_UNIT, 0))
    }

    @Test
    fun strokesThatDisagreeKeepApplyDisabled() {
        val activity = Robolectric.buildActivity(ReachCalibrationActivity::class.java).setup().get()
        val views = all(activity.window.decorView)
        val reach = views.first { it.javaClass.simpleName == "ReachView" }
        val apply = views.filterIsInstance<Button>().first { it.text == activity.getString(R.string.reach_apply) }
        for (mm in listOf(50f, 61f, 62f)) stroke(reach, -1, mm)
        for (mm in listOf(66f, 67f, 65f)) stroke(reach, 1, mm)
        assertFalse(apply.isEnabled)
        stroke(reach, -1, 60f)
        assertTrue(apply.isEnabled)
    }
}
