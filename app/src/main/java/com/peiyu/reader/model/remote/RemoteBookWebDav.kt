package com.peiyu.reader.model.remote

import android.net.Uri
import com.peiyu.reader.constant.AppPattern.archiveFileRegex
import com.peiyu.reader.constant.AppPattern.bookFileRegex
import com.peiyu.reader.constant.BookType
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.book.update
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.lib.webdav.Authorization
import com.peiyu.reader.lib.webdav.WebDav
import com.peiyu.reader.lib.webdav.WebDavFile
import com.peiyu.reader.model.analyzeRule.CustomUrl
import com.peiyu.reader.model.localBook.LocalBook
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.isContentScheme
import kotlinx.coroutines.runBlocking

class RemoteBookWebDav(
    val rootBookUrl: String,
    val authorization: Authorization,
    val serverID: Long? = null
) : RemoteBookManager() {

    init {
        runBlocking {
            WebDav(rootBookUrl, authorization).makeAsDir()
        }
    }


    @Throws(Exception::class)
    override suspend fun getRemoteBookList(path: String): MutableList<RemoteBook> {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可�?)
        val remoteBooks = mutableListOf<RemoteBook>()
        //读取文件列表
        val remoteWebDavFileList: List<WebDavFile> = WebDav(path, authorization).listFiles()
        //转化远程文件信息到本地对�?        remoteWebDavFileList.forEach { webDavFile ->
            if (webDavFile.isDir
                || bookFileRegex.matches(webDavFile.displayName)
                || archiveFileRegex.matches(webDavFile.displayName)
            ) {
                //扩展名符合阅读的格式则认为是书籍
                remoteBooks.add(RemoteBook(webDavFile))
            }
        }
        return remoteBooks
    }

    override suspend fun getRemoteBook(path: String): RemoteBook? {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可�?)
        val webDavFile = WebDav(path, authorization).getWebDavFile()
            ?: return null
        return RemoteBook(webDavFile)
    }

    override suspend fun downloadRemoteBook(remoteBook: RemoteBook): Uri {
        AppConfig.defaultBookTreeUri
            ?: throw NoStackTraceException("没有设置书籍保存位置!")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可�?)
        val webdav = WebDav(remoteBook.path, authorization)
        return webdav.downloadInputStream().let { inputStream ->
            LocalBook.saveBookFile(inputStream, remoteBook.filename)
        }
    }

    override suspend fun upload(book: Book) {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可�?)
        val localBookUri = Uri.parse(book.bookUrl)
        val putUrl = "$rootBookUrl${book.originName}"
        val webDav = WebDav(putUrl, authorization)
        if (localBookUri.isContentScheme()) {
            webDav.upload(localBookUri)
        } else {
            webDav.upload(localBookUri.path!!)
        }
        book.origin = BookType.webDavTag + CustomUrl(putUrl)
            .putAttribute("serverID", serverID)
            .toString()
        book.update()
    }

    override suspend fun delete(remoteBookUrl: String) {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可�?)
        WebDav(remoteBookUrl, authorization).delete()
    }

}
