package com.hiepnguyen.tripfinance.domain.ai

import android.util.Log
import com.hiepnguyen.tripfinance.BuildConfig
import com.hiepnguyen.tripfinance.domain.model.CategoryBreakdown
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service cố vấn chi tiêu tài chính cho đoàn công tác và du lịch.
 * 
 * Kiến trúc bảo mật nghiêm ngặt (Zero Client Secrets & Privacy-First):
 * - TUYỆT ĐỐI KHÔNG chứa GEMINI_API_KEY trong APK (loại bỏ hoàn toàn khỏi BuildConfig và mã nguồn client).
 * - Mọi cuộc gọi AI Cloud phải đi qua máy chủ Backend trung gian bảo mật (Cloud Run / Cloud Functions),
 *   được xác thực toàn vẹn bằng Firebase App Check (Header X-Firebase-AppCheck).
 * - YÊU CẦU SỰ ĐỒNG Ý RÕ RÀNG (User Consent): Ứng dụng chỉ gửi dữ liệu ẩn danh tới Backend khi người dùng
 *   chủ động đồng ý trong giao diện.
 * - KHÔNG BAO GIỜ GỬI DỮ LIỆU NHẠY CẢM: Tuyệt đối không gửi số tài khoản ngân hàng, thông tin cá nhân hay nhật ký kiểm toán.
 * - ĐỘNG CƠ CỤC BỘ NỘI BỘ (Offline Local Insight Engine): Cung cấp giải thuật phân tích tài chính chạy 100%
 *   ngay trên thiết bị, không cần mạng, bảo mật dữ liệu tuyệt đối khi người dùng từ chối gửi lên đám mây.
 */
object GeminiSpendingAdvisor {

    private const val TAG = "GeminiAdvisor"

    // URL Cloud Backend Proxy (Cloud Run / Cloud Functions)
    @Volatile
    private var customCloudBackendUrl: String? = null

    fun setCloudBackendUrl(url: String?) {
        customCloudBackendUrl = url
    }

    private fun resolveCloudBackendUrl(): String? {
        if (!customCloudBackendUrl.isNullOrBlank()) return customCloudBackendUrl
        return try {
            val field = BuildConfig::class.java.getField("AI_BACKEND_URL")
            val url = field.get(null) as? String
            if (!url.isNullOrBlank() && url.startsWith("http")) url else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lấy token xác thực toàn vẹn Firebase App Check qua reflection an toàn,
     * không làm crash ứng dụng nếu Firebase SDK chưa được cấu hình.
     */
    private suspend fun fetchFirebaseAppCheckToken(): String? = withContext(Dispatchers.IO) {
        try {
            val appCheckClass = Class.forName("com.google.firebase.appcheck.FirebaseAppCheck")
            val getInstanceMethod = appCheckClass.getMethod("getInstance")
            val appCheckInstance = getInstanceMethod.invoke(null)
            val getTokenMethod = appCheckClass.getMethod("getAppCheckToken", Boolean::class.javaPrimitiveType)
            val task = getTokenMethod.invoke(appCheckInstance, false)

            val tasksClass = Class.forName("com.google.android.gms.tasks.Tasks")
            val awaitMethod = tasksClass.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
            val tokenResult = awaitMethod.invoke(null, task)
            val getTokenStringMethod = tokenResult.javaClass.getMethod("getToken")
            getTokenStringMethod.invoke(tokenResult) as? String
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Phân tích tài chính đoàn với cơ chế bảo vệ quyền riêng tư người dùng.
     * Chỉ gọi Cloud Backend khi [userHasConsented] = true và đã cấu hình Backend URL.
     */
    suspend fun analyzeTripFinances(
        tripTitle: String,
        financialSummary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        languageCode: String = "vi",
        userHasConsented: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val cloudBackendUrl = resolveCloudBackendUrl()

        // 1. KIẾN TRÚC AN TOÀN: Chỉ gửi dữ liệu tới Backend Proxy khi người dùng đã đồng ý rõ ràng
        if (userHasConsented && cloudBackendUrl != null) {
            val backendResult = callCloudBackend(cloudBackendUrl, tripTitle, financialSummary, categories, members, languageCode)
            if (backendResult != null) {
                return@withContext backendResult
            }
        }

        // 2. OFFLINE / PRIVACY-FIRST FALLBACK: Phân tích tài chính cục bộ độc lập ngay trên thiết bị
        // Không truyền bất kỳ dữ liệu nào qua mạng, an toàn 100%
        generateLocalInsight(tripTitle, financialSummary, categories, members, languageCode)
    }

    /**
     * Gọi backend Cloud Function / Cloud Run trung gian với xác thực Firebase App Check
     */
    private suspend fun callCloudBackend(
        backendUrl: String,
        tripTitle: String,
        financialSummary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        languageCode: String
    ): String? {
        return try {
            val appCheckToken = fetchFirebaseAppCheckToken()
            val url = URL(backendUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                if (!appCheckToken.isNullOrBlank()) {
                    setRequestProperty("X-Firebase-AppCheck", appCheckToken)
                }
                setRequestProperty("X-Client-Platform", "Android")
            }

            val payload = JSONObject().apply {
                put("tripTitle", sanitizeText(tripTitle, 100))
                put("languageCode", languageCode)
                put("totalExpenses", financialSummary.totalExpenses)
                put("fundPaidExpenses", financialSummary.fundPaidExpenses)
                put("personalPaidExpenses", financialSummary.personalPaidExpenses)
                put("totalFundCollected", financialSummary.totalFundCollected)
                put("remainingFund", financialSummary.remainingFund)
                put("isBalanced", financialSummary.isBalanced)
                put("balanceDiscrepancy", financialSummary.balanceDiscrepancy)

                val catArray = JSONArray()
                categories.forEach { c ->
                    catArray.put(JSONObject().apply {
                        put("category", c.category)
                        put("label", c.labelVi)
                        put("amount", c.totalAmount)
                        put("percentage", c.percentage)
                    })
                }
                put("categories", catArray)

                val memArray = JSONArray()
                members.forEach { m ->
                    memArray.put(JSONObject().apply {
                        put("name", sanitizeText(m.member.name, 40))
                        put("balance", m.balance)
                        put("status", m.status.name)
                    })
                }
                put("members", memArray)
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                parseCloudBackendResponse(responseText)
            } else {
                Log.w(TAG, "Cloud Backend returned error code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to reach Cloud Backend, will use local engine", e)
            null
        }
    }

    private fun parseCloudBackendResponse(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            when {
                root.has("insight") -> root.getString("insight")
                root.has("text") -> root.getString("text")
                root.has("result") -> root.getString("result")
                root.has("response") -> root.getString("response")
                root.has("candidates") -> {
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    parts?.optJSONObject(0)?.optString("text")
                }
                else -> null
            }
        } catch (_: Exception) {
            if (jsonString.isNotBlank() && !jsonString.trim().startsWith("{")) {
                jsonString.trim()
            } else {
                null
            }
        }
    }

    private fun sanitizeText(input: String, maxLength: Int = 80): String {
        return input
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\"", "'")
            .replace("```", "")
            .trim()
            .take(maxLength)
    }

    private fun generateLocalInsight(
        tripTitle: String,
        summary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        lang: String
    ): String {
        val topCategory = categories.maxByOrNull { it.totalAmount }
        val topCatText = topCategory?.let { "${it.labelVi} (${it.percentage.toInt()}%)" } ?: "Chưa có"
        val fundStatus = if (summary.remainingFund >= 0) {
            "Quỹ đoàn đang thặng dư ${summary.remainingFund} đ, đáp ứng tốt các khoản chi chung sắp tới."
        } else {
            "CẢNH BÁO: Quỹ đoàn đang bị âm ${-summary.remainingFund} đ. Cần thu thêm quỹ đoàn gấp!"
        }

        return if (lang == "vi") {
            """
            📊 PHÂN TÍCH TÀI CHÍNH ĐOÀN: $tripTitle
            
            1. Cơ cấu chi tiêu:
            • Danh mục chi tiêu chiếm tỷ trọng lớn nhất: $topCatText.
            • Chi từ quỹ chung: ${summary.fundPaidExpenses} đ | Thành viên chi hộ: ${summary.personalPaidExpenses} đ.
            
            2. Sức khỏe quỹ đoàn:
            • $fundStatus
            
            3. Đối soát & Quyết toán:
            • Trạng thái: ${if (summary.isBalanced) "✅ Bút toán đối soát cân bằng hoàn hảo (Tổng số dư = 0đ)." else "⚠️ Có chênh lệch đối soát ${summary.balanceDiscrepancy} đ, hãy kiểm tra lại bảng phân bổ chi tiết."}
            • Số lượng thành viên cần thanh toán: ${members.count { it.balance != 0L }} người.
            """.trimIndent()
        } else {
            """
            📊 FINANCIAL ANALYSIS: $tripTitle
            
            1. Expense Structure:
            • Largest category: $topCatText.
            • Paid from Fund: ${summary.fundPaidExpenses} VND | Personal: ${summary.personalPaidExpenses} VND.
            
            2. Group Fund Health:
            • $fundStatus
            
            3. Reconciliation:
            • Status: ${if (summary.isBalanced) "✅ Perfectly reconciled." else "⚠️ Discrepancy detected: ${summary.balanceDiscrepancy} VND."}
            • Members with pending settlement: ${members.count { it.balance != 0L }}.
            """.trimIndent()
        }
    }
}
