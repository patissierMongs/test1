package io.github.patissiermongs.foldkey.engine

class EchoBuffer(private val limit: Int = 160) {
    data class Segment(val text: String, val token: Boolean, val count: Int = 1) {
        val display: String get() = if (token && count > 1) "$text×$count" else text
    }

    private val segments = ArrayList<Segment>()

    val items: List<Segment> get() = segments

    fun clear() = segments.clear()

    fun text(s: String) {
        if (s.isEmpty()) return
        val cut = s.lastIndexOf('\n')
        if (cut >= 0) {
            segments.clear()
            text(s.substring(cut + 1))
            return
        }
        val last = segments.lastOrNull()
        if (last != null && !last.token) {
            segments[segments.lastIndex] = Segment(last.text + s, false)
        } else {
            segments.add(Segment(s, false))
        }
        trim()
    }

    fun token(name: String) {
        val last = segments.lastOrNull()
        if (last != null && last.token && last.text == name) {
            segments[segments.lastIndex] = last.copy(count = last.count + 1)
        } else {
            segments.add(Segment(name, true))
        }
        trim()
    }

    fun backspace() {
        val last = segments.lastOrNull() ?: return
        if (last.token) {
            token(BACKSPACE)
            return
        }
        val t = last.text
        val cut = t.offsetByCodePoints(t.length, -1)
        if (cut == 0) segments.removeAt(segments.lastIndex) else segments[segments.lastIndex] = Segment(t.substring(0, cut), false)
    }

    private fun trim() {
        var total = segments.sumOf { it.display.length }
        while (total > limit && segments.isNotEmpty()) {
            val first = segments[0]
            if (first.token || first.text.length <= Character.charCount(first.text.codePointAt(0))) {
                segments.removeAt(0)
                total -= first.display.length
            } else {
                val n = Character.charCount(first.text.codePointAt(0))
                segments[0] = Segment(first.text.substring(n), false)
                total -= n
            }
        }
    }

    companion object {
        const val BACKSPACE = "⌫"
    }
}
