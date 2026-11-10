package io.github.patissiermongs.foldkey.ime

import io.github.patissiermongs.foldkey.ime.ClipboardHistory.Entry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ClipStore(private val file: File) {
    fun load(): List<Entry> {
        if (!file.isFile) return emptyList()
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                when (input.readInt()) {
                    PINS_VERSION -> List(count(input)) { Entry(text(input), 0L, pinned = true) }
                    VERSION -> List(count(input)) {
                        val pinned = input.readBoolean()
                        val time = input.readLong()
                        Entry(text(input), time, pinned)
                    }
                    else -> emptyList()
                }
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun save(entries: List<Entry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { stream ->
            val out = DataOutputStream(BufferedOutputStream(stream))
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeBoolean(entry.pinned)
                out.writeLong(entry.time)
                val bytes = entry.text.toByteArray(Charsets.UTF_8)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
            out.flush()
            stream.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("rename failed: $file")
        }
    }

    private fun count(input: DataInputStream): Int {
        val count = input.readInt()
        if (count !in 0..MAX_ITEMS) throw IOException("item count $count")
        return count
    }

    private fun text(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 0..MAX_BYTES) throw IOException("item size $size")
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val PINS_VERSION = 1
        private const val VERSION = 2
        private const val MAX_ITEMS = 1_000
        private const val MAX_BYTES = 1 shl 20
    }
}
