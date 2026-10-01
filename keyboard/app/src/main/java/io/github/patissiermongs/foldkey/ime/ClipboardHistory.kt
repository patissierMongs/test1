package io.github.patissiermongs.foldkey.ime

class ClipboardHistory(
    private val capacity: Int = 5,
    private val ttlMs: Long = 60 * 60 * 1000L,
    private val maxChars: Int = 20_000,
) {
    data class Entry(val text: String, val time: Long)

    private val entries = ArrayDeque<Entry>()

    fun add(text: String, now: Long): Boolean {
        if (text.isBlank() || text.length > maxChars) return false
        val changed = entries.firstOrNull()?.text != text
        entries.removeAll { it.text == text }
        entries.addFirst(Entry(text, now))
        while (entries.size > capacity) entries.removeLast()
        return changed
    }

    fun items(now: Long): List<Entry> {
        entries.removeAll { now - it.time > ttlMs || now < it.time }
        return entries.toList()
    }

    fun clear() = entries.clear()
}
