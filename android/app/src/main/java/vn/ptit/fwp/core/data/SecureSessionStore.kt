package vn.ptit.fwp.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BackendSession(val token: String, val userId: String, val email: String, val backendUrl: String)

interface SessionStorage {
    fun read(): BackendSession?
    fun write(session: BackendSession)
    fun clear()
}

/** The only persisted copy of the bearer token is encrypted with Android Keystore. */
class SecureSessionStore(context: Context) : SessionStorage {
    private val prefs = context.getSharedPreferences("backend_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = "fwp_backend_session_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun read(): BackendSession? {
        val encoded = prefs.getString("session", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            gson.fromJson(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8), BackendSession::class.java)
                ?.takeIf { it.token.isNotBlank() && it.userId.isNotBlank() && it.email.isNotBlank() && it.backendUrl.isNotBlank() }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun write(session: BackendSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("session", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }

    override fun clear() { check(prefs.edit().clear().commit()) }
}
