package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

class FakeEditor : Editor {
    val text = StringBuilder()
    var composingStart = -1
    val keys = ArrayList<Pair<Int, Int>>()
    val actions = ArrayList<Int>()
    val log = ArrayList<String>()
    var pastes = 0

    val composing: String get() = if (composingStart < 0) "" else text.substring(composingStart)

    override fun commitText(text: String) {
        log.add("commit:$text")
        if (composingStart >= 0) {
            this.text.setLength(composingStart)
            composingStart = -1
        }
        this.text.append(text)
    }

    override fun setComposingText(text: String) {
        log.add("compose:$text")
        if (composingStart >= 0) this.text.setLength(composingStart) else composingStart = this.text.length
        this.text.append(text)
    }

    override fun finishComposingText() {
        log.add("finish")
        composingStart = -1
    }

    override fun sendKey(keyCode: Int, meta: Int) {
        log.add("key:${KeyEvent.keyCodeToString(keyCode)}:$meta")
        keys.add(keyCode to meta)
        if (keyCode == KeyEvent.KEYCODE_DEL && meta == 0 && text.isNotEmpty()) {
            text.setLength(text.length - 1)
        }
    }

    override fun performEditorAction(action: Int) {
        log.add("action:$action")
        actions.add(action)
    }

    override fun paste(raw: Boolean) {
        log.add("paste:$raw")
        pastes++
    }

    override fun selectAll() {
        log.add("selectAll")
    }

    override fun copy() {
        log.add("copy")
    }

    var depth = 0
    var batches = 0

    override fun beginBatch() {
        depth++
        batches++
    }

    override fun endBatch() {
        depth--
    }
}

class RecordingListener : EngineListener {
    var preedit = ""
    val commands = ArrayList<Command>()
    var changes = 0

    override fun onStateChanged() {
        changes++
    }

    override fun onPreedit(text: String) {
        preedit = text
    }

    override fun onCommand(command: Command) {
        commands.add(command)
    }

    val echoes = ArrayList<String>()

    override fun onEcho(token: String) {
        echoes.add(token)
    }
}
