package io.github.patissiermongs.foldkey.ime

import io.github.patissiermongs.foldkey.ime.ClipboardHistory.Entry
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClipStoreTest {
    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun savedClipsComeBackUnchanged() {
        val store = ClipStore(File(dir.root, "clips.bin"))
        val entries = listOf(
            Entry("ssh fold@192.168.0.7\nls -la", 0L, pinned = true),
            Entry("한글 ·…「」", 1_792_000_000_000L),
            Entry("😀\ttab", 1_792_000_000_001L),
            Entry("", 7L),
        )
        store.save(entries)
        assertEquals(entries, ClipStore(File(dir.root, "clips.bin")).load())
    }

    @Test
    fun pinsSavedByTheEarlierFormatLoadAsPins() {
        val file = File(dir.root, "clips.bin")
        DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(1)
            out.writeInt(2)
            for (text in listOf("make test", "한글")) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }
        assertEquals(listOf(Entry("make test", 0L, pinned = true), Entry("한글", 0L, pinned = true)), ClipStore(file).load())
    }

    @Test
    fun missingOrDamagedFileLoadsNothing() {
        val file = File(dir.root, "clips.bin")
        assertEquals(emptyList<Entry>(), ClipStore(file).load())
        file.writeBytes(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 9, 65))
        assertEquals(emptyList<Entry>(), ClipStore(file).load())
        file.writeBytes(byteArrayOf(0, 0, 0, 2, 0, 0, 0, 1, 1, 0, 0, 0))
        assertEquals(emptyList<Entry>(), ClipStore(file).load())
        file.writeBytes(byteArrayOf(0, 0, 0, 3, 0, 0, 0, 0))
        assertEquals(emptyList<Entry>(), ClipStore(file).load())
    }

    @Test
    fun savingNothingRemovesTheFile() {
        val file = File(dir.root, "clips.bin")
        val store = ClipStore(file)
        store.save(listOf(Entry("a", 1L)))
        store.save(emptyList())
        assertFalse(file.exists())
        assertFalse(File(dir.root, "clips.bin.tmp").exists())
    }
}
