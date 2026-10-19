package app.oralyvo.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted session; the key stays in Android Keystore and the file
 * lives in noBackupFilesDir. Unattributed legacy history is never reassigned. */
class TokenStore(context: Context) : SessionStore {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "oralyvo-session.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun save(session: AuthSession) = synchronized(lock) {
        UUID.fromString(session.userId)
        require(session.accessToken.isNotBlank())
        val body = JSONObject().put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken).put("userId", session.userId)
            .put("email", session.email).put("expires_at", session.expiresAt).toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(body)
        val out = file.startWrite()
        try { out.write(data); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
        app.getSharedPreferences("oralyvo_session", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
    override fun session(): AuthSession? = synchronized(lock) {
        try {
            if (file.baseFile.exists()) {
                val bytes = file.readFully()
                val ivSize = bytes[0].toInt()
                require(ivSize == 12 && bytes.size > 1 + ivSize)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
                parse(JSONObject(String(cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)))))
            } else {
                // Upgrade old installs without leaving plaintext tokens behind.
                val legacy = app.getSharedPreferences("oralyvo_session", Context.MODE_PRIVATE)
                val access = legacy.getString("access_token", null) ?: return@synchronized null
                val claims = JSONObject(String(Base64.decode(access.split('.')[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
                val migrated = AuthSession(access, legacy.getString("refresh_token", null), claims.getString("sub"), legacy.getString("email", null), claims.optLong("exp"))
                save(migrated)
                migrated
            }
        } catch (_: Exception) { null }
    }
    fun accessToken(): String? = session()?.accessToken
    fun isSignedIn(): Boolean = session() != null
    fun clear() = synchronized(lock) {
        file.delete()
        app.getSharedPreferences("oralyvo_session", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }
    companion object {
        private const val ALIAS = "oralyvo.session.v1"
        private val lock = Any()
        fun parse(value: JSONObject): AuthSession {
            val id = value.getString("userId")
            UUID.fromString(id)
            return AuthSession(value.getString("access_token"), value.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" },
                id, value.optString("email").takeIf { it != "null" }, value.optLong("expires_at"))
        }
    }
}
