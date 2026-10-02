package io.github.patissiermongs.foldkey.ui

data class Clip(val text: String, val pinned: Boolean = false)

enum class ClipEdit { PIN, UNPIN, DELETE }
