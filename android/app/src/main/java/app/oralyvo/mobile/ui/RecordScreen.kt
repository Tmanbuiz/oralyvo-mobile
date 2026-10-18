package app.oralyvo.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.oralyvo.mobile.data.ApiClient
import app.oralyvo.mobile.data.ApiResult
import app.oralyvo.mobile.data.HistoryStore
import app.oralyvo.mobile.data.TokenStore
import kotlinx.coroutines.launch
import java.io.File

/**
 * Record + transcribe screen, shown once signed in. Uses the platform
 * MediaRecorder (no extra audio library needed) to capture to a single
 * reusable file in the app's cache dir, then uploads that file through
 * ApiClient.transcribe -- same /api/transcribe endpoint the web app calls.
 *
 * MediaRecorder() is the deprecated no-arg constructor rather than the
 * Context-taking one added in API 31, because minSdk here is 26.
 */
@Composable
fun RecordScreen(onViewHistory: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }
    val tokenStore = remember { TokenStore(context) }
    val historyStore = remember { HistoryStore(context) }
    val outputFile = remember { File(context.cacheDir, "oralyvo_recording.m4a") }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingStartMillis by remember { mutableStateOf(0L) }

    // If the user backgrounds the app mid-recording, release the recorder
    // rather than leak it -- the half-finished file is simply discarded.
    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.apply { stop(); release() }
            } catch (err: Exception) {
                // stop() throws if too little audio was captured; nothing to
                // clean up differently either way.
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun startRecording() {
        transcript = null
        errorMessage = null
        @Suppress("DEPRECATION")
        val mr = MediaRecorder()
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(outputFile.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            recordingStartMillis = System.currentTimeMillis()
            isRecording = true
        } catch (err: Exception) {
            errorMessage = "Couldn't start recording: ${err.message}"
            mr.release()
        }
    }

    fun stopRecordingAndTranscribe() {
        val durationMillis = System.currentTimeMillis() - recordingStartMillis
        try {
            recorder?.apply { stop(); release() }
        } catch (err: Exception) {
            // Too-short recordings can throw here; outputFile may still be
            // usable, so fall through rather than bail out.
        }
        recorder = null
        isRecording = false

        val token = tokenStore.accessToken()
        if (token == null) {
            errorMessage = "You're signed out. Please sign in again."
            return
        }

        isTranscribing = true
        scope.launch {
            val result = apiClient.transcribe(token, outputFile)
            isTranscribing = false
            when (result) {
                is ApiResult.Success -> {
                    transcript = result.data
                    historyStore.add(result.data, durationMillis)
                }
                is ApiResult.Failure -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Record", style = MaterialTheme.typography.headlineMedium)

        if (!hasPermission) {
            Text(
                text = "Oralyvo needs microphone access to transcribe your speech.",
                modifier = Modifier.padding(top = 16.dp)
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            ) {
                Text("Grant microphone access")
            }
        } else {
            Button(
                modifier = Modifier.padding(top = 24.dp),
                enabled = !isTranscribing,
                onClick = { if (isRecording) stopRecordingAndTranscribe() else startRecording() }
            ) {
                Text(if (isRecording) "Stop recording" else "Start recording")
            }

            if (isTranscribing) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                Text(text = "Transcribing...", modifier = Modifier.padding(top = 8.dp))
            }

            transcript?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        Row(modifier = Modifier.padding(top = 32.dp)) {
            OutlinedButton(onClick = onViewHistory) {
                Text("History")
            }
            TextButton(
                modifier = Modifier.padding(start = 8.dp),
                onClick = {
                    tokenStore.clear()
                    onSignOut()
                }
            ) {
                Text("Sign out")
            }
        }
    }
}
