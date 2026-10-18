package app.oralyvo.mobile.data

/**
 * Session returned by POST /api/auth/signin or /api/auth/signup -- this is
 * Supabase's own GoTrue token response, passed through unchanged by the
 * Worker (see handleAuthProxy in Speech2Text-Pro/src/index.js). Only the
 * fields this app actually uses are modeled; Supabase's response has more.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String?,
    val email: String?
)

/** One past transcription, kept locally on-device (see HistoryStore). */
data class TranscriptEntry(
    val id: String,
    val createdAtMillis: Long,
    val text: String,
    val durationMillis: Long
)

/** Outcome of a network call, kept deliberately small: every screen in this
 *  app only ever needs "it worked, here is the data" or "it failed, here is
 *  a message the user can read" -- never the raw exception type. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}
