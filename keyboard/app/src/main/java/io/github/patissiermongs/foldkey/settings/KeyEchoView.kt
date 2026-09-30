package io.github.patissiermongs.foldkey.settings

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import io.github.patissiermongs.foldkey.R

class KeyEchoView(context: Context) : TextView(context) {
    private val lines = ArrayDeque<String>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        typeface = Typeface.MONOSPACE
        setTextIsSelectable(false)
        minLines = 6
        render()
        setOnClickListener {
            requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                log("text " + describe(text.toString()))
                return true
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                log("composing " + describe(text.toString()))
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                log("delete $beforeLength,$afterLength")
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        log("down " + describe(event))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    fun clear() {
        lines.clear()
        render()
    }

    private fun describe(event: KeyEvent): String {
        val mods = buildList {
            if (event.isCtrlPressed) add("Ctrl")
            if (event.isAltPressed) add("Alt")
            if (event.isShiftPressed) add("Shift")
            if (event.isMetaPressed) add("Meta")
        }
        val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        return (mods + name).joinToString("+")
    }

    private fun describe(text: String): String = text.map {
        when {
            it == '\n' -> "\\n"
            it == '\t' -> "\\t"
            it.code < 0x20 -> "^" + (it.code + 0x40).toChar()
            else -> it.toString()
        }
    }.joinToString("", "\"", "\"")

    private fun log(line: String) {
        lines.addLast(line)
        while (lines.size > 8) lines.removeFirst()
        render()
    }

    private fun render() {
        text = if (lines.isEmpty()) context.getString(R.string.echo_placeholder) else lines.joinToString("\n")
    }
}
