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

    @Test
    fun pinnedClipsComeFirstAndDoNotExpire() {
        val h = ClipboardHistory(ttlMs = 100)
        h.add("ls -la", 0)
        h.add("git status", 10)
        assertTrue(h.pin("ls -la"))
        assertFalse(h.pin("ls -la"))
        assertFalse(h.add("ls -la", 20))
        assertEquals(listOf("ls -la" to true, "git status" to false), h.items(30).map { it.text to it.pinned })
        assertEquals(listOf("ls -la"), h.items(500).map { it.text })
    }

    @Test
    fun unpinnedClipGoesBackToTheTopOfRecent() {
        val h = ClipboardHistory()
        h.add("a", 0)
        h.add("b", 1)
        h.pin("a")
        assertTrue(h.unpin("a", 2))
        assertFalse(h.unpin("a", 3))
        assertEquals(listOf("a", "b"), h.items(4).map { it.text })
        assertEquals(emptyList<String>(), h.pinned)
    }

    @Test
    fun removeDropsPinnedAndRecentCopies() {
        val h = ClipboardHistory()
        h.add("a", 0)
        h.add("b", 1)
        h.pin("b")
        assertTrue(h.remove("b"))
        assertTrue(h.remove("a"))
        assertFalse(h.remove("c"))
        assertEquals(emptyList<String>(), h.items(2).map { it.text })
    }

    @Test
    fun pinLimitRefusesFurtherPins() {
        val h = ClipboardHistory(pinLimit = 2)
        assertTrue(h.pin("a"))
        assertTrue(h.pin("b"))
        assertTrue(h.pinsFull)
        assertFalse(h.pin("c"))
        assertEquals(listOf("b", "a"), h.pinned)
    }

    @Test
    fun loweringCapacityTrimsRecentButKeepsPins() {
        val h = ClipboardHistory(capacity = 5)
        for (i in 0 until 5) h.add("t$i", i.toLong())
        h.pin("t0")
        h.capacity = 2
        assertEquals(listOf("t0", "t4", "t3"), h.items(10).map { it.text })
    }

    @Test
    fun restoredPinsSkipBlankDuplicateAndExtraEntries() {
        val h = ClipboardHistory(maxChars = 5, pinLimit = 2)
        h.add("b", 0)
        h.restorePins(listOf(" ", "b", "b", "123456", "c", "d"))
        assertEquals(listOf("b", "c"), h.pinned)
        assertEquals(listOf("b", "c"), h.items(1).map { it.text })
        h.clearRecent()
        assertEquals(listOf("b", "c"), h.pinned)
    }
}
