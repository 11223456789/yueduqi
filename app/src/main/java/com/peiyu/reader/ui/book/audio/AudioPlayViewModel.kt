package com.peiyu.reader.ui.book.audio

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.BookType
import com.peiyu.reader.constant.EventBus
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookChapter
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.help.book.getBookSource
import com.peiyu.reader.help.book.removeType
import com.peiyu.reader.help.book.simulatedTotalChapterNum
import com.peiyu.reader.model.AudioPlay
import com.peiyu.reader.model.webBook.WebBook
import com.peiyu.reader.utils.postEvent
import com.peiyu.reader.utils.toastOnUi

class AudioPlayViewModel(application: Application) : BaseViewModel(application) {
    val titleData = MutableLiveData<String>()
    val coverData = MutableLiveData<String>()

    fun initData(intent: Intent) = AudioPlay.apply {
        execute {
            val bookUrl = intent.getStringExtra("bookUrl") ?: book?.bookUrl ?: return@execute
            val book = appDb.bookDao.getBook(bookUrl) ?: return@execute
            inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            initBook(book)
        }.onFinally {
            saveRead()
        }
    }

    private suspend fun initBook(book: Book) {
        val isSameBook = AudioPlay.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            AudioPlay.upData(book)
        } else {
            AudioPlay.resetData(book)
        }
        titleData.postValue(book.name)
        coverData.postValue(book.getDisplayCover())
        if (book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }
        if (AudioPlay.chapterSize == 0 && !loadChapterList(book)) {
            return
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(bookSource, book)
            return true
        } catch (e: Exception) {
            AppLog.put("详情页出�? ${e.localizedMessage}", e, true)
            return false
        }
    }

    private suspend fun loadChapterList(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        try {
            val oldBook = book.copy()
            val cList = WebBook.getChapterListAwait(bookSource, book).getOrThrow()
            if (oldBook.bookUrl == book.bookUrl) {
                appDb.bookDao.update(book)
            } else {
                appDb.bookDao.replace(oldBook, book)
            }
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookChapterDao.insert(*cList.toTypedArray())
            AudioPlay.chapterSize = cList.size
            AudioPlay.simulatedChapterSize = book.simulatedTotalChapterNum()
            AudioPlay.upDurChapter()
            return true
        } catch (e: Exception) {
            context.toastOnUi(R.string.error_load_toc)
            return false
        }
    }

    fun upSource() {
        execute {
            val book = AudioPlay.book ?: return@execute
            AudioPlay.bookSource = book.getBookSource()
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        execute {
            AudioPlay.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            AudioPlay.book?.delete()
            appDb.bookDao.insert(book)
            AudioPlay.book = book
            AudioPlay.bookSource = source
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            AudioPlay.upDurChapter()
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            AudioPlay.book?.let {
                appDb.bookDao.delete(it)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

}
