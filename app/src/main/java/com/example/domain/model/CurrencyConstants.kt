package com.example.domain.model

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
