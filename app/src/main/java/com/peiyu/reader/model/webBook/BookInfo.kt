package com.peiyu.reader.model.webBook

import android.text.TextUtils
import com.peiyu.reader.R
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.book.BookHelp
import com.peiyu.reader.help.book.isWebFile
import com.peiyu.reader.model.Debug
import com.peiyu.reader.model.analyzeRule.AnalyzeRule
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import com.peiyu.reader.utils.DebugLog
import com.peiyu.reader.utils.HtmlFormatter
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.StringUtils.wordCountFormat
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import kotlin.coroutines.coroutineContext


/**
 * 获取详情
 */
object BookInfo {

    @Throws(Exception::class)
    suspend fun analyzeBookInfo(
        bookSource: BookSource,
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        canReName: Boolean,
    ) {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成�?${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 20)
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(coroutineContext)
        analyzeBookInfo(book, body, analyzeRule, bookSource, baseUrl, redirectUrl, canReName)
    }

    suspend fun analyzeBookInfo(
        book: Book,
        body: String,
        analyzeRule: AnalyzeRule,
        bookSource: BookSource,
        baseUrl: String,
        redirectUrl: String,
        canReName: Boolean,
    ) {
        val infoRule = bookSource.getBookInfoRule()
        infoRule.init?.let {
            if (it.isNotBlank()) {
                coroutineContext.ensureActive()
                Debug.log(bookSource.bookSourceUrl, "≡执行详情页初始化规�?)
                analyzeRule.setContent(analyzeRule.getElement(it))
            }
        }
        val mCanReName = canReName && !infoRule.canReName.isNullOrBlank()
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取书�?)
        BookHelp.formatBookName(analyzeRule.getString(infoRule.name)).let {
            if (it.isNotEmpty() && (mCanReName || book.name.isEmpty())) {
                book.name = it
            }
            Debug.log(bookSource.bookSourceUrl, "�?{it}")
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取作�?)
        BookHelp.formatBookAuthor(analyzeRule.getString(infoRule.author)).let {
            if (it.isNotEmpty() && (mCanReName || book.author.isEmpty())) {
                book.author = it
            }
            Debug.log(bookSource.bookSourceUrl, "�?{it}")
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取分�?)
        try {
            analyzeRule.getStringList(infoRule.kind)
                ?.joinToString(",")
                ?.let {
                    if (it.isNotEmpty()) book.kind = it
                    Debug.log(bookSource.bookSourceUrl, "�?{it}")
                } ?: Debug.log(bookSource.bookSourceUrl, "�?)
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "�?{e.localizedMessage}")
            DebugLog.e("获取分类出错", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取字�?)
        try {
            wordCountFormat(analyzeRule.getString(infoRule.wordCount)).let {
                if (it.isNotEmpty()) book.wordCount = it
                Debug.log(bookSource.bookSourceUrl, "�?{it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "�?{e.localizedMessage}")
            DebugLog.e("获取字数出错", e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取最新章�?)
        try {
            analyzeRule.getString(infoRule.lastChapter).let {
                if (it.isNotEmpty()) book.latestChapterTitle = it
                Debug.log(bookSource.bookSourceUrl, "�?{it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "�?{e.localizedMessage}")
            DebugLog.e("获取最新章节出�?, e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取简�?)
        try {
            HtmlFormatter.format(analyzeRule.getString(infoRule.intro)).let {
                if (it.isNotEmpty()) book.intro = it
                Debug.log(bookSource.bookSourceUrl, "�?{it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "�?{e.localizedMessage}")
            DebugLog.e("获取简介出�?, e)
        }
        coroutineContext.ensureActive()
        Debug.log(bookSource.bookSourceUrl, "┌获取封面链�?)
        try {
            analyzeRule.getString(infoRule.coverUrl).let {
                if (it.isNotEmpty()) {
                    book.coverUrl =
                        NetworkUtils.getAbsoluteURL(redirectUrl, it)
                }
                Debug.log(bookSource.bookSourceUrl, "�?{it}")
            }
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Debug.log(bookSource.bookSourceUrl, "�?{e.localizedMessage}")
            DebugLog.e("获取封面出错", e)
        }
        coroutineContext.ensureActive()
        if (!book.isWebFile) {
            Debug.log(bookSource.bookSourceUrl, "┌获取目录链�?)
            book.tocUrl = analyzeRule.getString(infoRule.tocUrl, isUrl = true)
            if (book.tocUrl.isEmpty()) book.tocUrl = baseUrl
            if (book.tocUrl == baseUrl) {
                book.tocHtml = body
            }
            Debug.log(bookSource.bookSourceUrl, "�?{book.tocUrl}")
        } else {
            Debug.log(bookSource.bookSourceUrl, "┌获取文件下载链�?)
            book.downloadUrls = analyzeRule.getStringList(infoRule.downloadUrls, isUrl = true)
            if (book.downloadUrls.isNullOrEmpty()) {
                Debug.log(bookSource.bookSourceUrl, "�?)
                throw NoStackTraceException("下载链接为空")
            } else {
                Debug.log(
                    bookSource.bookSourceUrl,
                    "�? + TextUtils.join("，\n", book.downloadUrls!!)
                )
            }
        }
    }

}
