package com.peiyu.reader.ui.login

import android.app.Application
import android.content.Intent
import com.script.rhino.runScriptWithContext
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.appDb
import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.exception.NoStackTraceException
import com.peiyu.reader.utils.toastOnUi

class SourceLoginViewModel(application: Application) : BaseViewModel(application) {

    var source: BaseSource? = null
    var headerMap: Map<String, String> = emptyMap()

    fun initData(intent: Intent, success: (bookSource: BaseSource) -> Unit, error: () -> Unit) {
        execute {
            val sourceKey = intent.getStringExtra("key")
                ?: throw NoStackTraceException("没有参数")
            when (intent.getStringExtra("type")) {
                "bookSource" -> source = appDb.bookSourceDao.getBookSource(sourceKey)
                "rssSource" -> source = appDb.rssSourceDao.getByKey(sourceKey)
                "httpTts" -> source = appDb.httpTTSDao.get(sourceKey.toLong())
            }
            headerMap = runScriptWithContext {
                source?.getHeaderMap(true) ?: emptyMap()
            }
            source
        }.onSuccess {
            if (it != null) {
                success.invoke(it)
            } else {
                context.toastOnUi("未找到书�?)
            }
        }.onError {
            error.invoke()
            AppLog.put("登录 UI 初始化失败\n$it", it, true)
        }
    }

}
