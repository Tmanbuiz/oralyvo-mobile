package app.oralyvo.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local-only transcript history, stored as a small JSON file under the
 * app's private files directory -- no server-side history endpoint exists
 * yet (and none is needed for this device-only MVP), and a JSON file avoids
 * pulling in a Room/SQLite dependency for what is currently a short list.
 * Not shared across devices; that is a deliberate scope cut, not an
 * oversight -- revisit if/when the backend grows a /api/history endpoint.
 */
class HistoryStore(context: Context) {
    private val file: File = File(context.applicationContext.filesDir, "history.json")

    @Synchronized
    fun all(): List<TranscriptEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                TranscriptEntry(
                    id = o.getString("id"),
                    createdAtMillis = o.getLong("createdAtMillis"),
                    text = o.getString("text"),
                    durationMillis = o.optLong("durationMillis", 0L)
                )
            }.sortedByDescending { it.createdAtMillis }
        } catch (err: Exception) {
            // A corrupted or partially-written file should not crash the
            // history screen -- treat it as empty rather than propagate.
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String, durationMillis: Long): TranscriptEntry {
        val entry = TranscriptEntry(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            text = text,
            durationMillis = durationMillis
        )
        val updated = all() + entry
        val array = JSONArray()
        updated.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("createdAtMillis", e.createdAtMillis)
                    .put("text", e.text)
                    .put("durationMillis", e.durationMillis)
            )
        }
        file.writeText(array.toString())
        return entry
    }
}
