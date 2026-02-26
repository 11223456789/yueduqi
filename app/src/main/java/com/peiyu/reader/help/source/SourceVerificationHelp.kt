package com.peiyu.reader.help.source

import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.help.CacheManager
import com.peiyu.reader.help.IntentData
import com.peiyu.reader.ui.association.VerificationCodeActivity
import com.peiyu.reader.ui.browser.WebViewActivity
import com.peiyu.reader.utils.isMainThread
import com.peiyu.reader.utils.startActivity
import splitties.init.appCtx
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.minutes

/**
 * 源验�? */
object SourceVerificationHelp {

    private val waitTime = 1.minutes.inWholeNanoseconds

    private fun getVerificationResultKey(source: BaseSource) =
        getVerificationResultKey(source.getKey())

    private fun getVerificationResultKey(sourceKey: String) = "${sourceKey}_verificationResult"

    /**
     * 获取书源验证结果
     * 图片验证�?防爬 滑动验证�?点击字符 等等
     */
    @Synchronized
    fun getVerificationResult(
        source: BaseSource?,
        url: String,
        title: String,
        useBrowser: Boolean,
        refetchAfterSuccess: Boolean = true
    ): String {
        source
            ?: throw NoStackTraceException("getVerificationResult parameter source cannot be null")
        require(url.length < 64 * 1024) { "getVerificationResult parameter url too long" }
        check(!isMainThread) { "getVerificationResult must be called on a background thread" }

        clearResult(source.getKey())

        if (!useBrowser) {
            appCtx.startActivity<VerificationCodeActivity> {
                putExtra("imageUrl", url)
                putExtra("sourceOrigin", source.getKey())
                putExtra("sourceName", source.getTag())
                putExtra("sourceType", source.getSourceType())
                IntentData.put(getVerificationResultKey(source), Thread.currentThread())
            }
        } else {
            startBrowser(source, url, title, true, refetchAfterSuccess)
        }

        var waitUserInput = false
        while (getResult(source.getKey()) == null) {
            if (!waitUserInput) {
                AppLog.putDebug("等待返回验证结果...")
                waitUserInput = true
            }
            LockSupport.parkNanos(this, waitTime)
        }

        val result = getResult(source.getKey())!!
        clearResult(source.getKey())
        result.ifBlank {
            throw NoStackTraceException("验证结果为空")
        }

        return result
    }

    /**
     * 启动内置浏览�?     * @param saveResult 保存网页源代码到数据�?     */
    fun startBrowser(
        source: BaseSource?,
        url: String,
        title: String,
        saveResult: Boolean? = false,
        refetchAfterSuccess: Boolean? = true
    ) {
        source ?: throw NoStackTraceException("startBrowser parameter source cannot be null")
        require(url.length < 64 * 1024) { "startBrowser parameter url too long" }
        appCtx.startActivity<WebViewActivity> {
            putExtra("title", title)
            putExtra("url", url)
            putExtra("sourceOrigin", source.getKey())
            putExtra("sourceName", source.getTag())
            putExtra("sourceType", source.getSourceType())
            putExtra("sourceVerificationEnable", saveResult)
            putExtra("refetchAfterSuccess", refetchAfterSuccess)
            IntentData.put(getVerificationResultKey(source), Thread.currentThread())
        }
    }


    fun checkResult(sourceKey: String) {
        getResult(sourceKey) ?: setResult(sourceKey, "")
        val thread = IntentData.get<Thread>(getVerificationResultKey(sourceKey))
        LockSupport.unpark(thread)
    }

    fun setResult(sourceKey: String, result: String?) {
        CacheManager.putMemory(getVerificationResultKey(sourceKey), result ?: "")
    }

    fun getResult(sourceKey: String): String? {
        return CacheManager.getFromMemory(getVerificationResultKey(sourceKey)) as? String
    }

    fun clearResult(sourceKey: String) {
        CacheManager.delete(getVerificationResultKey(sourceKey))
    }
}
