package io.github.patissiermongs.foldkey.ime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PinStoreTest {
    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun savedTextComesBackUnchanged() {
        val store = PinStore(File(dir.root, "pins.bin"))
        val texts = listOf("ssh fold@192.168.0.7\nls -la", "한글 ·…「」", "😀\ttab", "")
        store.save(texts)
        assertEquals(texts, PinStore(File(dir.root, "pins.bin")).load())
    }

    @Test
    fun missingOrDamagedFileLoadsNothing() {
        val file = File(dir.root, "pins.bin")
        assertEquals(emptyList<String>(), PinStore(file).load())
        file.writeBytes(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 9, 65))
        assertEquals(emptyList<String>(), PinStore(file).load())
    }

    @Test
    fun savingNothingRemovesTheFile() {
        val file = File(dir.root, "pins.bin")
        val store = PinStore(file)
        store.save(listOf("a"))
        store.save(emptyList())
        assertFalse(file.exists())
        assertFalse(File(dir.root, "pins.bin.tmp").exists())
    }
}
