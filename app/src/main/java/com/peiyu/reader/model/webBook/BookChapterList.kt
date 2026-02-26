package com.peiyu.reader.model.webBook

import android.text.TextUtils
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import com.peiyu.reader.R
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookChapter
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.data.entities.rule.TocRule
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.exception.TocEmptyException
import com.peiyu.reader.help.book.ContentProcessor
import com.peiyu.reader.help.book.simulatedTotalChapterNum
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.model.Debug
import com.peiyu.reader.model.analyzeRule.AnalyzeRule
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setChapter
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.utils.isTrue
import com.peiyu.reader.utils.mapAsync
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.mozilla.javascript.Context
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext

/**
 * 获取目录
 */
object BookChapterList {

    suspend fun analyzeChapterList(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?
    ): List<BookChapter> {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        val chapterList = ArrayList<BookChapter>()
        Debug.log(bookSource.bookSourceUrl, "≡获取成�?${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 30)
        val tocRule = bookSource.getTocRule()
        val nextUrlList = arrayListOf(redirectUrl)
        var reverse = false
        var listRule = tocRule.chapterList ?: ""
        if (listRule.startsWith("-")) {
            reverse = true
            listRule = listRule.substring(1)
        }
        if (listRule.startsWith("+")) {
            listRule = listRule.substring(1)
        }
        var chapterData =
            analyzeChapterList(
                book, baseUrl, redirectUrl, body,
                tocRule, listRule, bookSource, log = true
            )
        chapterList.addAll(chapterData.first)
        when (chapterData.second.size) {
            0 -> Unit
            1 -> {
                var nextUrl = chapterData.second[0]
                while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                    nextUrlList.add(nextUrl)
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = nextUrl,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = coroutineContext
                    )
                    val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                    res.body?.let { nextBody ->
                        chapterData = analyzeChapterList(
                            book, nextUrl, nextUrl,
                            nextBody, tocRule, listRule, bookSource
                        )
                        nextUrl = chapterData.second.firstOrNull() ?: ""
                        chapterList.addAll(chapterData.first)
                    }
                }
                Debug.log(bookSource.bookSourceUrl, "◇目录总页�?${nextUrlList.size}")
            }

            else -> {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "◇并发解析目�?总页�?${chapterData.second.size}"
                )
                flow {
                    for (urlStr in chapterData.second) {
                        emit(urlStr)
                    }
                }.mapAsync(AppConfig.threadCount) { urlStr ->
                    val analyzeUrl = AnalyzeUrl(
                        mUrl = urlStr,
                        source = bookSource,
                        ruleData = book,
                        coroutineContext = coroutineContext
                    )
                    val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                    analyzeChapterList(
                        book, urlStr, res.url,
                        res.body!!, tocRule, listRule, bookSource, false
                    ).first
                }.collect {
                    chapterList.addAll(it)
                }
            }
        }
        if (chapterList.isEmpty()) {
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        if (!reverse) {
            chapterList.reverse()
        }
        coroutineContext.ensureActive()
        //去重
        val lh = LinkedHashSet(chapterList)
        val list = ArrayList(lh)
        if (!book.getReverseToc()) {
            list.reverse()
        }
        Debug.log(book.origin, "◇目录总数:${list.size}")
        coroutineContext.ensureActive()
        list.forEachIndexed { index, bookChapter ->
            bookChapter.index = index
        }
        val formatJs = tocRule.formatJs
        if (!formatJs.isNullOrBlank()) {
            Context.enter().use {
                val bindings = ScriptBindings()
                bindings["gInt"] = 0
                list.forEachIndexed { index, bookChapter ->
                    bindings["index"] = index + 1
                    bindings["chapter"] = bookChapter
                    bindings["title"] = bookChapter.title
                    RhinoScriptEngine.runCatching {
                        eval(formatJs, bindings)?.toString()?.let {
                            bookChapter.title = it
                        }
                    }.onFailure {
                        Debug.log(book.origin, "格式化标题出�? ${it.localizedMessage}")
                    }
                }
            }
        }
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        book.durChapterTitle = list.getOrElse(book.durChapterIndex) { list.last() }
            .getDisplayTitle(replaceRules, book.getUseReplaceRule())
        if (book.totalChapterNum < list.size) {
            book.lastCheckCount = list.size - book.totalChapterNum
            book.latestChapterTime = System.currentTimeMillis()
        }
        book.lastCheckTime = System.currentTimeMillis()
        book.totalChapterNum = list.size
        book.latestChapterTitle =
            list.getOrElse(book.simulatedTotalChapterNum() - 1) { list.last() }
                .getDisplayTitle(replaceRules, book.getUseReplaceRule())
        coroutineContext.ensureActive()
        getWordCount(list, book)
        return list
    }

    private suspend fun analyzeChapterList(
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String,
        tocRule: TocRule,
        listRule: String,
        bookSource: BookSource,
        getNextUrl: Boolean = true,
        log: Boolean = false
    ): Pair<List<BookChapter>, List<String>> {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(coroutineContext)
        //获取目录列表
        val chapterList = arrayListOf<BookChapter>()
        Debug.log(bookSource.bookSourceUrl, "┌获取目录列�?, log)
        val elements = analyzeRule.getElements(listRule)
        Debug.log(bookSource.bookSourceUrl, "└列表大�?${elements.size}", log)
        //获取下一页链�?        val nextUrlList = arrayListOf<String>()
        val nextTocRule = tocRule.nextTocUrl
        if (getNextUrl && !nextTocRule.isNullOrEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "┌获取目录下一页列�?, log)
            analyzeRule.getStringList(nextTocRule, isUrl = true)?.let {
                for (item in it) {
                    if (item != redirectUrl) {
                        nextUrlList.add(item)
                    }
                }
            }
            Debug.log(
                bookSource.bookSourceUrl,
                "�? + TextUtils.join("，\n", nextUrlList),
                log
            )
        }
        coroutineContext.ensureActive()
        if (elements.isNotEmpty()) {
            Debug.log(bookSource.bookSourceUrl, "┌解析目录列�?, log)
            val nameRule = analyzeRule.splitSourceRule(tocRule.chapterName)
            val urlRule = analyzeRule.splitSourceRule(tocRule.chapterUrl)
            val vipRule = analyzeRule.splitSourceRule(tocRule.isVip)
            val payRule = analyzeRule.splitSourceRule(tocRule.isPay)
            val upTimeRule = analyzeRule.splitSourceRule(tocRule.updateTime)
            val isVolumeRule = analyzeRule.splitSourceRule(tocRule.isVolume)
            elements.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                analyzeRule.setContent(item)
                val bookChapter = BookChapter(bookUrl = book.bookUrl, baseUrl = redirectUrl)
                analyzeRule.setChapter(bookChapter)
                bookChapter.title = analyzeRule.getString(nameRule)
                bookChapter.url = analyzeRule.getString(urlRule)
                bookChapter.tag = analyzeRule.getString(upTimeRule)
                val isVolume = analyzeRule.getString(isVolumeRule)
                bookChapter.isVolume = false
                if (isVolume.isTrue()) {
                    bookChapter.isVolume = true
                }
                if (bookChapter.url.isEmpty()) {
                    if (bookChapter.isVolume) {
                        bookChapter.url = bookChapter.title + index
                        Debug.log(
                            bookSource.bookSourceUrl,
                            "⇒一级目�?{index}未获取到url,使用标题替代"
                        )
                    } else {
                        bookChapter.url = baseUrl
                        Debug.log(
                            bookSource.bookSourceUrl,
                            "⇒目�?{index}未获取到url,使用baseUrl替代"
                        )
                    }
                }
                if (bookChapter.title.isNotEmpty()) {
                    val isVip = analyzeRule.getString(vipRule)
                    val isPay = analyzeRule.getString(payRule)
                    if (isVip.isTrue()) {
                        bookChapter.isVip = true
                    }
                    if (isPay.isTrue()) {
                        bookChapter.isPay = true
                    }
                    chapterList.add(bookChapter)
                }
            }
            Debug.log(bookSource.bookSourceUrl, "└目录列表解析完�?, log)
            if (chapterList.isEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "◇章节列表为�?, log)
            } else {
                Debug.log(bookSource.bookSourceUrl, "≡首章信�?, log)
                Debug.log(bookSource.bookSourceUrl, "◇章节名�?${chapterList[0].title}", log)
                Debug.log(bookSource.bookSourceUrl, "◇章节链�?${chapterList[0].url}", log)
                Debug.log(bookSource.bookSourceUrl, "◇章节信�?${chapterList[0].tag}", log)
                Debug.log(bookSource.bookSourceUrl, "◇是否VIP:${chapterList[0].isVip}", log)
                Debug.log(bookSource.bookSourceUrl, "◇是否购�?${chapterList[0].isPay}", log)
            }
        }
        return Pair(chapterList, nextUrlList)
    }

    private fun getWordCount(list: ArrayList<BookChapter>, book: Book) {
        if (!AppConfig.tocCountWords) {
            return
        }
        val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
        if (chapterList.isNotEmpty()) {
            val map = chapterList.associateBy({ it.getFileName() }, { it.wordCount })
            for (bookChapter in list) {
                val wordCount = map[bookChapter.getFileName()]
                if (wordCount != null) {
                    bookChapter.wordCount = wordCount
                }
            }
        }
    }

}
