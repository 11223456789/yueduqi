package com.peiyu.reader.data.entities.rule

import android.os.Parcelable
import com.google.gson.JsonDeserializer
import com.peiyu.reader.utils.INITIAL_GSON
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReviewRule(
    var reviewUrl: String? = null,          // 段评URL
    var avatarRule: String? = null,         // 段评发布者头�?
    var contentRule: String? = null,        // 段评内容
    var postTimeRule: String? = null,       // 段评发布时间
    var reviewQuoteUrl: String? = null,     // 获取段评回复URL

    // 这些功能将在以上功能完成以后实现
    var voteUpUrl: String? = null,          // 点赞URL
    var voteDownUrl: String? = null,        // 点踩URL
    var postReviewUrl: String? = null,      // 发送回复URL
    var postQuoteUrl: String? = null,       // 发送回复段评URL
    var deleteUrl: String? = null,          // 删除段评URL
) : Parcelable {

    companion object {

        val jsonDeserializer = JsonDeserializer<ReviewRule?> { json, _, _ ->
            when {
                json.isJsonObject -> INITIAL_GSON.fromJson(json, ReviewRule::class.java)
                json.isJsonPrimitive -> INITIAL_GSON.fromJson(json.asString, ReviewRule::class.java)
                else -> null
            }
        }

    }

}
