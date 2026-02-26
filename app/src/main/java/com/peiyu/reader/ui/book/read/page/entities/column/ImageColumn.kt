package com.peiyu.reader.ui.book.read.page.entities.column

import android.graphics.Canvas
import android.graphics.RectF
import androidx.annotation.Keep
import com.peiyu.reader.model.ImageProvider
import com.peiyu.reader.model.ReadBook
import com.peiyu.reader.ui.book.read.page.ContentTextView
import com.peiyu.reader.ui.book.read.page.entities.TextLine
import com.peiyu.reader.ui.book.read.page.entities.TextLine.Companion.emptyTextLine
import com.peiyu.reader.utils.toastOnUi
import splitties.init.appCtx

/**
 * 图片�? */
@Keep
data class ImageColumn(
    override var start: Float,
    override var end: Float,
    var src: String
) : BaseColumn {

    override var textLine: TextLine = emptyTextLine
    override fun draw(view: ContentTextView, canvas: Canvas) {
        val book = ReadBook.book ?: return

        val height = textLine.height

        val bitmap = ImageProvider.getImage(
            book,
            src,
            (end - start).toInt(),
            height.toInt()
        )

        val rectF = if (textLine.isImage) {
            RectF(start, 0f, end, height)
        } else {
            /*以宽度为基准保持图片的原始比例叠加，当div为负数时，允许高度比字符更高*/
            val h = (end - start) / bitmap.width * bitmap.height
            val div = (height - h) / 2
            RectF(start, div, end, height - div)
        }
        kotlin.runCatching {
            canvas.drawBitmap(bitmap, null, rectF, view.imagePaint)
        }.onFailure { e ->
            appCtx.toastOnUi(e.localizedMessage)
        }
    }

}
