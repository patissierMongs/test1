package io.github.patissiermongs.foldkey.hangul

object Dubeolsik {
    private const val KEYS = "qwertyuiopasdfghjklzxcvbnm"
    private const val BASE = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"
    private const val SHIFT = "ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ"

    fun jamo(latin: Char, shift: Boolean): Char? {
        val i = KEYS.indexOf(latin.lowercaseChar())
        if (i < 0) return null
        return if (shift) SHIFT[i] else BASE[i]
    }

    fun hasShiftVariant(latin: Char): Boolean {
        val i = KEYS.indexOf(latin.lowercaseChar())
        return i >= 0 && SHIFT[i] != BASE[i]
    }
}
