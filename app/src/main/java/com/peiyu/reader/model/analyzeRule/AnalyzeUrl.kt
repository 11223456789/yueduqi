package com.peiyu.reader.model.analyzeRule

import android.annotation.SuppressLint
import android.util.Base64
import androidx.annotation.Keep
import androidx.media3.common.MediaItem
import cn.hutool.core.codec.PercentCodec
import cn.hutool.core.net.RFC3986
import cn.hutool.core.util.HexUtil
import com.bumptech.glide.load.model.GlideUrl
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.runScriptWithContext
import com.peiyu.reader.constant.AppConst.UA_NAME
import com.peiyu.reader.constant.AppPattern
import com.peiyu.reader.constant.AppPattern.JS_PATTERN
import com.peiyu.reader.constant.AppPattern.dataUriRegex
import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.data.entities.BookChapter
import com.peiyu.reader.help.CacheManager
import com.peiyu.reader.help.ConcurrentRateLimiter
import com.peiyu.reader.help.JsExtensions
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.exoplayer.ExoPlayerHelper
import com.peiyu.reader.help.glide.GlideHeaders
import com.peiyu.reader.help.http.BackstageWebView
import com.peiyu.reader.help.http.CookieManager
import com.peiyu.reader.help.http.CookieManager.mergeCookies
import com.peiyu.reader.help.http.CookieStore
import com.peiyu.reader.help.http.RequestMethod
import com.peiyu.reader.help.http.StrResponse
import com.peiyu.reader.help.http.addHeaders
import com.peiyu.reader.help.http.get
import com.peiyu.reader.help.http.getProxyClient
import com.peiyu.reader.help.http.newCallResponse
import com.peiyu.reader.help.http.newCallStrResponse
import com.peiyu.reader.help.http.postForm
import com.peiyu.reader.help.http.postJson
import com.peiyu.reader.help.http.postMultipart
import com.peiyu.reader.help.source.getShareScope
import com.peiyu.reader.utils.EncoderUtils
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.GSONStrict
import com.peiyu.reader.utils.NetworkUtils
import com.peiyu.reader.utils.fromJsonArray
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.get
import com.peiyu.reader.utils.isJson
import com.peiyu.reader.utils.isJsonArray
import com.peiyu.reader.utils.isJsonObject
import com.peiyu.reader.utils.isXml
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max

/**
 * Created by GKF on 2018/1/24.
 * 搜索URL规则解析
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@Keep
@SuppressLint("DefaultLocale")
class AnalyzeUrl(
    private val mUrl: String,
    private val key: String? = null,
    private val page: Int? = null,
    private val speakText: String? = null,
    private val speakSpeed: Int? = null,
    private var baseUrl: String = "",
    private val source: BaseSource? = null,
    private val ruleData: RuleDataInterface? = null,
    private val chapter: BookChapter? = null,
    private val readTimeout: Long? = null,
    private val callTimeout: Long? = null,
    private var coroutineContext: CoroutineContext = EmptyCoroutineContext,
    headerMapF: Map<String, String>? = null,
    hasLoginHeader: Boolean = true
) : JsExtensions {

    var ruleUrl = ""
        private set
    var url: String = ""
        private set
    var type: String? = null
        private set
    val headerMap = LinkedHashMap<String, String>()
    private var body: String? = null
    private var urlNoQuery: String = ""
    private var encodedForm: String? = null
    private var encodedQuery: String? = null
    private var charset: String? = null
    private var method = RequestMethod.GET
    private var proxy: String? = null
    private var retry: Int = 0
    private var useWebView: Boolean = false
    private var webJs: String? = null
    private val enabledCookieJar = source?.enabledCookieJar == true
    private val domain: String
    private var webViewDelayTime: Long = 0
    private val concurrentRateLimiter = ConcurrentRateLimiter(source)

    // 服务器ID
    var serverID: Long? = null
        private set

    init {
        coroutineContext = coroutineContext.minusKey(ContinuationInterceptor)
        val urlMatcher = paramPattern.matcher(baseUrl)
        if (urlMatcher.find()) baseUrl = baseUrl.substring(0, urlMatcher.start())
        (headerMapF ?: runScriptWithContext(coroutineContext) {
            source?.getHeaderMap(hasLoginHeader)
        })?.let {
            headerMap.putAll(it)
            if (it.containsKey("proxy")) {
                proxy = it["proxy"]
                headerMap.remove("proxy")
            }
        }
        initUrl()
        domain = NetworkUtils.getSubDomain(source?.getKey() ?: url)
    }

    /**
     * 处理url
     */
    fun initUrl() {
        ruleUrl = mUrl
        //执行@js,<js></js>
        analyzeJs()
        //替换参数
        replaceKeyPageJs()
        //处理URL
        analyzeUrl()
    }

    /**
     * 执行@js,<js></js>
     */
    private fun analyzeJs() {
        var start = 0
        val jsMatcher = JS_PATTERN.matcher(ruleUrl)
        var result = ruleUrl
        while (jsMatcher.find()) {
            if (jsMatcher.start() > start) {
                ruleUrl.substring(start, jsMatcher.start()).trim().let {
                    if (it.isNotEmpty()) {
                        result = it.replace("@result", result)
                    }
                }
            }
            result = evalJS(jsMatcher.group(2) ?: jsMatcher.group(1), result).toString()
            start = jsMatcher.end()
        }
        if (ruleUrl.length > start) {
            ruleUrl.substring(start).trim().let {
                if (it.isNotEmpty()) {
                    result = it.replace("@result", result)
                }
            }
        }
        ruleUrl = result
    }

    /**
     * 替换关键�?页数,JS
     */
    private fun replaceKeyPageJs() { //先替换内嵌规则再替换页数规则，避免内嵌规则中存在大于小于号时，规则被切错
        //js
        if (ruleUrl.contains("{{") && ruleUrl.contains("}}")) {
            val analyze = RuleAnalyzer(ruleUrl) //创建解析
            //替换所有内嵌{{js}}
            val url = analyze.innerRule("{{", "}}") {
                val jsEval = evalJS(it) ?: ""
                when {
                    jsEval is String -> jsEval
                    jsEval is Double && jsEval % 1.0 == 0.0 -> String.format("%.0f", jsEval)
                    else -> jsEval.toString()
                }
            }
            if (url.isNotEmpty()) ruleUrl = url
        }
        //page
        page?.let {
            val matcher = pagePattern.matcher(ruleUrl)
            while (matcher.find()) {
                val pages = matcher.group(1)!!.split(",")
                ruleUrl = if (page < pages.size) { //pages[pages.size - 1]等同于pages.last()
                    ruleUrl.replace(matcher.group(), pages[page - 1].trim { it <= ' ' })
                } else {
                    ruleUrl.replace(matcher.group(), pages.last().trim { it <= ' ' })
                }
            }
        }
    }

    /**
     * 解析Url
     */
    private fun analyzeUrl() {
        //replaceKeyPageJs已经替换掉额外内容，此处url是基础形式，可以直接切首个�?’之前字符串�?        val urlMatcher = paramPattern.matcher(ruleUrl)
        val urlNoOption =
            if (urlMatcher.find()) ruleUrl.substring(0, urlMatcher.start()) else ruleUrl
        url = NetworkUtils.getAbsoluteURL(baseUrl, urlNoOption)
        NetworkUtils.getBaseUrl(url)?.let {
            baseUrl = it
        }
        if (urlNoOption.length != ruleUrl.length) {
            val urlOptionStr = ruleUrl.substring(urlMatcher.end())
            var urlOption = GSONStrict.fromJsonObject<UrlOption>(urlOptionStr).getOrNull()
            if (urlOption == null) {
                urlOption = GSON.fromJsonObject<UrlOption>(urlOptionStr).getOrNull()
                if (urlOption != null) {
                    log("链接参数 JSON 格式不规范，请改为规范格�?)
                }
            }
            urlOption?.let { option ->
                option.getMethod()?.let {
                    if (it.equals("POST", true)) method = RequestMethod.POST
                }
                option.getHeaderMap()?.forEach { entry ->
                    headerMap[entry.key.toString()] = entry.value.toString()
                }
                option.getBody()?.let {
                    body = it
                }
                type = option.getType()
                charset = option.getCharset()
                retry = option.getRetry()
                useWebView = option.useWebView()
                webJs = option.getWebJs()
                option.getJs()?.let { jsStr ->
                    evalJS(jsStr, url)?.toString()?.let {
                        url = it
                    }
                }
                serverID = option.getServerID()
                webViewDelayTime = max(0, option.getWebViewDelayTime() ?: 0)
            }
        }
        urlNoQuery = url
        when (method) {
            RequestMethod.GET -> {
                val pos = url.indexOf('?')
                if (pos != -1) {
                    analyzeQuery(url.substring(pos + 1))
                    urlNoQuery = url.substring(0, pos)
                }
            }

            RequestMethod.POST -> body?.let {
                if (!it.isJson() && !it.isXml() && headerMap["Content-Type"].isNullOrEmpty()) {
                    analyzeFields(it)
                }
            }
        }
    }

    /**
     * 解析QueryMap <key>=<value>
     * name=
     * name=name
     * name=<BASE64> eg name=bmFtZQ==
     */
    private fun analyzeFields(fieldsTxt: String) {
        encodedForm = encodeParams(fieldsTxt, charset, false)
    }

    private fun analyzeQuery(query: String) {
        encodedQuery = encodeParams(query, charset, true)
    }

    private fun encodeParams(params: String, charset: String?, isQuery: Boolean): String {
        val checkEncoded = charset.isNullOrEmpty()
        val charset = when {
            charset.isNullOrEmpty() -> Charsets.UTF_8
            charset == "escape" -> null
            else -> charset(charset)
        }
        if (isQuery && charset != null) {
            if (NetworkUtils.encodedQuery(params)) {
                return params
            }
            return queryEncoder.encode(params, charset)
        }
        val len = params.length
        val sb = StringBuilder()
        var pos = 0
        while (pos <= len) {
            if (sb.isNotEmpty()) {
                sb.append("&")
            }
            var ampOffset = params.indexOf("&", pos)
            if (ampOffset == -1) {
                ampOffset = len
            }
            val eqOffset = params.indexOf("=", pos)
            val key: String
            val value: String?
            if (eqOffset == -1 || eqOffset > ampOffset) {
                key = params.substring(pos, ampOffset)
                value = null
            } else {
                key = params.substring(pos, eqOffset)
                value = params.substring(eqOffset + 1, ampOffset)
            }
            sb.appendEncoded(key, checkEncoded, charset)
            if (value != null) {
                sb.append("=")
                sb.appendEncoded(value, checkEncoded, charset)
            }
            pos = ampOffset + 1
        }
        return sb.toString()
    }

    private fun StringBuilder.appendEncoded(
        value: String,
        checkEncoded: Boolean,
        charset: Charset?
    ) {
        if (checkEncoded && NetworkUtils.encodedForm(value)) {
            append(value)
        } else if (charset == null) {
            append(EncoderUtils.escape(value))
        } else {
            append(URLEncoder.encode(value, charset))
        }
    }

    /**
     * 执行JS
     */
    fun evalJS(jsStr: String, result: Any? = null): Any? {
        val bindings = buildScriptBindings { bindings ->
            bindings["java"] = this
            bindings["baseUrl"] = baseUrl
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings["page"] = page
            bindings["key"] = key
            bindings["speakText"] = speakText
            bindings["speakSpeed"] = speakSpeed
            bindings["book"] = ruleData as? Book
            bindings["source"] = source
            bindings["result"] = result
        }
        val sharedScope = source?.getShareScope(coroutineContext)
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply {
                prototype = sharedScope
            }
        }
        return RhinoScriptEngine.eval(jsStr, scope, coroutineContext)
    }

    fun put(key: String, value: String): String {
        chapter?.putVariable(key, value)
            ?: ruleData?.putVariable(key, value)
        return value
    }

    fun get(key: String): String {
        when (key) {
            "bookName" -> (ruleData as? Book)?.let {
                return it.name
            }

            "title" -> chapter?.let {
                return it.title
            }
        }
        return chapter?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ruleData?.getVariable(key)?.takeIf { it.isNotEmpty() }
            ?: ""
    }

    /**
     * 访问网站,返回StrResponse
     */
    suspend fun getStrResponseAwait(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
    ): StrResponse {
        if (type != null) {
            return StrResponse(url, HexUtil.encodeHexStr(getByteArrayAwait()))
        }
        concurrentRateLimiter.withLimit {
            setCookie()
            val strResponse: StrResponse
            if (this.useWebView && useWebView) {
                strResponse = when (method) {
                    RequestMethod.POST -> {
                        val res = getClient().newCallStrResponse(retry) {
                            addHeaders(headerMap)
                            url(urlNoQuery)
                            if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                                postForm(encodedForm ?: "")
                            } else {
                                postJson(body)
                            }
                        }
                        BackstageWebView(
                            url = res.url,
                            html = res.body,
                            tag = source?.getKey(),
                            javaScript = webJs ?: jsStr,
                            sourceRegex = sourceRegex,
                            headerMap = headerMap,
                            delayTime = webViewDelayTime
                        ).getStrResponse()
                    }

                    else -> BackstageWebView(
                        url = url,
                        tag = source?.getKey(),
                        javaScript = webJs ?: jsStr,
                        sourceRegex = sourceRegex,
                        headerMap = headerMap,
                        delayTime = webViewDelayTime
                    ).getStrResponse()
                }
            } else {
                strResponse = getClient().newCallStrResponse(retry) {
                    addHeaders(headerMap)
                    when (method) {
                        RequestMethod.POST -> {
                            url(urlNoQuery)
                            val contentType = headerMap["Content-Type"]
                            val body = body
                            if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                                postForm(encodedForm ?: "")
                            } else if (!contentType.isNullOrBlank()) {
                                val requestBody = body.toRequestBody(contentType.toMediaType())
                                post(requestBody)
                            } else {
                                postJson(body)
                            }
                        }

                        else -> get(urlNoQuery, encodedQuery)
                    }
                }.let {
                    val isXml = it.raw.body.contentType()?.toString()
                        ?.matches(AppPattern.xmlContentTypeRegex) == true
                    if (isXml && it.body?.trim()?.startsWith("<?xml", true) == false) {
                        StrResponse(it.raw, "<?xml version=\"1.0\"?>" + it.body)
                    } else it
                }
            }
            return strResponse
        }
    }

    @JvmOverloads
    fun getStrResponse(
        jsStr: String? = null,
        sourceRegex: String? = null,
        useWebView: Boolean = true,
    ): StrResponse {
        return runBlocking(coroutineContext) {
            getStrResponseAwait(jsStr, sourceRegex, useWebView)
        }
    }

    /**
     * 访问网站,返回Response
     */
    suspend fun getResponseAwait(): Response {
        concurrentRateLimiter.withLimit {
            setCookie()
            val response = getClient().newCallResponse(retry) {
                addHeaders(headerMap)
                when (method) {
                    RequestMethod.POST -> {
                        url(urlNoQuery)
                        val contentType = headerMap["Content-Type"]
                        val body = body
                        if (!encodedForm.isNullOrEmpty() || body.isNullOrBlank()) {
                            postForm(encodedForm ?: "")
                        } else if (!contentType.isNullOrBlank()) {
                            val requestBody = body.toRequestBody(contentType.toMediaType())
                            post(requestBody)
                        } else {
                            postJson(body)
                        }
                    }

                    else -> get(urlNoQuery, encodedQuery)
                }
            }
            return response
        }
    }

    private fun getClient(): OkHttpClient {
        val client = getProxyClient(proxy)
        if (readTimeout == null && callTimeout == null) {
            return client
        }
        return client.newBuilder().run {
            if (readTimeout != null) {
                readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                callTimeout(max(60 * 1000L, readTimeout * 2), TimeUnit.MILLISECONDS)
            }
            if (callTimeout != null) {
                callTimeout(callTimeout, TimeUnit.MILLISECONDS)
            }
            build()
        }
    }

    fun getResponse(): Response {
        return runBlocking(coroutineContext) {
            getResponseAwait()
        }
    }

    private fun getByteArrayIfDataUri(): ByteArray? {
        if (!urlNoQuery.startsWith("data:")) {
            return null
        }
        val dataUriFindResult = dataUriRegex.find(urlNoQuery)
        if (dataUriFindResult != null) {
            val dataUriBase64 = dataUriFindResult.groupValues[1]
            val byteArray = Base64.decode(dataUriBase64, Base64.DEFAULT)
            return byteArray
        }
        return null
    }

    /**
     * 访问网站,返回ByteArray
     */
    suspend fun getByteArrayAwait(): ByteArray {
        getByteArrayIfDataUri()?.let {
            return it
        }
        return getResponseAwait().body.bytes()
    }

    fun getByteArray(): ByteArray {
        return runBlocking(coroutineContext) {
            getByteArrayAwait()
        }
    }

    /**
     * 访问网站,返回InputStream
     */
    suspend fun getInputStreamAwait(): InputStream {
        getByteArrayIfDataUri()?.let {
            return ByteArrayInputStream(it)
        }
        return getResponseAwait().body.byteStream()
    }

    fun getInputStream(): InputStream {
        return runBlocking(coroutineContext) {
            getInputStreamAwait()
        }
    }

    /**
     * 上传文件
     */
    suspend fun upload(fileName: String, file: Any, contentType: String): StrResponse {
        return getProxyClient(proxy).newCallStrResponse(retry) {
            url(urlNoQuery)
            val bodyMap = GSON.fromJsonObject<HashMap<String, Any>>(body).getOrNull()!!
            bodyMap.forEach { entry ->
                if (entry.value.toString() == "fileRequest") {
                    bodyMap[entry.key] = mapOf(
                        Pair("fileName", fileName),
                        Pair("file", file),
                        Pair("contentType", contentType)
                    )
                }
            }
            postMultipart(type, bodyMap)
        }
    }

    /**
     * 设置cookie 优先�?     * urlOption临时cookie > 数据库cookie
     */
    private fun setCookie() {
        val cookie = kotlin.run {
            /* 每次调用getXX cookieJar已经保存过了
            if (enabledCookieJar) {
                val key = "${domain}_cookieJar"
                CacheManager.getFromMemory(key)?.let {
                    return@run it
                }
            }
            */
            CookieStore.getCookie(domain)
        }
        if (cookie.isNotEmpty()) {
            mergeCookies(cookie, headerMap["Cookie"])?.let {
                headerMap.put("Cookie", it)
            }
        }
        if (enabledCookieJar) {
            headerMap[CookieManager.cookieJarHeader] = "1"
        } else {
            headerMap.remove(CookieManager.cookieJarHeader)
        }
    }

    /**
     * 保存cookieJar中的cookie在访问结束时就保�?不等到下次访�?     */
    private fun saveCookie() {
        //书源启用保存cookie�?添加内存中的cookie到数据库
        if (enabledCookieJar) {
            val key = "${domain}_cookieJar"
            CacheManager.getFromMemory(key)?.let {
                if (it is String) {
                    CookieStore.replaceCookie(domain, it)
                    CacheManager.deleteMemory(key)
                }
            }
        }
    }

    /**
     *获取处理过阅读定义的urlOption和cookie的GlideUrl
     */
    fun getGlideUrl(): GlideUrl {
        setCookie()
        return GlideUrl(url, GlideHeaders(headerMap))
    }

    fun getUserAgent(): String {
        return headerMap.get(UA_NAME, true) ?: AppConfig.userAgent
    }

    fun isPost(): Boolean {
        return method == RequestMethod.POST
    }

    override fun getSource(): BaseSource? {
        return source
    }

    companion object {
        val paramPattern: Pattern = Pattern.compile("\\s*,\\s*(?=\\{)")
        private val pagePattern = Pattern.compile("<(.*?)>")
        private val queryEncoder =
            RFC3986.UNRESERVED.orNew(PercentCodec.of("!$%&()*+,/:;=?@[\\]^`{|}"))

        fun AnalyzeUrl.getMediaItem(): MediaItem {
            setCookie()
            return ExoPlayerHelper.createMediaItem(url, headerMap)
        }

    }

    @Keep
    data class UrlOption(
        private var method: String? = null,
        private var charset: String? = null,
        private var headers: Any? = null,
        private var body: Any? = null,
        /**
         * 源Url
         **/
        private var origin: String? = null,
        /**
         * 重试次数
         **/
        private var retry: Int? = null,
        /**
         * 类型
         **/
        private var type: String? = null,
        /**
         * 是否使用webView
         **/
        private var webView: Any? = null,
        /**
         * webView中执行的js
         **/
        private var webJs: String? = null,
        /**
         * 解析完url参数时执行的js
         * 执行结果会赋值给url
         */
        private var js: String? = null,
        /**
         * 服务器id
         */
        private var serverID: Long? = null,
        /**
         * webview等待页面加载完毕的延迟时间（毫秒�?         */
        private var webViewDelayTime: Long? = null,
    ) {
        fun setMethod(value: String?) {
            method = if (value.isNullOrBlank()) null else value
        }

        fun getMethod(): String? {
            return method
        }

        fun setCharset(value: String?) {
            charset = if (value.isNullOrBlank()) null else value
        }

        fun getCharset(): String? {
            return charset
        }

        fun setOrigin(value: String?) {
            origin = if (value.isNullOrBlank()) null else value
        }

        fun getOrigin(): String? {
            return origin
        }

        fun setRetry(value: String?) {
            retry = if (value.isNullOrEmpty()) null else value.toIntOrNull()
        }

        fun getRetry(): Int {
            return retry ?: 0
        }

        fun setType(value: String?) {
            type = if (value.isNullOrBlank()) null else value
        }

        fun getType(): String? {
            return type
        }

        fun useWebView(): Boolean {
            return when (webView) {
                null, "", false, "false" -> false
                else -> true
            }
        }

        fun useWebView(boolean: Boolean) {
            webView = if (boolean) true else null
        }

        fun setHeaders(value: String?) {
            headers = if (value.isNullOrBlank()) {
                null
            } else {
                GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
            }
        }

        fun getHeaderMap(): Map<*, *>? {
            return when (val value = headers) {
                is Map<*, *> -> value
                is String -> GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
                else -> null
            }
        }

        fun setBody(value: String?) {
            body = when {
                value.isNullOrBlank() -> null
                value.isJsonObject() -> GSON.fromJsonObject<Map<String, Any>>(value).getOrNull()
                value.isJsonArray() -> GSON.fromJsonArray<Map<String, Any>>(value).getOrNull()
                else -> value
            }
        }

        fun getBody(): String? {
            return body?.let {
                it as? String ?: GSON.toJson(it)
            }
        }

        fun setWebJs(value: String?) {
            webJs = if (value.isNullOrBlank()) null else value
        }

        fun getWebJs(): String? {
            return webJs
        }

        fun setJs(value: String?) {
            js = if (value.isNullOrBlank()) null else value
        }

        fun getJs(): String? {
            return js
        }

        fun setServerID(value: String?) {
            serverID = if (value.isNullOrBlank()) null else value.toLong()
        }

        fun getServerID(): Long? {
            return serverID
        }

        fun setWebViewDelayTime(value: String?) {
            webViewDelayTime = if (value.isNullOrBlank()) null else value.toLong()
        }

        fun getWebViewDelayTime(): Long? {
            return webViewDelayTime
        }
    }

    data class ConcurrentRecord(
        /**
         * 是否按频�?         */
        val isConcurrent: Boolean,
        /**
         * 开始访问时�?         */
        var time: Long,
        /**
         * 正在访问的个�?         */
        var frequency: Int
    )

}
