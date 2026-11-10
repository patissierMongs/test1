package io.github.patissiermongs.foldkey.ui

import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DpiTest {
    private fun metrics(xdpi: Float, ydpi: Float, density: Int) = DisplayMetrics().apply {
        this.xdpi = xdpi
        this.ydpi = ydpi
        densityDpi = density
    }

    @Test
    fun theReportedPanelDensityIsUsedAtTheDefaultDensity() {
        val dm = metrics(368f, 367f, 420)
        assertEquals(368f, Dpi.physical(dm, horizontal = true), 0f)
        assertEquals(367f, Dpi.physical(dm, horizontal = false), 0f)
    }

    @Test
    fun aSmallerDisplaySizeSettingDoesNotShrinkTheKeyboard() {
        for (density in listOf(340, 300, 272, 260, 230, 200, 160)) {
            val dm = metrics(368f, 368f, density)
            assertEquals("density $density", 368f, Dpi.physical(dm, horizontal = true), 0f)
            assertEquals("density $density", 368f, Dpi.physical(dm, horizontal = false), 0f)
        }
    }

    @Test
    fun aLargerDisplaySizeSettingDoesNotEnlargeTheKeyboard() {
        for (density in listOf(480, 540, 640, 800)) {
            val dm = metrics(368f, 368f, density)
            assertEquals("density $density", 368f, Dpi.physical(dm, horizontal = true), 0f)
        }
    }

    @Test
    fun valuesThatCannotDescribeAPanelFallBackToTheSystemDensity() {
        val cases = listOf(
            "none" to (0f to 0f),
            "not a number" to (Float.NaN to Float.NaN),
            "negative" to (-368f to -368f),
            "far too low" to (40f to 40f),
            "far too high" to (2400f to 2400f),
            "axes disagree" to (368f to 160f),
            "only one axis" to (368f to 0f),
        )
        for ((name, pair) in cases) {
            val dm = metrics(pair.first, pair.second, 420)
            assertEquals(name, 420f, Dpi.physical(dm, horizontal = true), 0f)
            assertEquals(name, 420f, Dpi.physical(dm, horizontal = false), 0f)
        }
    }

    @Test
    fun axesThatDifferByAFewPercentKeepTheirOwnValues() {
        val dm = metrics(403.4f, 404.6f, 420)
        assertEquals(403.4f, Dpi.physical(dm, horizontal = true), 0f)
        assertEquals(404.6f, Dpi.physical(dm, horizontal = false), 0f)
    }
}
