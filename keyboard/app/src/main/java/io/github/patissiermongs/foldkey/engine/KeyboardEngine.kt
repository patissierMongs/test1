package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.hangul.Dubeolsik
import io.github.patissiermongs.foldkey.hangul.HangulComposer
import kotlin.math.abs

enum class Lang { LATIN, HANGUL }

data class EngineSettings(
    val escToLatin: Boolean = true,
    val dualRoleTapMs: Long = 500L,
    val doubleTapMs: Long = 350L,
    val terminalEcho: Boolean = false,
)

interface EngineListener {
    fun onStateChanged()

    fun onPreedit(text: String)

    fun onCommand(command: Command)

    fun onEcho(token: String) {}
}

class KeyboardEngine(private val editor: Editor, private val listener: EngineListener) {
    var settings: EngineSettings = EngineSettings()
        set(value) {
            field = value
            modifiers.doubleTapMs = value.doubleTapMs
            if (!value.terminalEcho) typed.clear()
        }

    val modifiers = Modifiers(settings.doubleTapMs)

    var lang: Lang = Lang.LATIN
        private set

    var context: EditorContext = EditorContext()
        private set

    private var preferredLang = Lang.LATIN
    private val composer = HangulComposer()
    private var dualDownAt = 0L
    private val typed = EchoBuffer()

    val preedit: String get() = if (context.raw) composer.composing else ""

    val composingText: String get() = composer.composing

    val typedSegments: List<EchoBuffer.Segment> get() = typed.items

    private val recording: Boolean get() = context.raw && settings.terminalEcho

    fun startInput(ctx: EditorContext, restarting: Boolean = false) {
        if (composer.isComposing) {
            if (!context.raw) {
                batch { editor.finishComposingText() }
            } else if (restarting && ctx.raw) {
                batch { editor.commitText(composer.composing) }
            }
        }
        composer.reset()
        context = ctx
        typed.clear()
        modifiers.clear()
        lang = if (ctx.preferLatin) Lang.LATIN else preferredLang
        listener.onPreedit("")
        listener.onStateChanged()
    }

    fun finishInput() {
        batch { flushComposition() }
        typed.clear()
        modifiers.clear()
        listener.onStateChanged()
    }

    fun setLang(value: Lang) {
        if (lang == value) return
        batch { flushComposition() }
        lang = value
        if (!context.preferLatin) preferredLang = value
        listener.onStateChanged()
    }

    fun selectionChanged(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (context.raw || !composer.isComposing) return
        if (candidatesStart < 0 || newSelStart != newSelEnd || newSelEnd != candidatesEnd) {
            composer.reset()
            batch { editor.finishComposingText() }
        }
    }

    fun press(action: KeyAction, now: Long) {
        when (action) {
            is KeyAction.Mod -> modifiers.press(action.modifier)
            KeyAction.EscCtrl -> {
                modifiers.press(Modifier.CTRL)
                dualDownAt = now
            }
            else -> return
        }
        listener.onStateChanged()
    }

    fun release(action: KeyAction, now: Long) {
        when (action) {
            is KeyAction.Mod -> modifiers.release(action.modifier, now)
            KeyAction.EscCtrl -> {
                val tap = modifiers.release(Modifier.CTRL, now, toggle = false)
                if (tap && now - dualDownAt <= settings.dualRoleTapMs) batch {
                    if (modifiers.isActive(Modifier.FN)) sendCode(KeyEvent.KEYCODE_INSERT, false, false) else escape()
                }
            }
            else -> return
        }
        listener.onStateChanged()
    }

    fun perform(action: KeyAction, forceShift: Boolean = false, forceCtrl: Boolean = false) = batch {
        when (action) {
            is KeyAction.Char -> typeChar(action, forceShift, forceCtrl)
            is KeyAction.Code ->
                if (action.keyCode == KeyEvent.KEYCODE_ESCAPE) escape() else sendCode(action.keyCode, forceShift, forceCtrl)
            is KeyAction.Text -> typeText(action.text)
            KeyAction.Space -> space()
            KeyAction.Enter -> enter()
            KeyAction.Backspace -> backspace(forceCtrl)
            KeyAction.Lang -> setLang(if (lang == Lang.LATIN) Lang.HANGUL else Lang.LATIN)
            is KeyAction.Cmd -> command(action.command)
            is KeyAction.Mod, KeyAction.EscCtrl -> Unit
        }
    }

    fun moveCursor(steps: Int) = moveBy(steps, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

    fun moveCursorRows(steps: Int) = moveBy(steps, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

    private fun moveBy(steps: Int, backward: Int, forward: Int) {
        if (steps == 0) return
        batch {
            flushComposition()
            val code = if (steps < 0) backward else forward
            val meta = modifiers.metaState(modifiers.shiftForSymbols())
            repeat(abs(steps)) { editor.sendKey(code, meta) }
            if (recording) KeyNames.code(code)?.let { name -> repeat(abs(steps)) { typed.token(KeyNames.chord(meta, name)) } }
        }
    }

    fun pasteText(text: String) = batch {
        flushComposition()
        commit(text)
        finishKey()
    }

    fun endCursorMove() {
        finishKey()
    }

    fun flushComposition() {
        if (!composer.isComposing) return
        val text = composer.flush()
        if (context.raw) {
            commit(text)
            listener.onPreedit("")
        } else {
            editor.finishComposingText()
        }
    }

    private fun typeChar(a: KeyAction.Char, forceShift: Boolean, forceCtrl: Boolean) {
        val shift = forceShift || if (a.isLetter) modifiers.shiftForLetters() else modifiers.shiftForSymbols()
        if (forceCtrl || modifiers.hasCommandModifier()) {
            flushComposition()
            val ch = if (shift) a.shifted else a.base
            val ctrl = forceCtrl || modifiers.isActive(Modifier.CTRL)
            val control = controlCode(ch)
            val stroke = UsKeyMap.strokeFor(ch)
            var meta = modifiers.metaState(stroke?.shift ?: false)
            if (forceCtrl) meta = meta or Modifiers.CTRL_META
            if (context.raw && ctrl && modifiers.isActive(Modifier.ALT) && control != null) {
                editor.commitText("\u001b" + control)
            } else if (stroke != null) {
                editor.sendKey(stroke.keyCode, meta)
            } else {
                editor.commitText(ch.toString())
            }
            echo(KeyNames.chord(meta and Modifiers.SHIFT_META.inv(), ch.toString()))
            finishKey()
            if (ctrl && ch == '[') leaveHangulOnEscape()
            return
        }
        if (lang == Lang.HANGUL && a.isLetter) {
            val jamo = Dubeolsik.jamo(a.base, forceShift || modifiers.shiftForSymbols())
            if (jamo != null) {
                val done = composer.input(jamo)
                if (done.isNotEmpty()) commit(done)
                showComposition()
                finishKey()
                return
            }
        }
        flushComposition()
        commit((if (shift) a.shifted else a.base).toString())
        finishKey()
    }

    private fun typeText(text: String) {
        flushComposition()
        commit(text)
        finishKey()
    }

    private fun commit(text: String) {
        editor.commitText(text)
        if (recording) typed.text(text)
    }

    private fun echo(token: String) {
        listener.onEcho(token)
        if (recording) typed.token(token)
    }

    private fun showComposition() {
        if (context.raw) {
            listener.onPreedit(composer.composing)
        } else if (composer.isComposing) {
            editor.setComposingText(composer.composing)
        } else {
            editor.setComposingText("")
            editor.finishComposingText()
        }
    }

    private fun backspace(forceCtrl: Boolean) {
        if (composer.isComposing && !forceCtrl && !modifiers.hasCommandModifier()) {
            composer.backspace()
            showComposition()
            return
        }
        flushComposition()
        var meta = modifiers.metaState(modifiers.shiftForSymbols())
        if (forceCtrl) meta = meta or Modifiers.CTRL_META
        editor.sendKey(KeyEvent.KEYCODE_DEL, meta)
        if (meta != 0) {
            echo(KeyNames.chord(meta, "⌫"))
            finishKey()
        } else if (recording) {
            typed.backspace()
        }
    }

    private fun space() {
        flushComposition()
        if (modifiers.hasCommandModifier()) {
            val meta = modifiers.metaState(modifiers.shiftForSymbols())
            if (context.raw && modifiers.isActive(Modifier.CTRL) && modifiers.isActive(Modifier.ALT)) {
                editor.commitText("\u001b\u0000")
            } else {
                editor.sendKey(KeyEvent.KEYCODE_SPACE, meta)
            }
            echo(KeyNames.chord(meta, "␣"))
        } else {
            commit(" ")
        }
        finishKey()
    }

    private fun enter() {
        flushComposition()
        val meta = modifiers.metaState(modifiers.shiftForSymbols())
        val action = context.enterAction
        if (meta == 0 && !context.raw && action != null) {
            editor.performEditorAction(action)
        } else {
            editor.sendKey(KeyEvent.KEYCODE_ENTER, meta)
            listener.onEcho(KeyNames.chord(meta, "⏎"))
            if (recording) typed.clear()
        }
        finishKey()
    }

    private fun escape() {
        flushComposition()
        val meta = modifiers.metaState(modifiers.shiftForSymbols())
        editor.sendKey(KeyEvent.KEYCODE_ESCAPE, meta)
        echo(KeyNames.chord(meta, "Esc"))
        finishKey()
        leaveHangulOnEscape()
    }

    private fun leaveHangulOnEscape() {
        if (settings.escToLatin && lang == Lang.HANGUL) {
            lang = Lang.LATIN
            listener.onStateChanged()
        }
    }

    private fun sendCode(code: Int, forceShift: Boolean, forceCtrl: Boolean) {
        flushComposition()
        var meta = modifiers.metaState(forceShift || modifiers.shiftForSymbols())
        if (forceCtrl) meta = meta or Modifiers.CTRL_META
        editor.sendKey(code, meta)
        KeyNames.code(code)?.let { echo(KeyNames.chord(meta, it)) }
        finishKey()
    }

    private fun command(command: Command) {
        flushComposition()
        if (command == Command.PASTE) {
            editor.paste(context.raw)
            if (recording) typed.token(PASTE_TOKEN)
        } else {
            listener.onCommand(command)
        }
    }

    private fun controlCode(c: Char): Char? = when (c) {
        in 'a'..'z' -> (c - 'a' + 1).toChar()
        in 'A'..'Z' -> (c - 'A' + 1).toChar()
        '@', ' ', '2' -> 0.toChar()
        '[', '3' -> 27.toChar()
        '\\', '4' -> 28.toChar()
        ']', '5' -> 29.toChar()
        '^', '6' -> 30.toChar()
        '_', '7', '/' -> 31.toChar()
        '?', '8' -> 127.toChar()
        else -> null
    }

    private inline fun batch(block: () -> Unit) {
        editor.beginBatch()
        try {
            block()
        } finally {
            editor.endBatch()
        }
    }

    private fun finishKey() {
        modifiers.consume()
        listener.onStateChanged()
    }

    companion object {
        const val PASTE_TOKEN = "⎘"
    }
}
