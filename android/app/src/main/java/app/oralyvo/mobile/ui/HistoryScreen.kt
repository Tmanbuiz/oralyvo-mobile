package app.oralyvo.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.oralyvo.mobile.data.HistoryStore
import app.oralyvo.mobile.data.TranscriptEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only list of past transcripts -- see HistoryStore for why there is
 *  no backend history endpoint yet. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val historyStore = remember { HistoryStore(context) }
    var entries by remember { mutableStateOf(historyStore.all()) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "History", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "No transcripts yet. Record something to see it here.",
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(entries, key = { it.id }) { entry ->
                    TranscriptRow(entry)
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun TranscriptRow(entry: TranscriptEntry) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${dateFormat.format(Date(entry.createdAtMillis))} · ${formatDuration(entry.durationMillis)}",
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
