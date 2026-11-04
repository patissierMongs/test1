package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.content.SharedPreferences
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.EngineSettings
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile.COVER_LANDSCAPE
import io.github.patissiermongs.foldkey.ime.ScreenProfile.COVER_PORTRAIT
import io.github.patissiermongs.foldkey.ime.ScreenProfile.INNER_LANDSCAPE
import io.github.patissiermongs.foldkey.ime.ScreenProfile.INNER_PORTRAIT
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import org.junit.Assert.assertEquals
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
class ScreenSizeTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var view: KeyboardView
    private lateinit var prefs: Prefs
    private var pxPerMm = 0f
    private val gap = KeyboardView.GAP_MM

    private fun setup(ppi: Float, layer: Layer = Layer.CODE, values: (SharedPreferences.Editor) -> SharedPreferences.Editor) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = ppi
        dm.ydpi = ppi
        pxPerMm = ppi / 25.4f
        prefs = Prefs(activity)
        values(prefs.sp.edit().clear()).commit()
        val engine = KeyboardEngine(FakeEditor(), RecordingListener())
        engine.settings = EngineSettings(layer = layer)
        engine.startInput(EditorContext())
        assertEquals(layer, engine.layer)
        view = KeyboardView(activity, engine, prefs, Silent)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
        assertTrue(view.layoutKeys.isNotEmpty())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun reload(values: (SharedPreferences.Editor) -> SharedPreferences.Editor) {
        values(prefs.sp.edit()).commit()
        view.reload()
        idle()
    }

    private fun mm(px: Float) = px / pxPerMm

    private fun face(c: Char): Box = view.layoutKeys.filter { !it.ghost && (it.def.action as? KeyAction.Char)?.base == c }.minBy { it.face.left }.face

    private fun margins(row: Int): Pair<Float, Float> {
        val keys = view.layoutKeys.filter { it.row == row && !it.ghost }
        val half = gap / 2f * pxPerMm
        return mm(keys.minOf { it.face.left } - half) to mm(view.width - keys.maxOf { it.face.right } - half)
    }

    private fun assertCentered(rows: Iterable<Int>, units: Float, keyMm: Float) {
        val side = (mm(view.width.toFloat()) - units * keyMm) / 2f
        for (r in rows) {
            val (left, right) = margins(r)
            assertEquals("row $r left", side, left, 0.1f)
            assertEquals("row $r right", side, right, 0.1f)
        }
    }

    @Test
    fun theUnfoldedPortraitScreenUsesItsOwnRowHeightAndKeyWidth() {
        setup(368f) {
            it.putBoolean(Prefs.SPLIT_PORTRAIT, true)
                .putInt(Prefs.ROW_HEIGHT, 130).putInt(Prefs.SPLIT_UNIT, 80)
                .putInt(Prefs.rowKey(INNER_PORTRAIT), 110).putInt(Prefs.widthKey(INNER_PORTRAIT), 60)
                .putInt(Prefs.rowKey(INNER_LANDSCAPE), 70).putInt(Prefs.widthKey(INNER_LANDSCAPE), 100)
                .putInt(Prefs.rowKey(COVER_PORTRAIT), 80).putInt(Prefs.widthKey(COVER_PORTRAIT), 50)
        }
        assertEquals(INNER_PORTRAIT, view.profile())
        assertEquals(LayoutKind.SPLIT, view.layoutKind(view.width))
        assertEquals(11f - gap, mm(face('a').height), 0.05f)
        assertEquals(6f - gap, mm(face('q').width), 0.05f)
    }

    @Test
    fun sizesSavedBeforePerScreenSizesApplyUntilThisScreenGetsItsOwn() {
        setup(368f) { it.putBoolean(Prefs.SPLIT_PORTRAIT, true).putInt(Prefs.ROW_HEIGHT, 100).putInt(Prefs.SPLIT_UNIT, 70) }
        assertEquals(10f - gap, mm(face('a').height), 0.05f)
        assertEquals(7f - gap, mm(face('q').width), 0.05f)
        reload { it.putInt(Prefs.rowKey(INNER_LANDSCAPE), 120).putInt(Prefs.widthKey(COVER_LANDSCAPE), 60) }
        assertEquals(10f - gap, mm(face('a').height), 0.05f)
        assertEquals(7f - gap, mm(face('q').width), 0.05f)
        reload { it.putInt(Prefs.rowKey(INNER_PORTRAIT), 120).putInt(Prefs.widthKey(INNER_PORTRAIT), 80) }
        assertEquals(12f - gap, mm(face('a').height), 0.05f)
        assertEquals(8f - gap, mm(face('q').width), 0.05f)
    }

    @Test
    fun theFullLayoutFillsTheWidthUntilAKeyWidthIsSetAndThenStaysCentered() {
        setup(368f) { it.putBoolean(Prefs.SPLIT_PORTRAIT, false).putInt(Prefs.SPLIT_UNIT, 60) }
        assertEquals(LayoutKind.FULL, view.layoutKind(view.width))
        for (r in 1..4) {
            val (left, right) = margins(r)
            assertEquals(KeyboardView.SIDE_MM, left, 0.05f)
            assertEquals(KeyboardView.SIDE_MM, right, 0.05f)
        }
        reload { it.putInt(Prefs.widthKey(INNER_PORTRAIT), 70) }
        assertEquals(7f - gap, mm(face('q').width), 0.05f)
        assertCentered(1..4, Layouts.codeUnits(LayoutKind.FULL), 7f)
        val pad = view.fnLayoutKeys.filter { it.pad }
        assertEquals(view.layoutKeys.filter { !it.ghost }.maxOf { it.face.right }, pad.maxOf { it.face.right }, 1f)
        assertEquals(view.width.toFloat(), pad.maxOf { it.touch.right }, 0.5f)
        reload { it.putInt(Prefs.widthKey(INNER_PORTRAIT), Prefs.KEY_WIDTH_FILL) }
        assertEquals(KeyboardView.SIDE_MM, margins(1).first, 0.05f)
    }

    @Test
    fun theTextLayerKeepsTheWidthOfTheCodeLayer() {
        setup(368f, Layer.GENERAL) { it.putBoolean(Prefs.SPLIT_PORTRAIT, false).putInt(Prefs.widthKey(INNER_PORTRAIT), 70) }
        assertTrue(view.layoutKeys.none { it.def.action == KeyAction.EscCtrl })
        assertCentered(listOf(0, 1, 3, 4), 10f, Layouts.codeUnits(LayoutKind.FULL) * 7f / 10f)
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi")
    fun theUnfoldedLandscapeScreenUsesItsOwnSizeAndRemembersItForTheSlot() {
        setup(368f) {
            it.putInt(Prefs.rowKey(INNER_PORTRAIT), 120).putInt(Prefs.widthKey(INNER_PORTRAIT), 60)
                .putInt(Prefs.rowKey(INNER_LANDSCAPE), 80).putInt(Prefs.widthKey(INNER_LANDSCAPE), 75)
        }
        assertEquals(INNER_LANDSCAPE, view.profile())
        assertEquals(LayoutKind.SPLIT, view.layoutKind(view.width))
        assertEquals(8f - gap, mm(face('a').height), 0.05f)
        assertEquals(7.5f - gap, mm(face('q').width), 0.05f)
        assertEquals(INNER_LANDSCAPE, prefs.slotProfile(KeyboardView.offsetSlot(LayoutKind.SPLIT, Layer.CODE, view.width.toFloat(), pxPerMm)))
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp-420dpi")
    fun theCoverScreenKeepsItsOwnSizeApartFromTheUnfoldedScreen() {
        setup(422f) {
            it.putInt(Prefs.rowKey(INNER_PORTRAIT), 120).putInt(Prefs.widthKey(INNER_PORTRAIT), 90)
                .putInt(Prefs.rowKey(COVER_PORTRAIT), 80).putInt(Prefs.widthKey(COVER_PORTRAIT), 50)
        }
        assertEquals(COVER_PORTRAIT, view.profile())
        assertEquals(LayoutKind.COMPACT, view.layoutKind(view.width))
        assertEquals(8f - gap, mm(face('a').height), 0.05f)
        assertEquals(5f - gap, mm(face('q').width), 0.05f)
        assertCentered(0..5, Layouts.codeUnits(LayoutKind.COMPACT), 5f)
    }

    @Test
    @Config(qualifiers = "w900dp-h411dp-land-420dpi")
    fun theCoverScreenInLandscapeHasItsOwnProfile() {
        setup(422f) {
            it.putInt(Prefs.rowKey(COVER_PORTRAIT), 120).putInt(Prefs.widthKey(COVER_PORTRAIT), 50)
                .putInt(Prefs.rowKey(COVER_LANDSCAPE), 60).putInt(Prefs.widthKey(COVER_LANDSCAPE), 65)
        }
        assertEquals(COVER_LANDSCAPE, view.profile())
        assertEquals(LayoutKind.SPLIT, view.layoutKind(view.width))
        assertEquals(6f - gap, mm(face('a').height), 0.05f)
        assertEquals(6.5f - gap, mm(face('q').width), 0.05f)
    }
}
