package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONObject
import com.example.data.backup.BackupData
import com.example.data.backup.BackupMetadata
import com.example.data.backup.BackupRestoreManager
import com.example.data.backup.BackupCryptoUtils
import com.example.data.backup.RestoreResult
import com.example.data.db.AppDatabase
import com.example.data.entity.*
import com.example.domain.engine.SettlementEngine
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID

class TripFinanceRepository(private val db: AppDatabase) {

    // Trips
    val allTrips: Flow<List<TripEntity>> = db.tripDao().getAllTrips()

    fun getTrip(tripId: String): Flow<TripEntity?> = db.tripDao().getTripById(tripId)

    suspend fun createTrip(
        title: String,
        description: String,
        joinCode: String,
        startDate: Long,
        endDate: Long,
        adminName: String,
        adminBankName: String?,
        adminBankAccount: String?,
        adminBankHolder: String?
    ): String {
        val tripId = UUID.randomUUID().toString()
        val trip = TripEntity(
            id = tripId,
            title = title,
            description = description,
            joinCode = joinCode.uppercase().trim(),
            startDate = startDate,
            endDate = endDate,
            baseCurrency = "VND",
            isSettled = false,
            createdAt = System.currentTimeMillis()
        )
        db.withTransaction {
            db.tripDao().insertTrip(trip)

            // Add creator as Admin
            val adminMember = TripMemberEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                userId = "user_" + UUID.randomUUID().toString().take(6),
                name = adminName,
                role = "ADMIN",
                isActive = true,
                bankName = adminBankName,
                bankAccount = adminBankAccount,
                bankAccountHolder = adminBankHolder,
                joinedAt = System.currentTimeMillis()
            )
            db.tripMemberDao().insertMember(adminMember)

            // Seed default exchange rates from centralized DefaultExchangeRates
            val rates = listOf(
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "USD", DefaultExchangeRates.RATE_USD),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "EUR", DefaultExchangeRates.RATE_EUR),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "JPY", DefaultExchangeRates.RATE_JPY),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "KRW", DefaultExchangeRates.RATE_KRW),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "THB", DefaultExchangeRates.RATE_THB),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "SGD", DefaultExchangeRates.RATE_SGD),
                ExchangeRateEntity(UUID.randomUUID().toString(), tripId, "CNY", DefaultExchangeRates.RATE_CNY)
            )
            db.exchangeRateDao().insertExchangeRates(rates)

            logAction(
                tripId = tripId,
                actorMemberId = adminMember.id,
                actorName = adminName,
                action = "CREATE_TRIP",
                description = "Tạo mới đoàn '$title' (Mã: $joinCode)"
            )
        }

        return tripId
    }

    suspend fun joinTripByCode(code: String, userName: String): String? {
        val trip = db.tripDao().getTripByJoinCode(code.uppercase().trim()) ?: return null
        val member = TripMemberEntity(
            id = UUID.randomUUID().toString(),
            tripId = trip.id,
            userId = "user_" + UUID.randomUUID().toString().take(6),
            name = userName,
            role = "MEMBER",
            isActive = true,
            joinedAt = System.currentTimeMillis()
        )
        db.tripMemberDao().insertMember(member)
        logAction(
            tripId = trip.id,
            actorMemberId = member.id,
            actorName = userName,
            action = "JOIN_TRIP",
            description = "$userName đã tham gia đoàn qua mã $code"
        )
        return trip.id
    }

    suspend fun updateTrip(trip: TripEntity) = db.tripDao().updateTrip(trip)

    suspend fun updateTripDetails(
        tripId: String,
        title: String,
        description: String,
        startDate: Long,
        endDate: Long,
        actor: TripMemberEntity
    ) {
        val existing = db.tripDao().getTripByIdOnce(tripId) ?: return
        val updated = existing.copy(
            title = title,
            description = description,
            startDate = startDate,
            endDate = endDate
        )
        db.tripDao().updateTrip(updated)
        logAction(
            tripId = tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "UPDATE_TRIP",
            description = "Cập nhật thông tin đoàn '$title'"
        )
    }

    suspend fun deleteTripCascade(tripId: String) {
        db.withTransaction {
            // Cascade delete related records atomically
            db.expenseDao().deleteSplitsByTrip(tripId)
            db.expenseDao().deleteExpensesByTrip(tripId)
            db.fundDao().deleteFundsByTrip(tripId)
            db.exchangeRateDao().deleteExchangeRatesByTrip(tripId)
            db.settlementDao().deleteSnapshotsByTrip(tripId)
            db.auditLogDao().deleteAuditLogsByTrip(tripId)
            db.tripMemberDao().deleteMembersByTrip(tripId)
            db.tripDao().deleteTripById(tripId)
        }
    }

    // Members
    fun getMembers(tripId: String): Flow<List<TripMemberEntity>> =
        db.tripMemberDao().getMembersByTrip(tripId)

    suspend fun addMember(
        tripId: String,
        name: String,
        role: String,
        bankName: String?,
        bankAccount: String?,
        bankAccountHolder: String?,
        actor: TripMemberEntity
    ): String {
        val trip = db.tripDao().getTripByIdOnce(tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể thêm thành viên mới!")
        }
        val memberId = UUID.randomUUID().toString()
        val member = TripMemberEntity(
            id = memberId,
            tripId = tripId,
            userId = "user_" + UUID.randomUUID().toString().take(6),
            name = name,
            role = role,
            isActive = true,
            bankName = bankName,
            bankAccount = bankAccount,
            bankAccountHolder = bankAccountHolder,
            joinedAt = System.currentTimeMillis()
        )
        db.tripMemberDao().insertMember(member)
        logAction(
            tripId = tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "ADD_MEMBER",
            description = "Thêm thành viên $name với vai trò $role"
        )
        return memberId
    }

    suspend fun updateMember(member: TripMemberEntity, actor: TripMemberEntity) {
        val trip = db.tripDao().getTripByIdOnce(member.tripId)
        if (trip?.isSettled == true && actor.role != "ADMIN") {
            throw IllegalStateException("Chuyến đi đã khóa sổ. Không thể sửa thông tin thành viên!")
        }
        db.tripMemberDao().updateMember(member)
        logAction(
            tripId = member.tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "UPDATE_MEMBER",
            description = "Cập nhật thông tin thành viên ${member.name} (${member.role})"
        )
    }

    suspend fun removeOrDeactivateMember(member: TripMemberEntity, actor: TripMemberEntity) {
        val trip = db.tripDao().getTripByIdOnce(member.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể xóa hoặc ngừng hoạt động thành viên!")
        }
        val paidExpenseCount = db.expenseDao().countExpensesByPayer(member.id)
        val createdExpenseCount = db.expenseDao().countExpensesCreatedByMember(member.id)
        val splitCount = db.expenseDao().countSplitsByMember(member.id)
        val fundCount = db.fundDao().countFundContributionsByMember(member.id)
        val hasFinancialRecords = (paidExpenseCount + createdExpenseCount + splitCount + fundCount) > 0

        if (hasFinancialRecords) {
            // SRS 3: Thành viên có giao dịch tài chính không được phép "Xóa", chỉ được "Ngừng hoạt động (Deactivate)"
            db.tripMemberDao().setMemberActiveStatus(member.id, false)
            logAction(
                tripId = member.tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "DEACTIVATE_MEMBER",
                description = "Ngừng hoạt động thành viên ${member.name} (đã phát sinh: $paidExpenseCount chi trả, $splitCount phân bổ, $fundCount đóng quỹ)"
            )
        } else {
            // CSDL sẽ kiểm tra thêm qua trigger trg_prevent_delete_member_with_financials và ForeignKey RESTRICT
            db.tripMemberDao().deleteMember(member.id)
            logAction(
                tripId = member.tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "DELETE_MEMBER",
                description = "Xóa hoàn toàn thành viên ${member.name} khỏi đoàn (chưa có phát sinh chi tiêu)"
            )
        }
    }

    suspend fun deleteMemberDirect(memberId: String) {
        // Gọi xóa trực tiếp - SQLite Trigger & Foreign Key RESTRICT sẽ chặn đứng nếu có phát sinh tài chính
        db.tripMemberDao().deleteMember(memberId)
    }

    // Expenses
    fun getExpenses(tripId: String): Flow<List<ExpenseEntity>> =
        db.expenseDao().getExpensesByTrip(tripId)

    fun getSplitsForTrip(tripId: String): Flow<List<ExpenseSplitEntity>> =
        db.expenseDao().getAllSplitsByTrip(tripId)

    fun getSplitsByExpense(expenseId: String): Flow<List<ExpenseSplitEntity>> =
        db.expenseDao().getSplitsByExpense(expenseId)

    suspend fun addExpenseWithSplits(
        expense: ExpenseEntity,
        splits: List<ExpenseSplitEntity>,
        actor: TripMemberEntity
    ) {
        val trip = db.tripDao().getTripByIdOnce(expense.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể thêm khoản chi mới!")
        }
        db.withTransaction {
            db.expenseDao().insertExpenseWithSplits(expense, splits)
            val payerDesc = if (expense.payerType == "FUND") "Quỹ đoàn" else (actor.name)
            val afterJson = JSONObject().apply {
                put("title", expense.title)
                put("category", expense.category)
                put("payerType", expense.payerType)
                put("payerMemberId", expense.payerMemberId ?: "")
                put("totalAmount", expense.totalAmount)
                put("currency", expense.currency)
                put("exchangeRate", expense.exchangeRate)
                put("convertedTotalAmount", expense.convertedTotalAmount)
                put("splitType", expense.splitType)
                put("note", expense.note)
                put("createdMemberId", expense.createdMemberId)
                put("splitCount", splits.size)
            }.toString()

            logAction(
                tripId = expense.tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "CREATE_EXPENSE",
                description = "Tạo chi tiêu '${expense.title}': ${expense.convertedTotalAmount} VND (Người trả: $payerDesc)",
                before = null,
                after = afterJson
            )
        }
    }

    suspend fun updateExpenseWithSplits(
        expense: ExpenseEntity,
        splits: List<ExpenseSplitEntity>,
        actor: TripMemberEntity
    ) {
        val trip = db.tripDao().getTripByIdOnce(expense.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể chỉnh sửa khoản chi!")
        }
        db.withTransaction {
            val existingExpense = db.expenseDao().getExpenseById(expense.id)
            val existingSplits = db.expenseDao().getSplitsByExpenseOnce(expense.id)

            // CRITICAL FIX: Luôn bảo lưu người tạo ban đầu (createdMemberId), không để người sửa ghi đè
            val preservedCreatedMemberId = existingExpense?.createdMemberId ?: expense.createdMemberId
            val finalExpense = expense.copy(createdMemberId = preservedCreatedMemberId)

            val beforeJson = if (existingExpense != null) {
                JSONObject().apply {
                    put("title", existingExpense.title)
                    put("category", existingExpense.category)
                    put("payerType", existingExpense.payerType)
                    put("payerMemberId", existingExpense.payerMemberId ?: "")
                    put("totalAmount", existingExpense.totalAmount)
                    put("currency", existingExpense.currency)
                    put("exchangeRate", existingExpense.exchangeRate)
                    put("convertedTotalAmount", existingExpense.convertedTotalAmount)
                    put("splitType", existingExpense.splitType)
                    put("note", existingExpense.note)
                    put("createdMemberId", existingExpense.createdMemberId)
                    put("splitCount", existingSplits.size)
                }.toString()
            } else null

            val afterJson = JSONObject().apply {
                put("title", finalExpense.title)
                put("category", finalExpense.category)
                put("payerType", finalExpense.payerType)
                put("payerMemberId", finalExpense.payerMemberId ?: "")
                put("totalAmount", finalExpense.totalAmount)
                put("currency", finalExpense.currency)
                put("exchangeRate", finalExpense.exchangeRate)
                put("convertedTotalAmount", finalExpense.convertedTotalAmount)
                put("splitType", finalExpense.splitType)
                put("note", finalExpense.note)
                put("createdMemberId", finalExpense.createdMemberId)
                put("splitCount", splits.size)
            }.toString()

            db.expenseDao().updateExpenseWithSplits(finalExpense, splits)

            logAction(
                tripId = finalExpense.tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "UPDATE_EXPENSE",
                description = "Cập nhật chi tiêu '${finalExpense.title}': ${finalExpense.convertedTotalAmount} VND",
                before = beforeJson,
                after = afterJson
            )
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity, actor: TripMemberEntity) {
        val trip = db.tripDao().getTripByIdOnce(expense.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể xóa khoản chi!")
        }
        db.withTransaction {
            val beforeJson = JSONObject().apply {
                put("title", expense.title)
                put("category", expense.category)
                put("payerType", expense.payerType)
                put("payerMemberId", expense.payerMemberId ?: "")
                put("totalAmount", expense.totalAmount)
                put("currency", expense.currency)
                put("exchangeRate", expense.exchangeRate)
                put("convertedTotalAmount", expense.convertedTotalAmount)
                put("splitType", expense.splitType)
                put("createdMemberId", expense.createdMemberId)
            }.toString()

            db.expenseDao().deleteExpenseWithSplits(expense.id)

            logAction(
                tripId = expense.tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "DELETE_EXPENSE",
                description = "Xóa chi tiêu '${expense.title}' (${expense.convertedTotalAmount} VND)",
                before = beforeJson,
                after = null
            )
        }
    }

    // Funds
    fun getFundContributions(tripId: String): Flow<List<FundContributionEntity>> =
        db.fundDao().getFundContributions(tripId)

    suspend fun addFundContribution(
        contribution: FundContributionEntity,
        contributorName: String,
        actor: TripMemberEntity
    ) {
        val trip = db.tripDao().getTripByIdOnce(contribution.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể nộp thêm quỹ!")
        }
        db.fundDao().insertFundContribution(contribution)
        logAction(
            tripId = contribution.tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "CONTRIBUTE_FUND",
            description = "$contributorName nộp quỹ ${contribution.convertedAmount} VND (Ghi nhận bởi ${actor.name})"
        )
    }

    suspend fun deleteFundContribution(
        contribution: FundContributionEntity,
        contributorName: String,
        actor: TripMemberEntity
    ) {
        val trip = db.tripDao().getTripByIdOnce(contribution.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể xóa khoản nộp quỹ!")
        }
        db.fundDao().deleteFundContribution(contribution.id)
        logAction(
            tripId = contribution.tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "DELETE_FUND",
            description = "Xóa khoản nộp quỹ của $contributorName (${contribution.convertedAmount} VND)"
        )
    }

    // Exchange Rates
    fun getExchangeRates(tripId: String): Flow<List<ExchangeRateEntity>> =
        db.exchangeRateDao().getExchangeRates(tripId)

    suspend fun updateExchangeRate(rate: ExchangeRateEntity, actor: TripMemberEntity) {
        val trip = db.tripDao().getTripByIdOnce(rate.tripId)
        if (trip?.isSettled == true) {
            throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể thay đổi tỷ giá!")
        }
        db.exchangeRateDao().insertExchangeRate(rate)
        logAction(
            tripId = rate.tripId,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "UPDATE_RATE",
            description = "Cập nhật tỷ giá 1 ${rate.currencyCode} = ${rate.rateToBase} VND"
        )
    }

    // Settlement & Snapshots
    fun getSnapshots(tripId: String): Flow<List<SettlementSnapshotEntity>> =
        db.settlementDao().getSnapshotsByTrip(tripId)

    suspend fun finalizeSettlement(
        trip: TripEntity,
        snapshotTitle: String,
        summary: FinancialSummary,
        settlementJson: String,
        actor: TripMemberEntity
    ) {
        val snapshot = SettlementSnapshotEntity(
            id = UUID.randomUUID().toString(),
            tripId = trip.id,
            snapshotTitle = snapshotTitle,
            createdAt = System.currentTimeMillis(),
            totalExpenses = summary.totalExpenses,
            totalFundCollected = summary.totalFundCollected,
            totalFundSpent = summary.fundPaidExpenses,
            remainingFund = summary.remainingFund,
            settlementJson = settlementJson
        )
        db.settlementDao().insertSnapshot(snapshot)
        db.tripDao().updateTrip(trip.copy(isSettled = true, settledAt = System.currentTimeMillis()))

        logAction(
            tripId = trip.id,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "SETTLE_TRIP",
            description = "Khóa sổ và quyết toán chuyến đi. Snapshot: $snapshotTitle"
        )
    }

    suspend fun reopenSettlement(trip: TripEntity, actor: TripMemberEntity) {
        if (actor.role != "ADMIN") {
            throw IllegalStateException("Chỉ Trưởng đoàn (Admin) mới có quyền mở khóa sổ chuyến đi!")
        }
        db.tripDao().updateTrip(trip.copy(isSettled = false, settledAt = null))
        logAction(
            tripId = trip.id,
            actorMemberId = actor.id,
            actorName = actor.name,
            action = "REOPEN_SETTLEMENT",
            description = "Trưởng đoàn ${actor.name} đã mở khóa sổ chuyến đi '${trip.title}' để tiếp tục cập nhật dữ liệu"
        )
    }

    // Audit logs
    fun getAuditLogs(tripId: String): Flow<List<AuditLogEntity>> =
        db.auditLogDao().getAuditLogsByTrip(tripId)

    suspend fun logAction(
        tripId: String,
        actorMemberId: String,
        actorName: String,
        action: String,
        description: String,
        before: String? = null,
        after: String? = null
    ) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                actorMemberId = actorMemberId,
                actorName = actorName,
                action = action,
                description = description,
                detailBefore = before,
                detailAfter = after,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Nguồn chân lý tính toán đối soát (SRS Section 2)
     * Paid(A): Chi hộ của cá nhân + Tiền nộp quỹ
     * Owed(A): Tổng tiền chịu chi trong các expenseSplits
     * Balance(A) = Paid(A) - Owed(A)
     */
    fun observeFinancialStatus(tripId: String): Flow<Pair<FinancialSummary, List<MemberFinancialStatus>>> {
        return combine(
            getMembers(tripId),
            getExpenses(tripId),
            getSplitsForTrip(tripId),
            getFundContributions(tripId)
        ) { members, expenses, splits, fundContributions ->
            val totalExpenseSum = expenses.sumOf { it.convertedTotalAmount }
            val fundPaidExpensesSum = expenses
                .filter { it.payerType == "FUND" }
                .sumOf { it.convertedTotalAmount }
            val personalPaidExpensesSum = expenses
                .filter { it.payerType == "MEMBER" }
                .sumOf { it.convertedTotalAmount }
            val totalFundCollected = fundContributions.sumOf { it.convertedAmount }
            val remainingFund = totalFundCollected - fundPaidExpensesSum

            // Tính toán tài chính cho từng thành viên
            val memberStatuses = members.map { member ->
                // 1. Chi hộ thực tế từ túi thành viên (expenses where payerMemberId == member.id and payerType == MEMBER)
                val outOfPocket = expenses
                    .filter { it.payerType == "MEMBER" && it.payerMemberId == member.id }
                    .sumOf { it.convertedTotalAmount }

                // 2. Tiền đã đóng góp vào Quỹ chung
                val fundContributed = fundContributions
                    .filter { it.memberId == member.id }
                    .sumOf { it.convertedAmount }

                // Tổng tiền thành viên đã thực tế chi/nộp cho đoàn
                val totalPaid = outOfPocket + fundContributed

                // 3. Tiền phải chịu chi (Owed) từ các bảng phân bổ
                val totalOwed = splits
                    .filter { it.memberId == member.id }
                    .sumOf { it.amount }

                // 4. Số dư ròng (Balance) = Paid - Owed
                val balance = totalPaid - totalOwed

                val status = when {
                    balance > 0 -> BalanceStatus.RECEIVE
                    balance < 0 -> BalanceStatus.PAY
                    else -> BalanceStatus.BALANCED
                }

                MemberFinancialStatus(
                    member = member,
                    totalPaid = totalPaid,
                    outOfPocketPaid = outOfPocket,
                    fundContributed = fundContributed,
                    totalOwed = totalOwed,
                    balance = balance,
                    status = status
                )
            }

            val memberIds = members.map { it.id }.toSet()

            // ĐỐI SOÁT TÀI CHÍNH KẾ TOÁN (Reconciliation):
            // 1. Phân bổ chi phí: Mỗi khoản chi phải có tổng số tiền phân bổ trong splits đúng bằng số tiền của khoản chi
            val splitsByExpense = splits.groupBy { it.expenseId }
            val splitDiscrepancy = expenses.sumOf { exp ->
                val expSplits = splitsByExpense[exp.id] ?: emptyList()
                kotlin.math.abs(exp.convertedTotalAmount - expSplits.sumOf { it.amount })
            }

            // 2. Kiểm tra người chi cá nhân: Khoản chi cá nhân phải có người chi thuộc danh sách thành viên đoàn
            val unassignedPayerAmount = expenses
                .filter { it.payerType == "MEMBER" && (it.payerMemberId == null || !memberIds.contains(it.payerMemberId)) }
                .sumOf { it.convertedTotalAmount }

            // 3. Kiểm tra người nộp quỹ: Khoản nộp quỹ phải có người nộp thuộc danh sách thành viên đoàn
            val unassignedFundAmount = fundContributions
                .filter { !memberIds.contains(it.memberId) }
                .sumOf { it.convertedAmount }

            // 4. Kiểm tra phân bổ mồ côi: Split không được gán cho người không thuộc đoàn
            val orphanSplitAmount = splits
                .filter { !memberIds.contains(it.memberId) }
                .sumOf { it.amount }

            // Tổng chênh lệch đối soát thực tế:
            // Sổ sách chỉ được coi là cân bằng khi mọi khoản chi được phân bổ chính xác 100%
            // và mọi dòng tiền thu/chi đều có thành viên hợp lệ chịu trách nhiệm
            val discrepancy = splitDiscrepancy + unassignedPayerAmount + unassignedFundAmount + orphanSplitAmount
            val isBalanced = members.isNotEmpty() && discrepancy <= 5L

            val summary = FinancialSummary(
                totalExpenses = totalExpenseSum,
                personalPaidExpenses = personalPaidExpensesSum,
                fundPaidExpenses = fundPaidExpensesSum,
                totalFundCollected = totalFundCollected,
                remainingFund = remainingFund,
                isBalanced = isBalanced,
                balanceDiscrepancy = discrepancy,
                memberCount = members.size,
                expenseCount = expenses.size
            )

            summary to memberStatuses
        }
    }

    // ==========================================
    // BACKUP & RESTORE REPOSITORY OPERATIONS
    // ==========================================

    suspend fun createLocalBackup(context: Context, password: String? = null): File {
        return BackupRestoreManager.createLocalBackupFile(context, db, password)
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri, password: String? = null): Result<Unit> {
        return BackupRestoreManager.writeBackupToUri(context, uri, db, password)
    }

    fun isEncryptedBackupUri(context: Context, uri: Uri): Boolean {
        val readResult = BackupRestoreManager.readBackupFromUri(context, uri)
        return if (readResult.isSuccess) {
            BackupCryptoUtils.isEncryptedBackup(readResult.getOrThrow())
        } else false
    }

    fun isEncryptedBackupFile(file: File): Boolean {
        return try {
            val content = file.readText(Charsets.UTF_8)
            BackupCryptoUtils.isEncryptedBackup(content)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreBackupFromUri(context: Context, uri: Uri, clearExisting: Boolean = false, password: String? = null): Result<RestoreResult> {
        val readResult = BackupRestoreManager.readBackupFromUri(context, uri)
        if (readResult.isFailure) {
            return Result.failure(readResult.exceptionOrNull() ?: Exception("Không thể đọc tệp sao lưu"))
        }
        val parseResult = BackupRestoreManager.parseAndValidateBackup(readResult.getOrThrow(), password)
        if (parseResult.isFailure) {
            return Result.failure(parseResult.exceptionOrNull() ?: Exception("Dữ liệu sao lưu không hợp lệ"))
        }
        return BackupRestoreManager.restoreFromBackupData(db, parseResult.getOrThrow(), clearExisting)
    }

    suspend fun restoreBackupFromFile(file: File, clearExisting: Boolean = false, password: String? = null): Result<RestoreResult> {
        return try {
            val json = file.readText(Charsets.UTF_8)
            val parseResult = BackupRestoreManager.parseAndValidateBackup(json, password)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("Dữ liệu sao lưu không hợp lệ"))
            }
            BackupRestoreManager.restoreFromBackupData(db, parseResult.getOrThrow(), clearExisting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listLocalBackups(context: Context): List<File> {
        return BackupRestoreManager.listLocalBackups(context)
    }

    fun shareBackupFile(context: Context, file: File) {
        BackupRestoreManager.shareBackupFile(context, file)
    }
}
