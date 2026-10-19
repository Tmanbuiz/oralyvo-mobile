package app.oralyvo.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

class ApiClient(
    private val tokenStore: SessionStore,
    private val baseUrl: String = "https://oralyvo.app",
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private fun jsonRequest(path: String, body: JSONObject) = Request.Builder().url(baseUrl + path)
        .post(body.toString().toRequestBody(jsonMedia)).build()
    private fun error(obj: JSONObject, code: Int): String = listOf("error_description", "msg", "error", "message")
        .map { obj.optString(it) }.firstOrNull { it.isNotBlank() } ?: "Request failed (HTTP $code). Please retry."
    private fun execute(request: Request): Pair<Int, JSONObject> = client.newCall(request).execute().use { response ->
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        response.code to obj
    }
    private suspend fun renew(failedToken: String, ownerId: String): AuthSession = refreshLock.withLock {
        val old = tokenStore.session() ?: throw IllegalStateException("Please sign in again.")
        check(old.userId == ownerId) { "Account changed. Please retry." }
        if (old.accessToken != failedToken) return@withLock old
        val refresh = old.refreshToken ?: throw IllegalStateException("Please sign out and sign in again to renew your session.")
        val (healthCode, health) = execute(Request.Builder().url(baseUrl + "/api/health").build())
        check(healthCode in 200..299) { "Account service is unavailable. Please retry." }
        val url = health.getString("supabaseUrl").toHttpUrl()
        check(url.isHttps) { "Account service configuration is invalid." }
        val tokenUrl = url.newBuilder().addPathSegments("auth/v1/token").addQueryParameter("grant_type", "refresh_token").build()
        val (code, result) = execute(Request.Builder().url(tokenUrl)
            .header("apikey", health.getString("supabasePublishableKey"))
            .post(JSONObject().put("refresh_token", refresh).toString().toRequestBody(jsonMedia)).build())
        check(code in 200..299) { error(result, code) }
        val renewed = old.copy(accessToken = result.getString("access_token"),
            refreshToken = result.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh,
            expiresAt = System.currentTimeMillis() / 1000 + result.optLong("expires_in", 3600))
        check(tokenStore.session()?.userId == old.userId) { "Account changed. Please retry." }
        tokenStore.save(renewed)
        renewed
    }
    private suspend fun authorized(request: Request): JSONObject {
        var session = tokenStore.session() ?: throw IllegalStateException("Please sign in first.")
        val ownerId = session.userId
        if (session.expiresAt < System.currentTimeMillis() / 1000 + 60) session = renew(session.accessToken, ownerId)
        var response = execute(request.newBuilder().header("Authorization", "Bearer ${session.accessToken}").build())
        if (response.first == 401) {
            session = renew(session.accessToken, ownerId)
            response = execute(request.newBuilder().header("Authorization", "Bearer ${session.accessToken}").build())
        }
        check(tokenStore.session()?.userId == ownerId) { "Account changed. Please retry." }
        check(response.first in 200..299) { error(response.second, response.first) }
        return response.second
    }
    private suspend fun <T> result(block: suspend () -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        try { ApiResult.Success(block()) }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { ApiResult.Failure(error.message ?: "Could not reach Oralyvo. Check your connection and retry.") }
    }
    suspend fun transcribe(audio: File): ApiResult<String> = result {
        require(audio.exists() && audio.length() > 0) { "No usable recording is available. Please record again." }
        require(audio.length() <= 24 * 1024 * 1024) { "Recording is too large. Please record a shorter clip." }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("quality", "accurate").build()
        authorized(Request.Builder().url(baseUrl + "/api/transcribe").post(body).build()).optString("text")
            .also { check(it.isNotBlank()) { "No speech was detected. Retry or record again." } }
    }
    suspend fun process(text: String, mode: String, language: String): ApiResult<String> = result {
        authorized(jsonRequest("/api/process", JSONObject().put("transcript", text).put("mode", mode)
            .put("targetLanguage", language).put("provider", "engine-1"))).getString("summary")
    }
    suspend fun save(entry: TranscriptEntry): ApiResult<Unit> = result {
        authorized(jsonRequest("/api/history", JSONObject().put("content", entry.text).put("type", entry.type).put("legacy_id", entry.id)))
        Unit
    }
    suspend fun history(): ApiResult<List<TranscriptEntry>> = result {
        val entries = authorized(Request.Builder().url(baseUrl + "/api/history").build()).getJSONArray("entries")
        (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            TranscriptEntry(item.optString("legacy_id").takeIf { it.isNotBlank() && it != "null" } ?: item.getString("id"),
                Instant.parse(item.getString("created_at")).toEpochMilli(), item.getString("content"),
                type = item.getString("type"), cloudSaved = true)
        }
    }
    companion object { private val refreshLock = Mutex() }
}
