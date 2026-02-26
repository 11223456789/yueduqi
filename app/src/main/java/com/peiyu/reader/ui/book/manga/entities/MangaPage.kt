package com.peiyu.reader.ui.book.manga.entities

data class MangaPage(
    override val chapterIndex: Int = 0,//总章节位�?    val chapterSize: Int,//总章节数�?    val mImageUrl: String = "",//当前URL
    override val index: Int = 0,//当前章节位置
    var imageCount: Int = 0,//当前章节内容总数
    val mChapterName: String = "",//章节名称
) : BaseMangaPage
