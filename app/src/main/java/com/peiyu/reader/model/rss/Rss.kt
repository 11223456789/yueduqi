package com.peiyu.reader.model.rss

import com.peiyu.reader.data.entities.RssArticle
import com.peiyu.reader.data.entities.RssSource
import com.peiyu.reader.help.coroutine.Coroutine
import com.peiyu.reader.help.http.StrResponse
import com.peiyu.reader.model.Debug
import com.peiyu.reader.model.analyzeRule.AnalyzeRule
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.model.analyzeRule.RuleData
import com.peiyu.reader.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@Suppress("MemberVisibilityCanBePrivate")
object Rss {

    fun getArticles(
        scope: CoroutineScope,
        sortName: String,
        sortUrl: String,
        rssSource: RssSource,
        page: Int,
        context: CoroutineContext = Dispatchers.IO
    ): Coroutine<Pair<MutableList<RssArticle>, String?>> {
        return Coroutine.async(scope, context) {
            getArticlesAwait(sortName, sortUrl, rssSource, page)
        }
    }

    suspend fun getArticlesAwait(
        sortName: String,
        sortUrl: String,
        rssSource: RssSource,
        page: Int,
    ): Pair<MutableList<RssArticle>, String?> {
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            sortUrl,
            page = page,
            source = rssSource,
            ruleData = ruleData,
            coroutineContext = coroutineContext,
            hasLoginHeader = false
        )
        val res = analyzeUrl.getStrResponseAwait()
        checkRedirect(rssSource, res)
        return RssParserByRule.parseXML(sortName, sortUrl, res.url, res.body, rssSource, ruleData)
    }

    fun getContent(
        scope: CoroutineScope,
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource,
        context: CoroutineContext = Dispatchers.IO
    ): Coroutine<String> {
        return Coroutine.async(scope, context) {
            getContentAwait(rssArticle, ruleContent, rssSource)
        }
    }

    suspend fun getContentAwait(
        rssArticle: RssArticle,
        ruleContent: String,
        rssSource: RssSource,
    ): String {
        val analyzeUrl = AnalyzeUrl(
            rssArticle.link,
            baseUrl = rssArticle.origin,
            source = rssSource,
            ruleData = rssArticle,
            coroutineContext = coroutineContext,
            hasLoginHeader = false
        )
        val res = analyzeUrl.getStrResponseAwait()
        checkRedirect(rssSource, res)
        Debug.log(rssSource.sourceUrl, "≡获取成�?${rssSource.sourceUrl}")
        Debug.log(rssSource.sourceUrl, res.body ?: "", state = 20)
        val analyzeRule = AnalyzeRule(rssArticle, rssSource)
        analyzeRule.setContent(res.body)
            .setBaseUrl(NetworkUtils.getAbsoluteURL(rssArticle.origin, rssArticle.link))
            .setCoroutineContext(coroutineContext)
            .setRedirectUrl(res.url)
        return analyzeRule.getString(ruleContent)
    }

    /**
     * 检测重定向
     */
    private fun checkRedirect(rssSource: RssSource, response: StrResponse) {
        response.raw.priorResponse?.let {
            if (it.isRedirect) {
                Debug.log(rssSource.sourceUrl, "≡检测到重定�?${it.code})")
                Debug.log(rssSource.sourceUrl, "┌重定向后地址")
                Debug.log(rssSource.sourceUrl, "�?{response.url}")
            }
        }
    }
}
