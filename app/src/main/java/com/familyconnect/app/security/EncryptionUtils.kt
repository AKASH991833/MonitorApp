package com.familyconnect.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import timber.log.Timber

object EncryptionUtils {

    private const val KEY_ALIAS = "family_connect_aes_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun getKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }

    @RequiresApi(23)
    fun initKeystore() {
        try {
            val keyStore = getKeyStore()
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
                Timber.d("AES key generated in Android Keystore")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Android Keystore")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = getKeyStore()
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve secret key from Keystore")
            null
        }
    }

    @RequiresApi(23)
    fun encrypt(plainText: String): String {
        return try {
            val secretKey = getSecretKey()
            if (secretKey == null) {
                initKeystore()
                val retryKey = getSecretKey()
                    ?: throw IllegalStateException("Unable to obtain encryption key")
                encryptInternal(plainText, retryKey)
            } else {
                encryptInternal(plainText, secretKey)
            }
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }

    @RequiresApi(23)
    private fun encryptInternal(plainText: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    @RequiresApi(23)
    fun decrypt(cipherText: String): String {
        return try {
            val secretKey = getSecretKey()
                ?: throw IllegalStateException("No encryption key found in Keystore")

            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            val iv = decoded.copyOfRange(0, 12)
            val encrypted = decoded.copyOfRange(12, decoded.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }
}
