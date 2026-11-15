package com.carnect.roadcanvas.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.carnect.roadcanvas.RoadCanvasApp.Companion.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Pleos 음성 인식(온디바이스). 한 번 듣고 최종 문장을 [onFinal]로 넘긴다. */
class SttEngine(context: Context) {

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var onFinal: ((String) -> Unit)? = null
    private var lastRmsLog = 0L

    private val _status = MutableStateFlow("초기화 전")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _partial = MutableStateFlow("")
    /** 듣는 중 중간 결과. */
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            Log.d(TAG, "stt ${if (isFinal) "final" else "partial"} '$text'")
            _partial.value = text
            if (isFinal) {
                _listening.value = false
                val cb = onFinal
                onFinal = null
                if (text.isNotBlank()) main.post { cb?.invoke(text) }
            }
        }

        override fun onUpdatedEpdData(start: Long, end: Long) {
            Log.d(TAG, "stt epd $start~$end")
        }
        override fun onStartedRecognition() = update("듣는 중")
        override fun onEndedRecognition() {
            _listening.value = false
            update("대기")
        }

        override fun onError(type: ErrorType) {
            _listening.value = false
            update("오류 $type")
        }

        override fun onReady() = update("대기")
        override fun onUpdatedRms(rms: Double) {
            // 마이크 입력 크기 확인용(1초에 한 번 정도만)
            val now = System.currentTimeMillis()
            if (now - lastRmsLog >= 1000) {
                lastRmsLog = now
                Log.d(TAG, "stt rms %.1f".format(rms))
            }
        }
    }

    fun start() {
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    stt.setLocale(Locale.KOREA)
                    stt.addListener(listener)
                    ready = true
                    update("대기")
                }

                override fun onFailure(type: ErrorType) = update("초기화 실패 $type")
            })
        } catch (e: Exception) {
            Log.w(TAG, "stt initialize failed", e)
            update("초기화 실패 ${e.message}")
        }
    }

    /** 한 문장을 듣는다. 마이크 권한은 호출 전에 받아 둔다. */
    fun listen(onFinal: (String) -> Unit) {
        if (!ready) {
            update("음성 인식 준비 안 됨")
            return
        }
        this.onFinal = onFinal
        _partial.value = ""
        _listening.value = true
        try {
            stt.request()
        } catch (e: Exception) {
            Log.w(TAG, "stt request failed", e)
            _listening.value = false
            update("오류 ${e.message}")
        }
    }

    fun cancel() {
        onFinal = null
        _listening.value = false
        runCatching { stt.stop() }
    }

    private fun update(text: String) {
        Log.d(TAG, "stt $text")
        _status.value = text
    }
}
