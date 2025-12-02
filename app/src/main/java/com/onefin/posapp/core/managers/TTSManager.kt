package com.onefin.posapp.core.managers

import android.content.Context
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
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
        }
    }

    fun stop() {
        if (!isTTSAvailable)
            return
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
                retryInitialization()
            }
            return
        }

        try {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        } catch (_: Exception) {
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
                                        tts.language = Locale.getDefault()
                                    }
                                }

                                tts.setSpeechRate(1.0f)
                                tts.setPitch(1.0f)

                                isInitialized = true
                                isTTSAvailable = true
                            } catch (_: Exception) {
                                isInitialized = false
                                isTTSAvailable = false
                            }
                        }
                    }

                    TextToSpeech.ERROR -> {
                        isInitialized = false
                        isTTSAvailable = false

                        // ✅ Cleanup
                        textToSpeech?.shutdown()
                        textToSpeech = null
                    }

                    else -> {
                        isInitialized = false
                        isTTSAvailable = false
                    }
                }
            }
        } catch (e: Exception) {
            isInitialized = false
            isTTSAvailable = false
            textToSpeech = null
        }
    }
    private fun checkTTSAvailability(): Boolean {
        return try {
            // Check Google TTS package
            context.packageManager.getPackageInfo("com.google.android.tts", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            // Check any TTS engine
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val activities = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            val available = activities.isNotEmpty()
            available
        } catch (_: Exception) {
            false
        }
    }
    private fun retryInitialization(retryCount: Int = 0) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000L)
            if (!isInitialized) {
                initializeTTS()
            }
        }
    }
}