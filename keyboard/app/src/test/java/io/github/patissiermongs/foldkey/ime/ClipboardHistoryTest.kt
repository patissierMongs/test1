package io.github.patissiermongs.foldkey.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryTest {
    @Test
    fun newestFirstWithoutDuplicates() {
        val h = ClipboardHistory(capacity = 3)
        assertTrue(h.add("a", 0))
        assertTrue(h.add("b", 1))
        assertTrue(h.add("a", 2))
        assertFalse(h.add("a", 3))
        assertEquals(listOf("a", "b"), h.items(4).map { it.text })
    }

    @Test
    fun capacityDropsTheOldest() {
        val h = ClipboardHistory(capacity = 2)
        h.add("a", 0)
        h.add("b", 1)
        h.add("c", 2)
        assertEquals(listOf("c", "b"), h.items(3).map { it.text })
    }

    @Test
    fun entriesExpireAndRecopyRefreshesTheTime() {
        val h = ClipboardHistory(ttlMs = 100)
        h.add("a", 0)
        h.add("b", 50)
        h.add("a", 90)
        assertEquals(listOf("a"), h.items(160).map { it.text })
        assertEquals(emptyList<String>(), h.items(200).map { it.text })
    }

    @Test
    fun blankAndOversizedClipsAreSkipped() {
        val h = ClipboardHistory(maxChars = 5)
        assertFalse(h.add("  \n", 0))
        assertFalse(h.add("123456", 0))
        assertTrue(h.add("12345", 0))
        assertEquals(listOf("12345"), h.items(1).map { it.text })
    }
}
