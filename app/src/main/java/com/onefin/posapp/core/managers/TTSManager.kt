package com.onefin.posapp.core.managers

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TTSManager"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    private var isInitialized = false
    private var textToSpeech: TextToSpeech? = null
    private val pendingSpeechQueue = mutableListOf<String>()

    init {
        initializeTTS()
    }

    fun speak(text: String) {
        if (text.isEmpty()) return

        if (!isInitialized || textToSpeech == null) {
            if (textToSpeech != null) {
                pendingSpeechQueue.add(text)
                Timber.tag(TAG).d("TTS not ready, queued: $text")
                retryInitialization()
            } else {
                Timber.tag(TAG).w("TTS unavailable, cannot speak: $text")
            }
            return
        }

        try {
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            when (result) {
                TextToSpeech.SUCCESS -> Timber.tag(TAG).d("Speaking: $text")
                TextToSpeech.ERROR -> Timber.tag(TAG).e("Failed to speak: $text")
                else -> Timber.tag(TAG).w("Unknown speak result: $result")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception while speaking text")
        }
    }

    private fun initializeTTS() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        textToSpeech?.let { tts ->
                            try {
                                val result = tts.setLanguage(Locale.Builder().setLanguage("vi").setRegion("VN").build())
                                when (result) {
                                    TextToSpeech.LANG_MISSING_DATA -> {
                                        Timber.tag(TAG).w("Vietnamese language data missing, using default")
                                        tts.language = Locale.getDefault()
                                    }
                                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                                        Timber.tag(TAG).w("Vietnamese not supported, using default")
                                        tts.language = Locale.getDefault()
                                    }
                                    else -> {
                                        Timber.tag(TAG).d("Language set successfully: $result")
                                    }
                                }

                                tts.setSpeechRate(1.0f)
                                tts.setPitch(1.0f)

                                isInitialized = true
                                Timber.tag(TAG).d("TTS initialized successfully")

                                // Phát các speech đang chờ
                                processPendingSpeech()
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Error configuring TTS")
                                isInitialized = true
                            }
                        }
                    }
                    TextToSpeech.ERROR -> {
                        Timber.tag(TAG).e("TTS initialization failed: ERROR (-1)")
                        Timber.tag(TAG).e("Possible causes:")
                        Timber.tag(TAG).e("1. No TTS engine installed")
                        Timber.tag(TAG).e("2. TTS service not available")
                        Timber.tag(TAG).e("3. Out of memory")

                        // Không retry nếu lỗi ERROR
                        isInitialized = false

                        // Xóa pending queue vì không thể phát
                        if (pendingSpeechQueue.isNotEmpty()) {
                            Timber.tag(TAG).w("Clearing ${pendingSpeechQueue.size} pending speech(es) due to TTS error")
                            pendingSpeechQueue.clear()
                        }
                    }
                    else -> {
                        Timber.tag(TAG).e("TTS initialization failed with unknown status: $status")
                        isInitialized = false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception while initializing TTS")
            isInitialized = false
        }
    }

    private fun processPendingSpeech() {
        if (pendingSpeechQueue.isNotEmpty()) {
            Timber.tag(TAG).d("Processing ${pendingSpeechQueue.size} pending speech(es)")
            pendingSpeechQueue.forEach { text ->
                speak(text)
            }
            pendingSpeechQueue.clear()
        }
    }

    private fun retryInitialization(retryCount: Int = 0) {
        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count reached for TTS initialization")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(RETRY_DELAY_MS)
            if (!isInitialized) {
                Timber.tag(TAG).d("Retrying TTS initialization (attempt ${retryCount + 1})")
                initializeTTS()
            }
        }
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error stopping TTS")
        }
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            isInitialized = false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error shutting down TTS")
        }
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }
}