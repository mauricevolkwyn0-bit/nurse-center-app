package com.nursecenter.nurse.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the signed-in session on the device so the nurse stays signed in after the app is closed.
 * The session (which includes the refresh token) is encrypted with an AES key held in the Android Keystore,
 * so the stored value is useless if copied off the device.
 */
internal object SessionStore {
    private const val TAG = "SessionStore"
    private const val PREFS = "nurse_session"
    private const val KEY_DATA = "session"
    private const val KEY_ALIAS = "nurse_session_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun load(): AuthSession? {
        val stored = prefs?.getString(KEY_DATA, null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(stored))
            AuthSession(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                userId = json.getString("user_id"),
                email = json.optString("email"),
                expiresAt = json.getLong("expires_at"),
            )
        }.onFailure {
            // Unreadable (e.g. the key was reset): start signed out rather than crash.
            Log.w(TAG, "Couldn't read saved session: ${it.message}")
            clear()
        }.getOrNull()
    }

    fun save(session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user_id", session.userId)
            .put("email", session.email)
            .put("expires_at", session.expiresAt)
        runCatching { prefs?.edit()?.putString(KEY_DATA, encrypt(json.toString()))?.apply() }
            .onFailure { Log.w(TAG, "Couldn't save session: ${it.message}") }
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_DATA)?.apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    /** Returns base64 of IV + ciphertext. */
    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val iv = sealed.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        return String(cipher.doFinal(sealed, 12, sealed.size - 12), Charsets.UTF_8)
    }
}
