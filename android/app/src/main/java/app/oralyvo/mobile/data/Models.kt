package app.oralyvo.mobile.data

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val email: String?,
    val expiresAt: Long = 0L
)

data class TranscriptEntry(
    val id: String,
    val createdAtMillis: Long,
    val text: String,
    val durationMillis: Long = 0L,
    val type: String = "transcript",
    val cloudSaved: Boolean = false
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String) : ApiResult<Nothing>()
}

interface SessionStore {
    fun session(): AuthSession?
    fun save(session: AuthSession)
}
