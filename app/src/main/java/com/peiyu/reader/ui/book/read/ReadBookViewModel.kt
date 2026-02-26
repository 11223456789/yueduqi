package com.peiyu.reader.ui.book.read

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
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
import com.peiyu.reader.help.book.ContentProcessor
import com.peiyu.reader.help.book.isLocal
import com.peiyu.reader.help.book.isLocalModified
import com.peiyu.reader.help.book.removeType
import com.peiyu.reader.help.book.simulatedTotalChapterNum
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.coroutine.Coroutine
import com.peiyu.reader.model.ImageProvider
import com.peiyu.reader.model.ReadAloud
import com.peiyu.reader.model.ReadBook
import com.peiyu.reader.model.localBook.LocalBook
import com.peiyu.reader.model.webBook.WebBook
import com.peiyu.reader.service.BaseReadAloudService
import com.peiyu.reader.ui.book.read.page.entities.TextChapter
import com.peiyu.reader.ui.book.searchContent.SearchResult
import com.peiyu.reader.utils.DocumentUtils
import com.peiyu.reader.utils.FileUtils
import com.peiyu.reader.utils.isContentScheme
import com.peiyu.reader.utils.mapParallelSafe
import com.peiyu.reader.utils.postEvent
import com.peiyu.reader.utils.toStringArray
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * 阅读界面数据处理
 */
class ReadBookViewModel(application: Application) : BaseViewModel(application) {
    val permissionDenialLiveData = MutableLiveData<Int>()
    var isInitFinish = false
    var searchContentQuery = ""
    var searchResultList: List<SearchResult>? = null
    var searchResultIndex: Int = 0
    private var changeSourceCoroutine: Coroutine<*>? = null

    init {
        AppConfig.detectClickArea()
    }

    fun initReadBookConfig(intent: Intent) {
        val bookUrl = intent.getStringExtra("bookUrl")
        val book = when {
            bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
            else -> appDb.bookDao.getBook(bookUrl)
        } ?: return
        ReadBook.upReadBookConfig(book)
    }

    /**
     * 初始�?     */
    fun initData(intent: Intent, success: (() -> Unit)? = null) {
        execute {
            ReadBook.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            ReadBook.chapterChanged = intent.getBooleanExtra("chapterChanged", false)
            val bookUrl = intent.getStringExtra("bookUrl")
            val book = when {
                bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
                else -> appDb.bookDao.getBook(bookUrl)
            } ?: ReadBook.book
            when {
                book != null -> initBook(book)
                else -> {
                    ReadBook.upMsg(context.getString(R.string.no_book))
                    AppLog.put("未找到书籍\nbookUrl:$bookUrl")
                }
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            ReadBook.upMsg(msg)
            AppLog.put(msg, it)
        }.onFinally {
            ReadBook.saveRead()
        }
    }

    private suspend fun initBook(book: Book) {
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadBook.upData(book)
        } else {
            ReadBook.resetData(book)
        }
        isInitFinish = true
        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }
        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return
        }
        if ((ReadBook.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return
        }
        ReadBook.upMsg(null)
        if (!isSameBook) {
            ReadBook.loadContent(resetPageOffset = true)
        } else {
            ReadBook.loadOrUpContent()
        }
        if (ReadBook.chapterChanged) {
            // 有章节跳转不同步阅读进度
            ReadBook.chapterChanged = false
        } else if (!isSameBook || !BaseReadAloudService.isRun) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress({ progress -> ReadBook.callBack?.sureNewProgress(progress) })
            } else {
                syncBookProgress(book)
            }
        }
        if (!book.isLocal && ReadBook.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (e: Throwable) {
            ReadBook.upMsg("打开本地书籍出错: ${e.localizedMessage}")
            if (e is SecurityException || e is FileNotFoundException) {
                permissionDenialLiveData.postValue(0)
            }
            return false
        }
    }

    /**
     * 加载详情�?     */
    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadBook.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            return true
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            ReadBook.upMsg("详情页出�? ${e.localizedMessage}")
            return false
        }
    }

    /**
     * 加载目录
     */
    fun loadChapterList(book: Book) {
        execute {
            if (loadChapterListAwait(book)) {
                ReadBook.upMsg(null)
            }
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        if (book.isLocal) {
            kotlin.runCatching {
                LocalBook.getChapterList(book).let {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.onChapterListUpdated(book)
                }
                return true
            }.onFailure {
                when (it) {
                    is SecurityException, is FileNotFoundException -> {
                        permissionDenialLiveData.postValue(1)
                    }

                    else -> {
                        AppLog.put("LoadTocError:${it.localizedMessage}", it)
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }
                }
                return false
            }
        } else {
            ReadBook.bookSource?.let {
                val oldBook = book.copy()
                WebBook.getChapterListAwait(it, book, true)
                    .onSuccess { cList ->
                        if (oldBook.bookUrl == book.bookUrl) {
                            appDb.bookDao.update(book)
                        } else {
                            appDb.bookDao.replace(oldBook, book)
                            BookHelp.updateCacheFolder(oldBook, book)
                        }
                        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                        appDb.bookChapterDao.insert(*cList.toTypedArray())
                        ReadBook.onChapterListUpdated(book)
                        return true
                    }.onFailure {
                        coroutineContext.ensureActive()
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                        return false
                    }
            }
        }
        return true
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
                ReadBook.setProgress(progress)
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
            ReadBook.upMsg(context.getString(R.string.loading))
            ReadBook.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            ReadBook.book?.delete()
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadBook.resetData(book)
            ReadBook.upMsg(null)
            ReadBook.loadContent(resetPageOffset = true)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
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
                ReadBook.upMsg(context.getString(R.string.source_auto_changing))
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
                ReadBook.upMsg(null)
            }.catch {
                AppLog.put("自动换源失败\n${it.localizedMessage}", it)
                context.toastOnUi("自动换源失败\n${it.localizedMessage}")
            }.collect()
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0, success: (() -> Unit)? = null) {
        ReadBook.openChapter(index, durChapterPos, success = success)
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        val book = ReadBook.book
        Coroutine.async {
            book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun upBookSource(success: (() -> Unit)?) {
        execute {
            ReadBook.book?.let { book ->
                ReadBook.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun refreshContentDur(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    fun refreshContentAfter(book: Book) {
        execute {
            appDb.bookChapterDao.getChapterList(
                book.bookUrl,
                ReadBook.durChapterIndex,
                book.totalChapterNum
            ).forEach { chapter ->
                BookHelp.delContent(book, chapter)
            }
            ReadBook.loadContent(false)
        }
    }

    fun refreshContentAll(book: Book) {
        execute {
            BookHelp.clearCache(book)
            ReadBook.loadContent(false)
        }
    }

    /**
     * 保存内容
     */
    fun saveContent(book: Book, content: String) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.saveText(book, chapter, content)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    /**
     * 反转内容
     */
    fun reverseContent(book: Book) {
        execute {
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            val content = BookHelp.getContent(book, chapter) ?: return@execute
            val stringBuilder = StringBuilder()
            content.toStringArray().forEach {
                stringBuilder.insert(0, it)
            }
            BookHelp.saveText(book, chapter, stringBuilder.toString())
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    /**
     * 内容搜索跳转
     */
    fun searchResultPositions(
        textChapter: TextChapter,
        searchResult: SearchResult
    ): Array<Int> {
        // calculate search result's pageIndex
        val pages = textChapter.pages
        val content = textChapter.getContent()
        val queryLength = searchContentQuery.length

        var count = 0
        var index = content.indexOf(searchContentQuery)
        while (count != searchResult.resultCountWithinChapter) {
            index = content.indexOf(searchContentQuery, index + queryLength)
            count += 1
        }
        val contentPosition = index
        var pageIndex = 0
        var length = pages[pageIndex].text.length
        while (length < contentPosition && pageIndex + 1 < pages.size) {
            pageIndex += 1
            length += pages[pageIndex].text.length
        }

        // calculate search result's lineIndex
        val currentPage = pages[pageIndex]
        val curTextLines = currentPage.lines
        var lineIndex = 0
        var curLine = curTextLines[lineIndex]
        length = length - currentPage.text.length + curLine.text.length
        if (curLine.isParagraphEnd) length++
        while (length <= contentPosition && lineIndex + 1 < curTextLines.size) {
            lineIndex += 1
            curLine = curTextLines[lineIndex]
            length += curLine.text.length
            if (curLine.isParagraphEnd) length++
        }

        // charIndex
        val currentLine = currentPage.lines[lineIndex]
        var curLineLength = currentLine.text.length
        if (currentLine.isParagraphEnd) curLineLength++
        length -= curLineLength

        val charIndex = contentPosition - length
        var addLine = 0
        var charIndex2 = 0
        // change line
        if ((charIndex + queryLength) > curLineLength) {
            addLine = 1
            charIndex2 = charIndex + queryLength - curLineLength - 1
        }
        // changePage
        if ((lineIndex + addLine + 1) > currentPage.lines.size) {
            addLine = -1
            charIndex2 = charIndex + queryLength - curLineLength - 1
        }
        return arrayOf(pageIndex, lineIndex, charIndex, addLine, charIndex2)
    }

    /**
     * 翻转删除重复标题
     */
    fun reverseRemoveSameTitle() {
        execute {
            val book = ReadBook.book ?: return@execute
            val textChapter = ReadBook.curTextChapter ?: return@execute
            BookHelp.setRemoveSameTitle(
                book, textChapter.chapter, !textChapter.sameTitleRemoved
            )
            ReadBook.loadContent(ReadBook.durChapterIndex)
        }
    }

    /**
     * 刷新图片
     */
    fun refreshImage(src: String) {
        execute {
            ReadBook.book?.let { book ->
                val vFile = BookHelp.getImage(book, src)
                ImageProvider.bitmapLruCache.remove(vFile.absolutePath)
                vFile.delete()
            }
        }.onFinally {
            ReadBook.loadContent(false)
        }
    }

    /**
     * 保存图片
     */
    fun saveImage(src: String?, uri: Uri) {
        src ?: return
        val book = ReadBook.book ?: return
        execute {
            val image = BookHelp.getImage(book, src)
            FileInputStream(image).use { input ->
                if (uri.isContentScheme()) {
                    DocumentFile.fromTreeUri(context, uri)?.let { doc ->
                        val imageDoc = DocumentUtils.createFileIfNotExist(doc, image.name)!!
                        context.contentResolver.openOutputStream(imageDoc.uri)!!.use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    val dir = File(uri.path ?: uri.toString())
                    val file = FileUtils.createFileIfNotExist(dir, image.name)
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }.onError {
            AppLog.put("保存图片出错\n${it.localizedMessage}", it)
            context.toastOnUi("保存图片出错\n${it.localizedMessage}")
        }
    }

    /**
     * 替换规则变化
     */
    fun replaceRuleChanged() {
        execute {
            ReadBook.book?.let {
                ContentProcessor.get(it.name, it.origin).upReplaceRules()
                ReadBook.loadContent(resetPageOffset = false)
            }
        }
    }

    fun disableSource() {
        execute {
            ReadBook.bookSource?.let {
                it.enabled = false
                appDb.bookSourceDao.update(it)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (BaseReadAloudService.isRun && BaseReadAloudService.pause) {
            ReadAloud.stop(context)
        }
    }

}
