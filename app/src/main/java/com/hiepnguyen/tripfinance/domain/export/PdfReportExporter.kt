package com.hiepnguyen.tripfinance.domain.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hiepnguyen.tripfinance.data.entity.ExpenseEntity
import com.hiepnguyen.tripfinance.data.entity.ExpenseSplitEntity
import com.hiepnguyen.tripfinance.data.entity.FundContributionEntity
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.FinancialSummary
import com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

    /**
     * Chia một đoạn văn bản thành các dòng không vượt quá maxAvailableWidth
     */
    private fun wrapText(text: String, paint: Paint, maxAvailableWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxAvailableWidth) {
                current.append(if (current.isEmpty()) word else " $word")
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                } else {
                    lines.add(word)
                    current = StringBuilder()
                }
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    fun exportAndSharePdf(
        context: Context,
        trip: TripEntity?,
        members: List<TripMemberEntity>,
        summary: FinancialSummary,
        statuses: List<MemberFinancialStatus>,
        settlementTransfers: List<SettlementTransfer>,
        expenses: List<ExpenseEntity>,
        funds: List<FundContributionEntity>,
        splits: List<ExpenseSplitEntity> = emptyList(),
        creatorName: String
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // Standard A4 width in PostScript points (72 dpi)
            val pageHeight = 842 // Standard A4 height in PostScript points

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val paint = Paint().apply {
                isAntiAlias = true
            }

            var currentY = 40f
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)

            fun checkPageBreak(requiredHeight: Float) {
                if (currentY + requiredHeight > pageHeight - 40f) {
                    // Draw page footer
                    paint.color = Color.parseColor("#94A3B8")
                    paint.textSize = 8f
                    paint.typeface = Typeface.DEFAULT
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Trang $pageNumber", pageWidth - margin, pageHeight - 20f, paint)

                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 40f
                }
            }

            // =========================================================================
            // Header Banner (Primary Dark Indigo background)
            // =========================================================================
            val headerHeight = 70f
            paint.color = Color.parseColor("#1E1B4B")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + headerHeight, 8f, 8f, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 14.5f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("BÁO CÁO QUYẾT TOÁN TÀI CHÍNH & BẢNG KÊ CHI TIẾT", margin + (contentWidth / 2f), currentY + 28f, paint)

            paint.textSize = 10.5f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#E0E7FF")
            val tripTitle = trip?.title ?: "ĐOÀN CÔNG TÁC"
            canvas.drawText(tripTitle.uppercase(), margin + (contentWidth / 2f), currentY + 46f, paint)

            paint.textSize = 8.5f
            paint.color = Color.parseColor("#C7D2FE")
            val dateStr = dateFormat.format(Date())
            canvas.drawText("Ngày lập: $dateStr • Người lập: $creatorName • Thành viên: ${members.size} người", margin + (contentWidth / 2f), currentY + 60f, paint)

            currentY += headerHeight + 16f

            // =========================================================================
            // Section 1: Financial Overview Summary Box
            // =========================================================================
            checkPageBreak(90f)
            paint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 80f, 6f, 6f, paint)
            paint.color = Color.parseColor("#E2E8F0")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 80f, 6f, 6f, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10f
            canvas.drawText("I. TỔNG QUAN TÀI CHÍNH TOÀN ĐOÀN", margin + 12f, currentY + 18f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 9f
            paint.color = Color.parseColor("#334155")
            canvas.drawText("• Tổng chi tiêu toàn đoàn: ${NumberFormatUtils.formatVnd(summary.totalExpenses)}", margin + 12f, currentY + 34f, paint)
            canvas.drawText("• Thành viên tự chi hộ: ${NumberFormatUtils.formatVnd(summary.personalPaidExpenses)}", margin + 12f, currentY + 48f, paint)
            canvas.drawText("• Chi từ quỹ chung đoàn: ${NumberFormatUtils.formatVnd(summary.fundPaidExpenses)}", margin + 12f, currentY + 62f, paint)

            val col2X = margin + (contentWidth / 2f) + 10f
            canvas.drawText("• Tổng quỹ đoàn đã thu: ${NumberFormatUtils.formatVnd(summary.totalFundCollected)}", col2X, currentY + 34f, paint)
            canvas.drawText("• Số dư quỹ còn lại: ${NumberFormatUtils.formatVnd(summary.remainingFund)}", col2X, currentY + 48f, paint)
            val balanceText = if (summary.isBalanced) "HỢP LỆ (Cân bằng = 0 đ)" else "CẢNH BÁO: Lệch ${NumberFormatUtils.formatVnd(summary.balanceDiscrepancy)}"
            canvas.drawText("• Tình trạng đối soát: $balanceText", col2X, currentY + 62f, paint)

            currentY += 92f

            // =========================================================================
            // Section 2: Member Balance & Settlement Table
            // =========================================================================
            checkPageBreak(40f + (statuses.size * 20f))
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 10.5f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("II. BẢNG TỔNG HỢP SỐ DƯ & QUYẾT TOÁN TỪNG THÀNH VIÊN", margin, currentY, paint)
            currentY += 12f

            // Table Header
            paint.color = Color.parseColor("#047857") // Emerald Primary
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STT", margin + 4f, currentY + 13f, paint)
            canvas.drawText("Họ và tên", margin + 28f, currentY + 13f, paint)
            canvas.drawText("Chi hộ (A)", margin + 148f, currentY + 13f, paint)
            canvas.drawText("Nộp quỹ (B)", margin + 220f, currentY + 13f, paint)
            canvas.drawText("Phải chịu (C)", margin + 292f, currentY + 13f, paint)
            canvas.drawText("Số dư quyết toán (A+B-C)", margin + 372f, currentY + 13f, paint)
            currentY += 20f

            // Table Rows
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.5f
            statuses.forEachIndexed { idx, st ->
                checkPageBreak(22f)
                if (idx % 2 == 1) {
                    paint.color = Color.parseColor("#F1F5F9")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                }

                paint.color = Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                canvas.drawText("${idx + 1}", margin + 4f, currentY + 12f, paint)
                val memberNameDisplay = if (st.member.name.length > 20) st.member.name.take(19) + "…" else st.member.name
                canvas.drawText(memberNameDisplay, margin + 28f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.outOfPocketPaid), margin + 148f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.fundContributed), margin + 220f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(st.totalOwed), margin + 292f, currentY + 12f, paint)

                if (st.balance > 0) {
                    paint.color = Color.parseColor("#15803D") // Green
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("+${NumberFormatUtils.formatVnd(st.balance)} (Nhận lại)", margin + 372f, currentY + 12f, paint)
                } else if (st.balance < 0) {
                    paint.color = Color.parseColor("#B91C1C") // Red
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("${NumberFormatUtils.formatVnd(st.balance)} (Cần nộp)", margin + 372f, currentY + 12f, paint)
                } else {
                    paint.color = Color.parseColor("#64748B")
                    paint.typeface = Typeface.DEFAULT
                    canvas.drawText("0 đ (Cân bằng)", margin + 372f, currentY + 12f, paint)
                }

                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, currentY + 18f, margin + contentWidth, currentY + 18f, paint)
                currentY += 18f
            }

            currentY += 14f

            // =========================================================================
            // Section 3: KẾ HOẠCH QUYẾT TOÁN TỐI ƯU CHUYỂN KHOẢN GIỮA CÁC THÀNH VIÊN
            // =========================================================================
            val totalTransferAmount = settlementTransfers.sumOfSafe { it.amount }
            checkPageBreak(50f + if (settlementTransfers.isEmpty()) 40f else (settlementTransfers.size * 22f + 30f))
            
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.style = Paint.Style.FILL
            paint.textSize = 10.5f
            canvas.drawText("III. KẾ HOẠCH QUYẾT TOÁN TỐI ƯU CHUYỂN KHOẢN GIỮA CÁC THÀNH VIÊN", margin, currentY, paint)
            currentY += 13f

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8f
            paint.color = Color.parseColor("#475569")
            val settleDesc = if (settlementTransfers.isNotEmpty()) {
                "Thuật toán tham lam đã tối ưu hóa, rút gọn còn ${settlementTransfers.size} giao dịch chuyển khoản trực tiếp (Tổng số tiền luân chuyển: ${NumberFormatUtils.formatVnd(totalTransferAmount)}):"
            } else {
                "Tất cả các thành viên đã cân bằng tài chính hoặc số dư = 0 đ. Không phát sinh lệnh chuyển khoản bù trừ nào."
            }
            canvas.drawText(settleDesc, margin, currentY, paint)
            currentY += 10f

            if (settlementTransfers.isEmpty()) {
                // Hộp thông báo đã cân bằng
                paint.color = Color.parseColor("#F0FDF4")
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 28f, 6f, 6f, paint)
                paint.color = Color.parseColor("#BBF7D0")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(margin, currentY, margin + contentWidth, currentY + 28f, 6f, 6f, paint)

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#15803D")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                canvas.drawText("✓ Toàn bộ các thành viên trong đoàn đã cân bằng số dư (0 đ). Quyết toán hoàn tất thành công!", margin + 12f, currentY + 18f, paint)
                currentY += 36f
            } else {
                // Bảng chuyển khoản tối ưu
                paint.color = Color.parseColor("#4338CA") // Indigo 700
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Người chuyển (Nộp)", margin + 6f, currentY + 13f, paint)
                canvas.drawText("Người nhận (Hưởng)", margin + 115f, currentY + 13f, paint)
                canvas.drawText("Số tiền (VND)", margin + 225f, currentY + 13f, paint)
                canvas.drawText("Ngân hàng & STK Nhận", margin + 305f, currentY + 13f, paint)
                canvas.drawText("Nội dung CK", margin + 435f, currentY + 13f, paint)
                currentY += 20f

                paint.typeface = Typeface.DEFAULT
                settlementTransfers.forEachIndexed { i, tr ->
                    checkPageBreak(22f)
                    if (i % 2 == 1) {
                        paint.color = Color.parseColor("#EEF2FF")
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)
                    }

                    paint.color = Color.parseColor("#1E293B")
                    paint.style = Paint.Style.FILL
                    val fromName = if (tr.fromMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else tr.fromMember.name
                    val toName = if (tr.toMember.id == "FUND_ORGANIZATION") "Quỹ chung đoàn" else tr.toMember.name
                    canvas.drawText(fromName.take(20), margin + 6f, currentY + 13f, paint)
                    canvas.drawText(toName.take(20), margin + 115f, currentY + 13f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = Color.parseColor("#4338CA")
                    canvas.drawText(NumberFormatUtils.formatVnd(tr.amount), margin + 225f, currentY + 13f, paint)

                    paint.typeface = Typeface.DEFAULT
                    paint.color = Color.parseColor("#1E293B")
                    val bankInfo = "${tr.toMember.bankName ?: ""}: ${tr.toMember.bankAccount ?: "---"}"
                    canvas.drawText(bankInfo.take(24), margin + 305f, currentY + 13f, paint)
                    canvas.drawText(tr.transferNote.take(22), margin + 435f, currentY + 13f, paint)

                    paint.color = Color.parseColor("#E0E7FF")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(margin, currentY + 20f, margin + contentWidth, currentY + 20f, paint)
                    currentY += 20f
                }
                currentY += 14f
            }

            // =========================================================================
            // Section 4: BẢN KÊ CHI TIẾT CÁC KHOẢN CHI TIÊU (HIỂN THỊ ĐẦY ĐỦ TÊN MỌI THÀNH VIÊN)
            // =========================================================================
            checkPageBreak(50f)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.style = Paint.Style.FILL
            paint.textSize = 10.5f
            canvas.drawText("IV. BẢN KÊ CHI TIẾT CÁC KHOẢN CHI TIÊU (${expenses.size} khoản chi)", margin, currentY, paint)
            currentY += 12f

            // Expenses Table Header
            paint.color = Color.parseColor("#0284C7") // Sky Blue 600
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STT", margin + 4f, currentY + 13f, paint)
            canvas.drawText("Thời gian", margin + 26f, currentY + 13f, paint)
            canvas.drawText("Tên khoản chi & Ghi chú", margin + 110f, currentY + 13f, paint)
            canvas.drawText("Nguồn chi", margin + 270f, currentY + 13f, paint)
            canvas.drawText("Số tiền (VND)", margin + 360f, currentY + 13f, paint)
            canvas.drawText("Phân bổ", margin + 450f, currentY + 13f, paint)
            currentY += 20f

            val sublinePaint = Paint().apply {
                isAntiAlias = true
                textSize = 7.5f
                color = Color.parseColor("#475569")
                typeface = Typeface.DEFAULT
            }
            val maxSublineWidth = contentWidth - 116f

            expenses.sortedBy { it.timestamp }.forEachIndexed { idx, exp ->
                val expSplits = splits.filter { it.expenseId == exp.id }
                
                // Chuẩn bị toàn bộ tên thành viên tham gia khoản chi đó
                val participantsFullText: String = if (expSplits.isNotEmpty()) {
                    val formattedMembers = expSplits.map { sp ->
                        val mName = members.find { it.id == sp.memberId }?.name ?: "Thành viên"
                        "$mName (${NumberFormatUtils.formatVnd(sp.amount)})"
                    }.joinToString(", ")
                    "• Thành viên cùng chịu (${expSplits.size} người): $formattedMembers"
                } else {
                    val allNames = members.joinToString(", ") { it.name }
                    "• Chia đều tất cả ${members.size} thành viên trong đoàn: $allNames"
                }

                // Tách thành nhiều dòng vừa khít khổ trang, không bao giờ bị khuất chữ
                val participantLines = wrapText(participantsFullText, sublinePaint, maxSublineWidth)
                val noteLines = if (exp.note.isNotBlank()) {
                    wrapText("• Ghi chú: ${exp.note}", sublinePaint, maxSublineWidth)
                } else emptyList()

                val lineSpacing = 10f
                val totalSublinesHeight = (participantLines.size + noteLines.size) * lineSpacing
                val rowHeight = 18f + totalSublinesHeight + 4f

                checkPageBreak(rowHeight + 4f)
                if (idx % 2 == 1) {
                    paint.color = Color.parseColor("#F0F9FF")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + rowHeight, paint)
                }

                // Main Row info
                paint.color = Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                paint.textSize = 8f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText("${idx + 1}", margin + 4f, currentY + 11f, paint)
                canvas.drawText(dateOnlyFormat.format(Date(exp.timestamp)), margin + 26f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(exp.title.take(30), margin + 110f, currentY + 11f, paint)

                paint.typeface = Typeface.DEFAULT
                val payerName = if (exp.payerType == "FUND") "Quỹ đoàn" else (members.find { it.id == exp.payerMemberId }?.name ?: "Cá nhân")
                canvas.drawText(payerName.take(16), margin + 270f, currentY + 11f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#0369A1")
                canvas.drawText(NumberFormatUtils.formatVnd(exp.convertedTotalAmount), margin + 360f, currentY + 11f, paint)

                paint.typeface = Typeface.DEFAULT
                paint.color = Color.parseColor("#475569")
                val splitModeStr = when (exp.splitType) {
                    "EQUAL" -> "Chia đều"
                    "CUSTOM_PARTICIPANT" -> "Chọn người"
                    "RATIO" -> "Tỷ lệ %"
                    "CUSTOM_AMOUNT" -> "Tùy nhập"
                    else -> exp.splitType
                }
                canvas.drawText(splitModeStr, margin + 450f, currentY + 11f, paint)

                // Render đầy đủ các dòng danh sách thành viên tham gia (tự động xuống dòng)
                var sublineY = currentY + 22f
                sublinePaint.color = Color.parseColor("#0369A1")
                participantLines.forEach { line ->
                    canvas.drawText(line, margin + 110f, sublineY, sublinePaint)
                    sublineY += lineSpacing
                }

                // Render ghi chú nếu có
                sublinePaint.color = Color.parseColor("#64748B")
                noteLines.forEach { line ->
                    canvas.drawText(line, margin + 110f, sublineY, sublinePaint)
                    sublineY += lineSpacing
                }

                paint.color = Color.parseColor("#E0F2FE")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, currentY + rowHeight, margin + contentWidth, currentY + rowHeight, paint)
                currentY += rowHeight
            }

            // Summary row of expenses
            checkPageBreak(20f)
            paint.color = Color.parseColor("#E0F2FE")
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
            paint.color = Color.parseColor("#0369A1")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 8.5f
            canvas.drawText("TỔNG CỘNG CHI TIÊU TOÀN ĐOÀN:", margin + 110f, currentY + 12f, paint)
            canvas.drawText(NumberFormatUtils.formatVnd(summary.totalExpenses), margin + 360f, currentY + 12f, paint)
            currentY += 28f

            // =========================================================================
            // Section 5: BẢN KÊ THU QUỸ ĐOÀN (Nếu có nộp quỹ)
            // =========================================================================
            if (funds.isNotEmpty()) {
                checkPageBreak(50f + (funds.size * 20f))
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#0F172A")
                paint.style = Paint.Style.FILL
                paint.textSize = 10.5f
                canvas.drawText("V. BẢN KÊ ĐÓNG GÓP QUỸ ĐOÀN (${funds.size} đợt nộp)", margin, currentY, paint)
                currentY += 12f

                // Fund Table Header
                paint.color = Color.parseColor("#D97706") // Amber 600
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("STT", margin + 4f, currentY + 13f, paint)
                canvas.drawText("Thời gian", margin + 26f, currentY + 13f, paint)
                canvas.drawText("Thành viên nộp quỹ", margin + 110f, currentY + 13f, paint)
                canvas.drawText("Số tiền nộp (VND)", margin + 260f, currentY + 13f, paint)
                canvas.drawText("Ghi chú / Đợt nộp", margin + 380f, currentY + 13f, paint)
                currentY += 20f

                paint.typeface = Typeface.DEFAULT
                funds.sortedBy { it.timestamp }.forEachIndexed { idx, fund ->
                    checkPageBreak(18f)
                    if (idx % 2 == 1) {
                        paint.color = Color.parseColor("#FFFBEB")
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                    }

                    paint.color = Color.parseColor("#1E293B")
                    paint.style = Paint.Style.FILL
                    paint.textSize = 8.5f
                    canvas.drawText("${idx + 1}", margin + 4f, currentY + 12f, paint)
                    canvas.drawText(dateOnlyFormat.format(Date(fund.timestamp)), margin + 26f, currentY + 12f, paint)

                    val memName = members.find { it.id == fund.memberId }?.name ?: "Thành viên"
                    canvas.drawText(memName.take(22), margin + 110f, currentY + 12f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.color = Color.parseColor("#B45309")
                    canvas.drawText(NumberFormatUtils.formatVnd(fund.convertedAmount), margin + 260f, currentY + 12f, paint)

                    paint.typeface = Typeface.DEFAULT
                    paint.color = Color.parseColor("#475569")
                    val noteDisplay = if (fund.note.isNotBlank()) fund.note else "Nộp quỹ"
                    canvas.drawText(noteDisplay.take(25), margin + 380f, currentY + 12f, paint)

                    paint.color = Color.parseColor("#FEF3C7")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawLine(margin, currentY + 18f, margin + contentWidth, currentY + 18f, paint)
                    currentY += 18f
                }

                // Summary row of funds
                checkPageBreak(20f)
                paint.color = Color.parseColor("#FEF3C7")
                paint.style = Paint.Style.FILL
                canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                paint.color = Color.parseColor("#B45309")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8.5f
                canvas.drawText("TỔNG CỘNG TIỀN QUỸ ĐÃ THU:", margin + 110f, currentY + 12f, paint)
                canvas.drawText(NumberFormatUtils.formatVnd(summary.totalFundCollected), margin + 260f, currentY + 12f, paint)
                currentY += 28f
            }

            // =========================================================================
            // Section 6: Member Bank Accounts Appendix (DƯỚI CÙNG BÁO CÁO NHƯ YÊU CẦU)
            // =========================================================================
            checkPageBreak(40f + (members.size * 22f))
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F172A")
            paint.style = Paint.Style.FILL
            paint.textSize = 10.5f
            val appendixSectionIndex = if (funds.isNotEmpty()) "VI" else "V"
            canvas.drawText("$appendixSectionIndex. DANH SÁCH TÀI KHOẢN NGÂN HÀNG CÁC THÀNH VIÊN TRONG ĐOÀN", margin, currentY, paint)
            currentY += 12f

            paint.color = Color.parseColor("#334155") // Slate Dark
            paint.style = Paint.Style.FILL
            canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 20f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("STT", margin + 4f, currentY + 13f, paint)
            canvas.drawText("Họ và tên thành viên", margin + 26f, currentY + 13f, paint)
            canvas.drawText("Vai trò", margin + 148f, currentY + 13f, paint)
            canvas.drawText("Tên ngân hàng", margin + 215f, currentY + 13f, paint)
            canvas.drawText("Số tài khoản (STK)", margin + 302f, currentY + 13f, paint)
            canvas.drawText("Chủ tài khoản (Không dấu)", margin + 400f, currentY + 13f, paint)
            currentY += 20f

            paint.typeface = Typeface.DEFAULT
            members.forEachIndexed { index, mem ->
                checkPageBreak(22f)
                if (index % 2 == 1) {
                    paint.color = Color.parseColor("#F8FAFC")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(margin, currentY, margin + contentWidth, currentY + 18f, paint)
                }

                paint.color = Color.parseColor("#1E293B")
                paint.style = Paint.Style.FILL
                canvas.drawText("${index + 1}", margin + 4f, currentY + 12f, paint)
                canvas.drawText(mem.name.take(20), margin + 26f, currentY + 12f, paint)

                val roleLabel = when (mem.role) {
                    "ADMIN" -> "Trưởng đoàn"
                    "TREASURER" -> "Thủ quỹ"
                    "MEMBER" -> "Thành viên"
                    else -> "Người xem"
                }
                canvas.drawText(roleLabel, margin + 148f, currentY + 12f, paint)
                canvas.drawText(mem.bankName ?: "Chưa có", margin + 215f, currentY + 12f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.parseColor("#047857")
                canvas.drawText(mem.bankAccount ?: "Chưa có", margin + 302f, currentY + 12f, paint)

                paint.typeface = Typeface.DEFAULT
                paint.color = Color.parseColor("#334155")
                val holderName = mem.bankAccountHolder ?: mem.name.uppercase()
                canvas.drawText(holderName.take(20), margin + 400f, currentY + 12f, paint)

                paint.color = Color.parseColor("#E2E8F0")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.5f
                canvas.drawLine(margin, currentY + 18f, margin + contentWidth, currentY + 18f, paint)
                currentY += 18f
            }

            currentY += 18f

            // Signatures area
            checkPageBreak(65f)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            paint.textAlign = Paint.Align.CENTER

            val leftSignX = margin + (contentWidth * 0.25f)
            val rightSignX = margin + (contentWidth * 0.75f)

            canvas.drawText("TRƯỞNG ĐOÀN", leftSignX, currentY + 12f, paint)
            canvas.drawText("THỦ QUỸ ĐOÀN", rightSignX, currentY + 12f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8f
            paint.color = Color.parseColor("#64748B")
            canvas.drawText("(Ký và ghi rõ họ tên)", leftSignX, currentY + 24f, paint)
            canvas.drawText("(Ký và ghi rõ họ tên)", rightSignX, currentY + 24f, paint)

            // Page Number for last page
            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 8f
            paint.typeface = Typeface.DEFAULT
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Trang $pageNumber", pageWidth - margin, pageHeight - 20f, paint)

            pdfDocument.finishPage(page)

            // Write PDF to cache directory
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val sanitizeTripTitle = (trip?.title ?: "Trip").replace("[^a-zA-Z0-9]".toRegex(), "_")
            val pdfFile = File(reportsDir, "BaoCaoQuyetToan_${sanitizeTripTitle}_${System.currentTimeMillis()}.pdf")
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            fos.flush()
            fos.close()
            pdfDocument.close()

            // Open Share Intent for PDF
            sharePdfFile(context, pdfFile, tripTitle)
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi xuất file PDF: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun sharePdfFile(context: Context, file: File, tripTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Báo cáo quyết toán tài chính PDF - $tripTitle")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Chia sẻ file Báo cáo PDF qua...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
