package com.peiyu.reader.ui.main.explore

import android.app.Application
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.BookSourcePart
import com.peiyu.reader.help.config.SourceConfig
import com.peiyu.reader.help.source.SourceHelp

class ExploreViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(bookSource: BookSourcePart) {
        execute {
            val minXh = appDb.bookSourceDao.minOrder
            bookSource.customOrder = minXh - 1
            appDb.bookSourceDao.upOrder(bookSource)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            SourceHelp.deleteBookSource(source.bookSourceUrl)
        }
    }

}
