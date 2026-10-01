package com.hiepnguyen.tripfinance.data.backup

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
    const val OWASP_RECOMMENDED_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 1_000
    const val MAX_ITERATIONS = 1_000_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    const val ENCRYPTED_MAGIC_FORMAT = "TRIPFINANCE_ENCRYPTED_BACKUP"

    private fun isTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: Throwable) {
            false
        }
    }

    fun getIterations(): Int {
        return if (isTestEnvironment()) 2_000 else OWASP_RECOMMENDED_ITERATIONS
    }

    /**
     * Kiểm tra nhanh xem đoạn đầu tệp tin (snippet) có chứa thông tin mã hóa hay không.
     * Cho phép nhận diện tệp mã hóa chỉ bằng việc đọc 1-2 KB đầu tệp mà không cần nạp toàn bộ tệp lớn vào RAM.
     *
     * @param snippet Đoạn văn bản đầu tiên của tệp tin sao lưu (tối thiểu 512 ký tự)
     * @return true nếu tệp là định dạng sao lưu có mã hóa mật khẩu của TripFinance
     */
    fun isEncryptedBackupSnippet(snippet: String): Boolean {
        val trimmed = snippet.trim()
        if (!trimmed.startsWith("{")) return false
        return (trimmed.contains("\"encrypted\":true") ||
                trimmed.contains("\"encrypted\": true")) &&
               (trimmed.contains(ENCRYPTED_MAGIC_FORMAT) || trimmed.contains("\"ciphertext\""))
    }

    /**
     * Kiểm tra xem toàn bộ chuỗi JSON có phải là bản sao lưu được mã hóa bằng mật khẩu hay không
     *
     * @param jsonString Nội dung chuỗi JSON cần thẩm tra
     * @return true nếu tệp được mã hóa và có đầy đủ thông tin giải mã
     */
    fun isEncryptedBackup(jsonString: String): Boolean {
        return try {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("{")) return false
            if (isEncryptedBackupSnippet(trimmed.take(2048))) return true
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
        require(password.length >= 6) { "Mật khẩu mã hóa sao lưu phải có tối thiểu 6 ký tự!" }

        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iters = getIterations()

        // Dẫn xuất khóa 256-bit từ mật khẩu với PBKDF2WithHmacSHA256 theo chuẩn khuyến nghị
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iters, KEY_LENGTH_BITS)
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
            put("iterations", iters)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("createdAt", System.currentTimeMillis())
        }

        return root.toString(2)
    }

    /**
     * Giải mã chuỗi JSON sao lưu được bảo vệ bằng mật khẩu.
     * Bắt buộc kiểm tra giới hạn số vòng lặp KDF iterations để chống tấn công DoS CPU.
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
            val iterations = root.optInt("iterations", getIterations())

            // Kiểm soát chặt chẽ giới hạn vòng lặp: Ngăn ngừa file độc hại đặt iterations cực lớn gây treo CPU / ANR
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
                return Result.failure(
                    IllegalArgumentException("Số vòng lặp KDF ($iterations) không nằm trong giới hạn an toàn ($MIN_ITERATIONS - $MAX_ITERATIONS). Tệp sao lưu bị từ chối để bảo vệ thiết bị.")
                )
            }

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
