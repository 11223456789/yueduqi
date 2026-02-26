package com.peiyu.reader.ui.rss.source.edit

import android.app.Application
import android.content.Intent
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.RssSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.AppCacheManager
import com.peiyu.reader.help.RuleComplete
import com.peiyu.reader.help.http.CookieStore
import com.peiyu.reader.help.source.removeSortCache
import com.peiyu.reader.model.SharedJsScope
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.getClipText
import com.peiyu.reader.utils.printOnDebug
import com.peiyu.reader.utils.stackTraceStr
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.Dispatchers


class RssSourceEditViewModel(application: Application) : BaseViewModel(application) {
    var autoComplete = false
    var rssSource: RssSource? = null

    fun initData(intent: Intent, onFinally: () -> Unit) {
        execute {
            val key = intent.getStringExtra("sourceUrl")
            if (key != null) {
                appDb.rssSourceDao.getByKey(key)?.let {
                    rssSource = it
                }
            }
        }.onFinally {
            onFinally()
        }
    }

    fun save(source: RssSource, success: ((RssSource) -> Unit)) {
        execute {
            if (source.sourceName.isBlank() || source.sourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = rssSource ?: RssSource()
            if (!source.equal(oldSource)) {
                source.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.sortUrl != source.sortUrl) {
                    oldSource.removeSortCache()
                }
                if (oldSource.jsLib != source.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            rssSource?.let {
                appDb.rssSourceDao.delete(it)
                //更新收藏的源地址
                if (it.sourceUrl != source.sourceUrl) {
                    appDb.rssStarDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                    appDb.rssArticleDao.updateOrigin(source.sourceUrl, it.sourceUrl)
                    appDb.cacheDao.deleteSourceVariables(it.sourceUrl)
                    AppCacheManager.clearSourceVariables()
                }
            }
            appDb.rssSourceDao.insert(source)
            rssSource = source
            source
        }.onSuccess {
            success(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }
    }

    fun pasteSource(onSuccess: (source: RssSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            var source: RssSource? = null
            context.getClipText()?.let { json ->
                source = GSON.fromJsonObject<RssSource>(json).getOrThrow()
            }
            source
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }.onSuccess {
            if (it != null) {
                onSuccess(it)
            } else {
                context.toastOnUi("格式不对")
            }
        }
    }

    fun importSource(text: String, finally: (source: RssSource) -> Unit) {
        execute {
            val text1 = text.trim()
            GSON.fromJsonObject<RssSource>(text1).getOrThrow().let {
                finally.invoke(it)
            }
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun clearCookie(url: String) {
        execute {
            CookieStore.removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}
