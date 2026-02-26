package com.peiyu.reader.data.entities.rule

import android.os.Parcelable
import com.google.gson.JsonDeserializer
import com.peiyu.reader.utils.INITIAL_GSON
import kotlinx.parcelize.Parcelize

/**
 * 正文处理规则
 */
@Parcelize
data class ContentRule(
    var content: String? = null,
    var title: String? = null, //有些网站只能在正文中获取标题
    var nextContentUrl: String? = null,
    var webJs: String? = null,
    var sourceRegex: String? = null,
    var replaceRegex: String? = null, //替换规则
    var imageStyle: String? = null,   //默认大小居中,FULL最大宽�?    var imageDecode: String? = null, //图片bytes二次解密js, 返回解密后的bytes
    var payAction: String? = null,    //购买操作,js或者包含{{js}}的url
) : Parcelable {


    companion object {

        val jsonDeserializer = JsonDeserializer<ContentRule?> { json, _, _ ->
            when {
                json.isJsonObject -> INITIAL_GSON.fromJson(json, ContentRule::class.java)
                json.isJsonPrimitive -> INITIAL_GSON.fromJson(
                    json.asString,
                    ContentRule::class.java
                )
                else -> null
            }
        }

    }


}
