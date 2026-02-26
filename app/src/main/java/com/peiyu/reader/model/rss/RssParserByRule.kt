package com.peiyu.reader.model.rss

import androidx.annotation.Keep
import com.peiyu.reader.R
import com.peiyu.reader.data.entities.RssArticle
import com.peiyu.reader.data.entities.RssSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.model.Debug
import com.peiyu.reader.model.analyzeRule.AnalyzeRule
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import com.peiyu.reader.model.analyzeRule.AnalyzeRule.Companion.setRuleData
import com.peiyu.reader.model.analyzeRule.RuleData
import com.peiyu.reader.utils.NetworkUtils
import splitties.init.appCtx
import java.util.Locale
import kotlin.coroutines.coroutineContext

@Keep
object RssParserByRule {

    @Throws(Exception::class)
    suspend fun parseXML(
        sortName: String,
        sortUrl: String,
        redirectUrl: String,
        body: String?,
        rssSource: RssSource,
        ruleData: RuleData
    ): Pair<MutableList<RssArticle>, String?> {
        val sourceUrl = rssSource.sourceUrl
        var nextUrl: String? = null
        if (body.isNullOrBlank()) {
            throw NoStackTraceException(
                appCtx.getString(R.string.error_get_web_content, rssSource.sourceUrl)
            )
        }
        Debug.log(sourceUrl, "≡获取成�?$sourceUrl")
        Debug.log(sourceUrl, body, state = 10)
        var ruleArticles = rssSource.ruleArticles
        if (ruleArticles.isNullOrBlank()) {
            Debug.log(sourceUrl, "⇒列表规则为�? 使用默认规则解析")
            return RssParserDefault.parseXML(sortName, body, sourceUrl)
        } else {
            val articleList = mutableListOf<RssArticle>()
            val analyzeRule = AnalyzeRule(ruleData, rssSource)
            analyzeRule.setCoroutineContext(coroutineContext)
            analyzeRule.setContent(body).setBaseUrl(sortUrl)
            analyzeRule.setRedirectUrl(redirectUrl)
            var reverse = false
            if (ruleArticles.startsWith("-")) {
                reverse = true
                ruleArticles = ruleArticles.substring(1)
            }
            Debug.log(sourceUrl, "┌获取列�?)
            val collections = analyzeRule.getElements(ruleArticles)
            Debug.log(sourceUrl, "└列表大�?${collections.size}")
            if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                Debug.log(sourceUrl, "┌获取下一页链�?)
                if (rssSource.ruleNextPage!!.uppercase(Locale.getDefault()) == "PAGE") {
                    nextUrl = sortUrl
                } else {
                    nextUrl = analyzeRule.getString(rssSource.ruleNextPage)
                    if (nextUrl.isNotEmpty()) {
                        nextUrl = NetworkUtils.getAbsoluteURL(sortUrl, nextUrl)
                    }
                }
                Debug.log(sourceUrl, "�?nextUrl")
            }
            val ruleTitle = analyzeRule.splitSourceRule(rssSource.ruleTitle)
            val rulePubDate = analyzeRule.splitSourceRule(rssSource.rulePubDate)
            val ruleDescription = analyzeRule.splitSourceRule(rssSource.ruleDescription)
            val ruleImage = analyzeRule.splitSourceRule(rssSource.ruleImage)
            val ruleLink = analyzeRule.splitSourceRule(rssSource.ruleLink)
            val variable = ruleData.getVariable()
            for ((index, item) in collections.withIndex()) {
                getItem(
                    sourceUrl, item, analyzeRule, variable, index == 0,
                    ruleTitle, rulePubDate, ruleDescription, ruleImage, ruleLink
                )?.let {
                    it.sort = sortName
                    it.origin = sourceUrl
                    articleList.add(it)
                }
            }
            if (reverse) {
                articleList.reverse()
            }
            return Pair(articleList, nextUrl)
        }
    }

    private fun getItem(
        sourceUrl: String,
        item: Any,
        analyzeRule: AnalyzeRule,
        variable: String?,
        log: Boolean,
        ruleTitle: List<AnalyzeRule.SourceRule>,
        rulePubDate: List<AnalyzeRule.SourceRule>,
        ruleDescription: List<AnalyzeRule.SourceRule>,
        ruleImage: List<AnalyzeRule.SourceRule>,
        ruleLink: List<AnalyzeRule.SourceRule>
    ): RssArticle? {
        val rssArticle = RssArticle(variable = variable)
        analyzeRule.setRuleData(rssArticle)
        analyzeRule.setContent(item)
        Debug.log(sourceUrl, "┌获取标�?, log)
        rssArticle.title = analyzeRule.getString(ruleTitle)
        Debug.log(sourceUrl, "�?{rssArticle.title}", log)
        Debug.log(sourceUrl, "┌获取时�?, log)
        rssArticle.pubDate = analyzeRule.getString(rulePubDate)
        Debug.log(sourceUrl, "�?{rssArticle.pubDate}", log)
        Debug.log(sourceUrl, "┌获取描�?, log)
        if (ruleDescription.isEmpty()) {
            rssArticle.description = null
            Debug.log(sourceUrl, "└描述规则为空，将会解析内容�?, log)
        } else {
            rssArticle.description = analyzeRule.getString(ruleDescription)
            Debug.log(sourceUrl, "�?{rssArticle.description}", log)
        }
        Debug.log(sourceUrl, "┌获取图片url", log)
        rssArticle.image = analyzeRule.getString(ruleImage, isUrl = true)
        Debug.log(sourceUrl, "�?{rssArticle.image}", log)
        Debug.log(sourceUrl, "┌获取文章链�?, log)
        rssArticle.link = NetworkUtils.getAbsoluteURL(sourceUrl, analyzeRule.getString(ruleLink))
        Debug.log(sourceUrl, "�?{rssArticle.link}", log)
        if (rssArticle.title.isBlank()) {
            return null
        }
        return rssArticle
    }
}
