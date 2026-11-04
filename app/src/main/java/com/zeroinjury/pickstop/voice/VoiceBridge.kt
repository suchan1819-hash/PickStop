package com.zeroinjury.pickstop.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * 음성 입출력. 주행 중에는 화면 대신 음성으로만 주문을 주고받는다.
 * - 말하기: Pleos TextToSpeech SDK(차량 내장 엔진). 쓸 수 없으면 안드로이드 기본 TTS.
 * - 듣기: Pleos SpeechToText SDK(차량 마이크). 결과 문장을 onHeard 로 넘긴다.
 */
class VoiceBridge(context: Context) {

    enum class State { IDLE, LISTENING, SPEAKING }

    private val tag = "PickStop/Voice"
    private val ctx = context.applicationContext

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _heard = MutableStateFlow("")
    /** 지금 듣고 있는 문장(중간 결과 포함) */
    val heard: StateFlow<String> = _heard.asStateFlow()
    private val _lastSpoken = MutableStateFlow("")
    val lastSpoken: StateFlow<String> = _lastSpoken.asStateFlow()

    var onHeard: ((String) -> Unit)? = null

    private var pleosTts: TextToSpeech? = null
    private var pleosTtsReady = false
    private var androidTts: android.speech.tts.TextToSpeech? = null
    private var stt: SpeechToText? = null
    var sttReady = false
        private set

    fun start() {
        runCatching {
            val t = TextToSpeech(ctx, TtsMode.ON_DEVICE)
            t.initialize(object : TtsInit {
                override fun onSuccess() {
                    pleosTtsReady = true
                    runCatching { t.setLocale(Locale.KOREAN) }
                }
                override fun onFailure(errorType: TtsError) { Log.w(tag, "Pleos TTS init: $errorType") }
            })
            t.addEventListener(object : EventListener {
                override fun onStart() { _state.value = State.SPEAKING }
                override fun onReady() = Unit
                override fun onDone() { if (_state.value == State.SPEAKING) _state.value = State.IDLE }
                override fun onError(errorType: TtsError) { _state.value = State.IDLE }
                override fun onStop() { if (_state.value == State.SPEAKING) _state.value = State.IDLE }
                override fun onUpdatedRms(rms: Double) = Unit
            })
            pleosTts = t
        }.onFailure { Log.w(tag, "Pleos TTS unavailable: ${it.message}") }

        androidTts = android.speech.tts.TextToSpeech(ctx) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) androidTts?.language = Locale.KOREAN
        }

        runCatching {
            val s = SpeechToText(ctx, SttMode.ON_DEVICE)
            s.initialize(object : SttInit {
                override fun onSuccess() {
                    sttReady = true
                    runCatching { s.setLocale(Locale.KOREAN) }
                }
                override fun onFailure(errorType: SttError) { Log.w(tag, "Pleos STT init: $errorType") }
            })
            s.addListener(object : ResultListener {
                override fun onReady() = Unit
                override fun onStartedRecognition() { _state.value = State.LISTENING }
                override fun onUpdated(stt: String, completed: Boolean) {
                    _heard.value = stt
                    if (completed && stt.isNotBlank()) onHeard?.invoke(stt)
                }
                override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onEndedRecognition() { if (_state.value == State.LISTENING) _state.value = State.IDLE }
                override fun onError(errorType: SttError) {
                    Log.w(tag, "STT error $errorType")
                    _state.value = State.IDLE
                }
            })
            stt = s
        }.onFailure { Log.w(tag, "Pleos STT unavailable: ${it.message}") }
    }

    fun speak(text: String) {
        _lastSpoken.value = text
        val p = pleosTts
        if (p != null && pleosTtsReady && runCatching { p.speak(text, true) }.isSuccess) return
        _state.value = State.SPEAKING
        androidTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pickstop")
        _state.value = State.IDLE
    }

    /** 마이크를 열어 한 문장을 듣는다. */
    fun listen() {
        _heard.value = ""
        val s = stt
        if (s == null || !sttReady) {
            Log.w(tag, "STT not ready")
            return
        }
        runCatching { pleosTts?.stop() }
        runCatching { s.request() }.onFailure { Log.w(tag, "STT request failed: ${it.message}") }
    }

    /** 시연 패널에서 음성 대신 문장을 넣을 때 */
    fun inject(text: String) {
        _heard.value = text
        onHeard?.invoke(text)
    }

    fun release() {
        runCatching { stt?.release() }
        runCatching { pleosTts?.release() }
        runCatching { androidTts?.shutdown() }
    }
}
