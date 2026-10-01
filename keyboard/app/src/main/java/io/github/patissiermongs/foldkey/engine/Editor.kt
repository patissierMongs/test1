package io.github.patissiermongs.foldkey.engine

data class EditorContext(
    val raw: Boolean = false,
    val enterAction: Int? = null,
    val preferLatin: Boolean = false,
    val packageName: String? = null,
    val secret: Boolean = false,
    val multiLine: Boolean = false,
    val selStart: Int = -1,
    val selEnd: Int = -1,
)

interface Editor {
    fun commitText(text: String)

    fun setComposingText(text: String)

    fun finishComposingText()

    fun sendKey(keyCode: Int, meta: Int)

    fun performEditorAction(action: Int)

    fun paste(raw: Boolean)

    fun selectAll()

    fun copy()

    fun beginBatch() {}

    fun endBatch() {}
}
