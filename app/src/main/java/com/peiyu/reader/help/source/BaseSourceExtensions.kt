package com.peiyu.reader.help.source

import com.peiyu.reader.constant.SourceType
import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.data.entities.RssSource
import com.peiyu.reader.model.SharedJsScope
import org.mozilla.javascript.Scriptable
import kotlin.coroutines.CoroutineContext

fun BaseSource.getShareScope(coroutineContext: CoroutineContext? = null): Scriptable? {
    return SharedJsScope.getScope(jsLib, coroutineContext)
}

fun BaseSource.getSourceType(): Int {
    return when (this) {
        is BookSource -> SourceType.book
        is RssSource -> SourceType.rss
        else -> error("unknown source type: ${this::class.simpleName}.")
    }
}
