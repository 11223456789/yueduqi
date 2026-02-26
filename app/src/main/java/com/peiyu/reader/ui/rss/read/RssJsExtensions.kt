package com.peiyu.reader.ui.rss.read

import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.help.JsExtensions
import com.peiyu.reader.ui.association.AddToBookshelfDialog
import com.peiyu.reader.ui.book.search.SearchActivity
import com.peiyu.reader.utils.showDialogFragment

@Suppress("unused")
class RssJsExtensions(private val activity: ReadRssActivity) : JsExtensions {

    override fun getSource(): BaseSource? {
        return activity.getSource()
    }

    fun searchBook(key: String) {
        SearchActivity.start(activity, key)
    }

    fun addBook(bookUrl: String) {
        activity.showDialogFragment(AddToBookshelfDialog(bookUrl))
    }

}
