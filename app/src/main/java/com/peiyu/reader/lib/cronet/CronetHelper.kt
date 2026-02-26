@file:Keep
@file:Suppress("DEPRECATION")

package com.peiyu.reader.lib.cronet

import androidx.annotation.Keep
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.help.http.CookieManager.cookieJarHeader
import com.peiyu.reader.help.http.SSLHelper
import com.peiyu.reader.help.http.okHttpClient
import com.peiyu.reader.utils.DebugLog
import com.peiyu.reader.utils.externalCache
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import org.chromium.net.CronetEngine.Builder.HTTP_CACHE_DISK
import org.chromium.net.ExperimentalCronetEngine
import org.chromium.net.UploadDataProvider
import org.chromium.net.UrlRequest
import org.chromium.net.X509Util
import org.json.JSONObject
import splitties.init.appCtx

internal const val BUFFER_SIZE = 32 * 1024

val cronetEngine: ExperimentalCronetEngine? by lazy {
    CronetLoader.preDownload()
    disableCertificateVerify()
    val builder = ExperimentalCronetEngine.Builder(appCtx).apply {
        if (CronetLoader.install()) {
            setLibraryLoader(CronetLoader)//设置自定义so库加�?        }
        setStoragePath(appCtx.externalCache.absolutePath)//设置缓存路径
        enableHttpCache(HTTP_CACHE_DISK, (1024 * 1024 * 50).toLong())//设置50M的磁盘缓�?        enableQuic(true)//设置支持http/3
        enableHttp2(true)  //设置支持http/2
        enablePublicKeyPinningBypassForLocalTrustAnchors(true)
        enableBrotli(true)//Brotli压缩
        setExperimentalOptions(options)
    }
    try {
        val engine = builder.build()
        DebugLog.d("Cronet Version:", engine.versionString)
        return@lazy engine
    } catch (e: Throwable) {
        AppLog.put("初始化cronetEngine出错", e)
        return@lazy null
    }
}

val options by lazy {
    val options = JSONObject()

    //设置域名映射规则
    //MAP hostname ip,MAP hostname ip
//    val host = JSONObject()
//    host.put("host_resolver_rules","")
//    options.put("HostResolverRules", host)

    //启用DnsHttpsSvcb更容易迁移到http3
    val dnsSvcb = JSONObject()
    dnsSvcb.put("enable", true)
    dnsSvcb.put("enable_insecure", true)
    dnsSvcb.put("use_alpn", true)
    options.put("UseDnsHttpsSvcb", dnsSvcb)

    options.put("AsyncDNS", JSONObject("{'enable':true}"))


    options.toString()
}

fun buildRequest(request: Request, callback: UrlRequest.Callback): UrlRequest? {
    val url = request.url.toString()
    val headers: Headers = request.headers
    val requestBody = request.body
    return cronetEngine?.newUrlRequestBuilder(
        url,
        callback,
        okHttpClient.dispatcher.executorService
    )?.apply {
        setHttpMethod(request.method)//设置
        allowDirectExecutor()
        headers.forEachIndexed { index, _ ->
            if (headers.name(index) == cookieJarHeader) return@forEachIndexed
            addHeader(headers.name(index), headers.value(index))
        }
        if (requestBody != null) {
            val contentType: MediaType? = requestBody.contentType()
            if (contentType != null) {
                addHeader("Content-Type", contentType.toString())
            } else {
                addHeader("Content-Type", "text/plain")
            }
            val provider: UploadDataProvider = if (requestBody.contentLength() > BUFFER_SIZE) {
                LargeBodyUploadProvider(requestBody, okHttpClient.dispatcher.executorService)
            } else {
                BodyUploadProvider(requestBody)
            }
            provider.use {
                this.setUploadDataProvider(it, okHttpClient.dispatcher.executorService)
            }

        }

    }?.build()

}

private fun disableCertificateVerify() {
    runCatching {
        val sDefaultTrustManager = X509Util::class.java.getDeclaredField("sDefaultTrustManager")
        sDefaultTrustManager.isAccessible = true
        sDefaultTrustManager.set(null, SSLHelper.unsafeTrustManagerExtensions)
    }
    runCatching {
        val sTestTrustManager = X509Util::class.java.getDeclaredField("sTestTrustManager")
        sTestTrustManager.isAccessible = true
        sTestTrustManager.set(null, SSLHelper.unsafeTrustManagerExtensions)
    }
}
