package com.onefin.posapp.core.managers

import android.content.Context
import android.content.pm.PackageManager
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
    }

    private var isInitialized = false
    private var isTTSAvailable = false
    private var textToSpeech: TextToSpeech? = null
    private val pendingSpeechQueue = mutableListOf<String>()

    init {
        if (checkTTSAvailability()) {
            initializeTTS()
        } else {
            Timber.tag(TAG).w("⚠️ TTS not available on this device - feature disabled")
        }
    }

    fun stop() {
        if (!isTTSAvailable)
            return
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error stopping TTS")
        }
    }

    fun shutdown() {
        if (!isTTSAvailable)
            return

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            isInitialized = false
            isTTSAvailable = false
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "⚠️ Error shutting down TTS (non-critical)")
        }
    }

    fun speak(text: String) {
        if (text.isEmpty()) return
        if (!isTTSAvailable) {
            return
        }

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

    fun isSpeaking(): Boolean {
        if (!isTTSAvailable)
            return false
        return textToSpeech?.isSpeaking ?: false
    }

    private fun initializeTTS() {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        textToSpeech?.let { tts ->
                            try {
                                val result = tts.setLanguage(
                                    Locale.Builder()
                                        .setLanguage("vi")
                                        .setRegion("VN")
                                        .build()
                                )
                                when (result) {
                                    TextToSpeech.LANG_MISSING_DATA,
                                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                                        Timber.tag(TAG).w("⚠️ Vietnamese not available, using default")
                                        tts.language = Locale.getDefault()
                                    }
                                }

                                tts.setSpeechRate(1.0f)
                                tts.setPitch(1.0f)

                                isInitialized = true
                                isTTSAvailable = true
                                Timber.tag(TAG).d("✅ TTS initialized successfully")
                            } catch (e: Exception) {
                                Timber.tag(TAG).w(e, "⚠️ Error configuring TTS (non-critical)")
                                isInitialized = false
                                isTTSAvailable = false
                            }
                        }
                    }

                    TextToSpeech.ERROR -> {
                        Timber.tag(TAG).w("⚠️ TTS initialization failed - feature disabled")
                        isInitialized = false
                        isTTSAvailable = false

                        // ✅ Cleanup
                        textToSpeech?.shutdown()
                        textToSpeech = null
                    }

                    else -> {
                        Timber.tag(TAG).w("⚠️ TTS initialization failed with status: $status - feature disabled")
                        isInitialized = false
                        isTTSAvailable = false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "⚠️ Exception initializing TTS - feature disabled")
            isInitialized = false
            isTTSAvailable = false
            textToSpeech = null
        }
    }
    private fun checkTTSAvailability(): Boolean {
        return try {
            // Check Google TTS package
            context.packageManager.getPackageInfo("com.google.android.tts", 0)
            Timber.tag(TAG).d("✅ Google TTS found")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            // Check any TTS engine
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val activities = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            val available = activities.isNotEmpty()
            if (!available) {
                Timber.tag(TAG).w("⚠️ No TTS engine found on device")
            }
            available
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error checking TTS availability")
            false
        }
    }
    private fun retryInitialization(retryCount: Int = 0) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000L)
            if (!isInitialized) {
                Timber.tag(TAG).d("Retrying TTS initialization (attempt ${retryCount + 1})")
                initializeTTS()
            }
        }
    }
}