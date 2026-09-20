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
 * Sử dụng kết nối mạng tiêu chuẩn Android (HttpURLConnection), không phụ thuộc thư viện ngoài.
 */
object GeminiSpendingAdvisor {

    private const val TAG = "GeminiAdvisor"
    // Sử dụng model được khuyến nghị theo hướng dẫn kỹ năng gemini-api
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun analyzeTripFinances(
        tripTitle: String,
        financialSummary: FinancialSummary,
        categories: List<CategoryBreakdown>,
        members: List<MemberFinancialStatus>,
        languageCode: String = "vi"
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }

        val prompt = buildPrompt(tripTitle, financialSummary, categories, members, languageCode)

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Khi chưa cấu hình khóa API trong AI Studio Secrets, cung cấp phân tích phân tích cục bộ
            return@withContext generateLocalInsight(tripTitle, financialSummary, categories, members, languageCode)
        }

        try {
            val endpoint = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            // Xây dựng JSON payload chuẩn của Gemini API
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

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                parseGeminiResponse(responseText) ?: generateLocalInsight(tripTitle, financialSummary, categories, members, languageCode)
            } else {
                val errorStream = connection.errorStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }
                Log.w(TAG, "Gemini API error ($responseCode): $errorStream")
                generateLocalInsight(tripTitle, financialSummary, categories, members, languageCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini call failed", e)
            generateLocalInsight(tripTitle, financialSummary, categories, members, languageCode)
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
