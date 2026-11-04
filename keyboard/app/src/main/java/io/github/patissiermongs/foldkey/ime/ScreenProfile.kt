package io.github.patissiermongs.foldkey.ime

import android.content.Context
import android.view.WindowManager
import io.github.patissiermongs.foldkey.ui.Dpi

enum class ScreenProfile(val key: String, val cover: Boolean, val landscape: Boolean) {
    INNER_PORTRAIT("inner_portrait", false, false),
    INNER_LANDSCAPE("inner_landscape", false, true),
    COVER_PORTRAIT("cover_portrait", true, false),
    COVER_LANDSCAPE("cover_landscape", true, true);

    companion object {
        const val COVER_MAX_SHORT_SIDE_MM = 100f

        private const val MM_PER_INCH = 25.4f

        fun of(cover: Boolean, landscape: Boolean): ScreenProfile = entries.first { it.cover == cover && it.landscape == landscape }

        fun isCover(widthPx: Int, heightPx: Int, pxPerMmX: Float, pxPerMmY: Float): Boolean =
            minOf(widthPx / pxPerMmX, heightPx / pxPerMmY) < COVER_MAX_SHORT_SIDE_MM

        fun of(context: Context): ScreenProfile {
            val dm = context.resources.displayMetrics
            val bounds = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
            val width = bounds?.width()?.takeIf { it > 0 } ?: dm.widthPixels
            val height = bounds?.height()?.takeIf { it > 0 } ?: dm.heightPixels
            val pxPerMmX = Dpi.physical(dm, true) / MM_PER_INCH
            val pxPerMmY = Dpi.physical(dm, false) / MM_PER_INCH
            return of(isCover(width, height, pxPerMmX, pxPerMmY), width > height)
        }
    }
}
