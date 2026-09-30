package com.example.domain.model

import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Tỷ giá quy đổi ngoại tệ mặc định sang VND.
 * Thống nhất toàn bộ ứng dụng tại một nguồn duy nhất (Single Source of Truth).
 */
object DefaultExchangeRates {
    const val RATE_USD = 25450.0
    const val RATE_EUR = 27600.0
    const val RATE_JPY = 168.0
    const val RATE_KRW = 18.5
    const val RATE_THB = 720.0
    const val RATE_SGD = 19200.0
    const val RATE_CNY = 3550.0

    val DEFAULT_RATES_MAP: Map<String, Double> = mapOf(
        "USD" to RATE_USD,
        "EUR" to RATE_EUR,
        "JPY" to RATE_JPY,
        "KRW" to RATE_KRW,
        "THB" to RATE_THB,
        "SGD" to RATE_SGD,
        "CNY" to RATE_CNY
    )

    fun getRate(currency: String): Double {
        return DEFAULT_RATES_MAP[currency] ?: 1.0
    }
}

/**
 * Hạn mức và giới hạn số học cho các giao dịch tài chính
 */
object FinancialLimits {
    // Giới hạn số tiền giao dịch tối đa: 100 tỷ VND (hoặc 100 tỷ đơn vị tiền tệ)
    const val MAX_TRANSACTION_AMOUNT = 100_000_000_000L

    // Giới hạn tỷ giá tối đa
    const val MAX_EXCHANGE_RATE = 1_000_000_000.0
}

sealed class AmountValidationResult {
    data class Success(val amount: Double, val isWholeNumber: Boolean) : AmountValidationResult()
    data class Error(val message: String) : AmountValidationResult()
}

sealed class RateValidationResult {
    data class Success(val rate: Double) : RateValidationResult()
    data class Error(val message: String) : RateValidationResult()
}

/**
 * Bộ xử lý và kiểm định số tiền chặt chẽ cho toàn bộ ứng dụng.
 * - Với VND: Loại bỏ số thập phân hoàn toàn.
 *   Xử lý chính xác dấu phân cách hàng nghìn (1.500 -> 1500, 25.450 -> 25450, 1.500.000 -> 1500000).
 *   Từ chối số thập phân trong VND (1.5, 1,5, 25.4, 1500.5 -> Báo lỗi rõ ràng).
 * - Với Ngoại tệ: Cho phép số thập phân (cents), kiểm tra định dạng chính xác.
 * - Tỷ giá: Bắt buộc phải là số dương hợp lệ, cấm ký tự lạ (như abc), cấm âm thầm gán mặc định 1.0.
 * - Chống tràn số, chống NaN, chống Infinity, kiểm soát hạn mức trần 100 tỷ.
 */
object FinancialInputValidator {

    fun parseAmount(input: String, currency: String): AmountValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return AmountValidationResult.Error("Vui lòng nhập số tiền")
        }

        if (currency.equals("VND", ignoreCase = true)) {
            val normalized = trimmed.replace(" ", "")

            // 1. Chặn nếu có cả . và , (Ví dụ: 1.500.000,50) -> Số thập phân
            if (normalized.contains('.') && normalized.contains(',')) {
                return AmountValidationResult.Error("Tiền VND không hỗ trợ số thập phân")
            }

            // 2. Chặn ký tự lạ
            if (!normalized.all { it.isDigit() || it == '.' || it == ',' }) {
                return AmountValidationResult.Error("Số tiền VND chỉ bao gồm các chữ số")
            }

            // 3. Số nguyên thuần túy (VD: 1500, 25450, 1500000)
            if (normalized.all { it.isDigit() }) {
                val longVal = normalized.toLongOrNull()
                    ?: return AmountValidationResult.Error("Số tiền vượt quá giới hạn xử lý")
                if (longVal <= 0L) return AmountValidationResult.Error("Số tiền phải lớn hơn 0")
                if (longVal > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
                    return AmountValidationResult.Error("Số tiền tối đa là 100 tỷ VND")
                }
                return AmountValidationResult.Success(longVal.toDouble(), isWholeNumber = true)
            }

            // 4. Có dấu chấm '.'
            if (normalized.contains('.')) {
                val parts = normalized.split('.')
                // Kiểm tra xem có phải định dạng phân cách hàng nghìn chuẩn Việt Nam không:
                // Phần đầu 1..3 chữ số, tất cả các phần sau ĐÚNG 3 chữ số (VD: 1.500, 25.450, 1.500.000)
                val isThousandGrouping = parts.size > 1 &&
                        parts.all { it.isNotEmpty() && it.all { c -> c.isDigit() } } &&
                        parts.first().length in 1..3 &&
                        parts.drop(1).all { it.length == 3 }

                if (isThousandGrouping) {
                    val rawDigits = parts.joinToString("")
                    val longVal = rawDigits.toLongOrNull()
                        ?: return AmountValidationResult.Error("Số tiền vượt quá giới hạn xử lý")
                    if (longVal <= 0L) return AmountValidationResult.Error("Số tiền phải lớn hơn 0")
                    if (longVal > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
                        return AmountValidationResult.Error("Số tiền tối đa là 100 tỷ VND")
                    }
                    return AmountValidationResult.Success(longVal.toDouble(), isWholeNumber = true)
                } else {
                    // Chứa dấu chấm nhưng không phải phân cách hàng nghìn (VD: 1.5, 25.45, 1.50)
                    return AmountValidationResult.Error("Tiền VND không hỗ trợ số thập phân (Ví dụ gõ 1.500 cho 1 nghìn 5 trăm đồng)")
                }
            }

            // 5. Có dấu phẩy ','
            if (normalized.contains(',')) {
                val parts = normalized.split(',')
                val isThousandGrouping = parts.size > 1 &&
                        parts.all { it.isNotEmpty() && it.all { c -> c.isDigit() } } &&
                        parts.first().length in 1..3 &&
                        parts.drop(1).all { it.length == 3 }

                if (isThousandGrouping) {
                    val rawDigits = parts.joinToString("")
                    val longVal = rawDigits.toLongOrNull()
                        ?: return AmountValidationResult.Error("Số tiền vượt quá giới hạn xử lý")
                    if (longVal <= 0L) return AmountValidationResult.Error("Số tiền phải lớn hơn 0")
                    if (longVal > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
                        return AmountValidationResult.Error("Số tiền tối đa là 100 tỷ VND")
                    }
                    return AmountValidationResult.Success(longVal.toDouble(), isWholeNumber = true)
                } else {
                    return AmountValidationResult.Error("Tiền VND không hỗ trợ số thập phân (Ví dụ gõ 1,500 cho 1 nghìn 5 trăm đồng)")
                }
            }

            return AmountValidationResult.Error("Định dạng số tiền VND không hợp lệ")
        } else {
            // Ngoại tệ (USD, EUR, ...): Cho phép tối đa 1 dấu phân cách thập phân (. hoặc ,)
            var normalized = trimmed.replace(" ", "")
            if (!normalized.all { it.isDigit() || it == '.' || it == ',' }) {
                return AmountValidationResult.Error("Định dạng số tiền không hợp lệ")
            }

            val dotCount = normalized.count { it == '.' }
            val commaCount = normalized.count { it == ',' }

            var doubleVal: Double? = null
            if (dotCount == 0 && commaCount == 0) {
                doubleVal = normalized.toDoubleOrNull()
            } else if (dotCount > 1 && commaCount == 0) {
                // Dạng 1.500.000
                val parts = normalized.split('.')
                if (parts.size > 1 && parts.drop(1).all { it.length == 3 }) {
                    doubleVal = parts.joinToString("").toDoubleOrNull()
                }
            } else if (commaCount > 1 && dotCount == 0) {
                // Dạng 1,500,000
                val parts = normalized.split(',')
                if (parts.size > 1 && parts.drop(1).all { it.length == 3 }) {
                    doubleVal = parts.joinToString("").toDoubleOrNull()
                }
            } else if (dotCount >= 1 && commaCount == 1) {
                if (normalized.indexOf('.') < normalized.indexOf(',')) {
                    // 1.500,50
                    doubleVal = normalized.replace(".", "").replace(',', '.').toDoubleOrNull()
                } else {
                    // 1,500.50
                    doubleVal = normalized.replace(",", "").toDoubleOrNull()
                }
            } else if (commaCount >= 1 && dotCount == 1) {
                if (normalized.indexOf(',') < normalized.indexOf('.')) {
                    // 1,500.50
                    doubleVal = normalized.replace(",", "").toDoubleOrNull()
                } else {
                    // 1.500,50
                    doubleVal = normalized.replace(".", "").replace(',', '.').toDoubleOrNull()
                }
            } else if (commaCount == 1 && dotCount == 0) {
                // 12,50 hoặc 1,500
                val parts = normalized.split(',')
                if (parts.size == 2 && parts[1].length == 3 && parts[0].length in 1..3) {
                    doubleVal = parts.joinToString("").toDoubleOrNull()
                } else {
                    doubleVal = normalized.replace(',', '.').toDoubleOrNull()
                }
            } else if (dotCount == 1 && commaCount == 0) {
                // 12.50
                doubleVal = normalized.toDoubleOrNull()
            }

            if (doubleVal == null || !doubleVal.isFinite() || doubleVal.isNaN()) {
                return AmountValidationResult.Error("Định dạng số tiền không hợp lệ")
            }
            if (doubleVal <= 0.0) {
                return AmountValidationResult.Error("Số tiền phải lớn hơn 0")
            }
            if (doubleVal > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
                return AmountValidationResult.Error("Số tiền tối đa là 100 tỷ")
            }

            val isWhole = (doubleVal % 1.0 == 0.0)
            return AmountValidationResult.Success(doubleVal, isWholeNumber = isWhole)
        }
    }

    fun parseRate(input: String, currency: String): RateValidationResult {
        if (currency.equals("VND", ignoreCase = true)) {
            return RateValidationResult.Success(1.0)
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return RateValidationResult.Error("Vui lòng nhập tỷ giá quy đổi")
        }

        val normalized = trimmed.replace(" ", "")
        // Bắt buộc chỉ chứa chữ số hoặc dấu phân cách, cấm chữ cái như "abc"
        if (!normalized.all { it.isDigit() || it == '.' || it == ',' }) {
            return RateValidationResult.Error("Tỷ giá không hợp lệ. Vui lòng nhập số dương (Ví dụ: 25450)")
        }

        var rateVal: Double? = null
        if (normalized.contains('.') && normalized.split('.').size == 2 &&
            normalized.split('.')[1].length == 3 && normalized.split('.')[0].length in 1..3) {
            // Dạng 25.450 (nghìn)
            rateVal = normalized.replace(".", "").toDoubleOrNull()
        } else {
            rateVal = normalized.replace(',', '.').toDoubleOrNull()
        }

        if (rateVal == null || !rateVal.isFinite() || rateVal.isNaN() || rateVal <= 0.0) {
            return RateValidationResult.Error("Tỷ giá phải là số dương lớn hơn 0 (Ví dụ: 25450)")
        }
        if (rateVal > FinancialLimits.MAX_EXCHANGE_RATE) {
            return RateValidationResult.Error("Tỷ giá vượt quá giới hạn tối đa cho phép")
        }

        return RateValidationResult.Success(rateVal)
    }

    /**
     * Quy đổi số tiền ngoại tệ sang VND an toàn, chống tràn số và làm tròn chuẩn.
     */
    fun convertToVnd(amount: Double, rate: Double): Long {
        if (!amount.isFinite() || !rate.isFinite() || amount.isNaN() || rate.isNaN() || amount <= 0.0 || rate <= 0.0) return 0L
        val product = amount * rate
        if (product > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            return FinancialLimits.MAX_TRANSACTION_AMOUNT
        }
        return kotlin.math.round(product).toLong()
    }
}

/**
 * Các phép toán cộng trừ tài chính an toàn, bảo vệ chống tràn số (Overflow Protection).
 */
object FinancialMath {
    fun safeAdd(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (e: ArithmeticException) {
            if (a > 0 && b > 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    fun safeSubtract(a: Long, b: Long): Long {
        return try {
            Math.subtractExact(a, b)
        } catch (e: ArithmeticException) {
            if (a > 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }
}

inline fun <T> Iterable<T>.sumOfSafe(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        val value = selector(element)
        sum = FinancialMath.safeAdd(sum, value)
    }
    return sum
}

/**
 * Trình sinh mã đoàn ngẫu nhiên chuẩn mật mã (High Entropy Trip Code Generator).
 * 32^6 = 1.073.741.824 tổ hợp, triệt tiêu nguy cơ trùng lặp mã đoàn.
 */
object TripCodeGenerator {
    private val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray() // 32 ký tự, không nhầm lẫn 0/O, 1/I
    private val random = SecureRandom()

    fun generateCode(prefix: String = "TRIP-"): String {
        val sb = StringBuilder(prefix)
        for (i in 0 until 6) {
            sb.append(CHARS[random.nextInt(CHARS.size)])
        }
        return sb.toString()
    }
}
