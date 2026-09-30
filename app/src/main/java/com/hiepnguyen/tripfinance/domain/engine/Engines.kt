package com.hiepnguyen.tripfinance.domain.engine

import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

object SplitCalculator {

    /**
     * Chia đều tất cả thành viên (Equal Split)
     * Quy tắc làm tròn: Làm tròn nguyên phần chia cơ sở (totalAmount / count),
     * nếu phép chia có phần dư (remainder), cộng toàn bộ phần chênh lệch cho người đầu tiên trong danh sách (hoặc primaryMemberId).
     */
    fun calculateEqualSplit(
        totalAmount: Long,
        memberIds: List<String>,
        primaryMemberId: String? = null
    ): List<Pair<String, Long>> {
        if (memberIds.isEmpty() || totalAmount <= 0) return emptyList()
        val count = memberIds.size
        val baseShare = totalAmount / count
        val remainder = (totalAmount % count).toInt()

        val priorityIndex = if (primaryMemberId != null && memberIds.contains(primaryMemberId)) {
            memberIds.indexOf(primaryMemberId)
        } else {
            0 // Mặc định người đầu tiên trong danh sách
        }

        return memberIds.mapIndexed { index, memberId ->
            val extra = if (index == priorityIndex) remainder.toLong() else 0L
            memberId to (baseShare + extra)
        }
    }

    /**
     * Chia theo tỷ lệ phần trăm (Ratio Split)
     * Ràng buộc: Tổng % phải bằng 100.0%.
     * Chỉ bù chênh lệch làm tròn (vài đồng lẻ do số thập phân) KHI VÀ CHỈ KHI tổng % đạt 100%.
     * Tuyệt đối không dồn chênh lệch tỷ lệ vào người trả tiền khi tổng chưa bằng 100%,
     * và đảm bảo không bao giờ tạo ra số tiền âm.
     */
    fun calculateRatioSplit(
        totalAmount: Long,
        memberRatios: List<Pair<String, Double>>,
        primaryMemberId: String? = null
    ): Pair<List<Pair<String, Long>>, Boolean> {
        if (memberRatios.isEmpty()) return emptyList<Pair<String, Long>>() to false
        val totalRatio = memberRatios.sumOf { it.second }
        // Kiểm tra tổng tỷ lệ có bằng 100% không (cho phép sai số làm tròn số thực nhỏ 0.01%)
        val isRatioValid = abs(totalRatio - 100.0) <= 0.01

        var currentSum = 0L
        val splits = memberRatios.map { (memberId, ratio) ->
            val safeRatio = ratio.coerceAtLeast(0.0)
            val share = ((totalAmount * safeRatio) / 100.0).roundToLong().coerceAtLeast(0L)
            currentSum += share
            memberId to share
        }.toMutableList()

        // Bù chênh lệch làm tròn số lẻ (vài đồng) KHI VÀ CHỈ KHI tổng tỷ lệ % đã đạt 100%
        if (isRatioValid && splits.isNotEmpty()) {
            val diff = totalAmount - currentSum
            if (diff != 0L) {
                val targetIndex = if (primaryMemberId != null) {
                    val pIdx = splits.indexOfFirst { it.first == primaryMemberId }
                    if (pIdx >= 0 && splits[pIdx].second + diff >= 0L) pIdx
                    else splits.indices.maxByOrNull { splits[it].second } ?: 0
                } else {
                    splits.indices.maxByOrNull { splits[it].second } ?: 0
                }

                val current = splits[targetIndex]
                val safeNewAmount = (current.second + diff).coerceAtLeast(0L)
                splits[targetIndex] = current.first to safeNewAmount
            }
        }

        return splits to isRatioValid
    }

    /**
     * Chia theo nhóm người được chọn (Custom Participant Split)
     */
    fun calculateSelectedParticipantsSplit(
        totalAmount: Long,
        selectedMemberIds: List<String>,
        primaryMemberId: String? = null
    ): List<Pair<String, Long>> {
        return calculateEqualSplit(totalAmount, selectedMemberIds, primaryMemberId)
    }

    /**
     * Kiểm tra Toàn vẹn Dữ liệu (Integrity Check):
     * Hệ thống đảm bảo tổng các phần phân bổ bắt buộc phải bằng đúng tổng số tiền của khoản chi gốc.
     */
    fun validateSplits(totalAmount: Long, splits: List<Pair<String, Long>>): Boolean {
        if (splits.isEmpty() && totalAmount == 0L) return true
        return splits.sumOfSafe { it.second } == totalAmount
    }
}

data class SettlementCalculationResult(
    val transfers: List<SettlementTransfer>,
    val reconciliationError: String? = null
)

object SettlementEngine {

    // Ngưỡng sai số so sánh dập tắt hoàn toàn rủi ro sai số số học khiến vòng lặp chạy vô tận
    const val EPSILON = 0.001

    /**
     * Thuật toán Tham Lam (Greedy Algorithm) Tối Ưu Hóa Dòng Tiền:
     * - Ý tưởng cốt lõi: Luôn ưu tiên lấy bên nợ nhiều nhất (Max Debtor) trả cho bên đang được hệ thống nợ nhiều nhất (Max Creditor).
     * - Bằng cách triệt tiêu các khoản lớn trước, rút gọn đáng kể số lượng giao dịch chuyển khoản qua lại.
     * - Khi đoàn có Quỹ chung còn dư (remainingFund > 0) hoặc bị chi vượt (remainingFund < 0):
     *   Quỹ đoàn đóng vai trò là một bên tham gia quyết toán với số dư = -remainingFund.
     * - Trả về đối tượng kết quả SettlementCalculationResult(transfers, reconciliationError) phi trạng thái,
     *   hoàn toàn không sử dụng biến toàn cục có thể bị ghi đè khi gọi đồng thời (Race Condition).
     */
    fun computeSettlementWithStatus(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null
    ): SettlementCalculationResult {
        if (memberStatuses.isEmpty()) {
            val errorMsg = "Đoàn không có thành viên nào! Không thể thực hiện đối soát và quyết toán."
            return SettlementCalculationResult(emptyList(), errorMsg)
        }

        val totalMemberBalance = memberStatuses.sumOfSafe { it.balance }
        // Kiểm tra đối soát: Tổng balance toàn đoàn trừ đi số quỹ còn lại phải bằng 0 tuyệt đối (Zero discrepancy tolerance)
        // Không cho phép bất kỳ dung sai cố định 5 VND nào để chống thất thoát và lệch nhỏ tích lũy
        val discrepancy = totalMemberBalance - remainingFund
        if (discrepancy != 0L) {
            val errorMsg = "Phát hiện chênh lệch đối soát ($discrepancy VND). Chưa thể tạo kế hoạch chuyển khoản do các khoản thu chi chưa cân bằng tuyệt đối."
            return SettlementCalculationResult(
                transfers = emptyList(),
                reconciliationError = errorMsg
            )
        }

        val transfers = computeTransfersInternal(
            memberStatuses = memberStatuses,
            tripJoinCode = tripJoinCode,
            remainingFund = remainingFund,
            fundHolder = fundHolder,
            totalMemberBalance = totalMemberBalance,
            discrepancy = discrepancy
        )
        return SettlementCalculationResult(transfers = transfers, reconciliationError = null)
    }

    fun computeSimplifiedTransfers(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null
    ): List<SettlementTransfer> {
        return computeSettlementWithStatus(memberStatuses, tripJoinCode, remainingFund, fundHolder).transfers
    }

    private fun computeTransfersInternal(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String,
        remainingFund: Long,
        fundHolder: TripMemberEntity?,
        totalMemberBalance: Long,
        discrepancy: Long
    ): List<SettlementTransfer> {
        data class BalanceEntry(val member: TripMemberEntity, var balance: Long)

        val balanceEntries = mutableListOf<BalanceEntry>()
        memberStatuses.forEach {
            balanceEntries.add(BalanceEntry(it.member, it.balance))
        }

        // Nếu quỹ còn dư hoặc bị chi vượt, đưa Quỹ đoàn vào quyết toán như một bên tham gia
        // Gán số dư Quỹ = -totalMemberBalance để triệt tiêu tuyệt đối mọi chênh lệch làm tròn
        if (remainingFund != 0L || discrepancy != 0L) {
            val fundMemberName = if (fundHolder != null) {
                if (fundHolder.role == "TREASURER") "Quỹ đoàn (Thủ quỹ: ${fundHolder.name})"
                else "Quỹ đoàn (${fundHolder.name})"
            } else "Quỹ chung đoàn"

            val fundMember = TripMemberEntity(
                id = "FUND_ORGANIZATION",
                tripId = fundHolder?.tripId ?: "",
                userId = "fund",
                name = fundMemberName,
                role = "TREASURER",
                isActive = true,
                bankName = fundHolder?.bankName,
                bankAccount = fundHolder?.bankAccount,
                bankAccountHolder = fundHolder?.bankAccountHolder ?: fundHolder?.name?.uppercase(),
                joinedAt = 0L
            )
            balanceEntries.add(BalanceEntry(fundMember, -totalMemberBalance))
        }

        // 1. Phân loại và sắp xếp giảm dần:
        // Creditors: Bên có balance > 0 (được nhận lại tiền)
        val creditors = balanceEntries
            .filter { it.balance > 0L }
            .sortedByDescending { it.balance }
            .toMutableList()

        // Debtors: Bên có balance < 0 (phải trả / chuyển tiền đi)
        val debtors = balanceEntries
            .filter { it.balance < 0L }
            .map { BalanceEntry(it.member, -it.balance) }
            .sortedByDescending { it.balance }
            .toMutableList()

        val transfers = mutableListOf<SettlementTransfer>()
        var i = 0 // con trỏ debtors
        var j = 0 // con trỏ creditors

        // Vòng lặp tham lam giải quyết nợ từng cặp lớn nhất
        while (i < debtors.size && j < creditors.size) {
            val debtor = debtors[i]
            val creditor = creditors[j]

            if (debtor.balance <= 0L) {
                i++
                continue
            }
            if (creditor.balance <= 0L) {
                j++
                continue
            }

            // Triệt tiêu số tiền nhỏ hơn giữa 2 bên
            val settleAmount = minOf(debtor.balance, creditor.balance)

            if (settleAmount > 0L) {
                val transferNote = when {
                    debtor.member.id == "FUND_ORGANIZATION" -> "[$tripJoinCode] Trich Quy doan quyet toan cho ${creditor.member.name}"
                    creditor.member.id == "FUND_ORGANIZATION" -> "[$tripJoinCode] ${debtor.member.name} nop bu thieu hut Quy doan"
                    else -> "[$tripJoinCode] ${debtor.member.name} quyet toan cho ${creditor.member.name}"
                }

                transfers.add(
                    SettlementTransfer(
                        id = UUID.randomUUID().toString(),
                        fromMember = debtor.member,
                        toMember = creditor.member,
                        amount = settleAmount,
                        transferNote = transferNote
                    )
                )
            }

            debtor.balance -= settleAmount
            creditor.balance -= settleAmount

            if (debtor.balance <= 0L) i++
            if (creditor.balance <= 0L) j++
        }

        return transfers
    }
}

object CashFlowMinimizer {
    fun minimizeTransfers(
        memberStatuses: List<MemberFinancialStatus>,
        tripJoinCode: String = "",
        remainingFund: Long = 0L,
        fundHolder: TripMemberEntity? = null
    ): List<SettlementTransfer> {
        return SettlementEngine.computeSimplifiedTransfers(memberStatuses, tripJoinCode, remainingFund, fundHolder)
    }
}
