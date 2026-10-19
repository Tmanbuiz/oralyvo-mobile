package app.oralyvo.mobile.ui

import android.app.Application
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.oralyvo.mobile.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Survives rotation. Foreground lifecycle stop finalizes a clip without
 * sending audio until the user explicitly chooses Transcribe. */
class RecordViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val api = ApiClient(store)
    private var recorder: MediaRecorder? = null
    private var ticker: Job? = null
    private var started = 0L
    var recording by mutableStateOf(false); private set
    var seconds by mutableIntStateOf(0); private set
    var file by mutableStateOf<File?>(null); private set
    var busy by mutableStateOf(false); private set
    var transcript by mutableStateOf<TranscriptEntry?>(null); private set
    var result by mutableStateOf<TranscriptEntry?>(null); private set
    var message by mutableStateOf<String?>(null)
    var language by mutableStateOf("French")

    fun start() {
        if (recording || busy) return
        message = null
        val output = File(getApplication<Application>().cacheDir, "oralyvo-${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION") val next = MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44100)
            next.setAudioEncodingBitRate(64000)
            next.setOutputFile(output.absolutePath)
            next.setMaxDuration(20 * 60 * 1000)
            next.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop(automatic = true)
            }
            next.setOnErrorListener { _, _, _ -> stop(); message = "Recording was interrupted. Check the clip or record again." }
            next.prepare(); next.start()
            file?.delete()
            file = output; recorder = next; recording = true
            transcript = null; result = null; seconds = 0
            started = SystemClock.elapsedRealtime()
            ticker = viewModelScope.launch {
                while (recording) { delay(1000); seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt() }
            }
        } catch (_: Exception) {
            next.release(); output.delete()
            message = "Could not start recording. Check microphone access in Settings and try again."
        }
    }
    fun stop(automatic: Boolean = false) {
        val current = recorder ?: return
        recorder = null
        ticker?.cancel(); ticker = null
        seconds = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
        recording = false
        try { if (!automatic) current.stop() }
        catch (_: Exception) {
            file?.delete(); file = null
            message = "The clip was too short or interrupted. Please record again."
        } finally { current.release() }
    }
    private fun keepLocal(entry: TranscriptEntry) {
        try {
            val id = store.session()?.userId ?: return
            HistoryStore(getApplication(), id).add(entry)
        } catch (_: Exception) { message = "Text is ready, but its offline copy could not be saved. Share it or save it to your account." }
    }
    fun transcribe() {
        if (recording || busy) return
        val audio = file ?: return
        busy = true; message = null
        viewModelScope.launch {
            try {
                when (val response = api.transcribe(audio)) {
                    is ApiResult.Success -> {
                        val entry = TranscriptEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), response.data, seconds * 1000L)
                        transcript = entry; result = null
                        keepLocal(entry)
                    }
                    is ApiResult.Failure -> { message = response.message + " Your clip is still available to retry." }
                }
            } finally { busy = false }
        }
    }
    fun process(translate: Boolean) {
        val text = transcript?.text ?: return
        if (busy || recording) return
        busy = true; message = null
        viewModelScope.launch {
            try {
                when (val response = api.process(text, if (translate) "translate" else "summarize", language)) {
                    is ApiResult.Success -> {
                        val entry = TranscriptEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), response.data,
                            type = if (translate) "translation" else "summary")
                        result = entry; keepLocal(entry)
                    }
                    is ApiResult.Failure -> { message = response.message }
                }
            } finally { busy = false }
        }
    }
    fun save(entry: TranscriptEntry) {
        if (busy || recording) return
        busy = true; message = null
        viewModelScope.launch {
            try {
                when (val response = api.save(entry)) {
                    is ApiResult.Success -> {
                        val saved = entry.copy(cloudSaved = true)
                        if (transcript?.id == entry.id) transcript = saved
                        if (result?.id == entry.id) result = saved
                        message = "Saved to your Oralyvo account."
                        keepLocal(saved)
                    }
                    is ApiResult.Failure -> { message = response.message + " Your text is still here. You can retry or share a copy." }
                }
            } finally { busy = false }
        }
    }
    override fun onCleared() { stop(); file?.delete(); super.onCleared() }
}
