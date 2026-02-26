package com.peiyu.reader.model

import android.content.Context
import com.peiyu.reader.constant.IntentAction
import com.peiyu.reader.service.DownloadService
import com.peiyu.reader.utils.startService

object Download {


    fun start(context: Context, url: String, fileName: String) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra("url", url)
            putExtra("fileName", fileName)
        }
    }

}
