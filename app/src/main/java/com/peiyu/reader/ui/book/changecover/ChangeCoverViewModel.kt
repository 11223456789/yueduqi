package com.peiyu.reader.ui.book.changecover

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppConst
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.AppPattern
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.BookSource
import com.peiyu.reader.data.entities.BookSourcePart
import com.peiyu.reader.data.entities.SearchBook
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.model.webBook.WebBook
import com.peiyu.reader.utils.mapParallelSafe
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.min

class ChangeCoverViewModel(application: Application) : BaseViewModel(application) {
    private val threadCount = AppConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    private var searchSuccess: ((SearchBook) -> Unit)? = null
    private var upAdapter: (() -> Unit)? = null
    private var bookSourceParts = arrayListOf<BookSourcePart>()
    private val defaultCover by lazy {
        listOf(
            SearchBook(
                originName = "默认封面",
                name = name,
                author = author,
                coverUrl = "use_default_cover"
            )
        )
    }
    private var task: Job? = null
    val searchStateData = MutableLiveData<Boolean>()
    var name: String = ""
    var author: String = ""
    val searchBooks: MutableList<SearchBook> = Collections.synchronizedList(arrayListOf())
    val dataFlow = callbackFlow {

        searchSuccess = { searchBook ->
            if (!searchBooks.contains(searchBook)) {
                searchBooks.add(searchBook)
                trySend(defaultCover + searchBooks.sortedBy { it.originOrder })
            }
        }

        upAdapter = {
            trySend(defaultCover + searchBooks.sortedBy { it.originOrder })
        }

        appDb.searchBookDao.getEnableHasCover(name, author).let {
            searchBooks.addAll(it)
            trySend(defaultCover + searchBooks.toList())
        }

        if (searchBooks.size <= 1) {
            startSearch()
        }

        awaitClose {
            searchBooks.clear()
            searchSuccess = null
            upAdapter = null
        }
    }.flowOn(IO)

    fun initData(arguments: Bundle?) {
        arguments?.let { bundle ->
            bundle.getString("name")?.let {
                name = it
            }
            bundle.getString("author")?.let {
                author = it.replace(AppPattern.authorRegex, "")
            }
        }
    }

    private fun initSearchPool() {
        searchPool = Executors
            .newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    }

    private fun startSearch() {
        execute {
            stopSearch()
            searchBooks.clear()
            upAdapter?.invoke()
            bookSourceParts.clear()
            bookSourceParts.addAll(appDb.bookSourceDao.allEnabledPart)
            initSearchPool()
            search()
        }
    }

    private fun search() {
        task = viewModelScope.launch(searchPool!!) {
            flow {
                for (bs in bookSourceParts) {
                    bs.getBookSource()?.let {
                        emit(it)
                    }
                }
            }.onStart {
                searchStateData.postValue(true)
            }.mapParallelSafe(threadCount) {
                withTimeout(60000L) {
                    search(it)
                }
            }.onCompletion {
                searchStateData.postValue(false)
            }.catch {
                AppLog.put("封面换源搜索出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    private suspend fun search(source: BookSource) {
        if (source.getSearchRule().coverUrl.isNullOrBlank()) {
            return
        }
        val searchBook = WebBook.searchBookAwait(
            source, name,
            shouldBreak = { it > 0 }).firstOrNull() ?: return
        if (searchBook.name == name && searchBook.author == author
            && !searchBook.coverUrl.isNullOrEmpty()
        ) {
            appDb.searchBookDao.insert(searchBook)
            searchSuccess?.invoke(searchBook)
        }
    }

    fun startOrStopSearch() {
        if (task == null || !task!!.isActive) {
            startSearch()
        } else {
            stopSearch()
        }
    }

    private fun stopSearch() {
        task?.cancel()
        searchPool?.close()
        searchStateData.postValue(false)
    }

    override fun onCleared() {
        super.onCleared()
        searchPool?.close()
    }

}
