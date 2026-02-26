package com.peiyu.reader.ui.book.read.config

import android.app.Application
import android.speech.tts.TextToSpeech
import com.peiyu.reader.base.BaseViewModel
import com.peiyu.reader.help.DefaultData

class SpeakEngineViewModel(application: Application) : BaseViewModel(application) {

    val sysEngines: List<TextToSpeech.EngineInfo> by lazy {
        val tts = TextToSpeech(context, null)
        val engines = tts.engines
        tts.shutdown()
        engines
    }

    fun importDefault() {
        execute {
            DefaultData.importDefaultHttpTTS()
        }
    }

}
