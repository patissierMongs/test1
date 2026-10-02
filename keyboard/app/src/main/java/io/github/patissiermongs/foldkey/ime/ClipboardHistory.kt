package io.github.patissiermongs.foldkey.ime

class ClipboardHistory(
    capacity: Int = DEFAULT_CAPACITY,
    var ttlMs: Long = DEFAULT_TTL_MS,
    private val maxChars: Int = 20_000,
    val pinLimit: Int = PIN_LIMIT,
) {
    data class Entry(val text: String, val time: Long, val pinned: Boolean = false)

    private val recent = ArrayDeque<Entry>()
    private val pins = ArrayList<String>()

    var capacity: Int = capacity.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            trim()
        }

    val pinned: List<String> get() = pins.toList()

    val pinsFull: Boolean get() = pins.size >= pinLimit

    fun add(text: String, now: Long): Boolean {
        if (!accepts(text) || text in pins) return false
        val changed = recent.firstOrNull()?.text != text
        recent.removeAll { it.text == text }
        recent.addFirst(Entry(text, now))
        trim()
        return changed
    }

    fun items(now: Long): List<Entry> {
        recent.removeAll { now - it.time > ttlMs || now < it.time }
        return pins.map { Entry(it, 0L, pinned = true) } + recent
    }

    fun pin(text: String): Boolean {
        if (!accepts(text) || text in pins || pinsFull) return false
        pins.add(0, text)
        recent.removeAll { it.text == text }
        return true
    }

    fun unpin(text: String, now: Long): Boolean {
        if (!pins.remove(text)) return false
        recent.addFirst(Entry(text, now))
        trim()
        return true
    }

    fun remove(text: String): Boolean {
        val pinRemoved = pins.remove(text)
        val recentRemoved = recent.removeAll { it.text == text }
        return pinRemoved || recentRemoved
    }

    fun restorePins(texts: List<String>) {
        pins.clear()
        texts.filter { accepts(it) }.distinct().take(pinLimit).forEach { pins.add(it) }
        recent.removeAll { it.text in pins }
    }

    fun clearRecent() = recent.clear()

    fun clear() {
        recent.clear()
        pins.clear()
    }

    private fun accepts(text: String): Boolean = text.isNotBlank() && text.length <= maxChars

    private fun trim() {
        while (recent.size > capacity) recent.removeLast()
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
        const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L
        const val PIN_LIMIT = 20
    }
}
