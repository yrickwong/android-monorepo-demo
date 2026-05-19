package com.demo.foundations.slidepane.spi

import androidx.annotation.FloatRange
import androidx.annotation.Px
import com.demo.foundations.slidepane.PaneApi

/**
 * Pane 宽度规格。
 */
@PaneApi
sealed class PaneWidthSpec {
    /** 宽度等于容器宽度（默认） */
    @PaneApi
    object MatchParent : PaneWidthSpec()

    /** 宽度 = 容器宽度 * [ratio] */
    @PaneApi
    data class Fraction(
        @FloatRange(from = 0.1, to = 1.0) val ratio: Float
    ) : PaneWidthSpec()

    /** 固定像素宽度 */
    @PaneApi
    data class Fixed(@Px val widthPx: Int) : PaneWidthSpec()
}
