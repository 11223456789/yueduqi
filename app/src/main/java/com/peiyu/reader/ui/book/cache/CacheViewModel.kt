package com.peiyu.reader.ui.book.cache

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.help.book.BookHelp
import com.peiyu.reader.help.book.isLocal
import com.peiyu.reader.help.coroutine.Coroutine
import com.peiyu.reader.utils.sendValue
import kotlinx.coroutines.ensureActive
import kotlin.collections.set


class CacheViewModel(application: Application) : BaseViewModel(application) {
    val upAdapterLiveData = MutableLiveData<String>()

    private var loadChapterCoroutine: Coroutine<Unit>? = null
    val cacheChapters = hashMapOf<String, HashSet<String>>()

    fun loadCacheFiles(books: List<Book>) {
        loadChapterCoroutine?.cancel()
        loadChapterCoroutine = execute {
            books.forEach { book ->
                if (!book.isLocal && !cacheChapters.contains(book.bookUrl)) {
                    val chapterCaches = hashSetOf<String>()
                    val cacheNames = BookHelp.getChapterFiles(book)
                    if (cacheNames.isNotEmpty()) {
                        appDb.bookChapterDao.getChapterList(book.bookUrl).also {
                            book.totalChapterNum = it.size
                        }.forEach { chapter ->
                            if (cacheNames.contains(chapter.getFileName()) || chapter.isVolume) {
                                chapterCaches.add(chapter.url)
                            }
                        }
                    }
                    cacheChapters[book.bookUrl] = chapterCaches
                    upAdapterLiveData.sendValue(book.bookUrl)
                }
                ensureActive()
            }
        }
    }

}
