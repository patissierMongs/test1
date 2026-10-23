package io.github.patissiermongs.foldkey.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.LayoutKind
import java.io.File
import java.io.FileOutputStream
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
class OffsetMapActivityTest {
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun zones(vararg filled: Pair<Int, String>): String = (0 until 20).joinToString(";") { z ->
        filled.firstOrNull { it.first == z }?.second ?: "0.0,0.0,0"
    }

    @Test
    fun slotNamesAreParsed() {
        assertEquals(OffsetSlot("split_ansi_150mm", LayoutKind.SPLIT, Layer.CODE, 150), OffsetSlot.parse("split_ansi_150mm"))
        assertEquals(Layer.GENERAL, OffsetSlot.parse("split_general_135mm")!!.layer)
        assertEquals(LayoutKind.FULL, OffsetSlot.parse("full_135mm")!!.kind)
        assertEquals(LayoutKind.COMPACT, OffsetSlot.parse("compact_general_64mm")!!.kind)
        assertNull(OffsetSlot.parse("split_150"))
        assertNull(OffsetSlot.parse("split_ansi_mm"))
    }

    @Test
    fun learnedOffsetsAreDrawnForEachStoredLayout() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.resources.displayMetrics.apply { xdpi = 368f; ydpi = 368f }
        Prefs(ctx).sp.edit().clear()
            .putString(Prefs.OFFSETS_PREFIX + "split_ansi_150mm", zones(4 to "0.8,1.2,30", 6 to "-0.5,0.9,12"))
            .putString(Prefs.OFFSETS_PREFIX + "split_general_150mm", zones(9 to "0.3,-0.6,8"))
            .commit()
        val activity = Robolectric.buildActivity(OffsetMapActivity::class.java).setup().get()
        val views = all(activity.window.decorView)
        val slots = views.filterIsInstance<Button>()
        assertEquals(listOf("Split · Code layer · 150 mm wide", "Split · Text layer · 150 mm wide"), slots.map { it.text.toString() })
        assertFalse(slots[0].isEnabled)
        val texts = { all(activity.window.decorView).filterIsInstance<TextView>().filter { it !is Button }.joinToString("\n") { it.text.toString() } }
        assertTrue(texts(), texts().contains("42 samples · mean offset from key centres: x +0.4 mm (+ is right), y +1.1 mm (+ is down)"))
        val map = views.first { it.javaClass.simpleName == "OffsetMapView" }
        assertTrue(map.height > 0)
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        FileOutputStream(File(dir, "offset_map.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        slots[1].performClick()
        assertTrue(texts(), texts().contains("8 samples · mean offset from key centres: x +0.3 mm (+ is right), y -0.6 mm (+ is down)"))
    }

    @Test
    fun emptyStateIsExplained() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).sp.edit().clear().commit()
        val activity = Robolectric.buildActivity(OffsetMapActivity::class.java).setup().get()
        val texts = all(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.contains(activity.getString(R.string.offsets_empty)))
        assertTrue(all(activity.window.decorView).filterIsInstance<Button>().isEmpty())
    }
}
