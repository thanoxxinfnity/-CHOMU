package com.chomu.aiagent.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceGender { MADHUR_MALE, SWARA_FEMALE }

@Singleton
class VoiceManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val TAG = "VoiceManager"

    // Edge TTS endpoint — Microsoft's public neural TTS (same voices as paid Azure)
    private val EDGE_WSS =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
        "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    private val client = OkHttpClient()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mediaPlayer: MediaPlayer? = null
    private var lastSpokenText = ""
    var currentGender: VoiceGender = VoiceGender.SWARA_FEMALE

    // Android TTS fallback (used if Edge WS fails)
    private var fallbackTts: TextToSpeech? = null
    private var fallbackReady = false
    private var pendingFallback: String? = null

    init { initFallbackTts() }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setGender(gender: VoiceGender) { currentGender = gender }

    fun speak(text: String) {
        if (text.isBlank()) return
        lastSpokenText = text
        val clean = text.replace(Regex("[*#`~_]"), "").trim()
        scope.launch { speakEdge(clean) }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.stop()
    }

    fun shutdown() {
        stop()
        scope.cancel()
        fallbackTts?.shutdown()
        fallbackTts = null
        client.dispatcher.executorService.shutdown()
    }

    // ── Edge TTS via WebSocket ────────────────────────────────────────────────

    private suspend fun speakEdge(text: String) {
        val voiceName = when (currentGender) {
            VoiceGender.SWARA_FEMALE -> "hi-IN-SwaraNeural"
            VoiceGender.MADHUR_MALE  -> "hi-IN-MadhurNeural"
        }

        val requestId  = UUID.randomUUID().toString().replace("-", "")
        val timestamp  = edgeTimestamp()
        val audioChunks = mutableListOf<ByteArray>()
        val done = CompletableDeferred<Boolean>()

        val configMsg = buildString {
            append("X-Timestamp:$timestamp\r\n")
            append("Content-Type:application/json; charset=utf-8\r\n")
            append("Path:speech.config\r\n\r\n")
            append("""{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""")
        }

        val ssmlMsg = buildString {
            append("X-RequestId:$requestId\r\n")
            append("X-Timestamp:$timestamp\r\n")
            append("Content-Type:application/ssml+xml\r\n")
            append("Path:ssml\r\n\r\n")
            append("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='hi-IN'>")
            append("<voice name='$voiceName'>")
            append("<prosody rate='-3%' pitch='+5Hz'>")
            append(text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"))
            append("</prosody></voice></speak>")
        }

        val request = Request.Builder().url(
            "$EDGE_WSS&ConnectionId=$requestId"
        ).apply {
            header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(configMsg)
                ws.send(ssmlMsg)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if ("Path:turn.end" in text) {
                    ws.close(1000, null)
                    done.complete(true)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Binary message: 2-byte header length, then header text, then audio bytes
                val raw = bytes.toByteArray()
                if (raw.size < 2) return
                val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                if (raw.size > headerLen + 2) {
                    audioChunks.add(raw.copyOfRange(headerLen + 2, raw.size))
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Edge TTS WS failed: ${t.message} — falling back to Android TTS")
                done.complete(false)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) done.complete(audioChunks.isNotEmpty())
            }
        }

        client.newWebSocket(request, listener)

        val success = withTimeoutOrNull(12_000) { done.await() } ?: false

        if (success && audioChunks.isNotEmpty()) {
            playMp3Chunks(audioChunks)
        } else {
            Log.w(TAG, "Edge TTS produced no audio, using fallback")
            withContext(Dispatchers.Main) { speakFallback(text) }
        }
    }

    private suspend fun playMp3Chunks(chunks: List<ByteArray>) = withContext(Dispatchers.Main) {
        try {
            stop() // stop any current playback
            val totalSize = chunks.sumOf { it.size }
            val combined  = ByteArray(totalSize)
            var offset = 0
            for (chunk in chunks) { chunk.copyInto(combined, offset); offset += chunk.size }

            val tmp = File(context.cacheDir, "edge_tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tmp).use { it.write(combined) }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    tmp.delete()
                    it.release()
                    if (mediaPlayer == it) mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    tmp.delete()
                    speakFallback(lastSpokenText)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer error: ${e.message}")
            speakFallback(lastSpokenText)
        }
    }

    // ── Android TTS fallback ──────────────────────────────────────────────────

    private fun initFallbackTts() {
        fallbackTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fallbackReady = true
                val locale = Locale("hi", "IN")
                if (fallbackTts?.isLanguageAvailable(locale) ?: -1 >= TextToSpeech.LANG_AVAILABLE) {
                    fallbackTts?.language = locale
                }
                val voices = fallbackTts?.voices?.filter { it.locale.language == "hi" } ?: emptyList()
                val femaleVoice = voices.firstOrNull { "swara" in it.name.lowercase() }
                    ?: voices.firstOrNull { "female" in it.name.lowercase() }
                    ?: voices.drop(1).firstOrNull()
                femaleVoice?.let { fallbackTts?.voice = it }
                fallbackTts?.setPitch(1.18f)
                fallbackTts?.setSpeechRate(0.92f)
                pendingFallback?.let { speakFallback(it) }
                pendingFallback = null
            }
        }
    }

    private fun speakFallback(text: String) {
        if (!fallbackReady) { pendingFallback = text; return }
        val clean = text.replace(Regex("[*#`~_]"), "").trim()
        fallbackTts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "fb_${System.currentTimeMillis()}")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun edgeTimestamp(): String {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = now
        return String.format(
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND),
            cal.get(Calendar.MILLISECOND)
        )
    }
}
