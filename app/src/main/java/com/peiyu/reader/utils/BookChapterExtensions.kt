package com.peiyu.reader.utils

import com.peiyu.reader.data.entities.BookChapter

fun BookChapter.internString() {
    title = title.intern()
    bookUrl = bookUrl.intern()
}
