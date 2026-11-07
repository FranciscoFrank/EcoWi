package ua.ecowi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small AES-256-GCM wrapper over Android Keystore for the API keys.
 * (androidx.security:security-crypto is deprecated, and this is all we need.)
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("ecowi_secure", Context.MODE_PRIVATE)

    fun putString(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = b64(cipher.iv) + ":" + b64(encrypted)
        prefs.edit().putString(name, packed).apply()
    }

    fun getString(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        return try {
            val (iv, data) = packed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. restore on another device) — user has to re-enter keys.
            Log.w("SecureStore", "Cannot decrypt $name", e)
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "ecowi_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
