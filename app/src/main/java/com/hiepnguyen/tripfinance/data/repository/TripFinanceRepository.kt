package com.hiepnguyen.tripfinance.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONArray
import com.hiepnguyen.tripfinance.domain.engine.CashFlowMinimizer
import com.hiepnguyen.tripfinance.data.backup.BackupData
import com.hiepnguyen.tripfinance.data.backup.BackupMetadata
import com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager
import com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils
import com.hiepnguyen.tripfinance.data.backup.RestoreResult
import com.hiepnguyen.tripfinance.data.db.AppDatabase
import com.hiepnguyen.tripfinance.data.entity.*
import com.hiepnguyen.tripfinance.domain.engine.SettlementEngine
import com.hiepnguyen.tripfinance.domain.model.*
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
        var finalCode = joinCode.uppercase().trim()
        if (finalCode.isBlank()) {
            do {
                finalCode = com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("TRIP-")
            } while (db.tripDao().getTripByJoinCode(finalCode) != null)
        } else {
            if (db.tripDao().getTripByJoinCode(finalCode) != null) {
                throw IllegalArgumentException("Mã đoàn '$finalCode' đã được sử dụng. Vui lòng chọn một mã khác.")
            }
        }

        val trip = TripEntity(
            id = tripId,
            title = title,
            description = description,
            joinCode = finalCode,
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

    suspend fun updateTrip(trip: TripEntity) {
        db.withTransaction {
            val existing = db.tripDao().getTripByIdOnce(trip.id)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (existing.isSettled) {
                throw IllegalStateException("Chuyến đi '${existing.title}' đã được khóa sổ quyết toán. Không thể sửa thông tin!")
            }
            db.tripDao().updateTrip(trip.copy(version = existing.version + 1))
        }
    }

    suspend fun updateTripDetails(
        tripId: String,
        title: String,
        description: String,
        startDate: Long,
        endDate: Long,
        actor: TripMemberEntity
    ) {
        db.withTransaction {
            val existing = db.tripDao().getTripByIdOnce(tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (existing.isSettled) {
                throw IllegalStateException("Chuyến đi '${existing.title}' đã được khóa sổ quyết toán. Không thể sửa đổi thông tin!")
            }
            val updated = existing.copy(
                title = title,
                description = description,
                startDate = startDate,
                endDate = endDate,
                version = existing.version + 1
            )
            db.tripDao().updateTrip(updated)
            logAction(
                tripId = tripId,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "UPDATE_TRIP",
                description = "Cập nhật thông tin đoàn '$title' (Phiên bản v${existing.version} -> v${updated.version})"
            )
        }
    }

    suspend fun deleteTripCascade(tripId: String) {
        db.withTransaction {
            val existing = db.tripDao().getTripByIdOnce(tripId)
            if (existing != null && existing.isSettled) {
                throw IllegalStateException("Chuyến đi '${existing.title}' đã được khóa sổ quyết toán niêm phong. Không thể xóa!")
            }
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
        return db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
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
            memberId
        }
    }

    suspend fun updateMember(member: TripMemberEntity, actor: TripMemberEntity) {
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(member.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled && actor.role != "ADMIN") {
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
    }

    suspend fun removeOrDeactivateMember(member: TripMemberEntity, actor: TripMemberEntity) {
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(member.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
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
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(expense.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
                throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể thêm khoản chi mới!")
            }

            // KIỂM TRA TOÀN VẸN THÀNH VIÊN THEO ĐOÀN (CROSS-TRIP MEMBER INTEGRITY)
            val tripMembers = db.tripMemberDao().getMembersByTripOnce(trip.id).associateBy { it.id }

            // 1. Kiểm tra người chi trả (payerMemberId)
            if (expense.payerType == "MEMBER") {
                val payerId = expense.payerMemberId
                    ?: throw IllegalArgumentException("Khoản chi cá nhân bắt buộc phải có người chi trả (payerMemberId)!")
                if (!tripMembers.containsKey(payerId)) {
                    throw IllegalArgumentException("Người chi trả ($payerId) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
                }
            }

            // 2. Kiểm tra người tạo khoản chi (createdMemberId)
            if (!tripMembers.containsKey(expense.createdMemberId)) {
                throw IllegalArgumentException("Người tạo khoản chi (${expense.createdMemberId}) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
            }

            // 3. Kiểm tra tất cả người chịu chi trong splits
            if (splits.isEmpty()) {
                throw IllegalArgumentException("Khoản chi phải có ít nhất một thành viên chịu chi!")
            }
            for (split in splits) {
                if (split.tripId != trip.id) {
                    throw IllegalArgumentException("Bản ghi phân bổ (split) có tripId '${split.tripId}' không khớp với chuyến đi '${trip.id}'!")
                }
                if (!tripMembers.containsKey(split.memberId)) {
                    throw IllegalArgumentException("Thành viên chịu chi (${split.memberId}) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
                }
            }

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
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(expense.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
                throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể chỉnh sửa khoản chi!")
            }

            // KIỂM TRA TOÀN VẸN THÀNH VIÊN THEO ĐOÀN (CROSS-TRIP MEMBER INTEGRITY)
            val tripMembers = db.tripMemberDao().getMembersByTripOnce(trip.id).associateBy { it.id }

            if (expense.payerType == "MEMBER") {
                val payerId = expense.payerMemberId
                    ?: throw IllegalArgumentException("Khoản chi cá nhân bắt buộc phải có người chi trả (payerMemberId)!")
                if (!tripMembers.containsKey(payerId)) {
                    throw IllegalArgumentException("Người chi trả ($payerId) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
                }
            }

            // CRITICAL FIX: Luôn bảo lưu người tạo ban đầu (createdMemberId), không để người sửa ghi đè
            val existingExpense = db.expenseDao().getExpenseById(expense.id)
            val existingSplits = db.expenseDao().getSplitsByExpenseOnce(expense.id)
            val preservedCreatedMemberId = existingExpense?.createdMemberId ?: expense.createdMemberId

            if (!tripMembers.containsKey(preservedCreatedMemberId)) {
                throw IllegalArgumentException("Người tạo khoản chi ($preservedCreatedMemberId) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
            }

            if (splits.isEmpty()) {
                throw IllegalArgumentException("Khoản chi phải có ít nhất một thành viên chịu chi!")
            }
            for (split in splits) {
                if (split.tripId != trip.id) {
                    throw IllegalArgumentException("Bản ghi phân bổ (split) có tripId '${split.tripId}' không khớp với chuyến đi '${trip.id}'!")
                }
                if (!tripMembers.containsKey(split.memberId)) {
                    throw IllegalArgumentException("Thành viên chịu chi (${split.memberId}) không thuộc danh sách thành viên của đoàn '${trip.title}'!")
                }
            }

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
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(expense.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
                throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể xóa khoản chi!")
            }
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
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(contribution.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
                throw IllegalStateException("Chuyến đi '${trip.title}' đã được khóa sổ quyết toán. Không thể nộp thêm quỹ!")
            }

            // KIỂM TRA TOÀN VẸN THÀNH VIÊN THEO ĐOÀN (CROSS-TRIP MEMBER INTEGRITY)
            val member = db.tripMemberDao().getMemberById(contribution.memberId)
            if (member == null || member.tripId != trip.id) {
                throw IllegalArgumentException("Thành viên nộp quỹ (${contribution.memberId}) không thuộc đoàn '${trip.title}'!")
            }
            val recorder = db.tripMemberDao().getMemberById(contribution.recordedByMemberId)
            if (recorder == null || recorder.tripId != trip.id) {
                throw IllegalArgumentException("Thành viên ghi nhận nộp quỹ (${contribution.recordedByMemberId}) không thuộc đoàn '${trip.title}'!")
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
    }

    suspend fun deleteFundContribution(
        contribution: FundContributionEntity,
        contributorName: String,
        actor: TripMemberEntity
    ) {
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(contribution.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
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
    }

    // Exchange Rates
    fun getExchangeRates(tripId: String): Flow<List<ExchangeRateEntity>> =
        db.exchangeRateDao().getExchangeRates(tripId)

    suspend fun updateExchangeRate(rate: ExchangeRateEntity, actor: TripMemberEntity) {
        db.withTransaction {
            val trip = db.tripDao().getTripByIdOnce(rate.tripId)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại")
            if (trip.isSettled) {
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
    ): SettlementSnapshotEntity {
        return db.withTransaction {
            // 1. Kiểm tra quyền Admin
            if (actor.role != "ADMIN") {
                throw IllegalStateException("Chỉ Trưởng đoàn (Admin) mới có quyền khóa sổ và quyết toán chuyến đi!")
            }

            // 2. Lấy dữ liệu mới nhất từ CSDL bên trong transaction để chống triệt để TOCTOU (Time-of-Check to Time-of-Use)
            val freshTrip = db.tripDao().getTripByIdOnce(trip.id)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại trên hệ thống!")

            if (freshTrip.isSettled) {
                throw IllegalStateException("Chuyến đi '${freshTrip.title}' đã được khóa sổ quyết toán từ trước!")
            }

            // 3. Khóa lạc quan (Optimistic Locking): Ngăn chặn ghi đè thay đổi đồng thời nếu phiên bản bị lệch
            if (freshTrip.version != trip.version) {
                throw java.util.ConcurrentModificationException(
                    "Dữ liệu chuyến đi '${trip.title}' đã bị thay đổi đồng thời bởi thao tác khác (Phiên bản trên máy: ${trip.version}, phiên bản hệ thống: ${freshTrip.version}). Vui lòng tải lại dữ liệu trước khi khóa sổ!"
                )
            }

            // 4. Kiểm tra danh sách thành viên: Đoàn không có thành viên nào TUYỆT ĐỐI không được khóa sổ
            val members = db.tripMemberDao().getMembersByTripOnce(trip.id)
            if (members.isEmpty()) {
                throw IllegalStateException("Đoàn không có thành viên nào! Không thể thực hiện quyết toán và khóa sổ.")
            }

            // 5. Tính toán đối soát từ CSDL tươi để thẩm định tính toàn vẹn (Zero discrepancy tolerance)
            val expenses = db.expenseDao().getExpensesByTripOnce(trip.id)
            val splits = db.expenseDao().getAllSplitsByTripOnce(trip.id)
            val funds = db.fundDao().getFundContributionsOnce(trip.id)

            val (freshSummary, memberStatuses) = calculateFinancialSummaryAndStatuses(
                members = members,
                expenses = expenses,
                splits = splits,
                fundContributions = funds
            )

            // Dung sai chênh lệch đối soát phải bằng 0 tuyệt đối (không cho phép lệch nhỏ tích lũy)
            if (!freshSummary.isBalanced || freshSummary.balanceDiscrepancy != 0L) {
                throw IllegalStateException(
                    "Không thể khóa sổ: Phát hiện chênh lệch đối soát (${freshSummary.balanceDiscrepancy} VND). Toàn bộ chi tiêu phải được phân bổ cân bằng tuyệt đối (chênh lệch = 0 đ)!"
                )
            }

            val settledAt = System.currentTimeMillis()

            // 6. Thực thi khóa sổ và tăng version nguyên tử qua SQLite Query
            val rowsUpdated = db.tripDao().lockSettlementWithOptimisticLock(
                tripId = trip.id,
                expectedVersion = trip.version,
                settledAt = settledAt
            )
            if (rowsUpdated == 0) {
                throw java.util.ConcurrentModificationException(
                    "Không thể khóa sổ do xung đột đồng thời (Optimistic Lock Failure). Vui lòng thử lại sau khi làm mới trang."
                )
            }

            // 7. Tạo chuỗi JSON cấu trúc chuẩn RFC 8259 (JSON thực thụ, không phải văn bản thô)
            val transfers = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSimplifiedTransfers(
                memberStatuses = memberStatuses,
                tripJoinCode = freshTrip.joinCode,
                remainingFund = freshSummary.remainingFund
            )
            val structuredSettlementJson = JSONObject().apply {
                put("version", 1)
                put("tripId", freshTrip.id)
                put("tripTitle", freshTrip.title)
                put("joinCode", freshTrip.joinCode)
                put("settledAt", settledAt)
                put("settledByMemberId", actor.id)
                put("settledByMemberName", actor.name)
                put("summary", JSONObject().apply {
                    put("totalExpenses", freshSummary.totalExpenses)
                    put("personalPaidExpenses", freshSummary.personalPaidExpenses)
                    put("fundPaidExpenses", freshSummary.fundPaidExpenses)
                    put("totalFundCollected", freshSummary.totalFundCollected)
                    put("remainingFund", freshSummary.remainingFund)
                    put("balanceDiscrepancy", freshSummary.balanceDiscrepancy)
                    put("isBalanced", freshSummary.isBalanced)
                    put("memberCount", freshSummary.memberCount)
                    put("expenseCount", freshSummary.expenseCount)
                })
                put("members", JSONArray().apply {
                    memberStatuses.forEach { st ->
                        put(JSONObject().apply {
                            put("memberId", st.member.id)
                            put("name", st.member.name)
                            put("role", st.member.role)
                            put("totalPaid", st.totalPaid)
                            put("outOfPocketPaid", st.outOfPocketPaid)
                            put("fundContributed", st.fundContributed)
                            put("totalOwed", st.totalOwed)
                            put("balance", st.balance)
                            put("status", st.status.name)
                            put("bankAccount", st.member.bankAccount ?: JSONObject.NULL)
                            put("bankName", st.member.bankName ?: JSONObject.NULL)
                            put("bankAccountHolder", st.member.bankAccountHolder ?: JSONObject.NULL)
                        })
                    }
                })
                put("transfers", JSONArray().apply {
                    transfers.forEach { tr ->
                        put(JSONObject().apply {
                            put("fromMemberId", tr.fromMember.id)
                            put("fromMemberName", tr.fromMember.name)
                            put("toMemberId", tr.toMember.id)
                            put("toMemberName", tr.toMember.name)
                            put("amount", tr.amount)
                            put("bankName", tr.toMember.bankName ?: JSONObject.NULL)
                            put("bankAccount", tr.toMember.bankAccount ?: JSONObject.NULL)
                            put("bankAccountHolder", tr.toMember.bankAccountHolder ?: JSONObject.NULL)
                            put("transferNote", tr.transferNote)
                        })
                    }
                })
            }.toString(2)

            val snapshot = SettlementSnapshotEntity(
                id = UUID.randomUUID().toString(),
                tripId = trip.id,
                snapshotTitle = snapshotTitle,
                createdAt = settledAt,
                totalExpenses = freshSummary.totalExpenses,
                totalFundCollected = freshSummary.totalFundCollected,
                totalFundSpent = freshSummary.fundPaidExpenses,
                remainingFund = freshSummary.remainingFund,
                settlementJson = structuredSettlementJson
            )
            db.settlementDao().insertSnapshot(snapshot)

            logAction(
                tripId = trip.id,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "SETTLE_TRIP",
                description = "Khóa sổ và quyết toán chuyến đi. Snapshot: $snapshotTitle (Phiên bản v${freshTrip.version} -> v${freshTrip.version + 1})"
            )

            snapshot
        }
    }

    suspend fun reopenSettlement(trip: TripEntity, actor: TripMemberEntity) {
        db.withTransaction {
            if (actor.role != "ADMIN") {
                throw IllegalStateException("Chỉ Trưởng đoàn (Admin) mới có quyền mở khóa sổ chuyến đi!")
            }
            val freshTrip = db.tripDao().getTripByIdOnce(trip.id)
                ?: throw IllegalArgumentException("Chuyến đi không tồn tại!")

            if (!freshTrip.isSettled) {
                throw IllegalStateException("Chuyến đi '${freshTrip.title}' hiện chưa khóa sổ!")
            }

            if (freshTrip.version != trip.version) {
                throw java.util.ConcurrentModificationException(
                    "Dữ liệu chuyến đi đã bị thay đổi đồng thời (phiên bản trên máy: ${trip.version}, trên hệ thống: ${freshTrip.version}). Vui lòng tải lại trang!"
                )
            }

            val rowsUpdated = db.tripDao().reopenSettlementWithOptimisticLock(
                tripId = trip.id,
                expectedVersion = trip.version
            )
            if (rowsUpdated == 0) {
                throw java.util.ConcurrentModificationException("Không thể mở khóa sổ do xung đột đồng thời. Vui lòng thử lại sau khi làm mới!")
            }

            logAction(
                tripId = trip.id,
                actorMemberId = actor.id,
                actorName = actor.name,
                action = "REOPEN_SETTLEMENT",
                description = "Trưởng đoàn ${actor.name} đã mở khóa sổ chuyến đi '${trip.title}' để tiếp tục cập nhật dữ liệu (Phiên bản v${freshTrip.version} -> v${freshTrip.version + 1})"
            )
        }
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
            calculateFinancialSummaryAndStatuses(members, expenses, splits, fundContributions)
        }
    }

    fun calculateFinancialSummaryAndStatuses(
        members: List<TripMemberEntity>,
        expenses: List<ExpenseEntity>,
        splits: List<ExpenseSplitEntity>,
        fundContributions: List<FundContributionEntity>
    ): Pair<FinancialSummary, List<MemberFinancialStatus>> {
        val totalExpenseSum = expenses.sumOfSafe { it.convertedTotalAmount }
        val fundPaidExpensesSum = expenses
            .filter { it.payerType == "FUND" }
            .sumOfSafe { it.convertedTotalAmount }
        val personalPaidExpensesSum = expenses
            .filter { it.payerType == "MEMBER" }
            .sumOfSafe { it.convertedTotalAmount }
        val totalFundCollected = fundContributions.sumOfSafe { it.convertedAmount }
        val remainingFund = totalFundCollected - fundPaidExpensesSum

        // Tính toán tài chính cho từng thành viên
        val memberStatuses = members.map { member ->
            // 1. Chi hộ thực tế từ túi thành viên (expenses where payerMemberId == member.id and payerType == MEMBER)
            val outOfPocket = expenses
                .filter { it.payerType == "MEMBER" && it.payerMemberId == member.id }
                .sumOfSafe { it.convertedTotalAmount }

            // 2. Tiền đã đóng góp vào Quỹ chung
            val fundContributed = fundContributions
                .filter { it.memberId == member.id }
                .sumOfSafe { it.convertedAmount }

            // Tổng tiền thành viên đã thực tế chi/nộp cho đoàn
            val totalPaid = outOfPocket + fundContributed

            // 3. Tiền phải chịu chi (Owed) từ các bảng phân bổ
            val totalOwed = splits
                .filter { it.memberId == member.id }
                .sumOfSafe { it.amount }

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
        val splitDiscrepancy = expenses.sumOfSafe { exp ->
            val expSplits = splitsByExpense[exp.id] ?: emptyList()
            kotlin.math.abs(exp.convertedTotalAmount - expSplits.sumOfSafe { it.amount })
        }

        // 2. Kiểm tra người chi cá nhân: Khoản chi cá nhân phải có người chi thuộc danh sách thành viên đoàn
        val unassignedPayerAmount = expenses
            .filter { it.payerType == "MEMBER" && (it.payerMemberId == null || !memberIds.contains(it.payerMemberId)) }
            .sumOfSafe { it.convertedTotalAmount }

        // 3. Kiểm tra người nộp quỹ: Khoản nộp quỹ phải có người nộp thuộc danh sách thành viên đoàn
        val unassignedFundAmount = fundContributions
            .filter { !memberIds.contains(it.memberId) }
            .sumOfSafe { it.convertedAmount }

        // 4. Kiểm tra phân bổ mồ côi: Split không được gán cho người không thuộc đoàn
        val orphanSplitAmount = splits
            .filter { !memberIds.contains(it.memberId) }
            .sumOfSafe { it.amount }

        // Tổng chênh lệch đối soát thực tế:
        // Sổ sách chỉ được coi là cân bằng khi mọi khoản chi được phân bổ chính xác 100% (chênh lệch = 0đ)
        // và mọi dòng tiền thu/chi đều có thành viên hợp lệ chịu trách nhiệm
        val discrepancy = splitDiscrepancy + unassignedPayerAmount + unassignedFundAmount + orphanSplitAmount
        val isBalanced = members.isNotEmpty() && discrepancy == 0L

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

        return summary to memberStatuses
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

    /**
     * Kiểm tra xem tệp sao lưu tại Uri có được mã hóa bằng mật khẩu hay không.
     * Tối ưu hóa bộ nhớ: Chỉ đọc tối đa 2KB phần đầu của tệp.
     */
    fun isEncryptedBackupUri(context: Context, uri: Uri): Boolean {
        return BackupRestoreManager.isEncryptedBackupUri(context, uri)
    }

    /**
     * Kiểm tra xem tệp sao lưu File cục bộ có được mã hóa bằng mật khẩu hay không.
     * Tối ưu hóa bộ nhớ: Chỉ đọc tối đa 2KB phần đầu của tệp.
     */
    fun isEncryptedBackupFile(file: File): Boolean {
        return BackupRestoreManager.isEncryptedBackupFile(file)
    }

    /**
     * Khôi phục toàn bộ CSDL từ tệp sao lưu Storage Access Framework Uri.
     * Hỗ trợ streaming JsonReader O(1) Memory với tệp không mã hóa, và giải mã AES-256-GCM an toàn với tệp có mật khẩu.
     */
    suspend fun restoreBackupFromUri(context: Context, uri: Uri, clearExisting: Boolean = false, password: String? = null): Result<RestoreResult> {
        return BackupRestoreManager.restoreFromUriStreaming(context, db, uri, clearExisting, password)
    }

    /**
     * Khôi phục toàn bộ CSDL từ tệp sao lưu File cục bộ.
     * Hỗ trợ streaming JsonReader O(1) Memory với tệp không mã hóa, và giải mã AES-256-GCM an toàn với tệp có mật khẩu.
     */
    suspend fun restoreBackupFromFile(file: File, clearExisting: Boolean = false, password: String? = null): Result<RestoreResult> {
        return BackupRestoreManager.restoreFromFileStreaming(db, file, clearExisting, password)
    }

    fun listLocalBackups(context: Context): List<File> {
        return BackupRestoreManager.listLocalBackups(context)
    }

    fun shareBackupFile(context: Context, file: File) {
        BackupRestoreManager.shareBackupFile(context, file)
    }
}
