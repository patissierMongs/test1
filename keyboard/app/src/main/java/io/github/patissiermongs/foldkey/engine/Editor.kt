package io.github.patissiermongs.foldkey.engine

data class EditorContext(
    val raw: Boolean = false,
    val enterAction: Int? = null,
    val preferLatin: Boolean = false,
    val packageName: String? = null,
    val secret: Boolean = false,
)

interface Editor {
    fun commitText(text: String)

    fun setComposingText(text: String)

    fun finishComposingText()

    fun sendKey(keyCode: Int, meta: Int)

    fun performEditorAction(action: Int)

    fun paste(raw: Boolean)

    fun beginBatch() {}

    fun endBatch() {}
}
