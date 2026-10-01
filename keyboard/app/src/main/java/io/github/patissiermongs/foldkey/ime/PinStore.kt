package io.github.patissiermongs.foldkey.ime

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PinStore(private val file: File) {
    fun load(): List<String> {
        if (!file.isFile) return emptyList()
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
                if (input.readInt() != VERSION) return emptyList()
                val count = input.readInt()
                if (count !in 0..MAX_ITEMS) return emptyList()
                List(count) {
                    val size = input.readInt()
                    if (size !in 0..MAX_BYTES) throw IOException("item size $size")
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    String(bytes, Charsets.UTF_8)
                }
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun save(texts: List<String>) {
        if (texts.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { stream ->
            val out = DataOutputStream(BufferedOutputStream(stream))
            out.writeInt(VERSION)
            out.writeInt(texts.size)
            for (text in texts) {
                val bytes = text.toByteArray(Charsets.UTF_8)
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

    companion object {
        private const val VERSION = 1
        private const val MAX_ITEMS = 1_000
        private const val MAX_BYTES = 1 shl 20
    }
}
