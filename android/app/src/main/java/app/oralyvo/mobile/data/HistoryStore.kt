package app.oralyvo.mobile.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Account-scoped private offline copies. The old shared history.json is
 * preserved on disk but never exposed or assigned to an arbitrary account. */
class HistoryStore(context: Context, userId: String) {
    private val file: AtomicFile
    init {
        UUID.fromString(userId)
        file = AtomicFile(File(context.applicationContext.filesDir, "history-$userId.json"))
    }
    fun all(): List<TranscriptEntry> = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized emptyList()
        val array = JSONArray(String(file.readFully()))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            TranscriptEntry(item.getString("id"), item.getLong("createdAtMillis"), item.getString("text"),
                item.optLong("durationMillis"), item.optString("type", "transcript"), item.optBoolean("cloudSaved"))
        }.sortedByDescending { it.createdAtMillis }
    }
    fun add(entry: TranscriptEntry) = synchronized(lock) {
        val entries = all().filterNot { it.id == entry.id } + entry
        val array = JSONArray()
        entries.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("createdAtMillis", item.createdAtMillis)
                .put("text", item.text).put("durationMillis", item.durationMillis).put("type", item.type).put("cloudSaved", item.cloudSaved))
        }
        val out = file.startWrite()
        try { out.write(array.toString().toByteArray()); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }
    companion object { private val lock = Any() }
}
