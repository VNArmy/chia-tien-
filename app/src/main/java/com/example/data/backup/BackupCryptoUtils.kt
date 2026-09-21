package com.example.data.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tiện ích mã hóa tệp sao lưu chuẩn công nghiệp AES-256-GCM kết hợp PBKDF2WithHmacSHA256
 * Bảo vệ tuyệt đối số tài khoản ngân hàng và dữ liệu tài chính đoàn công tác theo yêu cầu bảo mật.
 */
object BackupCryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    const val ENCRYPTED_MAGIC_FORMAT = "TRIPFINANCE_ENCRYPTED_BACKUP"

    /**
     * Kiểm tra xem tệp JSON có phải là bản sao lưu được mã hóa bằng mật khẩu hay không
     */
    fun isEncryptedBackup(jsonString: String): Boolean {
        return try {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("{")) return false
            val obj = JSONObject(trimmed)
            obj.optBoolean("encrypted", false) && (obj.optString("format") == ENCRYPTED_MAGIC_FORMAT || obj.has("ciphertext"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mã hóa chuỗi JSON sao lưu bằng AES-256-GCM và PBKDF2WithHmacSHA256
     */
    fun encryptBackup(plainJson: String, password: String): String {
        require(password.isNotBlank()) { "Mật khẩu mã hóa không được để trống!" }

        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        // Dẫn xuất khóa 256-bit từ mật khẩu với PBKDF2WithHmacSHA256
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Mã hóa với AES-GCM
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val root = JSONObject().apply {
            put("encrypted", true)
            put("format", ENCRYPTED_MAGIC_FORMAT)
            put("version", 1)
            put("algorithm", "AES-256-GCM")
            put("kdf", KDF_ALGORITHM)
            put("iterations", ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("createdAt", System.currentTimeMillis())
        }

        return root.toString(2)
    }

    /**
     * Giải mã chuỗi JSON sao lưu được bảo vệ bằng mật khẩu
     */
    fun decryptBackup(encryptedJson: String, password: String): Result<String> {
        return try {
            val root = JSONObject(encryptedJson)
            if (!root.optBoolean("encrypted", false)) {
                return Result.failure(IllegalArgumentException("Tệp sao lưu này không được mã hóa!"))
            }

            val saltBase64 = root.getString("salt")
            val ivBase64 = root.getString("iv")
            val ciphertextBase64 = root.getString("ciphertext")
            val iterations = root.optInt("iterations", ITERATIONS)

            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            // Tái tạo khóa giải mã
            val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
            val keyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val keyBytes = keyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            val plainJson = String(decryptedBytes, Charsets.UTF_8)
            Result.success(plainJson)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(IllegalArgumentException("Mật khẩu giải mã không chính xác hoặc tệp sao lưu đã bị thay đổi!"))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Không thể giải mã sao lưu: ${e.message ?: "Mật khẩu không đúng"}"))
        }
    }
}
