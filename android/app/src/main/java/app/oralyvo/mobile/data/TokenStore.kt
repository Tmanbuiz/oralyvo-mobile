package app.oralyvo.mobile.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the signed-in session on-device.
 *
 * Plain SharedPreferences for now, not EncryptedSharedPreferences -- that
 * needs an extra androidx.security dependency and a master-key setup this
 * MVP skips to keep the first working build small. The access token is
 * short-lived (Supabase default: 1 hour) and this is a dev-stage build with
 * no store listing yet, so the tradeoff is deliberate; switching the two
 * calls below to an encrypted file later is a self-contained change -- every
 * other screen only ever talks to this class, never to SharedPreferences
 * directly.
 */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("oralyvo_session", Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun email(): String? = prefs.getString(KEY_EMAIL, null)

    fun isSignedIn(): Boolean = !accessToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EMAIL = "email"
    }
}
