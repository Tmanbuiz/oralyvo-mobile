package app.oralyvo.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.oralyvo.mobile.data.TokenStore
import app.oralyvo.mobile.data.TranscriptEntry
import java.util.Locale

@Composable
fun RecordScreen(onViewHistory: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val model: RecordViewModel = viewModel()
    val owner = LocalLifecycleOwner.current
    var confirmNew by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) model.start() else model.message = "Microphone access is off. You can enable it in Settings."
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stop()
            if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.stop() }
    }
    fun start() {
        if (permission) model.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Oralyvo · Record", style = MaterialTheme.typography.headlineMedium)
        Text(String.format(Locale.ROOT, "%02d:%02d", model.seconds / 60, model.seconds % 60), style = MaterialTheme.typography.displaySmall)
        Text(if (model.recording) "Recording…" else if (model.file != null) "Recording ready" else "Ready when you are")
        if (model.recording) {
            Button(onClick = { model.stop() }) { Text("Stop recording") }
        } else if (model.file != null) {
            Button(enabled = !model.busy, onClick = { model.transcribe() }) { Text("Transcribe recording") }
            OutlinedButton(enabled = !model.busy, onClick = { confirmNew = true }) { Text("New recording") }
        } else {
            Button(enabled = !model.busy, onClick = { start() }) { Text("Start recording") }
        }
        Text("Up to 20 minutes per clip. Keep the app open while recording. Audio is sent to Oralyvo only when you tap Transcribe.", style = MaterialTheme.typography.bodySmall)
        if (!permission) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Microphone settings") }
        }
        if (model.busy) { CircularProgressIndicator(); Text("Working…") }
        model.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        model.transcript?.let { entry ->
            ResultCard(entry, busy = model.busy, onSave = { model.save(entry) })
            Box {
                OutlinedButton(enabled = !model.busy, onClick = { showLanguages = true }) { Text("Translate to: ${model.language}") }
                DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                    listOf("English", "French", "Yoruba", "Spanish", "Arabic", "Portuguese", "Hindi", "Chinese").forEach { language ->
                        DropdownMenuItem(text = { Text(language) }, onClick = { model.language = language; showLanguages = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !model.busy, onClick = { model.process(true) }) { Text("Translate") }
                OutlinedButton(enabled = !model.busy, onClick = { model.process(false) }) { Text("AI notes") }
            }
        }
        model.result?.let { entry -> ResultCard(entry, busy = model.busy, onSave = { model.save(entry) }) }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = !model.busy && !model.recording, onClick = onViewHistory) { Text("History") }
            TextButton(enabled = !model.busy && !model.recording, onClick = { TokenStore(context).clear(); onSignOut() }) { Text("Sign out") }
        }
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Start a new recording?") },
        text = { Text("The current audio clip will be replaced. Generated text remains in this account’s offline history.") },
        confirmButton = { TextButton(onClick = { confirmNew = false; start() }) { Text("Start new") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } })
}

@Composable
internal fun ResultCard(entry: TranscriptEntry, busy: Boolean, onSave: (() -> Unit)? = null) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (entry.type == "summary") "AI notes" else entry.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        SelectionContainer { Text(entry.text) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, entry.text) }, "Share text"))
            }) { Text("Share") }
            if (onSave != null) TextButton(enabled = !busy && !entry.cloudSaved, onClick = onSave) { Text(if (entry.cloudSaved) "Saved to account" else "Save to account") }
        }
    }
}
