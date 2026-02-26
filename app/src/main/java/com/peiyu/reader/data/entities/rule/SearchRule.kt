package com.peiyu.reader.data.entities.rule

import android.os.Parcelable
import com.google.gson.JsonDeserializer
import com.peiyu.reader.utils.INITIAL_GSON
import kotlinx.parcelize.Parcelize

/**
 * 搜索结果处理规则
 */
@Parcelize
data class SearchRule(
    /**校验关键�?*/
    var checkKeyWord: String? = null,
    override var bookList: String? = null,
    override var name: String? = null,
    override var author: String? = null,
    override var intro: String? = null,
    override var kind: String? = null,
    override var lastChapter: String? = null,
    override var updateTime: String? = null,
    override var bookUrl: String? = null,
    override var coverUrl: String? = null,
    override var wordCount: String? = null
) : BookListRule, Parcelable {

    companion object {

        val jsonDeserializer = JsonDeserializer<SearchRule?> { json, _, _ ->
            when {
                json.isJsonObject -> INITIAL_GSON.fromJson(json, SearchRule::class.java)
                json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, SearchRule::class.java)
                else -> null
            }
        }

    }

}
