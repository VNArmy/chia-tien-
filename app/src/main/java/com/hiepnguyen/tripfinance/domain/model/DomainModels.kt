package com.hiepnguyen.tripfinance.domain.model

import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity

data class FinancialSummary(
    val totalExpenses: Long = 0L,
    val personalPaidExpenses: Long = 0L,
    val fundPaidExpenses: Long = 0L,
    val totalFundCollected: Long = 0L,
    val remainingFund: Long = 0L,
    val isBalanced: Boolean = true,
    val balanceDiscrepancy: Long = 0L,
    val memberCount: Int = 0,
    val expenseCount: Int = 0
)

data class MemberFinancialStatus(
    val member: TripMemberEntity,
    val totalPaid: Long, // Out of pocket + Fund contribution
    val outOfPocketPaid: Long,
    val fundContributed: Long,
    val totalOwed: Long, // Total liability from all splits
    val balance: Long, // totalPaid - totalOwed
    val status: BalanceStatus
)

enum class BalanceStatus {
    RECEIVE, // > 0: Đoàn nợ thành viên (Nhận lại tiền)
    PAY,     // < 0: Thành viên nợ đoàn (Phải nộp thêm)
    BALANCED // = 0: Cân bằng
}

data class SettlementTransfer(
    val id: String,
    val fromMember: TripMemberEntity,
    val toMember: TripMemberEntity,
    val amount: Long,
    val transferNote: String,
    val isCompleted: Boolean = false
)

data class CategoryBreakdown(
    val category: String,
    val labelVi: String,
    val iconName: String,
    val totalAmount: Long,
    val percentage: Double,
    val count: Int
)

data class SplitItem(
    val memberId: String,
    val memberName: String,
    val amount: Long,
    val percentage: Double? = null,
    val isSelected: Boolean = true
)
