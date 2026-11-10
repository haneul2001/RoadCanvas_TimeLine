package com.carnect.roadcanvas.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Pleos TTS(온디바이스) 래퍼. 지역 진입·도착 같은 짧은 음성 안내에 쓴다. */
class TtsEngine(context: Context) {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var started = false

    private val _status = MutableStateFlow("초기화 전")
    val status: StateFlow<String> = _status.asStateFlow()

    private val events = object : EventListener {
        override fun onReady() = update("준비")
        override fun onStart() = update("말하는 중")
        override fun onDone() = update("완료")
        override fun onStop() = update("중지")
        override fun onError(type: ErrorType) = update("오류 $type")
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        if (started) return
        started = true
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // 국가 코드 없이 "ko"만 주면 온디바이스 엔진이 지원하지 않는 로캘로 거부한다.
                    tts.setLocale(Locale.KOREA)
                    tts.addEventListener(events)
                    update("초기화 완료")
                }

                override fun onFailure(type: ErrorType) {
                    update("초기화 실패 $type")
                    started = false
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "tts initialize failed", e)
            update("초기화 실패 ${e.message}")
            started = false
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        Log.d(TAG, "tts speak '$text'")
        tts.speak(text, flush)
    }

    fun release() {
        if (!started) return
        tts.removeEventListener(events)
        tts.release()
        started = false
    }

    private fun update(text: String) {
        Log.d(TAG, "tts $text")
        _status.value = text
    }
}
