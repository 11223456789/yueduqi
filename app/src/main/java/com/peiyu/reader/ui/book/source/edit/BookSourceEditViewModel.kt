package com.peiyu.reader.ui.book.source.edit

import android.app.Application
import android.content.Intent
import com.peiyu.reader.R
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.RuleComplete
import com.peiyu.reader.help.config.SourceConfig
import com.peiyu.reader.help.http.CookieStore
import com.peiyu.reader.help.http.newCallStrResponse
import com.peiyu.reader.help.http.okHttpClient
import com.peiyu.reader.help.source.SourceHelp
import com.peiyu.reader.help.source.clearExploreKindsCache
import com.peiyu.reader.help.storage.ImportOldData
import com.peiyu.reader.model.SharedJsScope
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.fromJsonArray
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.getClipText
import com.peiyu.reader.utils.isAbsUrl
import com.peiyu.reader.utils.isJsonArray
import com.peiyu.reader.utils.isJsonObject
import com.peiyu.reader.utils.jsonPath
import com.peiyu.reader.utils.printOnDebug
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.Dispatchers


class BookSourceEditViewModel(application: Application) : BaseViewModel(application) {
    var autoComplete = false
    var bookSource: BookSource? = null

    fun initData(intent: Intent, onFinally: () -> Unit) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            var source: BookSource? = null
            if (sourceUrl != null) {
                source = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            source?.let {
                bookSource = it
            }
        }.onFinally {
            onFinally()
        }
    }

    fun save(source: BookSource, success: ((BookSource) -> Unit)? = null) {
        execute {
            if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = bookSource ?: BookSource()
            if (!source.equal(oldSource)) {
                source.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.exploreUrl != source.exploreUrl) {
                    oldSource.clearExploreKindsCache()
                }
                if (oldSource.jsLib != source.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            bookSource?.let {
                if (it.bookSourceUrl != source.bookSourceUrl) {
                    SourceHelp.deleteBookSource(it.bookSourceUrl)
                } else {
                    appDb.bookSourceDao.delete(it)
                    SourceConfig.removeSource(it.bookSourceUrl)
                }
            }
            appDb.bookSourceDao.insert(source)
            bookSource = source
            source
        }.onSuccess {
            success?.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }
    }

    fun pasteSource(onSuccess: (source: BookSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为�?)
            } else {
                importSource(text, onSuccess)
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    fun importSource(text: String, finally: (source: BookSource) -> Unit) {
        execute {
            importSource(text)
        }.onSuccess {
            finally.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    suspend fun importSource(text: String): BookSource {
        return when {
            text.isAbsUrl() -> {
                val text1 = okHttpClient.newCallStrResponse { url(text) }.body
                importSource(text1!!)
            }

            text.isJsonArray() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val items: List<Map<String, Any>> = jsonPath.parse(text).read("$")
                    val jsonItem = jsonPath.parse(items[0])
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonArray<BookSource>(text).getOrThrow()[0]
                }
            }

            text.isJsonObject() -> {
                if (text.contains("ruleSearchUrl") || text.contains("ruleFindUrl")) {
                    val jsonItem = jsonPath.parse(text)
                    ImportOldData.fromOldBookSource(jsonItem)
                } else {
                    GSON.fromJsonObject<BookSource>(text).getOrThrow()
                }
            }

            else -> throw NoStackTraceException("格式不对")
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
