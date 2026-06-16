package com.example.todo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CanvasSyncLocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun hasSavedToken(): Boolean =
        preferences.contains(TokenCipherTextKey) && preferences.contains(TokenIvKey)

    fun saveToken(token: String) {
        val normalizedToken = token.trim()
        require(normalizedToken.isNotBlank()) {
            appContext
                .localizedContext(AppLanguageStore.load(appContext))
                .getString(R.string.canvas_token_empty)
        }

        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherText = cipher.doFinal(normalizedToken.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(TokenCipherTextKey, cipherText.base64())
            .putString(TokenIvKey, cipher.iv.base64())
            .apply()
    }

    fun loadToken(): String? =
        runCatching {
            val encodedCipherText = preferences.getString(TokenCipherTextKey, null) ?: return null
            val encodedIv = preferences.getString(TokenIvKey, null) ?: return null
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GcmTagBits, encodedIv.fromBase64())
            )
            String(cipher.doFinal(encodedCipherText.fromBase64()), Charsets.UTF_8)
                .trim()
                .takeIf { it.isNotBlank() }
        }.getOrNull()

    fun clearToken() {
        preferences.edit()
            .remove(TokenCipherTextKey)
            .remove(TokenIvKey)
            .apply()
    }

    fun loadLastSyncTimeMillis(): Long? =
        preferences.getLong(LastSyncMillisKey, 0L).takeIf { it > 0L }

    fun saveLastSyncTimeMillis(timeMillis: Long) {
        preferences.edit()
            .putLong(LastSyncMillisKey, timeMillis)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        val existing = keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.base64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PreferencesName = "canvas_sync"
        const val TokenCipherTextKey = "token_cipher_text"
        const val TokenIvKey = "token_iv"
        const val LastSyncMillisKey = "last_sync_millis"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "tongji_canvas_sync_token"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
    }
}
