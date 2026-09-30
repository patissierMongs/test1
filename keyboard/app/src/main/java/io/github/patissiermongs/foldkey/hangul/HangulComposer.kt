package io.github.patissiermongs.foldkey.hangul

object Jamo {
    const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    const val SYLLABLE_BASE = 0xAC00

    val JUNG_COMBINE: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㅗ' to 'ㅏ') to 'ㅘ',
        ('ㅗ' to 'ㅐ') to 'ㅙ',
        ('ㅗ' to 'ㅣ') to 'ㅚ',
        ('ㅜ' to 'ㅓ') to 'ㅝ',
        ('ㅜ' to 'ㅔ') to 'ㅞ',
        ('ㅜ' to 'ㅣ') to 'ㅟ',
        ('ㅡ' to 'ㅣ') to 'ㅢ',
    )

    val JONG_COMBINE: Map<Pair<Char, Char>, Char> = mapOf(
        ('ㄱ' to 'ㅅ') to 'ㄳ',
        ('ㄴ' to 'ㅈ') to 'ㄵ',
        ('ㄴ' to 'ㅎ') to 'ㄶ',
        ('ㄹ' to 'ㄱ') to 'ㄺ',
        ('ㄹ' to 'ㅁ') to 'ㄻ',
        ('ㄹ' to 'ㅂ') to 'ㄼ',
        ('ㄹ' to 'ㅅ') to 'ㄽ',
        ('ㄹ' to 'ㅌ') to 'ㄾ',
        ('ㄹ' to 'ㅍ') to 'ㄿ',
        ('ㄹ' to 'ㅎ') to 'ㅀ',
        ('ㅂ' to 'ㅅ') to 'ㅄ',
    )

    val JONG_SPLIT: Map<Char, Pair<Char, Char>> = JONG_COMBINE.entries.associate { it.value to it.key }

    fun isVowel(c: Char): Boolean = JUNG.indexOf(c) >= 0

    fun isConsonant(c: Char): Boolean = CHO.indexOf(c) >= 0

    fun isJamo(c: Char): Boolean = isVowel(c) || isConsonant(c)
}

data class Syllable(val cho: Int = -1, val jung: Int = -1, val jong: Int = 0) {
    val isEmpty: Boolean get() = cho < 0 && jung < 0 && jong == 0

    val isCluster: Boolean get() = cho < 0 && jung < 0 && jong > 0

    fun text(): String = when {
        cho >= 0 && jung >= 0 -> String(Character.toChars(Jamo.SYLLABLE_BASE + (cho * 21 + jung) * 28 + jong))
        cho >= 0 -> Jamo.CHO[cho].toString()
        jung >= 0 -> Jamo.JUNG[jung].toString()
        jong > 0 -> Jamo.JONG[jong].toString()
        else -> ""
    }
}

class HangulComposer {
    private val stack = ArrayList<Syllable>()

    val composing: String get() = current().text()

    val isComposing: Boolean get() = stack.isNotEmpty()

    fun input(c: Char): String {
        if (Jamo.isVowel(c)) return inputVowel(c)
        if (Jamo.isConsonant(c)) return inputConsonant(c)
        return flush() + c
    }

    fun backspace(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.size - 1)
        return true
    }

    fun flush(): String {
        val text = composing
        stack.clear()
        return text
    }

    fun reset() {
        stack.clear()
    }

    private fun current(): Syllable = if (stack.isEmpty()) Syllable() else stack[stack.size - 1]

    private fun restart(vararg states: Syllable) {
        stack.clear()
        stack.addAll(states)
    }

    private fun inputVowel(c: Char): String {
        val cur = current()
        val v = Jamo.JUNG.indexOf(c)
        if (cur.isEmpty) {
            stack.add(Syllable(jung = v))
            return ""
        }
        if (cur.isCluster) {
            val (first, second) = Jamo.JONG_SPLIT.getValue(Jamo.JONG[cur.jong])
            val cho = Jamo.CHO.indexOf(second)
            restart(Syllable(cho = cho), Syllable(cho = cho, jung = v))
            return first.toString()
        }
        if (cur.jung < 0) {
            stack.add(cur.copy(jung = v))
            return ""
        }
        if (cur.jong == 0) {
            val combined = Jamo.JUNG_COMBINE[Jamo.JUNG[cur.jung] to c]
            if (combined != null) {
                stack.add(cur.copy(jung = Jamo.JUNG.indexOf(combined)))
                return ""
            }
            val out = cur.text()
            restart(Syllable(jung = v))
            return out
        }
        val jongChar = Jamo.JONG[cur.jong]
        val split = Jamo.JONG_SPLIT[jongChar]
        val kept = if (split == null) 0 else Jamo.JONG.indexOf(split.first)
        val moved = split?.second ?: jongChar
        val out = cur.copy(jong = kept).text()
        val cho = Jamo.CHO.indexOf(moved)
        restart(Syllable(cho = cho), Syllable(cho = cho, jung = v))
        return out
    }

    private fun inputConsonant(c: Char): String {
        val cur = current()
        val cho = Jamo.CHO.indexOf(c)
        if (cur.isEmpty) {
            stack.add(Syllable(cho = cho))
            return ""
        }
        if (cur.cho >= 0 && cur.jung < 0) {
            val cluster = Jamo.JONG_COMBINE[Jamo.CHO[cur.cho] to c]
            if (cluster != null) {
                stack.add(Syllable(jong = Jamo.JONG.indexOf(cluster)))
                return ""
            }
        }
        if (cur.jung < 0 || cur.cho < 0) {
            val out = cur.text()
            restart(Syllable(cho = cho))
            return out
        }
        if (cur.jong == 0) {
            val jong = Jamo.JONG.indexOf(c)
            if (jong > 0) {
                stack.add(cur.copy(jong = jong))
                return ""
            }
        } else {
            val combined = Jamo.JONG_COMBINE[Jamo.JONG[cur.jong] to c]
            if (combined != null) {
                stack.add(cur.copy(jong = Jamo.JONG.indexOf(combined)))
                return ""
            }
        }
        val out = cur.text()
        restart(Syllable(cho = cho))
        return out
    }
}
