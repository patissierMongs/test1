package io.github.patissiermongs.foldkey.ui

import android.content.Context
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.KeyDef
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import kotlin.math.max

object LayoutBuilder {
    data class Sides(val leftMm: Float, val rightMm: Float)

    fun sides(prefs: Prefs, layer: Layer): Sides =
        if (layer == Layer.GENERAL) Sides(prefs.generalSideLeftMm, prefs.generalSideRightMm)
        else Sides(prefs.codeSideLeftMm, prefs.codeSideRightMm)

    fun splitUnitMm(prefs: Prefs, layer: Layer): Float {
        if (layer != Layer.GENERAL) return prefs.splitUnitMm
        prefs.generalUnitMm?.let { return it }
        val code = sides(prefs, Layer.CODE)
        val general = sides(prefs, Layer.GENERAL)
        fun edge(mm: Float) = max(mm, KeyboardView.SIDE_MM)
        return Layouts.generalSplitUnitMm(
            prefs.splitUnitMm,
            edge(general.leftMm) - edge(code.leftMm),
            edge(general.rightMm) - edge(code.rightMm),
        )
    }

    fun spec(
        prefs: Prefs,
        kind: LayoutKind,
        layer: Layer,
        widthPx: Float,
        pxPerMmX: Float,
        pxPerMmY: Float,
        topPx: Float,
        rowHeightMm: Float,
        bottomPx: Float = 0f,
        sideInsetPx: Float = 0f,
        liftPx: Float = 0f,
        panelMinPx: Float = 0f,
    ): GeometrySpec {
        val split = kind == LayoutKind.SPLIT
        val sides = sides(prefs, layer)
        return GeometrySpec(
            widthPx = widthPx,
            pxPerMmX = pxPerMmX,
            pxPerMmY = pxPerMmY,
            topPx = topPx,
            rowHeightMm = rowHeightMm,
            gapMm = KeyboardView.GAP_MM,
            sidePaddingMm = KeyboardView.SIDE_MM,
            sideInsetPx = sideInsetPx,
            splitMarginLeftPx = if (split) sides.leftMm * pxPerMmX else 0f,
            splitMarginRightPx = if (split) sides.rightMm * pxPerMmX else 0f,
            bottomPaddingPx = bottomPx,
            maxUnitMm = KeyboardView.MAX_UNIT_MM,
            splitUnitMm = splitUnitMm(prefs, layer),
            ghostUnits = if (split) KeyboardView.GHOST_UNITS else 0f,
            liftPx = liftPx,
            panelMinPx = panelMinPx,
        )
    }

    fun previewLabel(context: Context, def: KeyDef, layer: Layer): String {
        val action = def.action
        if (action is KeyAction.Char) return action.base.toString()
        if (action == Layouts.LAYER_TOGGLE) {
            return context.getString(if (layer == Layer.GENERAL) R.string.key_layer_code else R.string.key_layer_general)
        }
        return def.label.orEmpty()
    }

    fun build(kind: LayoutKind, layer: Layer, spec: GeometrySpec): KeyboardGeometry {
        val general = layer == Layer.GENERAL
        return when {
            general && kind == LayoutKind.SPLIT -> KeyboardGeometry.split(Layouts.generalSplit, spec)
            general -> KeyboardGeometry.full(Layouts.general, spec)
            kind == LayoutKind.SPLIT -> KeyboardGeometry.split(Layouts.split, spec, Layouts.pad)
            kind == LayoutKind.FULL -> KeyboardGeometry.full(Layouts.full, spec, Layouts.pad)
            else -> KeyboardGeometry.full(Layouts.compact, spec, Layouts.pad)
        }
    }
}
