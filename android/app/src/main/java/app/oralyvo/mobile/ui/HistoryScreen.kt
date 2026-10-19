package app.oralyvo.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.oralyvo.mobile.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val api = remember { ApiClient(store) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<TranscriptEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val formatter = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    suspend fun load() {
        if (loading) return
        val id = store.session()?.userId ?: return
        loading = true; message = null
        try {
            val local = try { withContext(Dispatchers.IO) { HistoryStore(context, id).all() } }
                catch (_: Exception) { message = "Offline history could not be read. Loading your account history."; emptyList() }
            entries = local
            when (val response = api.history()) {
                is ApiResult.Success -> {
                    entries = (local + response.data).associateBy { it.id }.values.sortedByDescending { it.createdAtMillis }
                }
                is ApiResult.Failure -> { message = response.message + " Showing offline copies for this account." }
            }
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) { Text("Back") }
        }
        TextButton(enabled = !loading, onClick = { scope.launch { load() } }) { Text("Refresh") }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
        if (entries.isEmpty() && !loading) Text("No saved text yet. Record something to get started.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(entries, key = { it.id }) { entry ->
                Text(formatter.format(Date(entry.createdAtMillis)), style = MaterialTheme.typography.labelMedium)
                Text(if (entry.cloudSaved) "Saved to account" else "Offline copy", style = MaterialTheme.typography.labelSmall)
                ResultCard(entry, busy = loading, onSave = {
                    loading = true
                    scope.launch {
                        try {
                            when (val result = api.save(entry)) {
                                is ApiResult.Success -> {
                                    val saved = entry.copy(cloudSaved = true)
                                    entries = entries.map { if (it.id == entry.id) saved else it }
                                    withContext(Dispatchers.IO) { HistoryStore(context, store.session()!!.userId).add(saved) }
                                    message = "Saved to your Oralyvo account."
                                }
                                is ApiResult.Failure -> { message = result.message }
                            }
                        } catch (_: Exception) { message = "Could not update the offline copy. Refresh to check your account history." }
                        finally { loading = false }
                    }
                })
                HorizontalDivider()
            }
        }
    }
}
