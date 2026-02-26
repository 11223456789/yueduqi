package com.peiyu.reader.lib.webdav

import android.annotation.SuppressLint
import android.net.Uri
import cn.hutool.core.net.URLDecoder
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.http.newCallResponse
import com.peiyu.reader.help.http.okHttpClient
import com.peiyu.reader.help.http.text
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl
import com.peiyu.reader.model.analyzeRule.CustomUrl
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.findNS
import com.peiyu.reader.utils.findNSPrefix
import com.peiyu.reader.utils.printOnDebug
import com.peiyu.reader.utils.toRequestBody
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class WebDav(
    val path: String,
    val authorization: Authorization
) {
    companion object {

        fun fromPath(path: String): WebDav {
            val id = AnalyzeUrl(path).serverID ?: throw WebDavException("没有serverID")
            val authorization = Authorization(id)
            return WebDav(path, authorization)
        }

        @SuppressLint("DateTimeFormatter")
        private val dateTimeFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

        // 指定返回哪些属�?        @Language("xml")
        private const val DIR =
            """<?xml version="1.0"?>
            <a:propfind xmlns:a="DAV:">
                <a:prop>
                    <a:displayname/>
                    <a:resourcetype/>
                    <a:getcontentlength/>
                    <a:creationdate/>
                    <a:getlastmodified/>
                    %s
                </a:prop>
            </a:propfind>"""

        @Language("xml")
        private const val EXISTS =
            """<?xml version="1.0"?>
            <propfind xmlns="DAV:">
               <prop>
                  <resourcetype />
               </prop>
            </propfind>"""

        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
    }


    private val url: URL = URL(CustomUrl(path).getUrl())
    private val httpUrl: String? by lazy {
        val raw = url.toString()
            .replace("davs://", "https://")
            .replace("dav://", "http://")
        return@lazy kotlin.runCatching {
            raw.toHttpUrl().toString()
        }.getOrNull()
    }
    private val webDavClient by lazy {
        val authInterceptor = Interceptor { chain ->
            var request = chain.request()
            if (request.url.host.equals(host, true)) {
                request = request
                    .newBuilder()
                    .header(authorization.name, authorization.data)
                    .build()
            }
            chain.proceed(request)
        }
        okHttpClient.newBuilder().run {
            callTimeout(0, TimeUnit.SECONDS)
            interceptors().add(0, authInterceptor)
            addNetworkInterceptor(authInterceptor)
            build()
        }
    }
    private val host: String?
        get() = url.host?.let {
            if (it.startsWith("[")) {
                it.substring(1, it.lastIndex)
            } else {
                it
            }
        }

    /**
     * 获取当前url文件信息
     */
    @Throws(WebDavException::class)
    suspend fun getWebDavFile(): WebDavFile? {
        return propFindResponse(depth = 0)?.let {
            parseBody(it).firstOrNull()
        }
    }

    /**
     * 列出当前路径下的文件
     * @return 文件列表
     */
    @Throws(WebDavException::class)
    suspend fun listFiles(): List<WebDavFile> {
        propFindResponse()?.let { body ->
            return parseBody(body).filter {
                it.path != path
            }
        }
        return emptyList()
    }

    /**
     * @param propsList 指定列出文件的哪些属�?     */
    @Throws(WebDavException::class)
    private suspend fun propFindResponse(
        propsList: List<String> = emptyList(),
        depth: Int = 1
    ): String? {
        val requestProps = StringBuilder()
        for (p in propsList) {
            requestProps.append("<a:").append(p).append("/>\n")
        }
        val requestPropsStr: String = if (requestProps.toString().isEmpty()) {
            DIR.replace("%s", "")
        } else {
            String.format(DIR, requestProps.toString() + "\n")
        }
        val url = httpUrl ?: return null
        return webDavClient.newCallResponse {
            url(url)
            addHeader("Depth", depth.toString())
            // 添加RequestBody对象，可以只返回的属性。如果设为null，则会返回全部属�?            // 注意：尽量手动指定需要返回的属性。若返回全部属性，可能后由于Prop.java里没有该属性名，而崩溃�?            val requestBody = requestPropsStr.toRequestBody("text/plain".toMediaType())
            method("PROPFIND", requestBody)
        }.apply {
            checkResult(this)
        }.body.text()
    }

    /**
     * 解析webDav返回的xml
     */
    private fun parseBody(s: String): List<WebDavFile> {
        val list = ArrayList<WebDavFile>()
        val document = kotlin.runCatching {
            Jsoup.parse(s, Parser.xmlParser())
        }.getOrElse {
            Jsoup.parse(s)
        }
        val ns = document.findNSPrefix("DAV:")
        val elements = document.findNS("response", ns)
        val urlStr = httpUrl ?: return list
        val baseUrl = NetworkUtils.getBaseUrl(urlStr)
        for (element in elements) {
            //依然是优化支�?caddy 自建�?WebDav ，其目录后缀都为�?�? 所以删除�?”的判定，不然无法获取该目录�?            val href = element.findNS("href", ns)[0].text()
            val hrefDecode = URLDecoder.decodeForPath(href, Charsets.UTF_8)
            val fileName = hrefDecode.removeSuffix("/").substringAfterLast("/")
            val webDavFile: WebDav
            try {
                val urlName = hrefDecode.ifEmpty {
                    url.file.replace("/", "")
                }
                val displayName = element
                    .findNS("displayname", ns)
                    .firstOrNull()?.text()?.takeIf { it.isNotEmpty() }
                    ?.let { URLDecoder.decodeForPath(it, Charsets.UTF_8) } ?: fileName
                val contentType = element
                    .findNS("getcontenttype", ns)
                    .firstOrNull()?.text().orEmpty()
                val resourceType = element
                    .findNS("resourcetype", ns)
                    .firstOrNull()?.html()?.trim().orEmpty()
                val size = kotlin.runCatching {
                    element.findNS("getcontentlength", ns)
                        .firstOrNull()?.text()?.toLong() ?: 0
                }.getOrDefault(0)
                val lastModify: Long = kotlin.runCatching {
                    element.findNS("getlastmodified", ns)
                        .firstOrNull()?.text()?.let {
                            ZonedDateTime.parse(it, dateTimeFormatter)
                                .toInstant().toEpochMilli()
                        }
                }.getOrNull() ?: 0
                var fullURL = NetworkUtils.getAbsoluteURL(baseUrl, hrefDecode)
                if (WebDavFile.isDir(contentType, resourceType) && !fullURL.endsWith("/")) {
                    fullURL += "/"
                }
                webDavFile = WebDavFile(
                    fullURL,
                    authorization,
                    displayName = displayName,
                    urlName = urlName,
                    size = size,
                    contentType = contentType,
                    resourceType = resourceType,
                    lastModify = lastModify
                )
                list.add(webDavFile)
            } catch (e: MalformedURLException) {
                e.printOnDebug()
            }
        }
        return list
    }

    /**
     * 文件是否存在
     */
    suspend fun exists(): Boolean {
        val url = httpUrl ?: return false
        return kotlin.runCatching {
            return webDavClient.newCallResponse {
                url(url)
                addHeader("Depth", "0")
                val requestBody = EXISTS.toRequestBody("application/xml".toMediaType())
                method("PROPFIND", requestBody)
            }.use { it.isSuccessful }
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(false)
    }

    /**
     * 检查用户名密码是否有效
     */
    suspend fun check(): Boolean {
        return kotlin.runCatching {
            webDavClient.newCallResponse {
                url(url)
                addHeader("Depth", "0")
                val requestBody = EXISTS.toRequestBody("application/xml".toMediaType())
                method("PROPFIND", requestBody)
            }.use { it.code != 401 }
        }.onFailure {
            currentCoroutineContext().ensureActive()
        }.getOrDefault(true)
    }

    /**
     * 根据自己的URL，在远程处创建对应的文件�?     * @return 是否创建成功
     */
    suspend fun makeAsDir(): Boolean {
        val url = httpUrl ?: return false
        //防止报错
        return kotlin.runCatching {
            if (!exists()) {
                webDavClient.newCallResponse {
                    url(url)
                    method("MKCOL", null)
                }.use {
                    checkResult(it)
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav创建目录失败\n${it.localizedMessage}", it)
        }.isSuccess
    }

    /**
     * 下载到本�?     * @param savedPath       本地的完整路径，包括最后的文件�?     * @param replaceExisting 是否替换本地的同名文�?     */
    @Throws(WebDavException::class)
    suspend fun downloadTo(savedPath: String, replaceExisting: Boolean) {
        val file = File(savedPath)
        if (file.exists() && !replaceExisting) {
            return
        }
        downloadInputStream().use { byteStream ->
            FileOutputStream(file).use {
                byteStream.copyTo(it)
            }
        }
    }

    /**
     * 下载文件,返回ByteArray
     */
    @Throws(WebDavException::class)
    suspend fun download(): ByteArray {
        return downloadInputStream().use {
            it.readBytes()
        }
    }

    /**
     * 上传文件
     */
    @Throws(WebDavException::class)
    suspend fun upload(localPath: String, contentType: String = DEFAULT_CONTENT_TYPE) {
        upload(File(localPath), contentType)
    }

    @Throws(WebDavException::class)
    suspend fun upload(file: File, contentType: String = DEFAULT_CONTENT_TYPE) {
        kotlin.runCatching {
            withContext(IO) {
                if (!file.exists()) throw WebDavException("文件不存�?)
                // 务必注意RequestBody不要嵌套，不然上传时内容可能会被追加多余的文件信�?                val fileBody = file.asRequestBody(contentType.toMediaType())
                val url = httpUrl ?: throw WebDavException("url不能为空")
                webDavClient.newCallResponse {
                    url(url)
                    put(fileBody)
                }.use {
                    checkResult(it)
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav上传失败\n${it.localizedMessage}", it)
            throw WebDavException("WebDav上传失败\n${it.localizedMessage}")
        }
    }

    @Throws(WebDavException::class)
    suspend fun upload(byteArray: ByteArray, contentType: String = DEFAULT_CONTENT_TYPE) {
        // 务必注意RequestBody不要嵌套，不然上传时内容可能会被追加多余的文件信�?        kotlin.runCatching {
            withContext(IO) {
                val fileBody = byteArray.toRequestBody(contentType.toMediaType())
                val url = httpUrl ?: throw NoStackTraceException("url不能为空")
                webDavClient.newCallResponse {
                    url(url)
                    put(fileBody)
                }.use {
                    checkResult(it)
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav上传失败\n${it.localizedMessage}", it)
            throw WebDavException("WebDav上传失败\n${it.localizedMessage}")
        }
    }

    @Throws(WebDavException::class)
    suspend fun upload(uri: Uri, contentType: String = DEFAULT_CONTENT_TYPE) {
        // 务必注意RequestBody不要嵌套，不然上传时内容可能会被追加多余的文件信�?        kotlin.runCatching {
            withContext(IO) {
                val fileBody = uri.toRequestBody(contentType.toMediaType())
                val url = httpUrl ?: throw NoStackTraceException("url不能为空")
                webDavClient.newCallResponse {
                    url(url)
                    put(fileBody)
                }.use {
                    checkResult(it)
                }
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav上传失败\n${it.localizedMessage}", it)
            throw WebDavException("WebDav上传失败\n${it.localizedMessage}")
        }
    }

    @Throws(WebDavException::class)
    suspend fun downloadInputStream(): InputStream {
        val url = httpUrl ?: throw WebDavException("WebDav下载出错\nurl为空")
        val byteStream = webDavClient.newCallResponse {
            url(url)
        }.apply {
            checkResult(this)
        }.body.byteStream()
        return byteStream
    }

    /**
     * 移除文件/文件�?     */
    suspend fun delete(): Boolean {
        val url = httpUrl ?: return false
        //防止报错
        return kotlin.runCatching {
            webDavClient.newCallResponse {
                url(url)
                method("DELETE", null)
            }.use {
                checkResult(it)
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("WebDav删除失败\n${it.localizedMessage}", it)
        }.isSuccess
    }

    /**
     * 检测返回结果是否正�?     */
    private fun checkResult(response: Response) {
        if (!response.isSuccessful) {
            val body = response.body.string()
            if (response.code == 401) {
                val headers = response.headers("WWW-Authenticate")
                val supportBasicAuth = headers.any {
                    it.startsWith("Basic", ignoreCase = true)
                }
                if (headers.isNotEmpty() && !supportBasicAuth) {
                    AppLog.put("服务器不支持BasicAuth认证")
                }
            }

            if (response.message.isNotBlank() || body.isBlank()) {
                throw WebDavException("${url}\n${response.code}:${response.message}")
            }
            val document = Jsoup.parse(body)
            val exception = document.getElementsByTag("s:exception").firstOrNull()?.text()
            val message = document.getElementsByTag("s:message").firstOrNull()?.text()
            if (exception == "ObjectNotFound") {
                throw ObjectNotFoundException(
                    message ?: "$path doesn't exist. code:${response.code}"
                )
            }
            throw WebDavException(message ?: "未知错误 code:${response.code}")
        }
    }

}
