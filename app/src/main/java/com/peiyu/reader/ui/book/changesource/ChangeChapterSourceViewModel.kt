package com.peiyu.reader.ui.book.changesource

import android.app.Application
import android.os.Bundle
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookChapter
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.model.webBook.WebBook

@Suppress("MemberVisibilityCanBePrivate")
class ChangeChapterSourceViewModel(application: Application) :
    ChangeBookSourceViewModel(application) {

    var chapterIndex: Int = 0
    var chapterTitle: String = ""

    override fun initData(arguments: Bundle?, book: Book?, fromReadBookActivity: Boolean) {
        super.initData(arguments, book, fromReadBookActivity)
        arguments?.let { bundle ->
            bundle.getString("chapterTitle")?.let {
                chapterTitle = it
            }
            chapterIndex = bundle.getInt("chapterIndex")
        }
    }

    fun getContent(
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String?,
        success: (content: String) -> Unit,
        error: (msg: String) -> Unit
    ) {
        execute {
            val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                ?: throw NoStackTraceException("书源不存�?)
            WebBook.getContentAwait(bookSource, book, chapter, nextChapterUrl, false)
        }.onSuccess {
            success.invoke(it)
        }.onError {
            error.invoke(it.localizedMessage ?: "获取正文出错")
        }
    }

}
