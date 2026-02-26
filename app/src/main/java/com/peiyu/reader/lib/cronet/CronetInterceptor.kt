package com.peiyu.reader.lib.cronet

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.Keep
import com.peiyu.reader.help.http.CookieManager
import com.peiyu.reader.help.http.CookieManager.cookieJarHeader
import com.peiyu.reader.utils.printOnDebug
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Keep
@Suppress("unused")
class CronetInterceptor(private val cookieJar: CookieJar) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.call().isCanceled()) {
            throw IOException("Canceled")
        }
        val original: Request = chain.request()
        //Cronet未初始化
        if (!CronetLoader.install() || cronetEngine == null) {
            return chain.proceed(original)
        }
        val cronetException: Exception
        try {
            val builder: Request.Builder = original.newBuilder()
            //移除Keep-Alive,手动设置会导�?00 BadRequest
            builder.removeHeader("Keep-Alive")
            builder.removeHeader("Accept-Encoding")

            // https://github.com/gedoor/legado/issues/5025#issuecomment-2851156500
            if (!original.isHttps &&
                original.header("User-Agent")?.startsWith("Mozilla", true) == true
            ) {
                val referer = original.header("Referer")
                if (referer != null && referer.startsWith("https:", true)) {
                    builder.header("Referer", "http" + referer.substring(5))
                }
            }

            var newReq = builder.build()

            if (newReq.header(cookieJarHeader) != null) {
                newReq = CookieManager.loadRequest(newReq)
            }

            return proceedWithCronet(newReq, chain.call(), chain.readTimeoutMillis())!!
        } catch (e: Exception) {
            cronetException = e
            //不能抛出错误,抛出错误会导致应用崩�?            //遇到Cronet处理有问题时的情况，如证书过期等等，回退到okhttp处理
            if (!e.message.toString().contains("ERR_CERT_", true)
                && !e.message.toString().contains("ERR_SSL_", true)
            ) {
                e.printOnDebug()
            }
        }
        try {
            return chain.proceed(original)
        } catch (e: Exception) {
            e.addSuppressed(cronetException)
            throw e
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @Throws(IOException::class)
    private fun proceedWithCronet(request: Request, call: Call, readTimeoutMillis: Int): Response? {
        val callBack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NewCallBack(request, call, readTimeoutMillis)
        } else {
            OldCallback(request, call, readTimeoutMillis)
        }
        buildRequest(request, callBack)?.let {
            return callBack.waitForDone(it)
        }
        return null
    }


    /** Returns a 'Cookie' HTTP request header with all cookies, like `a=b; c=d`. */
    private fun getCookie(url: HttpUrl): String = buildString {
        val cookies = cookieJar.loadForRequest(url)
        cookies.forEachIndexed { index, cookie ->
            if (index > 0) append("; ")
            append(cookie.name).append('=').append(cookie.value)
        }
    }

}
