package com.peiyu.reader.ui.book.import.local

import com.peiyu.reader.model.localBook.LocalBook
import com.peiyu.reader.utils.FileDoc

data class ImportBook(
    val file: FileDoc,
    var isOnBookShelf: Boolean = !file.isDir && LocalBook.isOnBookShelf(file.name)
) {
    val name get() = file.name
    val isDir get() = file.isDir
    val size get() = file.size
    val lastModified get() = file.lastModified
}
