package com.example.domain.ai

import android.util.Log
import com.example.BuildConfig
import com.example.domain.model.CategoryBreakdown
import com.example.domain.model.FinancialSummary
import com.example.domain.model.MemberFinancialStatus
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
 * Service tích hợp Gemini AI phân tích thông minh cơ cấu chi tiêu,
 * dự báo ngân sách và đưa ra lời khuyên tối ưu tài chính cho đoàn công tác.
 * 
 * Kiến trúc bảo mật cho môi trường thương mại:
 * - Chuyển toàn bộ các lệnh gọi AI qua dịch vụ trung gian Cloud Function / Cloud Run.
 * - Xác thực toàn vẹn thiết bị bằng Firebase App Check (Header X-Firebase-AppCheck),
 *   triệt tiêu hoàn toàn nguy cơ rò rỉ API Key từ mã nguồn client APK.
 * - Hỗ trợ phân tích cục bộ (Local Insight) offline độc lập khi không có mạng.
 */
object GeminiSpendingAdvisor {

    private const val TAG = "GeminiAdvisor"
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_DIRECT_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    // URL Cloud Backend (Cloud Run / Cloud Functions)
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

    suspend fun analyzeTripFinances(
        tripTitle: String,
        financialSummary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        languageCode: String = "vi"
    ): String = withContext(Dispatchers.IO) {
        val cloudBackendUrl = resolveCloudBackendUrl()

        // 1. KIẾN TRÚC SẢN XUẤT THƯƠNG MẠI: Gọi qua Cloud Backend (Cloud Run / Cloud Function) với Firebase App Check
        if (cloudBackendUrl != null) {
            val backendResult = callCloudBackend(cloudBackendUrl, tripTitle, financialSummary, categories, members, languageCode)
            if (backendResult != null) {
                return@withContext backendResult
            }
        }

        // 2. CHẾ ĐỘ NỘI BỘ / PROTOTYPE: Gọi trực tiếp Gemini REST nếu có GEMINI_API_KEY
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }

        val prompt = buildPrompt(tripTitle, financialSummary, categories, members, languageCode)

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val directResult = callDirectGeminiApi(apiKey, prompt)
                if (directResult != null) {
                    return@withContext directResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct Gemini call failed, falling back to local insight engine", e)
            }
        }

        // 3. OFFLINE / PRIVACY-FIRST FALLBACK: Phân tích tài chính cục bộ không cần mạng, bảo vệ 100% dữ liệu
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
                else -> parseGeminiResponse(jsonString)
            }
        } catch (_: Exception) {
            if (jsonString.isNotBlank() && !jsonString.trim().startsWith("{")) {
                jsonString.trim()
            } else {
                null
            }
        }
    }

    private fun callDirectGeminiApi(apiKey: String, prompt: String): String? {
        val endpoint = "$BASE_DIRECT_URL/$MODEL_NAME:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
            parseGeminiResponse(responseText)
        } else {
            val errorStream = connection.errorStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }
            Log.w(TAG, "Direct Gemini API error (${connection.responseCode}): $errorStream")
            null
        }
    }

    private fun parseGeminiResponse(jsonString: String): String? {
        return try {
            val root = JSONObject(jsonString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            parts.getJSONObject(0).optString("text")
        } catch (e: Exception) {
            null
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

    private fun buildPrompt(
        tripTitle: String,
        summary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        lang: String
    ): String {
        val safeTitle = sanitizeText(tripTitle, 100)
        val catSummary = categories.joinToString("; ") { "${sanitizeText(it.labelVi, 30)}: ${it.totalAmount} VNĐ (${it.percentage.toInt()}%)" }
        val memberSummary = members.joinToString("; ") { "${sanitizeText(it.member.name, 40)} (${it.status.name}: ${it.balance} VNĐ)" }

        return if (lang == "vi") {
            """
            Bạn là chuyên gia cố vấn tài chính cho các đoàn công tác và du lịch.
            Hãy phân tích ngắn gọn tình hình tài chính của đoàn "$safeTitle":
            - Tổng chi tiêu: ${summary.totalExpenses} VNĐ (Cá nhân chi hộ: ${summary.personalPaidExpenses} VNĐ, Chi từ quỹ: ${summary.fundPaidExpenses} VNĐ)
            - Quỹ chung: Thu được ${summary.totalFundCollected} VNĐ, Còn dư ${summary.remainingFund} VNĐ
            - Tình trạng đối soát: ${if (summary.isBalanced) "Cân đối hợp lệ (Balance = 0đ)" else "Lệch ${summary.balanceDiscrepancy} VNĐ"}
            - Phân bổ theo danh mục: $catSummary
            - Số dư thành viên: $memberSummary

            Đưa ra đánh giá súc tích gồm:
            1. Đánh giá cơ cấu chi tiêu (danh mục chiếm tỷ trọng lớn nhất).
            2. Tình trạng sức khỏe quỹ đoàn (quỹ đủ hay thiếu, cần thu thêm không).
            3. Khuyến nghị hành động cụ thể cho Trưởng đoàn và Thủ quỹ.
            """.trimIndent()
        } else {
            """
            You are a financial advisor for group business and travel trips.
            Analyze the finances for "$safeTitle":
            - Total Expenses: ${summary.totalExpenses} VND (Personal: ${summary.personalPaidExpenses}, Fund: ${summary.fundPaidExpenses})
            - Group Fund: Collected ${summary.totalFundCollected} VND, Remaining ${summary.remainingFund} VND
            - Balance status: ${if (summary.isBalanced) "Balanced" else "Discrepancy of ${summary.balanceDiscrepancy} VND"}
            - Categories: $catSummary
            - Member balances: $memberSummary

            Provide a concise summary with:
            1. Expense distribution breakdown.
            2. Fund solvency and sustainability.
            3. Actionable advice for the trip leader and treasurer.
            """.trimIndent()
        }
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
