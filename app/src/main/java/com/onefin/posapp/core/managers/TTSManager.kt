package com.onefin.posapp.core.managers

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onefin.posapp.core.models.ResultApi
import com.onefin.posapp.core.models.data.BalanceData
import com.onefin.posapp.core.services.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TTSManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiServiceProvider: Provider<ApiService>,
    private val gson: Gson
) {


    companion object {
        private const val TAG = "TTSManager"
    }

    private var isInitialized = false
    private var isTTSAvailable = false
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private val ttsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())


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

        // Release MediaPlayer
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {
        }

        // Cancel coroutine scope
        ttsScope.cancel()
    }

    fun speak(text: String) {
        if (text.isEmpty()) return

        // Nếu TTS sẵn sàng thì dùng TTS
        if (isTTSAvailable && isInitialized && textToSpeech != null) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            } catch (_: Exception) {
            }
            return
        }

        // Nếu TTS không sẵn sàng thì thử gọi API để lấy audio URL
        ttsScope.launch {
            try {
                val audioUrl = fetchAudioUrlFromApi(text)
                if (audioUrl.isNotEmpty()) {
                    Timber.tag(TAG).w("Audio URL: $audioUrl")
                    playAudioFromUrl(audioUrl)
                } else {
                    Timber.tag(TAG).w("Audio URL empty from API")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to fetch audio from API: ${e.message}")
            }
        }
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
    private suspend fun playAudioFromUrl(url: String) = withContext(Dispatchers.Main) {
        try {
            // Release MediaPlayer cũ nếu đang chạy
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }

            // Tạo MediaPlayer mới
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { mp ->
                    Timber.tag(TAG).d("MediaPlayer prepared, starting playback")
                    mp.start()
                }
                setOnCompletionListener {
                    Timber.tag(TAG).d("MediaPlayer completed")
                    it.release()
                    mediaPlayer = null
                }
                setOnErrorListener { mp, what, extra ->
                    Timber.tag(TAG).e("MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error playing audio from URL: ${e.message}")
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    private suspend fun fetchAudioUrlFromApi(text: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val endpoint = "/api/card/textToAudio"
            val body = mapOf(
                "Text" to text
            )
            val resultApi = apiServiceProvider.get().post(endpoint, body) as ResultApi<*>
            if (resultApi.isSuccess()) {
                resultApi.data
            }
            ""
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching audio URL from API")
            ""
        }
    }
}