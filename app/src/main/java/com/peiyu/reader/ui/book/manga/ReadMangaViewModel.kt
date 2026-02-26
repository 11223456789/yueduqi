package com.peiyu.reader.ui.book.manga

import android.app.Application
import android.content.Intent
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.BookType
import com.peiyu.reader.constant.EventBus
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookChapter
import com.peiyu.reader.data.entities.BookProgress
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.AppWebDav
import com.peiyu.reader.help.book.BookHelp
import com.peiyu.reader.help.book.isLocal
import com.peiyu.reader.help.book.isLocalModified
import com.peiyu.reader.help.book.removeType
import com.peiyu.reader.help.book.simulatedTotalChapterNum
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.coroutine.Coroutine
import com.peiyu.reader.model.ReadManga
import com.peiyu.reader.model.localBook.LocalBook
import com.peiyu.reader.model.webBook.WebBook
import com.peiyu.reader.utils.mapParallelSafe
import com.peiyu.reader.utils.postEvent
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import splitties.init.appCtx

class ReadMangaViewModel(application: Application) : BaseViewModel(application) {

    private var changeSourceCoroutine: Coroutine<*>? = null

    /**
     * 初始�?     */
    fun initData(intent: Intent, success: (() -> Unit)? = null) {
        execute {
            ReadManga.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            ReadManga.chapterChanged = intent.getBooleanExtra("chapterChanged", false)
            val bookUrl = intent.getStringExtra("bookUrl")
            val book = when {
                bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
                else -> appDb.bookDao.getBook(bookUrl)
            } ?: ReadManga.book
            when {
                book != null -> initManga(book)
                else -> {
                    ReadManga.loadFail(context.getString(R.string.no_book), false)
                    AppLog.put("未找到漫画书籍\nbookUrl:$bookUrl")
                }
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            AppLog.put(msg, it)
        }.onFinally {
            ReadManga.saveRead()
        }
    }

    private suspend fun initManga(book: Book) {
        val isSameBook = ReadManga.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadManga.upData(book)
        } else {
            ReadManga.resetData(book)
        }
        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }

        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return
        }

        if ((ReadManga.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return
        }

        //开始加载内�?        if (!isSameBook) {
            ReadManga.loadContent()
        } else {
            ReadManga.loadOrUpContent()
        }

        if (ReadManga.chapterChanged) {
            // 有章节跳转不同步阅读进度
            ReadManga.chapterChanged = false
        } else {
            if (AppConfig.syncBookProgressPlus) {
                ReadManga.syncProgress(
                    { progress -> ReadManga.mCallback?.sureNewProgress(progress) })
            } else {
                syncBookProgress(book)
            }
        }

        //自动换源
        if (!book.isLocal && ReadManga.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        val bookSource = ReadManga.bookSource ?: return true
        val oldBook = book.copy()
        WebBook.getChapterListAwait(bookSource, book, true).onSuccess { cList ->
            if (oldBook.bookUrl == book.bookUrl) {
                appDb.bookDao.update(book)
            } else {
                appDb.bookDao.replace(oldBook, book)
                BookHelp.updateCacheFolder(oldBook, book)
            }
            appDb.bookChapterDao.delByBook(oldBook.bookUrl)
            appDb.bookChapterDao.insert(*cList.toTypedArray())
            ReadManga.onChapterListUpdated(book)
            return true
        }.onFailure {
            //加载章节出错
            ReadManga.mCallback?.loadFail(appCtx.getString(R.string.error_load_toc))
            return false
        }
        return true
    }

    /**
     * 加载详情�?     */
    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadManga.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            return true
        } catch (e: Throwable) {
            ReadManga.mCallback?.loadFail("详情页出�? ${e.localizedMessage}")
            return false
        }
    }

    /**
     * 自动换源
     */
    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            val sources = appDb.bookSourceDao.allTextEnabledPart
            flow {
                for (source in sources) {
                    source.getBookSource()?.let {
                        emit(it)
                    }
                }
            }.onStart {
                // 自动换源

            }.mapParallelSafe(AppConfig.threadCount) { source ->
                val book = WebBook.preciseSearchAwait(source, name, author).getOrThrow()
                if (book.tocUrl.isEmpty()) {
                    WebBook.getBookInfoAwait(source, book)
                }
                val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
                val chapter = toc.getOrElse(book.durChapterIndex) {
                    toc.last()
                }
                val nextChapter = toc.getOrElse(chapter.index) {
                    toc.first()
                }
                WebBook.getContentAwait(
                    bookSource = source,
                    book = book,
                    bookChapter = chapter,
                    nextChapterUrl = nextChapter.url
                )
                book to toc
            }.take(1).onEach { (book, toc) ->
                changeTo(book, toc)
            }.onEmpty {
                throw NoStackTraceException("没有合适书�?)
            }.onCompletion {
                // 换源完成
            }.catch {
                AppLog.put("自动换源失败\n${it.localizedMessage}", it)
                context.toastOnUi("自动换源失败\n${it.localizedMessage}")
            }.collect()
        }
    }

    /**
     * 同步进度
     */
    fun syncBookProgress(
        book: Book,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        execute {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败�?{book.name}》\n${it.localizedMessage}", it)
        }.onSuccess { progress ->
            progress ?: return@onSuccess
            if (progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                alertSync?.invoke(progress)
            } else if (progress.durChapterIndex < book.simulatedTotalChapterNum()) {
                ReadManga.setProgress(progress)
                AppLog.put("自动同步阅读进度成功�?{book.name}�?${progress.durChapterTitle}")
            }
        }
    }

    /**
     * 换源
     */
    fun changeTo(book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            //换源�?            ReadManga.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            ReadManga.book?.delete()
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadManga.resetData(book)
            ReadManga.loadContent()
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0) {
        if (index < ReadManga.chapterSize) {
            ReadManga.showLoading()
            ReadManga.durChapterIndex = index
            ReadManga.durChapterPos = durChapterPos
            ReadManga.saveRead()
            ReadManga.loadContent()
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        val book = ReadManga.book
        Coroutine.async {
            book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    override fun onCleared() {
        super.onCleared()
        changeSourceCoroutine?.cancel()
    }

    fun refreshContentDur(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadManga.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    openChapter(ReadManga.durChapterIndex, ReadManga.durChapterPos)
                }
        }
    }
}
