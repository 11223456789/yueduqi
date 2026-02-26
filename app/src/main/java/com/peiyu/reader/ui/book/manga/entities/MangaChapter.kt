package com.peiyu.reader.ui.book.manga.entities

import com.peiyu.reader.data.entities.BookChapter

data class MangaChapter(
    val chapter: BookChapter,
    val pages: List<BaseMangaPage>,
    val imageCount: Int
)
