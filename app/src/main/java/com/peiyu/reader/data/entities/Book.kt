package com.peiyu.reader.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.peiyu.reader.constant.AppPattern
import com.peiyu.reader.constant.BookType
import com.peiyu.reader.constant.PageAnim
import com.peiyu.reader.data.appDb
import com.peiyu.reader.help.book.BookHelp
import com.peiyu.reader.help.book.ContentProcessor
import com.peiyu.reader.help.book.getFolderNameNoCache
import com.peiyu.reader.help.book.isEpub
import com.peiyu.reader.help.book.isImage
import com.peiyu.reader.help.book.simulatedTotalChapterNum
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.config.ReadBookConfig
import com.peiyu.reader.model.ReadBook
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.fromJsonObject
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.nio.charset.Charset
import java.time.LocalDate
import kotlin.math.max

@Parcelize
@TypeConverters(Book.Converters::class)
@Entity(
    tableName = "books",
    indices = [Index(value = ["name", "author"], unique = true)]
)
data class Book(
    // 详情页Url(本地书源存储完整文件路径)
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    override var bookUrl: String = "",
    // 目录页Url (toc=table of Contents)
    @ColumnInfo(defaultValue = "")
    var tocUrl: String = "",
    // 书源URL(默认BookType.local)
    @ColumnInfo(defaultValue = BookType.localTag)
    var origin: String = BookType.localTag,
    //书源名称 or 本地书籍文件�?    @ColumnInfo(defaultValue = "")
    var originName: String = "",
    // 书籍名称(书源获取)
    @ColumnInfo(defaultValue = "")
    override var name: String = "",
    // 作者名�?书源获取)
    @ColumnInfo(defaultValue = "")
    override var author: String = "",
    // 分类信息(书源获取)
    override var kind: String? = null,
    // 分类信息(用户修改)
    var customTag: String? = null,
    // 封面Url(书源获取)
    var coverUrl: String? = null,
    // 封面Url(用户修改)
    var customCoverUrl: String? = null,
    // 简介内�?书源获取)
    var intro: String? = null,
    // 简介内�?用户修改)
    var customIntro: String? = null,
    // 自定义字符集名称(仅适用于本地书�?
    var charset: String? = null,
    // 类型,详见BookType
    @ColumnInfo(defaultValue = "0")
    var type: Int = BookType.text,
    // 自定义分组索引号
    @ColumnInfo(defaultValue = "0")
    var group: Long = 0,
    // 最新章节标�?    var latestChapterTitle: String? = null,
    // 最新章节标题更新时�?    @ColumnInfo(defaultValue = "0")
    var latestChapterTime: Long = System.currentTimeMillis(),
    // 最近一次更新书籍信息的时间
    @ColumnInfo(defaultValue = "0")
    var lastCheckTime: Long = System.currentTimeMillis(),
    // 最近一次发现新章节的数�?    @ColumnInfo(defaultValue = "0")
    var lastCheckCount: Int = 0,
    // 书籍目录总数
    @ColumnInfo(defaultValue = "0")
    var totalChapterNum: Int = 0,
    // 当前章节名称
    var durChapterTitle: String? = null,
    // 当前章节索引
    @ColumnInfo(defaultValue = "0")
    var durChapterIndex: Int = 0,
    // 当前阅读的进�?首行字符的索引位�?
    @ColumnInfo(defaultValue = "0")
    var durChapterPos: Int = 0,
    // 最近一次阅读书籍的时间(打开正文的时�?
    @ColumnInfo(defaultValue = "0")
    var durChapterTime: Long = System.currentTimeMillis(),
    //字数
    override var wordCount: String? = null,
    // 刷新书架时更新书籍信�?    @ColumnInfo(defaultValue = "1")
    var canUpdate: Boolean = true,
    // 手动排序
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    //书源排序
    @ColumnInfo(defaultValue = "0")
    var originOrder: Int = 0,
    // 自定义书籍变量信�?用于书源规则检索书籍信�?
    override var variable: String? = null,
    //阅读设置
    var readConfig: ReadConfig? = null,
    //同步时间
    @ColumnInfo(defaultValue = "0")
    var syncTime: Long = 0L
) : Parcelable, BaseBook {

    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            return other.bookUrl == bookUrl
        }
        return false
    }

    override fun hashCode(): Int {
        return bookUrl.hashCode()
    }

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    @Ignore
    @IgnoredOnParcel
    override var infoHtml: String? = null

    @Ignore
    @IgnoredOnParcel
    override var tocHtml: String? = null

    @Ignore
    @IgnoredOnParcel
    var downloadUrls: List<String>? = null

    @Ignore
    @IgnoredOnParcel
    private var folderName: String? = null

    @get:Ignore
    @IgnoredOnParcel
    val lastChapterIndex get() = totalChapterNum - 1

    fun getRealAuthor() = author.replace(AppPattern.authorRegex, "")

    fun getUnreadChapterNum() = max(simulatedTotalChapterNum() - durChapterIndex - 1, 0)

    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    fun getDisplayIntro() = if (customIntro.isNullOrEmpty()) intro else customIntro

    //自定义简介有自动更新的需求时，可通过更新intro再调用upCustomIntro()完成
    @Suppress("unused")
    fun upCustomIntro() {
        customIntro = intro
    }

    fun fileCharset(): Charset {
        return charset(charset ?: "UTF-8")
    }

    @IgnoredOnParcel
    val config: ReadConfig
        get() {
            if (readConfig == null) {
                readConfig = ReadConfig()
            }
            return readConfig!!
        }

    fun setReverseToc(reverseToc: Boolean) {
        config.reverseToc = reverseToc
    }

    fun getReverseToc(): Boolean {
        return config.reverseToc
    }

    fun setUseReplaceRule(useReplaceRule: Boolean) {
        config.useReplaceRule = useReplaceRule
    }

    fun getUseReplaceRule(): Boolean {
        val useReplaceRule = config.useReplaceRule
        if (useReplaceRule != null) {
            return useReplaceRule
        }
        //图片类书�?epub本地 默认关闭净�?        if (isImage || isEpub) {
            return false
        }
        return AppConfig.replaceEnableDefault
    }

    fun setReSegment(reSegment: Boolean) {
        config.reSegment = reSegment
    }

    fun getReSegment(): Boolean {
        return config.reSegment
    }

    fun setPageAnim(pageAnim: Int?) {
        config.pageAnim = pageAnim
    }

    fun getPageAnim(): Int {
        var pageAnim = config.pageAnim
            ?: if (isImage) PageAnim.scrollPageAnim else ReadBookConfig.pageAnim
        if (pageAnim < 0) {
            pageAnim = ReadBookConfig.pageAnim
        }
        return pageAnim
    }

    fun setImageStyle(imageStyle: String?) {
        config.imageStyle = imageStyle
    }

    fun getImageStyle(): String? {
        return config.imageStyle
    }

    fun setTtsEngine(ttsEngine: String?) {
        config.ttsEngine = ttsEngine
    }

    fun getTtsEngine(): String? {
        return config.ttsEngine
    }

    fun setSplitLongChapter(limitLongContent: Boolean) {
        config.splitLongChapter = limitLongContent
    }

    fun getSplitLongChapter(): Boolean {
        return config.splitLongChapter
    }

    // readSimulating �?setter �?getter
    fun setReadSimulating(readSimulating: Boolean) {
        config.readSimulating = readSimulating
    }

    fun getReadSimulating(): Boolean {
        return config.readSimulating
    }

    // startDate �?setter �?getter
    fun setStartDate(startDate: LocalDate?) {
        config.startDate = startDate
    }

    fun getStartDate(): LocalDate? {
        if (!config.readSimulating || config.startDate == null) {
            return LocalDate.now()
        }
        return config.startDate
    }

    // startChapter �?setter �?getter
    fun setStartChapter(startChapter: Int) {
        config.startChapter = startChapter
    }

    fun getStartChapter(): Int {
        if (config.readSimulating) return config.startChapter ?: 0
        return this.durChapterIndex
    }

    // dailyChapters �?setter �?getter
    fun setDailyChapters(dailyChapters: Int) {
        config.dailyChapters = dailyChapters
    }

    fun getDailyChapters(): Int {
        return config.dailyChapters
    }

    fun getDelTag(tag: Long): Boolean {
        return config.delTag and tag == tag
    }

    fun addDelTag(tag: Long) {
        config.delTag = config.delTag and tag
    }

    fun removeDelTag(tag: Long) {
        config.delTag = config.delTag and tag.inv()
    }

    fun getFolderName(): String {
        folderName?.let {
            return it
        }
        //防止书名过长,只取9�?        folderName = getFolderNameNoCache()
        return folderName!!
    }

    fun toSearchBook() = SearchBook(
        name = name,
        author = author,
        kind = kind,
        bookUrl = bookUrl,
        origin = origin,
        originName = originName,
        type = type,
        wordCount = wordCount,
        latestChapterTitle = latestChapterTitle,
        coverUrl = coverUrl,
        intro = intro,
        tocUrl = tocUrl,
        originOrder = originOrder,
        variable = variable
    ).apply {
        this.infoHtml = this@Book.infoHtml
        this.tocHtml = this@Book.tocHtml
    }

    /**
     * 迁移旧的书籍的一些信息到新的书籍�?     */
    fun migrateTo(newBook: Book, toc: List<BookChapter>): Book {
        newBook.durChapterIndex = BookHelp
            .getDurChapter(durChapterIndex, durChapterTitle, toc, totalChapterNum)
        newBook.durChapterTitle = toc[newBook.durChapterIndex].getDisplayTitle(
            ContentProcessor.get(newBook.name, newBook.origin).getTitleReplaceRules(),
            getUseReplaceRule()
        )
        newBook.durChapterPos = durChapterPos
        newBook.durChapterTime = durChapterTime
        newBook.group = group
        newBook.order = order
        newBook.customCoverUrl = customCoverUrl
        newBook.customIntro = customIntro
        newBook.customTag = customTag
        newBook.canUpdate = canUpdate
        newBook.readConfig = readConfig
        return newBook
    }

    fun createBookMark(): Bookmark {
        return Bookmark(
            bookName = name,
            bookAuthor = author,
        )
    }

    fun save() {
        if (appDb.bookDao.has(bookUrl)) {
            appDb.bookDao.update(this)
        } else {
            appDb.bookDao.insert(this)
        }
    }

    fun delete() {
        if (ReadBook.book?.bookUrl == bookUrl) {
            ReadBook.book = null
        }
        appDb.bookDao.delete(this)
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val hTag = 2L
        const val rubyTag = 4L
        const val imgStyleDefault = "DEFAULT"
        const val imgStyleFull = "FULL"
        const val imgStyleText = "TEXT"
        const val imgStyleSingle = "SINGLE"
    }

    @Parcelize
    data class ReadConfig(
        var reverseToc: Boolean = false,
        var pageAnim: Int? = null,
        var reSegment: Boolean = false,
        var imageStyle: String? = null,
        var useReplaceRule: Boolean? = null,// 正文使用净化替换规�?        var delTag: Long = 0L,//去除标签
        var ttsEngine: String? = null,
        var splitLongChapter: Boolean = true,
        var readSimulating: Boolean = false,
        var startDate: LocalDate? = null,
        var startChapter: Int? = null,     // 用户设置的起始章�?        var dailyChapters: Int = 3    // 用户设置的每日更新章节数
    ) : Parcelable

    class Converters {

        @TypeConverter
        fun readConfigToString(config: ReadConfig?): String = GSON.toJson(config)

        @TypeConverter
        fun stringToReadConfig(json: String?) = GSON.fromJsonObject<ReadConfig>(json).getOrNull()
    }
}
