package io.github.patissiermongs.foldkey.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.ScreenProfile.COVER_LANDSCAPE
import io.github.patissiermongs.foldkey.ime.ScreenProfile.COVER_PORTRAIT
import io.github.patissiermongs.foldkey.ime.ScreenProfile.INNER_LANDSCAPE
import io.github.patissiermongs.foldkey.ime.ScreenProfile.INNER_PORTRAIT
import io.github.patissiermongs.foldkey.input.TypingCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenProfileTest {
    private fun prefs(): Prefs = Prefs(ApplicationProvider.getApplicationContext<Context>()).also { it.sp.edit().clear().commit() }

    private fun pxPerMm(ppi: Float) = ppi / 25.4f

    @Test
    fun coverAndInnerScreensAreToldApartByTheShortSide() {
        assertFalse(ScreenProfile.isCover(1968, 2184, pxPerMm(368f), pxPerMm(368f)))
        assertFalse(ScreenProfile.isCover(2184, 1968, pxPerMm(368f), pxPerMm(368f)))
        assertTrue(ScreenProfile.isCover(1080, 2520, pxPerMm(422f), pxPerMm(422f)))
        assertTrue(ScreenProfile.isCover(2520, 1080, pxPerMm(422f), pxPerMm(422f)))
        assertFalse(ScreenProfile.isCover(1812, 2176, pxPerMm(374f), pxPerMm(374f)))
        assertTrue(ScreenProfile.isCover(904, 2316, pxPerMm(402f), pxPerMm(402f)))
        assertEquals(COVER_LANDSCAPE, ScreenProfile.of(cover = true, landscape = true))
        assertEquals(INNER_PORTRAIT, ScreenProfile.of(cover = false, landscape = false))
    }

    @Test
    fun withNothingStoredEveryScreenStartsFromTheSharedDefaults() {
        val p = prefs()
        for (s in ScreenProfile.entries) {
            assertEquals(9.5f, p.rowHeightMm(s), 1e-4f)
            assertEquals(8.5f, p.keyWidthMm(s, split = true)!!, 1e-4f)
            assertNull(p.keyWidthMm(s, split = false))
        }
    }

    @Test
    fun sizesSavedBeforePerScreenSizesStayUntilAScreenGetsItsOwn() {
        val p = prefs()
        p.sp.edit()
            .putInt(Prefs.ROW_HEIGHT, 100)
            .putInt(Prefs.SPLIT_UNIT, 70)
            .putInt(Prefs.rowKey(COVER_PORTRAIT), 80)
            .putInt(Prefs.widthKey(INNER_LANDSCAPE), 90)
            .commit()
        assertEquals(8f, p.rowHeightMm(COVER_PORTRAIT), 1e-4f)
        for (s in ScreenProfile.entries - COVER_PORTRAIT) assertEquals(10f, p.rowHeightMm(s), 1e-4f)
        assertEquals(9f, p.keyWidthMm(INNER_LANDSCAPE, split = true)!!, 1e-4f)
        assertEquals(9f, p.keyWidthMm(INNER_LANDSCAPE, split = false)!!, 1e-4f)
        for (s in ScreenProfile.entries - INNER_LANDSCAPE) {
            assertEquals(7f, p.keyWidthMm(s, split = true)!!, 1e-4f)
            assertNull(p.keyWidthMm(s, split = false))
        }
        p.sp.edit().putInt(Prefs.widthKey(INNER_PORTRAIT), Prefs.KEY_WIDTH_FILL).commit()
        assertNull(p.keyWidthMm(INNER_PORTRAIT, split = true))
    }

    @Test
    fun onlyTheCoverPortraitScreenNeverSplits() {
        val p = prefs()
        assertFalse(p.usesSplit(INNER_PORTRAIT))
        assertTrue(p.usesSplit(INNER_LANDSCAPE))
        assertFalse(p.usesSplit(COVER_PORTRAIT))
        assertTrue(p.usesSplit(COVER_LANDSCAPE))
        p.sp.edit().putBoolean(Prefs.SPLIT_PORTRAIT, true).putBoolean(Prefs.SPLIT_LANDSCAPE, false).commit()
        assertTrue(p.usesSplit(INNER_PORTRAIT))
        assertFalse(p.usesSplit(INNER_LANDSCAPE))
        assertFalse(p.usesSplit(COVER_PORTRAIT))
        assertFalse(p.usesSplit(COVER_LANDSCAPE))
    }

    @Test
    fun calibrationWritesOnlyTheScreenItRanOn() {
        val p = prefs()
        val placement = TypingCalibration.Placement(unitMm = 7.2f, rowMm = 8.8f, leftMm = 3f, rightMm = 4f, liftMm = 2f)
        p.applyCalibration(mapOf(Layer.CODE to placement), mapOf("split_ansi_150mm" to "0,0,1"), INNER_LANDSCAPE)
        assertEquals(72, p.sp.getInt(Prefs.widthKey(INNER_LANDSCAPE), 0))
        assertEquals(88, p.sp.getInt(Prefs.rowKey(INNER_LANDSCAPE), 0))
        for (s in ScreenProfile.entries - INNER_LANDSCAPE) {
            assertFalse(p.sp.contains(Prefs.widthKey(s)))
            assertFalse(p.sp.contains(Prefs.rowKey(s)))
        }
        assertFalse(p.sp.contains(Prefs.SPLIT_UNIT))
        assertFalse(p.sp.contains(Prefs.ROW_HEIGHT))
        assertEquals(30, p.sp.getInt(Prefs.CODE_SIDE_LEFT, 0))
        assertEquals(40, p.sp.getInt(Prefs.CODE_SIDE_RIGHT, 0))
        assertEquals(20, p.sp.getInt(Prefs.SPLIT_LIFT, 0))
        assertEquals(INNER_LANDSCAPE, p.slotProfile("split_ansi_150mm"))
        assertNull(p.slotProfile("split_ansi_120mm"))
        p.rememberSlotProfile("split_ansi_150mm", COVER_LANDSCAPE)
        assertEquals(COVER_LANDSCAPE, p.slotProfile("split_ansi_150mm"))
    }

    @Test
    fun heatSavedBeforePerScreenSizesStaysReadableAndOnlyItsOwnScreenResetsIt() {
        val p = prefs()
        p.sp.edit().putInt(Prefs.SPLIT_UNIT, 70).putInt(Prefs.ROW_HEIGHT, 100).commit()
        for (s in ScreenProfile.entries) assertEquals("70,-1,-1,-1,-1,-1,-1,100", p.heatSignature(s))
        val before = ScreenProfile.entries.associateWith { p.heatSignature(it) }
        p.sp.edit().putInt(Prefs.rowKey(COVER_PORTRAIT), 80).commit()
        assertNotEquals(before[COVER_PORTRAIT], p.heatSignature(COVER_PORTRAIT))
        for (s in ScreenProfile.entries - COVER_PORTRAIT) assertEquals(before[s], p.heatSignature(s))
        val cover = p.heatSignature(COVER_PORTRAIT)
        p.sp.edit().putInt(Prefs.widthKey(COVER_PORTRAIT), 50).commit()
        assertNotEquals(cover, p.heatSignature(COVER_PORTRAIT))
        val all = ScreenProfile.entries.associateWith { p.heatSignature(it) }
        p.sp.edit().putInt(Prefs.SPLIT_LIFT, 20).commit()
        for (s in ScreenProfile.entries) assertNotEquals(all[s], p.heatSignature(s))
    }
}
