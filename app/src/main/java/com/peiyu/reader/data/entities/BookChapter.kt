package com.peiyu.reader.data.entities

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.AppPattern
import com.peiyu.reader.data.appDb
import com.peiyu.reader.exception.RegexTimeoutException
import com.peiyu.reader.help.RuleBigDataHelp
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.model.analyzeRule.RuleDataInterface
import com.peiyu.reader.utils.ChineseUtils
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.MD5Utils
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.replace
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import splitties.init.appCtx

@Parcelize
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"],
    indices = [(Index(value = ["bookUrl"], unique = false)),
        (Index(value = ["bookUrl", "index"], unique = true))],
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE
    ))]
)    // 删除书籍时自动删除章�?data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷�?    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购�?    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信�?    var wordCount: String? = null,      // 本章节字�?    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null        //变量
) : Parcelable, RuleDataInterface {

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    @Ignore
    @IgnoredOnParcel
    var titleMD5: String? = null

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putChapterVariable(bookUrl, url, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return RuleBigDataHelp.getChapterVariable(bookUrl, url, key)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String {
        return bookUrl + url
    }

    fun getDisplayTitle(
        replaceRules: List<ReplaceRule>? = null,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
    ): String {
        var displayTitle = title.replace(AppPattern.rnRegex, "")
        if (chineseConvert) {
            when (AppConfig.chineseConverterType) {
                1 -> displayTitle = ChineseUtils.t2s(displayTitle)
                2 -> displayTitle = ChineseUtils.s2t(displayTitle)
            }
        }
        if (useReplace && replaceRules != null) kotlin.run {
            replaceRules.forEach { item ->
                if (item.pattern.isNotEmpty()) {
                    try {
                        val mDisplayTitle = if (item.isRegex) {
                            displayTitle.replace(
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond()
                            )
                        } else {
                            displayTitle.replace(item.pattern, item.replacement)
                        }
                        if (mDisplayTitle.isNotBlank()) {
                            displayTitle = mDisplayTitle
                        }
                    } catch (e: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                    } catch (e: CancellationException) {
                        return@run
                    } catch (e: Exception) {
                        AppLog.put("${item.name}替换出错\n替换内容\n${displayTitle}", e)
                        appCtx.toastOnUi("${item.name}替换出错")
                    }
                }
            }
        }
        return displayTitle
    }

    fun getAbsoluteURL(): String {
        //二级目录解析的卷链接为空 返回目录页的链接
        if (url.startsWith(title) && isVolume) return baseUrl
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if (urlMatcher.find()) url.substring(0, urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlBefore.length == url.length) {
            urlAbsoluteBefore
        } else {
            "$urlAbsoluteBefore," + url.substring(urlMatcher.end())
        }
    }

    private fun ensureTitleMD5Init() {
        if (titleMD5 == null) {
            titleMD5 = MD5Utils.md5Encode16(title)
        }
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFileName(suffix: String = "nb"): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.%s", index, titleMD5, suffix)
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFontName(): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.ttf", index, titleMD5)
    }
}

