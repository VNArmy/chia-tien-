package com.example.domain.export

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExpenseSplitEntity
import com.example.data.entity.FundContributionEntity
import com.example.data.entity.TripEntity
import com.example.data.entity.TripMemberEntity
import com.example.domain.model.FinancialSummary
import com.example.domain.model.MemberFinancialStatus
import com.example.domain.model.SettlementTransfer
import com.example.domain.model.sumOfSafe
import com.example.ui.components.NumberFormatUtils
import java.text.SimpleDateFormat
import java.util.*

enum class ReportType(val title: String, val description: String) {
    FULL_SETTLEMENT("Báo Cáo Tổng Hợp Quyết Toán", "Đầy đủ thống kê, số dư từng người, kế hoạch chuyển khoản & đối soát"),
    EXPENSE_LEDGER("Bảng Kê Chi Tiết Thu - Chi", "Danh sách từng khoản chi tiêu, người thanh toán và đóng góp quỹ"),
    TRANSFER_INSTRUCTIONS("Lệnh Chuyển Khoản Quyết Toán", "Vắn tắt thông tin STK, ngân hàng, số tiền và cú pháp chuyển khoản"),
    MEMBER_BREAKDOWN("Báo Cáo Phân Bổ Theo Từng Thành Viên", "Bảng kê chi tiết tiền đã chi và tiền chịu chi của mỗi cá nhân")
}

object ReportGenerator {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

    fun generateTextReport(
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        financialSummary: FinancialSummary,
        memberStatuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        fundContributions: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        reportType: ReportType,
        creatorName: String
    ): String {
        val now = Date()
        val tripTitle = trip?.title ?: "ĐOÀN CÔNG TÁC / DU LỊCH"
        val joinCode = trip?.joinCode ?: "---"

        return when (reportType) {
            ReportType.FULL_SETTLEMENT -> generateFullSettlementReport(
                tripTitle, joinCode, members, financialSummary, memberStatuses, settlementTransfers, expenses, creatorName, now
            )
            ReportType.EXPENSE_LEDGER -> generateExpenseLedgerReport(
                tripTitle, joinCode, members, financialSummary, expenses, fundContributions, splits, creatorName, now
            )
            ReportType.TRANSFER_INSTRUCTIONS -> generateTransferInstructionsReport(
                tripTitle, joinCode, settlementTransfers, creatorName, now
            )
            ReportType.MEMBER_BREAKDOWN -> generateMemberBreakdownReport(
                tripTitle, joinCode, members, memberStatuses, creatorName, now
            )
        }
    }

    private fun generateFullSettlementReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        transfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                       CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM")
        appendLine("                            Độc lập - Tự do - Hạnh phúc")
        appendLine("                                     -------o0o-------")
        appendLine()
        appendLine("                   BÁO CÁO TỔNG HỢP QUYẾT TOÁN TÀI CHÍNH")
        appendLine("                             ĐOÀN: ${tripTitle.uppercase()}")
        appendLine("                               (Mã đoàn: $joinCode)")
        appendLine("================================================================================")
        appendLine("Thời gian lập báo cáo : ${dateFormat.format(date)}")
        appendLine("Người lập báo cáo     : $creatorName")
        appendLine("Tổng số thành viên    : ${members.size} người")
        appendLine("Tình trạng đối soát   : ${if (summary.isBalanced) "HỢP LỆ (Tổng số dư cân bằng = 0 đ)" else "CẢNH BÁO LỆCH: " + NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)}")
        appendLine("--------------------------------------------------------------------------------")
        appendLine()
        appendLine("I. TỔNG QUAN THU - CHI ĐOÀN")
        appendLine("  1. Tổng chi tiêu toàn đoàn       : ${NumberFormatUtils.formatVnd(summary.totalExpenses)}")
        appendLine("     - Thành viên tự chi hộ (Paid)  : ${NumberFormatUtils.formatVnd(summary.personalPaidExpenses)}")
        appendLine("     - Chi từ nguồn Quỹ chung đoàn  : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
        appendLine("  2. Tình hình Quỹ chung:")
        appendLine("     - Tổng tiền đã thu vào quỹ     : ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}")
        appendLine("     - Tổng tiền quỹ đã giải ngân   : ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}")
        appendLine("     - Số dư quỹ chung còn lại      : ${NumberFormatUtils.formatVnd(summary.remainingFund)}")
        appendLine()
        appendLine("II. BẢNG TỔNG HỢP SỐ DƯ & QUYẾT TOÁN TỪNG THÀNH VIÊN")
        appendLine(String.format("%-4s | %-18s | %-12s | %-12s | %-12s | %-18s", "STT", "Họ và tên", "Đã chi hộ", "Nộp quỹ", "Phải chịu", "Số dư / Trạng thái"))
        appendLine("--------------------------------------------------------------------------------")
        statuses.forEachIndexed { index, st ->
            val statusStr = when {
                st.balance > 0 -> "+${NumberFormatUtils.formatVnd(st.balance)} (Nhận lại)"
                st.balance < 0 -> "${NumberFormatUtils.formatVnd(st.balance)} (Cần nộp)"
                else -> "0 đ (Đã cân bằng)"
            }
            appendLine(
                String.format(
                    "%-4d | %-18s | %-12s | %-12s | %-12s | %-18s",
                    index + 1,
                    st.member.name.take(18),
                    NumberFormatUtils.formatVnd(st.outOfPocketPaid),
                    NumberFormatUtils.formatVnd(st.fundContributed),
                    NumberFormatUtils.formatVnd(st.totalOwed),
                    statusStr
                )
            )
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine()
        appendLine("III. KẾ HOẠCH CHUYỂN KHOẢN THANH TOÁN (TỐI ƯU HÓA DÒNG TIỀN)")
        if (transfers.isEmpty()) {
            appendLine("  -> Tất cả các thành viên đã cân bằng tài chính. Không cần chuyển khoản thêm.")
        } else {
            appendLine("  (Thuật toán tham lam đã rút gọn còn ${transfers.size} giao dịch chuyển khoản tối thiểu):")
            appendLine()
            transfers.forEachIndexed { idx, tr ->
                appendLine("  [Giao dịch #${idx + 1}]")
                appendLine("  • Người chuyển (Nộp) : ${tr.fromMember.name}")
                appendLine("  • Người nhận tiền    : ${tr.toMember.name}")
                appendLine("  • Số tiền            : ${NumberFormatUtils.formatVnd(tr.amount)}")
                appendLine("  • Ngân hàng          : ${tr.toMember.bankName ?: "Chưa cập nhật"}")
                appendLine("  • Số tài khoản       : ${tr.toMember.bankAccount ?: "Chưa cập nhật"}")
                appendLine("  • Chủ tài khoản      : ${tr.toMember.bankAccountHolder ?: tr.toMember.name.uppercase()}")
                appendLine("  • Nội dung CK        : ${tr.transferNote}")
                appendLine()
            }
        }
        appendLine("IV. PHÂN LOẠI CHI TIÊU THEO DANH MỤC")
        val catMap = mapOf(
            "FOOD" to "Ăn uống",
            "TRANSPORT" to "Di chuyển",
            "HOTEL" to "Khách sạn/Lưu trú",
            "SIGHTSEEING" to "Vé tham quan",
            "ENTERTAINMENT" to "Vui chơi/Giải trí",
            "SHOPPING" to "Mua sắm",
            "OTHER" to "Chi phí khác"
        )
        expenses.groupBy { it.category }.forEach { (cat, list) ->
            val sum = list.sumOfSafe { it.convertedTotalAmount }
            val pct = if (summary.totalExpenses > 0) (sum * 100.0 / summary.totalExpenses) else 0.0
            appendLine(String.format("  • %-20s: %15s (%4.1f%%) - %d khoản", catMap[cat] ?: cat, NumberFormatUtils.formatVnd(sum), pct, list.size))
        }
        appendLine()
        appendLine("V. DANH SÁCH SỐ TÀI KHOẢN NGÂN HÀNG CÁC THÀNH VIÊN TRONG ĐOÀN")
        appendLine(String.format("%-4s | %-18s | %-12s | %-12s | %-16s | %-18s", "STT", "Họ và tên", "Vai trò", "Ngân hàng", "Số tài khoản", "Chủ tài khoản"))
        appendLine("--------------------------------------------------------------------------------")
        members.forEachIndexed { i, m ->
            val roleName = when (m.role) {
                "ADMIN" -> "Trưởng đoàn"
                "TREASURER" -> "Thủ quỹ"
                "MEMBER" -> "Thành viên"
                else -> "Người xem"
            }
            appendLine(
                String.format(
                    "%-4d | %-18s | %-12s | %-12s | %-16s | %-18s",
                    i + 1,
                    m.name.take(18),
                    roleName,
                    (m.bankName ?: "Chưa có").take(12),
                    (m.bankAccount ?: "Chưa có").take(16),
                    (m.bankAccountHolder ?: m.name.uppercase()).take(18)
                )
            )
        }
        appendLine()
        appendLine("================================================================================")
        appendLine("                                   XÁC NHẬN")
        appendLine("           TRƯỞNG ĐOÀN                                THỦ QUỸ")
        appendLine("        (Ký và ghi rõ họ tên)                  (Ký và ghi rõ họ tên)")
        appendLine()
        appendLine()
        appendLine("================================================================================")
    }

    private fun generateExpenseLedgerReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                   BẢNG KÊ CHI TIẾT CÁC KHOẢN THU - CHI ĐOÀN")
        appendLine("                             ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày lập: ${dateFormat.format(date)} | Người lập: $creatorName")
        appendLine()
        appendLine("PHẦN 1: DANH SÁCH CHI TIÊU (${expenses.size} khoản chi)")
        appendLine(String.format("%-4s | %-14s | %-20s | %-14s | %-12s | %-10s", "STT", "Thời gian", "Tên khoản chi", "Nguồn chi", "Số tiền", "Kiểu chia"))
        appendLine("--------------------------------------------------------------------------------")
        expenses.sortedBy { it.timestamp }.forEachIndexed { idx, exp ->
            val payer = if (exp.payerType == "FUND") "Quỹ đoàn" else (members.find { it.id == exp.payerMemberId }?.name ?: "Cá nhân")
            val splitMode = when (exp.splitType) {
                "EQUAL" -> "Chia đều"
                "CUSTOM_PARTICIPANT" -> "Chọn người"
                "RATIO" -> "Tỷ lệ %"
                "CUSTOM_AMOUNT" -> "Tùy nhập"
                else -> exp.splitType
            }
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-14s | %-12s | %-10s",
                    idx + 1,
                    dateFormat.format(Date(exp.timestamp)),
                    exp.title.take(20),
                    payer.take(14),
                    NumberFormatUtils.formatVnd(exp.convertedTotalAmount),
                    splitMode
                )
            )

            // Danh sách người tham gia được chọn cho mỗi khoản chi
            val expSplits = splits.filter { it.expenseId == exp.id }
            if (expSplits.isNotEmpty()) {
                val participantsDetail = expSplits.joinToString("; ") { sp ->
                    val memName = members.find { it.id == sp.memberId }?.name ?: "Thành viên"
                    "$memName: ${NumberFormatUtils.formatVnd(sp.amount)}"
                }
                appendLine("       -> Người cùng chịu (${expSplits.size} người): $participantsDetail")
            }

            if (exp.note.isNotBlank()) {
                appendLine("       -> Ghi chú: ${exp.note}")
            }
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine("TỔNG CỘNG CHI TIÊU: ${NumberFormatUtils.formatVnd(summary.totalExpenses)}")
        appendLine()
        appendLine("PHẦN 2: LỊCH SỬ ĐÓNG GÓP QUỸ (${funds.size} đợt nộp)")
        appendLine(String.format("%-4s | %-14s | %-20s | %-16s | %-20s", "STT", "Thời gian", "Người nộp", "Số tiền", "Ghi chú"))
        appendLine("--------------------------------------------------------------------------------")
        funds.sortedBy { it.timestamp }.forEachIndexed { idx, f ->
            val contributor = members.find { it.id == f.memberId }?.name ?: "Thành viên"
            appendLine(
                String.format(
                    "%-4d | %-14s | %-20s | %-16s | %-20s",
                    idx + 1,
                    dateFormat.format(Date(f.timestamp)),
                    contributor.take(20),
                    NumberFormatUtils.formatVnd(f.convertedAmount),
                    f.note.take(20)
                )
            )
        }
        appendLine("--------------------------------------------------------------------------------")
        appendLine("TỔNG CỘNG THU QUỸ: ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}")
        appendLine("================================================================================")
    }

    private fun generateTransferInstructionsReport(
        tripTitle: String,
        joinCode: String,
        transfers: List<SettlementTransfer>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("             HƯỚNG DẪN CHUYỂN KHOẢN THANH TOÁN QUYẾT TOÁN ĐOÀN")
        appendLine("                      ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày xuất: ${dateFormat.format(date)} | Tạo bởi: $creatorName")
        appendLine()
        if (transfers.isEmpty()) {
            appendLine("Tất cả thành viên đã hoàn thành quyết toán cân bằng 100%. Không cần chuyển khoản.")
        } else {
            transfers.forEachIndexed { i, t ->
                appendLine("--------------------------------------------------------------------------------")
                appendLine("LỆNH CHUYỂN KHOẢN #${i + 1}:")
                appendLine("  • Người gửi (Cần nộp) : ${t.fromMember.name}")
                appendLine("  • Người nhận (Hưởng)  : ${t.toMember.name}")
                appendLine("  • Số tiền             : ${NumberFormatUtils.formatVnd(t.amount)}")
                appendLine("  • Ngân hàng           : ${t.toMember.bankName ?: "Chưa có"}")
                appendLine("  • Số tài khoản        : ${t.toMember.bankAccount ?: "Chưa có"}")
                appendLine("  • Tên chủ tài khoản   : ${t.toMember.bankAccountHolder ?: t.toMember.name.uppercase()}")
                appendLine("  • Nội dung chuyển     : ${t.transferNote}")
            }
            appendLine("--------------------------------------------------------------------------------")
            appendLine("Lưu ý: Quý thành viên vui lòng điền chính xác nội dung chuyển khoản để đối soát tự động.")
        }
    }

    private fun generateMemberBreakdownReport(
        tripTitle: String,
        joinCode: String,
        members: List<TripMemberEntity>,
        statuses: List<MemberFinancialStatus>,
        creatorName: String,
        date: Date
    ): String = buildString {
        appendLine("================================================================================")
        appendLine("                 BÁO CÁO PHÂN BỔ CHI PHÍ TỪNG THÀNH VIÊN")
        appendLine("                      ĐOÀN: ${tripTitle.uppercase()} (Mã: $joinCode)")
        appendLine("================================================================================")
        appendLine("Ngày xuất: ${dateFormat.format(date)} | Người lập: $creatorName")
        appendLine()
        statuses.forEachIndexed { idx, st ->
            appendLine("--------------------------------------------------------------------------------")
            appendLine("THÀNH VIÊN #${idx + 1}: ${st.member.name.uppercase()} (Vai trò: ${st.member.role})")
            appendLine("  • Tiền cá nhân tự chi hộ (Out-of-pocket) : ${NumberFormatUtils.formatVnd(st.outOfPocketPaid)}")
            appendLine("  • Tiền đã nộp vào Quỹ chung              : ${NumberFormatUtils.formatVnd(st.fundContributed)}")
            appendLine("  • TỔNG CỘNG ĐÃ BỎ RA (A)                 : ${NumberFormatUtils.formatVnd(st.totalPaid)}")
            appendLine("  • TỔNG CHI PHÍ PHẢI CHỊU (B)             : ${NumberFormatUtils.formatVnd(st.totalOwed)}")
            appendLine("  • KẾT QUẢ SỐ DƯ (A - B)                  : ${NumberFormatUtils.formatVnd(st.balance)}")
            if (st.balance > 0) {
                appendLine("  ==> KẾT LUẬN: ĐƯỢC NHẬN LẠI ${NumberFormatUtils.formatVnd(st.balance)}")
            } else if (st.balance < 0) {
                appendLine("  ==> KẾT LUẬN: CẦN NỘP THÊM ${NumberFormatUtils.formatVnd(-st.balance)}")
            } else {
                appendLine("  ==> KẾT LUẬN: ĐÃ CÂN BẰNG TÀI CHÍNH")
            }
            appendLine("  • Tài khoản nhận tiền: ${st.member.bankAccount ?: "Chưa có"} (${st.member.bankName ?: "Chưa có"})")
        }
        appendLine("================================================================================")
    }

    /**
     * Vô hiệu hóa tấn công CSV Injection (Formula Injection) và thoát ký tự theo chuẩn RFC 4180:
     * 1. Thay thế dấu xuống dòng trong ô để bảo toàn tính toàn vẹn của từng hàng dữ liệu.
     * 2. Nếu ô bắt đầu bằng các ký tự công thức spreadsheet (=, +, -, @, tab, cr, pipe),
     *    thêm tiền tố dấu nháy đơn (') theo chuẩn bảo mật OWASP để Excel / Google Sheets xử lý như văn bản thuần.
     * 3. Thoát dấu ngoặc kép bằng cách nhân đôi (" -> "") theo chuẩn RFC 4180.
     * 4. Đóng gói toàn bộ ô trong cặp dấu ngoặc kép ("...").
     */
    fun escapeCsv(value: Any?): String {
        if (value == null) return "\"\""
        var str = value.toString()
            .replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")

        val trimmed = str.trimStart()
        val startsWithFormulaChar = trimmed.isNotEmpty() && (
            trimmed.startsWith("=") ||
            trimmed.startsWith("+") ||
            trimmed.startsWith("-") ||
            trimmed.startsWith("@") ||
            trimmed.startsWith("\t") ||
            trimmed.startsWith("|") ||
            trimmed.startsWith("%")
        )
        if (startsWithFormulaChar && !str.startsWith("'")) {
            str = "'$str"
        }

        val escaped = str.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * Generates a clean, UTF-8 CSV string with BOM (`\uFEFF`) to guarantee flawless Vietnamese
     * rendering in Excel, Sheets, and all spreadsheet applications.
     */
    fun generateCsvReport(
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList()
    ): String = buildString {
        // UTF-8 BOM prefix for Microsoft Excel Vietnamese compatibility
        append('\uFEFF')

        // Section 1: Trip Info
        appendLine("BÁO CÁO TỔNG KẾT TÀI CHÍNH VÀ QUYẾT TOÁN ĐOÀN")
        appendLine("Tên đoàn,${escapeCsv(trip?.title ?: "")}")
        appendLine("Mã đoàn,${escapeCsv(trip?.joinCode ?: "")}")
        appendLine("Ngày xuất báo cáo,${escapeCsv(dateFormat.format(Date()))}")
        appendLine("Tổng chi tiêu đoàn (VND),${summary.totalExpenses}")
        appendLine("Chi hộ cá nhân (VND),${summary.personalPaidExpenses}")
        appendLine("Chi từ quỹ chung (VND),${summary.fundPaidExpenses}")
        appendLine("Tổng quỹ đã thu (VND),${summary.totalFundCollected}")
        appendLine("Số dư quỹ còn lại (VND),${summary.remainingFund}")
        appendLine()

        // Section 2: Member Balance Table
        appendLine("BẢNG TỔNG HỢP SỐ DƯ TỪNG THÀNH VIÊN")
        appendLine("STT,Họ và tên,Vai trò,Chi hộ cá nhân (VND),Nộp quỹ (VND),Tổng đã bỏ ra (VND),Phải chịu (VND),Số dư (VND),Trạng thái,Số tài khoản,Ngân hàng,Chủ tài khoản")
        statuses.forEachIndexed { i, s ->
            val statusText = when {
                s.balance > 0 -> "Nhận lại"
                s.balance < 0 -> "Cần nộp"
                else -> "Cân bằng"
            }
            appendLine("${i + 1},${escapeCsv(s.member.name)},${escapeCsv(s.member.role)},${s.outOfPocketPaid},${s.fundContributed},${s.totalPaid},${s.totalOwed},${s.balance},${escapeCsv(statusText)},${escapeCsv(s.member.bankAccount ?: "")},${escapeCsv(s.member.bankName ?: "")},${escapeCsv(s.member.bankAccountHolder ?: "")}")
        }
        appendLine()

        // Section 3: Transfers
        appendLine("KẾ HOẠCH CHUYỂN KHOẢN QUYẾT TOÁN")
        appendLine("STT,Người chuyển,Người nhận,Số tiền (VND),Ngân hàng,Số tài khoản,Chủ tài khoản,Nội dung chuyển khoản")
        settlementTransfers.forEachIndexed { i, t ->
            appendLine("${i + 1},${escapeCsv(t.fromMember.name)},${escapeCsv(t.toMember.name)},${t.amount},${escapeCsv(t.toMember.bankName ?: "")},${escapeCsv(t.toMember.bankAccount ?: "")},${escapeCsv(t.toMember.bankAccountHolder ?: "")},${escapeCsv(t.transferNote)}")
        }
        appendLine()

        // Section 4: Expense Ledger
        appendLine("DANH SÁCH CHI TIÊU CHI TIẾT")
        appendLine("STT,Thời gian,Tên khoản chi,Danh mục,Nguồn thanh toán,Số tiền gốc,Ngoại tệ,Tỷ giá,Quy đổi VND,Kiểu phân bổ,Danh sách người cùng chịu chi,Ghi chú")
        expenses.sortedBy { it.timestamp }.forEachIndexed { i, e ->
            val payer = if (e.payerType == "FUND") "Quỹ chung" else (members.find { it.id == e.payerMemberId }?.name ?: "")
            val expSplits = splits.filter { it.expenseId == e.id }
            val participantsDetail = if (expSplits.isNotEmpty()) {
                expSplits.joinToString("; ") { sp ->
                    val memName = members.find { it.id == sp.memberId }?.name ?: "TV"
                    "$memName: ${sp.amount}"
                }
            } else {
                ""
            }
            appendLine("${i + 1},${escapeCsv(dateFormat.format(Date(e.timestamp)))},${escapeCsv(e.title)},${escapeCsv(e.category)},${escapeCsv(payer)},${e.totalAmount},${escapeCsv(e.currency)},${e.exchangeRate},${e.convertedTotalAmount},${escapeCsv(e.splitType)},${escapeCsv(participantsDetail)},${escapeCsv(e.note)}")
        }
        appendLine()

        // Section 5: Fund Ledger
        appendLine("LỊCH SỬ NỘP QUỸ ĐOÀN")
        appendLine("STT,Thời gian,Người nộp,Số tiền gốc,Ngoại tệ,Tỷ giá,Quy đổi VND,Ghi chú")
        funds.sortedBy { it.timestamp }.forEachIndexed { i, f ->
            val contributor = members.find { it.id == f.memberId }?.name ?: ""
            appendLine("${i + 1},${escapeCsv(dateFormat.format(Date(f.timestamp)))},${escapeCsv(contributor)},${f.amount},${escapeCsv(f.currency)},${f.exchangeRate},${f.convertedAmount},${escapeCsv(f.note)}")
        }
    }
}
