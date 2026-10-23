package io.github.patissiermongs.foldkey.settings

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.TapHeat
import io.github.patissiermongs.foldkey.layout.Layouts
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeatmapActivityTest {
    private val code = "split_ansi_150mm"
    private val general = "split_general_150mm"
    private val pattern = floatArrayOf(-1.5f, -0.75f, 0f, 0.75f, 1.5f)

    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun texts(a: Activity) = all(a.window.decorView).filterIsInstance<TextView>().filter { it !is Button }.joinToString("\n") { it.text.toString() }

    private fun button(a: Activity, id: Int) = all(a.window.decorView).filterIsInstance<Button>().single { it.text == a.getString(id) }

    private fun letters(left: Boolean) = Layouts.split.flatMap { if (left) it.left.keys else it.right.keys }
        .mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }

    private fun heat(signature: String, perKey: Int, left: Pair<Float, Float>, right: Pair<Float, Float>): String {
        val heat = TapHeat(signature)
        for ((keys, mean) in listOf(letters(true) to left, letters(false) to right)) {
            for (c in keys) for (i in 0 until perKey) {
                heat.add(c, mean.first + pattern[i % 5], mean.second + pattern[(i / 5) % 5], 9f, 9f)
            }
        }
        return heat.serialize()
    }

    private fun zones(left: Float, right: Float, y: Float) = (0 until 20).joinToString(";") { z -> "${if (z % 4 < 2) left else right},$y,20" }

    private fun prefs(): Prefs {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.resources.displayMetrics.apply { xdpi = 368f; ydpi = 368f }
        return Prefs(ctx)
    }

    private fun render(a: Activity, name: String) {
        val root = a.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun suggestionMovesTheHalvesAndTheLearnedOffsetsTogether() {
        val prefs = prefs()
        prefs.sp.edit().clear().putInt(Prefs.SPLIT_LIFT, 30).putInt(Prefs.CODE_SIDE_LEFT, 50).putInt(Prefs.CODE_SIDE_RIGHT, 60).commit()
        prefs.sp.edit()
            .putString(Prefs.HEAT_PREFIX + code, heat(prefs.layoutSignature, 25, 0.8f to 0.6f, -0.5f to 0.6f))
            .putString(Prefs.HEAT_PREFIX + "full_150mm", heat("stale", 25, 0f to 0f, 0f to 0f))
            .putString(Prefs.OFFSETS_PREFIX + code, zones(0.8f, -0.5f, 0.6f))
            .putString(Prefs.OFFSETS_PREFIX + general, zones(0.3f, 0.3f, 0.6f))
            .commit()
        val epoch = prefs.offsetsEpoch
        val activity = Robolectric.buildActivity(HeatmapActivity::class.java).setup().get()
        val slots = all(activity.window.decorView).filterIsInstance<Button>().filter { it.text.startsWith("Split") }
        assertEquals(listOf("Split · Code layer · 150 mm wide"), slots.map { it.text.toString() })
        val text = texts(activity)
        assertTrue(text, text.contains("650 taps"))
        assertTrue(text, text.contains("Left thumb: 350 taps, x +0.8 mm, y +0.6 mm"))
        assertTrue(text, text.contains("Right thumb: 300 taps, x -0.5 mm, y +0.6 mm"))
        assertTrue(text, text.contains("Move the left half 0.8 mm right (left side distance 5.0 → 5.8 mm)"))
        assertTrue(text, text.contains("Move the right half 0.5 mm left (right side distance 6.0 → 6.5 mm)"))
        assertTrue(text, text.contains("Lower both halves 0.6 mm (raise 3.0 → 2.4 mm, shared by both layers)"))
        render(activity, "heatmap.png")
        val apply = button(activity, R.string.heat_apply)
        assertTrue(apply.isEnabled)
        apply.performClick()
        assertEquals(58, prefs.sp.getInt(Prefs.CODE_SIDE_LEFT, 0))
        assertEquals(65, prefs.sp.getInt(Prefs.CODE_SIDE_RIGHT, 0))
        assertEquals(24, prefs.sp.getInt(Prefs.SPLIT_LIFT, 0))
        assertFalse(prefs.sp.contains(Prefs.GENERAL_SIDE_LEFT))
        assertEquals(epoch + 1, prefs.offsetsEpoch)
        assertNull(prefs.heat(code))
        val codeModel = OffsetModel(20).also { it.load(prefs.offsets(code)) }
        for (z in 0 until 20) {
            assertEquals("zone $z", 0f, codeModel.meanMm(z).first, 0.02f)
            assertEquals("zone $z", 0f, codeModel.meanMm(z).second, 0.02f)
            assertEquals(20, codeModel.samples(z))
        }
        val generalModel = OffsetModel(20).also { it.load(prefs.offsets(general)) }
        for (z in 0 until 20) {
            assertEquals("zone $z", 0f, generalModel.meanMm(z).second, 0.02f)
            assertTrue("zone $z", abs(generalModel.meanMm(z).first - 0.3f) < 0.5f)
        }
    }

    @Test
    fun fewTapsAskForMoreAndTheHeatmapCanBeCleared() {
        val prefs = prefs()
        prefs.sp.edit().clear().commit()
        prefs.sp.edit().putString(Prefs.HEAT_PREFIX + code, heat(prefs.layoutSignature, 4, 1.5f to 1.5f, -1.5f to 1.5f)).commit()
        val epoch = prefs.offsetsEpoch
        val activity = Robolectric.buildActivity(HeatmapActivity::class.java).setup().get()
        val text = texts(activity)
        assertTrue(text, text.contains("A suggestion needs at least 150 taps per thumb (now left 56, right 48)."))
        assertFalse(button(activity, R.string.heat_apply).isEnabled)
        val reset = button(activity, R.string.heat_reset)
        assertTrue(reset.isEnabled)
        reset.performClick()
        assertNull(prefs.heat(code))
        assertEquals(epoch + 1, prefs.offsetsEpoch)
    }

    @Test
    fun smallOffsetsNeedNoMove() {
        val prefs = prefs()
        prefs.sp.edit().clear().commit()
        prefs.sp.edit().putString(Prefs.HEAT_PREFIX + code, heat(prefs.layoutSignature, 25, 0.2f to -0.1f, -0.2f to 0.1f)).commit()
        val activity = Robolectric.buildActivity(HeatmapActivity::class.java).setup().get()
        assertTrue(texts(activity), texts(activity).contains(activity.getString(R.string.heat_keep)))
        assertFalse(button(activity, R.string.heat_apply).isEnabled)
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-night-420dpi")
    fun rendersInTheDarkTheme() {
        val prefs = prefs()
        prefs.sp.edit().clear().commit()
        prefs.sp.edit().putString(Prefs.HEAT_PREFIX + code, heat(prefs.layoutSignature, 25, 0.8f to 0.6f, -0.5f to 0.6f)).commit()
        val activity = Robolectric.buildActivity(HeatmapActivity::class.java).setup().get()
        render(activity, "heatmap_dark.png")
        val text = texts(activity)
        assertTrue(text, text.contains("650 taps"))
        assertTrue(text, text.contains("Move the left half 0.8 mm right (left side distance 0.5 → 1.3 mm)"))
        assertTrue(text, text.contains(activity.getString(R.string.heat_limit_lift)))
        assertTrue(button(activity, R.string.heat_apply).isEnabled)
    }
}
