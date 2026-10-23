package io.github.patissiermongs.foldkey.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapHeatTest {
    @Test
    fun tapsAreSummedPerKeyWithoutTheirOrder() {
        val heat = TapHeat("sig")
        for ((dx, dy) in listOf(1f to 0.5f, 2f to -0.5f, 0f to 1.5f, 1f to 0.5f)) assertTrue(heat.add('f', dx, dy, 4f, 4.5f))
        assertTrue(heat.add(';', -0.5f, 0f, 3f, 4.5f))
        val f = heat.cloud('f')!!
        assertEquals(4, f.n)
        assertEquals(1f, f.meanXMm, 1e-5f)
        assertEquals(0.5f, f.meanYMm, 1e-5f)
        assertEquals(0.5f, f.varXMm2, 1e-5f)
        assertEquals(0.5f, f.varYMm2, 1e-5f)
        assertEquals(-0.5f, f.covMm2, 1e-5f)
        assertEquals(setOf('f', ';'), heat.keys)
        assertEquals(5, heat.taps)
    }

    @Test
    fun tapsOutsideTheKeyCellAreLeftOut() {
        val heat = TapHeat()
        assertFalse(heat.add('a', 4.1f, 0f, 4f, 4.5f))
        assertFalse(heat.add('a', 0f, -4.6f, 4f, 4.5f))
        assertTrue(heat.add('a', -4f, 4.5f, 4f, 4.5f))
        assertEquals(1, heat.taps)
    }

    @Test
    fun savedHeatComesBackOnlyForTheSameLayout() {
        val heat = TapHeat("85,39,5,5,-1,-1,0,95")
        heat.add('q', 0.25f, -1f, 4f, 4f)
        heat.add('q', 0.75f, 1f, 4f, 4f)
        heat.add(',', -0.5f, 0.125f, 4f, 4f)
        heat.add('|', 0f, 0f, 4f, 4f)
        val data = heat.serialize()
        val back = TapHeat.parse(data, "85,39,5,5,-1,-1,0,95")
        assertEquals(heat.keys, back.keys)
        for (c in heat.keys) {
            val a = heat.cloud(c)!!
            val b = back.cloud(c)!!
            assertEquals(a.n, b.n)
            assertEquals(a.meanXMm, b.meanXMm, 1e-6f)
            assertEquals(a.varYMm2, b.varYMm2, 1e-6f)
            assertEquals(a.covMm2, b.covMm2, 1e-6f)
        }
        val other = TapHeat.parse(data, "86,39,5,5,-1,-1,0,95")
        assertEquals(0, other.taps)
        assertEquals("86,39,5,5,-1,-1,0,95", other.signature)
        assertEquals(0, TapHeat.parse(null, "s").taps)
        assertEquals(0, TapHeat.parse("no separator", "s").taps)
    }

    @Test
    fun damagedEntriesAreSkipped() {
        val heat = TapHeat.parse("s|102:2,1.0,0.0,1.0,0.0,0.0;x:1,0,0,0,0,0;106:3,NaN,0,0,0,0;107:0,0,0,0,0,0;108:1,1,1,1,1;59:1,0.5,0,0.25,0,0", "s")
        assertEquals(setOf('f', ';'), heat.keys)
        assertEquals(0.5f, heat.cloud('f')!!.meanXMm, 1e-6f)
        assertNull(heat.cloud('j'))
    }
}
