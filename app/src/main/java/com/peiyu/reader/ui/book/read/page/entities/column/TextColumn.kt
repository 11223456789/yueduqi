package com.peiyu.reader.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.Keep
import com.peiyu.reader.help.config.ReadBookConfig
import com.peiyu.reader.lib.theme.ThemeStore
import com.peiyu.reader.ui.book.read.page.ContentTextView
import com.peiyu.reader.ui.book.read.page.entities.TextLine
import com.peiyu.reader.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import com.peiyu.reader.ui.book.read.page.provider.ChapterProvider

/**
 * 文字�? */
@Keep
data class TextColumn(
    override var start: Float,
    override var end: Float,
    val charData: String,
) : BaseColumn {

    override var textLine: TextLine = emptyTextLine

    var selected: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
            }
            field = value
        }
    var isSearchResult: Boolean = false
        set(value) {
            if (field != value) {
                textLine.invalidate()
                if (value) {
                    textLine.searchResultColumnCount++
                } else {
                    textLine.searchResultColumnCount--
                }
            }
            field = value
        }

    override fun draw(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (textLine.isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (textLine.isReadAloud || isSearchResult) {
            ThemeStore.accentColor
        } else {
            ReadBookConfig.textColor
        }
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }
        val y = textLine.lineBase - textLine.lineTop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val letterSpacing = textPaint.letterSpacing * textPaint.textSize
            val letterSpacingHalf = letterSpacing * 0.5f
            canvas.drawText(charData, start + letterSpacingHalf, y, textPaint)
        } else {
            canvas.drawText(charData, start, y, textPaint)
        }
        if (selected) {
            canvas.drawRect(start, 0f, end, textLine.height, view.selectedPaint)
        }
    }

}
