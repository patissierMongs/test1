package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
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

    fun codeSplitUnitMm(prefs: Prefs, profile: ScreenProfile): Float = prefs.keyWidthMm(profile, true) ?: KeyboardView.MAX_UNIT_MM

    fun splitUnitMm(prefs: Prefs, layer: Layer, profile: ScreenProfile): Float {
        val codeUnit = codeSplitUnitMm(prefs, profile)
        if (layer != Layer.GENERAL) return codeUnit
        prefs.generalUnitMm?.let { return it }
        val code = sides(prefs, Layer.CODE)
        val general = sides(prefs, Layer.GENERAL)
        fun edge(mm: Float) = max(mm, KeyboardView.SIDE_MM)
        return Layouts.generalSplitUnitMm(
            codeUnit,
            edge(general.leftMm) - edge(code.leftMm),
            edge(general.rightMm) - edge(code.rightMm),
        )
    }

    fun maxWidthPx(prefs: Prefs, kind: LayoutKind, profile: ScreenProfile, pxPerMmX: Float): Float {
        if (kind == LayoutKind.SPLIT) return Float.MAX_VALUE
        val keyMm = prefs.keyWidthMm(profile, false) ?: return Float.MAX_VALUE
        return Layouts.codeUnits(kind) * keyMm * pxPerMmX
    }

    fun spec(
        prefs: Prefs,
        kind: LayoutKind,
        layer: Layer,
        profile: ScreenProfile,
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
            maxWidthPx = maxWidthPx(prefs, kind, profile, pxPerMmX),
            splitUnitMm = splitUnitMm(prefs, layer, profile),
            ghostUnits = if (split) KeyboardView.GHOST_UNITS else 0f,
            liftPx = liftPx,
            panelMinPx = panelMinPx,
        )
    }

    fun previewLabel(def: KeyDef): String {
        val action = def.action
        if (action is KeyAction.Char) return action.base.toString()
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
