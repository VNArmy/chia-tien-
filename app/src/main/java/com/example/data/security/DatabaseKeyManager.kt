package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Quản lý khóa mã hóa CSDL SQLite tại chỗ (SQLCipher) an toàn thông qua phần cứng Android KeyStore.
 * - Khóa gốc (Master Key) được tạo và lưu trữ bên trong Hardware Security Module / TEE / StrongBox.
 * - Khóa giải mã CSDL (32 bytes ngẫu nhiên) được mã hóa bằng AES-256-GCM bảo vệ toàn vẹn.
 */
object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "TripFinance_Room_DB_MasterKey"
    private const val PREFS_NAME = "trip_finance_db_sec_vault"
    private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val KEY_IV = "enc_db_iv"
    private const val GCM_TAG_LENGTH = 128
    private const val PASSPHRASE_LENGTH_BYTES = 32

    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphraseB64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivB64 = prefs.getString(KEY_IV, null)

        return try {
            val masterKey = getOrCreateMasterKey()

            if (encryptedPassphraseB64 != null && ivB64 != null) {
                // Đã có khóa mã hóa trước đó, giải mã an toàn bằng Android KeyStore phần cứng
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedPassphraseB64, Base64.NO_WRAP)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
                cipher.doFinal(encryptedBytes)
            } else {
                // Lần khởi tạo đầu tiên: Tạo chuỗi 32 byte ngẫu nhiên chuẩn mật mã (CSPRNG)
                val rawPassphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
                SecureRandom().nextBytes(rawPassphrase)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, masterKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(rawPassphrase)

                prefs.edit()
                    .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()

                rawPassphrase
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi truy xuất phần cứng Android KeyStore: ${e.message}", e)
            // Nếu CSDL đã được mã hóa trước đó bằng KeyStore mà KeyStore tạm thời lỗi,
            // không tự ý đổi sang fallback seed ngẫu nhiên mới để tránh lỗi Corrupt/Invalid Key
            if (encryptedPassphraseB64 != null && ivB64 != null) {
                throw IllegalStateException("Không thể giải mã khóa CSDL từ Android KeyStore: ${e.message}", e)
            }
            // Dự phòng cho môi trường kiểm thử JVM / Robolectric không có Android KeyStore phần cứng
            getFallbackPassphrase(context)
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keyGenSpec)
            return keyGenerator.generateKey()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Không thể đọc khóa bảo mật từ Android KeyStore")
        return entry.secretKey
    }

    private fun getFallbackPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackKey = "fallback_db_seed"
        var seed = prefs.getString(fallbackKey, null)
        if (seed == null) {
            val bytes = ByteArray(PASSPHRASE_LENGTH_BYTES)
            SecureRandom().nextBytes(bytes)
            seed = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString(fallbackKey, seed).apply()
        }
        return Base64.decode(seed, Base64.NO_WRAP)
    }
}
