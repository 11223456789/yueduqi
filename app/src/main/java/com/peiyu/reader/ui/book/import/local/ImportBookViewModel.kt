package com.peiyu.reader.ui.book.import.local

import android.app.Application
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.constant.AppPattern.archiveFileRegex
import com.peiyu.reader.constant.AppPattern.bookFileRegex
import com.peiyu.reader.constant.PreferKey
import com.peiyu.reader.model.localBook.LocalBook
import com.peiyu.reader.utils.AlphanumComparator
import com.peiyu.reader.utils.FileDoc
import com.peiyu.reader.utils.delete
import com.peiyu.reader.utils.getPrefInt
import com.peiyu.reader.utils.list
import com.peiyu.reader.utils.mapParallel
import com.peiyu.reader.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.util.Collections

class ImportBookViewModel(application: Application) : BaseViewModel(application) {
    var rootDoc: FileDoc? = null
    val subDocs = arrayListOf<FileDoc>()
    var sort = context.getPrefInt(PreferKey.localBookImportSort)
    var dataCallback: DataCallback? = null
    var dataFlowStart: (() -> Unit)? = null
    var filterKey: String? = null
    val dataFlow = callbackFlow<List<ImportBook>> {

        val list = Collections.synchronizedList(ArrayList<ImportBook>())

        dataCallback = object : DataCallback {

            override fun setItems(fileDocs: List<FileDoc>) {
                list.clear()
                fileDocs.mapTo(list) {
                    ImportBook(it)
                }
                trySend(list)
            }

            override fun addItems(fileDocs: List<FileDoc>) {
                fileDocs.mapTo(list) {
                    ImportBook(it)
                }
                trySend(list)
            }

            override fun clear() {
                list.clear()
                trySend(emptyList())
            }

            override fun upAdapter() {
                trySend(list)
            }
        }

        withContext(Main) {
            dataFlowStart?.invoke()
        }

        awaitClose {
            dataCallback = null
        }

    }.map { docList ->
        val docList = docList.toList()
        val filterKey = filterKey
        val skipFilter = filterKey.isNullOrBlank()
        val comparator = when (sort) {
            2 -> compareBy<ImportBook>({ !it.isDir }, { -it.lastModified })
            1 -> compareBy({ !it.isDir }, { -it.size })
            else -> compareBy { !it.isDir }
        } then compareBy(AlphanumComparator) { it.name }
        docList.asSequence().filter {
            skipFilter || it.name.contains(filterKey)
        }.sortedWith(comparator).toList()
    }.flowOn(IO)

    fun addToBookshelf(bookList: HashSet<ImportBook>, finally: () -> Unit) {
        execute {
            val fileUris = bookList.map {
                it.file.uri
            }
            LocalBook.importFiles(fileUris)
        }.onError {
            context.toastOnUi("添加书架失败，请尝试重新选择文件�?)
            AppLog.put("添加书架失败\n${it.localizedMessage}", it)
        }.onSuccess {
            context.toastOnUi("添加书架成功")
        }.onFinally {
            finally.invoke()
        }
    }

    fun deleteDoc(bookList: HashSet<ImportBook>, finally: () -> Unit) {
        execute {
            bookList.forEach {
                it.file.delete()
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun loadDoc(fileDoc: FileDoc) {
        execute {
            val docList = fileDoc.list { item ->
                when {
                    item.name.startsWith(".") -> false
                    item.isDir -> true
                    else -> item.name.matches(bookFileRegex) || item.name.matches(archiveFileRegex)
                }
            }
            dataCallback?.setItems(docList!!)
        }.onError {
            context.toastOnUi("获取文件列表出错\n${it.localizedMessage}")
        }
    }

    suspend fun scanDoc(fileDoc: FileDoc) {
        dataCallback?.clear()
        val channel = Channel<FileDoc>(UNLIMITED)
        var n = 1
        channel.trySend(fileDoc)
        val list = arrayListOf<FileDoc>()
        channel.consumeAsFlow()
            .mapParallel(16) { fileDoc ->
                fileDoc.list()!!
            }.onEach { fileDocs ->
                n--
                list.clear()
                fileDocs.forEach {
                    if (it.isDir) {
                        n++
                        channel.trySend(it)
                    } else if (it.name.matches(bookFileRegex)
                        || it.name.matches(archiveFileRegex)
                    ) {
                        list.add(it)
                    }
                }
                dataCallback?.addItems(list)
            }.takeWhile {
                n > 0
            }.catch {
                context.toastOnUi("扫描文件夹出错\n${it.localizedMessage}")
            }.collect()
    }

    fun updateCallBackFlow(filterKey: String?) {
        this.filterKey = filterKey
        dataCallback?.upAdapter()
    }

    interface DataCallback {

        fun setItems(fileDocs: List<FileDoc>)

        fun addItems(fileDocs: List<FileDoc>)

        fun clear()

        fun upAdapter()

    }

}
