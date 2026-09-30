package io.github.patissiermongs.foldkey.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OffsetModelTest {
    @Test
    fun convergesToSystematicOffsetUnderNoise() {
        val m = OffsetModel(4)
        val rnd = Random(7)
        repeat(400) {
            m.add(1, 1.2f + (rnd.nextFloat() - 0.5f) * 2f, -0.8f + (rnd.nextFloat() - 0.5f) * 2f, 9f, 9f)
        }
        val (cx, cy) = m.correctionMm(1, 9f, 9f)
        assertEquals(1.2f, cx, 0.35f)
        assertEquals(-0.8f, cy, 0.35f)
        assertEquals(0f to 0f, m.correctionMm(0, 9f, 9f))
    }

    @Test
    fun warmupScalesEarlyCorrection() {
        val m = OffsetModel(1)
        m.add(0, 2f, 0f, 9f, 9f)
        val (cx, _) = m.correctionMm(0, 9f, 9f)
        assertEquals(2f / m.warmup, cx, 1e-4f)
    }

    @Test
    fun outliersAreRejectedAndCorrectionIsClamped() {
        val m = OffsetModel(1)
        assertFalse(m.add(0, 5f, 0f, 9f, 9f))
        repeat(100) { assertTrue(m.add(0, 4f, 0f, 9f, 9f)) }
        val (cx, _) = m.correctionMm(0, 9f, 9f)
        assertEquals(9f * m.maxShare, cx, 1e-4f)
    }

    @Test
    fun serializationRoundTrip() {
        val m = OffsetModel(3)
        repeat(30) { m.add(2, 0.5f, -0.25f, 9f, 9f) }
        val copy = OffsetModel(3)
        copy.load(m.serialize())
        assertEquals(m.serialize(), copy.serialize())
        copy.load("garbage")
        assertEquals(0, copy.samples(2))
    }
}
