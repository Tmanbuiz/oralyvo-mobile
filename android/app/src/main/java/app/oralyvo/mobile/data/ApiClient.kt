package app.oralyvo.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client for the existing Speech2Text-Pro Cloudflare Worker API
 * (github.com/Tmanbuiz/Speech2Text-Pro) -- there is no separate mobile
 * backend; this app is just another caller of the same endpoints the web
 * app uses. Endpoint contracts below were read directly from
 * src/index.js / src/auth.js in that repo, not guessed:
 *
 *  - POST /api/auth/signin, /api/auth/signup
 *      body: {email, password, turnstileToken}  (signup also accepts
 *      optional "data": {...} for profile fields)
 *      The Worker forwards this to Supabase Auth itself (after verifying
 *      the Turnstile token) and returns Supabase's response unchanged, so
 *      a success body looks like {access_token, refresh_token, user: {...}}
 *      and a failure body varies by Supabase version -- see parseAuthError.
 *
 *  - POST /api/transcribe  (multipart/form-data, Authorization: Bearer <token>)
 *      fields: file (audio blob, filename extension matters -- it tells the
 *      server the audio format), quality ("fast" | "accurate")
 *      response: {text, operationId, segments?}
 *
 * No Origin header is sent by OkHttp for a plain POST (that is a browser
 * concept), and the Worker's same-origin check explicitly allows requests
 * with no Origin header through -- so none of these calls need any special
 * header to pass rejectCrossOrigin() on the server.
 */
class ApiClient(private val baseUrl: String = "https://oralyvo.app") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun signIn(email: String, password: String, turnstileToken: String): ApiResult<AuthSession> =
        authRequest("/api/auth/signin", email, password, turnstileToken)

    suspend fun signUp(email: String, password: String, turnstileToken: String): ApiResult<AuthSession> =
        authRequest("/api/auth/signup", email, password, turnstileToken)

    private suspend fun authRequest(
        path: String,
        email: String,
        password: String,
        turnstileToken: String
    ): ApiResult<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("turnstileToken", turnstileToken)
                .toString()
                .toRequestBody(jsonMedia)

            val request = Request.Builder()
                .url(baseUrl + path)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = try { JSONObject(raw) } catch (err: Exception) { null }

                if (!response.isSuccessful || obj == null || !obj.has("access_token")) {
                    return@withContext ApiResult.Failure(parseAuthError(obj, response.code))
                }

                val user = obj.optJSONObject("user")
                ApiResult.Success(
                    AuthSession(
                        accessToken = obj.getString("access_token"),
                        refreshToken = obj.optString("refresh_token", null),
                        userId = user?.optString("id"),
                        email = user?.optString("email") ?: email
                    )
                )
            }
        } catch (err: Exception) {
            ApiResult.Failure(err.message ?: "Could not reach Oralyvo. Check your connection.")
        }
    }

    // Supabase's error shape differs by version ("error_description", or
    // newer "msg"/"error_code"); the Worker's own pre-Supabase failures (bad
    // Turnstile token, rate limit) use a plain {"error": "..."} shape
    // instead. Trying each known field in order covers every case observed
    // without depending on which one this deployment currently returns.
    private fun parseAuthError(obj: JSONObject?, httpStatus: Int): String {
        if (obj == null) return "Unexpected response (HTTP $httpStatus). Please try again."
        return obj.optString("error_description").ifBlank { null }
            ?: obj.optString("msg").ifBlank { null }
            ?: obj.optString("error").ifBlank { null }
            ?: obj.optString("message").ifBlank { null }
            ?: "Sign-in failed (HTTP $httpStatus)."
    }

    suspend fun transcribe(
        accessToken: String,
        audioFile: File,
        quality: String = "accurate"
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val audioMedia = "audio/mp4".toMediaType()
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(audioMedia))
                .addFormDataPart("quality", quality)
                .build()

            val request = Request.Builder()
                .url(baseUrl + "/api/transcribe")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val obj = try { JSONObject(raw) } catch (err: Exception) { null }

                if (!response.isSuccessful || obj == null) {
                    val message = obj?.optString("error")?.ifBlank { null }
                        ?: "Transcription failed (HTTP ${response.code})."
                    return@withContext ApiResult.Failure(message)
                }

                ApiResult.Success(obj.optString("text", ""))
            }
        } catch (err: Exception) {
            ApiResult.Failure(err.message ?: "Could not reach Oralyvo. Check your connection.")
        }
    }
}
