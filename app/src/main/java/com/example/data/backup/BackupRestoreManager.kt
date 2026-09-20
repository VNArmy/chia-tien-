package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupMetadata(
    val appName: String = "TripFinance",
    val backupVersion: Int = 1,
    val schemaVersion: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val createdAtFormatted: String = "",
    val totalTrips: Int = 0,
    val totalMembers: Int = 0,
    val totalExpenses: Int = 0,
    val totalSplits: Int = 0,
    val totalFunds: Int = 0,
    val totalRates: Int = 0,
    val totalSnapshots: Int = 0,
    val totalLogs: Int = 0
)

data class BackupData(
    val metadata: BackupMetadata,
    val trips: List<TripEntity>,
    val members: List<TripMemberEntity>,
    val expenses: List<ExpenseEntity>,
    val splits: List<ExpenseSplitEntity>,
    val fundContributions: List<FundContributionEntity>,
    val exchangeRates: List<ExchangeRateEntity>,
    val settlementSnapshots: List<SettlementSnapshotEntity>,
    val auditLogs: List<AuditLogEntity>
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val tripsRestored: Int,
    val membersRestored: Int,
    val expensesRestored: Int,
    val splitsRestored: Int,
    val fundsRestored: Int
)

object BackupRestoreManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportBackupToJson(db: AppDatabase): String {
        val trips = db.tripDao().getAllTripsOnce()
        val members = db.tripMemberDao().getAllMembersOnce()
        val expenses = db.expenseDao().getAllExpensesOnce()
        val splits = db.expenseDao().getAllSplitsOnce()
        val funds = db.fundDao().getAllFundsOnce()
        val rates = db.exchangeRateDao().getAllExchangeRatesOnce()
        val snapshots = db.settlementDao().getAllSnapshotsOnce()
        val logs = db.auditLogDao().getAllAuditLogsOnce()

        val now = System.currentTimeMillis()
        val metaJson = JSONObject().apply {
            put("appName", "TripFinance")
            put("backupVersion", 1)
            put("schemaVersion", 3)
            put("createdAt", now)
            put("createdAtFormatted", dateFormat.format(Date(now)))
            put("totalTrips", trips.size)
            put("totalMembers", members.size)
            put("totalExpenses", expenses.size)
            put("totalSplits", splits.size)
            put("totalFunds", funds.size)
            put("totalRates", rates.size)
            put("totalSnapshots", snapshots.size)
            put("totalLogs", logs.size)
        }

        val tripsArray = JSONArray()
        trips.forEach { t ->
            tripsArray.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("description", t.description)
                put("joinCode", t.joinCode)
                put("startDate", t.startDate)
                put("endDate", t.endDate)
                put("baseCurrency", t.baseCurrency)
                put("isSettled", t.isSettled)
                put("settledAt", t.settledAt ?: JSONObject.NULL)
                put("createdAt", t.createdAt)
            })
        }

        val membersArray = JSONArray()
        members.forEach { m ->
            membersArray.put(JSONObject().apply {
                put("id", m.id)
                put("tripId", m.tripId)
                put("userId", m.userId)
                put("name", m.name)
                put("role", m.role)
                put("bankName", m.bankName ?: JSONObject.NULL)
                put("bankAccount", m.bankAccount ?: JSONObject.NULL)
                put("bankAccountHolder", m.bankAccountHolder ?: JSONObject.NULL)
                put("isActive", m.isActive)
                put("joinedAt", m.joinedAt)
            })
        }

        val expensesArray = JSONArray()
        expenses.forEach { e ->
            expensesArray.put(JSONObject().apply {
                put("id", e.id)
                put("tripId", e.tripId)
                put("title", e.title)
                put("category", e.category)
                put("payerType", e.payerType)
                put("payerMemberId", e.payerMemberId ?: JSONObject.NULL)
                put("totalAmount", e.totalAmount)
                put("currency", e.currency)
                put("exchangeRate", e.exchangeRate)
                put("convertedTotalAmount", e.convertedTotalAmount)
                put("splitType", e.splitType)
                put("note", e.note)
                put("timestamp", e.timestamp)
                put("createdMemberId", e.createdMemberId)
                put("isSynced", e.isSynced)
            })
        }

        val splitsArray = JSONArray()
        splits.forEach { s ->
            splitsArray.put(JSONObject().apply {
                put("id", s.id)
                put("expenseId", s.expenseId)
                put("tripId", s.tripId)
                put("memberId", s.memberId)
                put("amount", s.amount)
            })
        }

        val fundsArray = JSONArray()
        funds.forEach { f ->
            fundsArray.put(JSONObject().apply {
                put("id", f.id)
                put("tripId", f.tripId)
                put("memberId", f.memberId)
                put("amount", f.amount)
                put("currency", f.currency)
                put("exchangeRate", f.exchangeRate)
                put("convertedAmount", f.convertedAmount)
                put("timestamp", f.timestamp)
                put("note", f.note)
                put("recordedByMemberId", f.recordedByMemberId)
            })
        }

        val ratesArray = JSONArray()
        rates.forEach { r ->
            ratesArray.put(JSONObject().apply {
                put("id", r.id)
                put("tripId", r.tripId)
                put("currencyCode", r.currencyCode)
                put("rateToBase", r.rateToBase)
                put("updatedAt", r.updatedAt)
            })
        }

        val snapshotsArray = JSONArray()
        snapshots.forEach { sn ->
            snapshotsArray.put(JSONObject().apply {
                put("id", sn.id)
                put("tripId", sn.tripId)
                put("snapshotTitle", sn.snapshotTitle)
                put("createdAt", sn.createdAt)
                put("totalExpenses", sn.totalExpenses)
                put("totalFundCollected", sn.totalFundCollected)
                put("totalFundSpent", sn.totalFundSpent)
                put("remainingFund", sn.remainingFund)
                put("settlementJson", sn.settlementJson)
            })
        }

        val logsArray = JSONArray()
        logs.forEach { l ->
            logsArray.put(JSONObject().apply {
                put("id", l.id)
                put("tripId", l.tripId)
                put("actorMemberId", l.actorMemberId)
                put("actorName", l.actorName)
                put("action", l.action)
                put("description", l.description)
                put("detailBefore", l.detailBefore ?: JSONObject.NULL)
                put("detailAfter", l.detailAfter ?: JSONObject.NULL)
                put("timestamp", l.timestamp)
            })
        }

        val root = JSONObject().apply {
            put("metadata", metaJson)
            put("trips", tripsArray)
            put("members", membersArray)
            put("expenses", expensesArray)
            put("splits", splitsArray)
            put("fundContributions", fundsArray)
            put("exchangeRates", ratesArray)
            put("settlementSnapshots", snapshotsArray)
            put("auditLogs", logsArray)
        }

        return root.toString(2)
    }

    suspend fun createLocalBackupFile(context: Context, db: AppDatabase): File {
        val json = exportBackupToJson(db)
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val filename = "tripfinance_backup_${fileDateFormat.format(Date())}.json"
        val file = File(backupDir, filename)
        file.writeText(json, Charsets.UTF_8)

        // Tự động dọn dẹp các bản sao lưu cũ, chỉ giữ lại tối đa 10 bản sao lưu mới nhất
        val files = backupDir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
        if (files != null && files.size > 10) {
            files.drop(10).forEach { it.delete() }
        }

        return file
    }

    suspend fun writeBackupToUri(context: Context, uri: Uri, db: AppDatabase): Result<Unit> {
        return try {
            val json = exportBackupToJson(db)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return Result.failure(Exception("Không thể mở tệp để ghi dữ liệu"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readBackupFromUri(context: Context, uri: Uri): Result<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader(Charsets.UTF_8).readText()
                Result.success(content)
            } ?: Result.failure(Exception("Không thể mở tệp đã chọn"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseAndValidateBackup(jsonString: String): Result<BackupData> {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("metadata") || !root.has("trips") || !root.has("members")) {
                return Result.failure(IllegalArgumentException("Tệp sao lưu không đúng định dạng của TripFinance!"))
            }

            val metaJson = root.getJSONObject("metadata")
            val metadata = BackupMetadata(
                appName = metaJson.optString("appName", "TripFinance"),
                backupVersion = metaJson.optInt("backupVersion", 1),
                schemaVersion = metaJson.optInt("schemaVersion", 3),
                createdAt = metaJson.optLong("createdAt", 0L),
                createdAtFormatted = metaJson.optString("createdAtFormatted", ""),
                totalTrips = metaJson.optInt("totalTrips", 0),
                totalMembers = metaJson.optInt("totalMembers", 0),
                totalExpenses = metaJson.optInt("totalExpenses", 0),
                totalSplits = metaJson.optInt("totalSplits", 0),
                totalFunds = metaJson.optInt("totalFunds", 0),
                totalRates = metaJson.optInt("totalRates", 0),
                totalSnapshots = metaJson.optInt("totalSnapshots", 0),
                totalLogs = metaJson.optInt("totalLogs", 0)
            )

            val tripsList = mutableListOf<TripEntity>()
            val tripsArray = root.optJSONArray("trips") ?: JSONArray()
            for (i in 0 until tripsArray.length()) {
                val obj = tripsArray.getJSONObject(i)
                tripsList.add(
                    TripEntity(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        joinCode = obj.getString("joinCode"),
                        startDate = obj.getLong("startDate"),
                        endDate = obj.getLong("endDate"),
                        baseCurrency = obj.optString("baseCurrency", "VND"),
                        isSettled = obj.optBoolean("isSettled", false),
                        settledAt = if (obj.isNull("settledAt")) null else obj.optLong("settledAt"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            val membersList = mutableListOf<TripMemberEntity>()
            val membersArray = root.optJSONArray("members") ?: JSONArray()
            for (i in 0 until membersArray.length()) {
                val obj = membersArray.getJSONObject(i)
                membersList.add(
                    TripMemberEntity(
                        id = obj.getString("id"),
                        tripId = obj.getString("tripId"),
                        userId = obj.optString("userId", UUID.randomUUID().toString()),
                        name = obj.getString("name"),
                        role = obj.optString("role", "MEMBER"),
                        bankName = if (obj.isNull("bankName")) null else obj.optString("bankName"),
                        bankAccount = if (obj.isNull("bankAccount")) null else obj.optString("bankAccount"),
                        bankAccountHolder = if (obj.isNull("bankAccountHolder")) null else obj.optString("bankAccountHolder"),
                        isActive = obj.optBoolean("isActive", true),
                        joinedAt = obj.optLong("joinedAt", System.currentTimeMillis())
                    )
                )
            }

            val expensesList = mutableListOf<ExpenseEntity>()
            val expensesArray = root.optJSONArray("expenses") ?: JSONArray()
            for (i in 0 until expensesArray.length()) {
                val obj = expensesArray.getJSONObject(i)
                expensesList.add(
                    ExpenseEntity(
                        id = obj.getString("id"),
                        tripId = obj.getString("tripId"),
                        title = obj.getString("title"),
                        category = obj.optString("category", "OTHER"),
                        payerType = obj.getString("payerType"),
                        payerMemberId = if (obj.isNull("payerMemberId")) null else obj.optString("payerMemberId"),
                        totalAmount = obj.optDouble("totalAmount", obj.optLong("totalAmount", 0L).toDouble()),
                        currency = obj.optString("currency", "VND"),
                        exchangeRate = obj.optDouble("exchangeRate", 1.0),
                        convertedTotalAmount = obj.getLong("convertedTotalAmount"),
                        splitType = obj.optString("splitType", "EQUAL"),
                        note = obj.optString("note", ""),
                        timestamp = obj.getLong("timestamp"),
                        createdMemberId = obj.optString("createdMemberId", ""),
                        isSynced = obj.optBoolean("isSynced", true)
                    )
                )
            }

            val splitsList = mutableListOf<ExpenseSplitEntity>()
            val splitsArray = root.optJSONArray("splits") ?: JSONArray()
            for (i in 0 until splitsArray.length()) {
                val obj = splitsArray.getJSONObject(i)
                splitsList.add(
                    ExpenseSplitEntity(
                        id = obj.getString("id"),
                        expenseId = obj.getString("expenseId"),
                        tripId = obj.getString("tripId"),
                        memberId = obj.getString("memberId"),
                        amount = obj.getLong("amount")
                    )
                )
            }

            val fundsList = mutableListOf<FundContributionEntity>()
            val fundsArray = root.optJSONArray("fundContributions") ?: JSONArray()
            for (i in 0 until fundsArray.length()) {
                val obj = fundsArray.getJSONObject(i)
                fundsList.add(
                    FundContributionEntity(
                        id = obj.getString("id"),
                        tripId = obj.getString("tripId"),
                        memberId = obj.getString("memberId"),
                        amount = obj.getLong("amount"),
                        currency = obj.optString("currency", "VND"),
                        exchangeRate = obj.optDouble("exchangeRate", 1.0),
                        convertedAmount = obj.getLong("convertedAmount"),
                        note = obj.optString("note", ""),
                        timestamp = obj.getLong("timestamp"),
                        recordedByMemberId = obj.optString("recordedByMemberId", obj.getString("memberId"))
                    )
                )
            }

            val ratesList = mutableListOf<ExchangeRateEntity>()
            val ratesArray = root.optJSONArray("exchangeRates") ?: JSONArray()
            for (i in 0 until ratesArray.length()) {
                val obj = ratesArray.getJSONObject(i)
                ratesList.add(
                    ExchangeRateEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        tripId = obj.getString("tripId"),
                        currencyCode = obj.getString("currencyCode"),
                        rateToBase = obj.getDouble("rateToBase"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val snapshotsList = mutableListOf<SettlementSnapshotEntity>()
            val snapshotsArray = root.optJSONArray("settlementSnapshots") ?: JSONArray()
            for (i in 0 until snapshotsArray.length()) {
                val obj = snapshotsArray.getJSONObject(i)
                snapshotsList.add(
                    SettlementSnapshotEntity(
                        id = obj.getString("id"),
                        tripId = obj.getString("tripId"),
                        snapshotTitle = obj.optString("snapshotTitle", "Quyết toán chuyến đi"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        totalExpenses = obj.optLong("totalExpenses", 0L),
                        totalFundCollected = obj.optLong("totalFundCollected", 0L),
                        totalFundSpent = obj.optLong("totalFundSpent", 0L),
                        remainingFund = obj.optLong("remainingFund", 0L),
                        settlementJson = obj.optString("settlementJson", "{}")
                    )
                )
            }

            val logsList = mutableListOf<AuditLogEntity>()
            val logsArray = root.optJSONArray("auditLogs") ?: JSONArray()
            for (i in 0 until logsArray.length()) {
                val obj = logsArray.getJSONObject(i)
                logsList.add(
                    AuditLogEntity(
                        id = obj.getString("id"),
                        tripId = obj.getString("tripId"),
                        actorMemberId = obj.optString("actorMemberId", "SYSTEM"),
                        actorName = obj.optString("actorName", "Hệ thống"),
                        action = obj.getString("action"),
                        description = obj.optString("description", ""),
                        detailBefore = if (obj.isNull("detailBefore")) null else obj.optString("detailBefore"),
                        detailAfter = if (obj.isNull("detailAfter")) null else obj.optString("detailAfter"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }

            Result.success(
                BackupData(
                    metadata = metadata,
                    trips = tripsList,
                    members = membersList,
                    expenses = expensesList,
                    splits = splitsList,
                    fundContributions = fundsList,
                    exchangeRates = ratesList,
                    settlementSnapshots = snapshotsList,
                    auditLogs = logsList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackupData(
        db: AppDatabase,
        backupData: BackupData,
        clearExisting: Boolean = false
    ): Result<RestoreResult> {
        return try {
            db.withTransaction {
                if (clearExisting) {
                    val existingTrips = db.tripDao().getAllTripsOnce()
                    existingTrips.forEach { t ->
                        db.tripDao().deleteTripById(t.id)
                    }
                }

                // Chèn theo thứ tự toàn vẹn dữ liệu khóa ngoại (Trips -> Members -> Rates -> Expenses -> Splits -> Funds -> Snapshots -> Logs)
                if (backupData.trips.isNotEmpty()) {
                    db.tripDao().insertTrips(backupData.trips)
                }
                if (backupData.members.isNotEmpty()) {
                    db.tripMemberDao().insertMembers(backupData.members)
                }
                if (backupData.exchangeRates.isNotEmpty()) {
                    db.exchangeRateDao().insertExchangeRates(backupData.exchangeRates)
                }
                if (backupData.expenses.isNotEmpty()) {
                    db.expenseDao().insertExpenses(backupData.expenses)
                }
                if (backupData.splits.isNotEmpty()) {
                    db.expenseDao().insertSplits(backupData.splits)
                }
                if (backupData.fundContributions.isNotEmpty()) {
                    db.fundDao().insertFundContributions(backupData.fundContributions)
                }
                if (backupData.settlementSnapshots.isNotEmpty()) {
                    db.settlementDao().insertSnapshots(backupData.settlementSnapshots)
                }
                if (backupData.auditLogs.isNotEmpty()) {
                    db.auditLogDao().insertLogs(backupData.auditLogs)
                }

                // Ghi nhận nhật ký khôi phục
                backupData.trips.firstOrNull()?.let { firstTrip ->
                    db.auditLogDao().insertLog(
                        AuditLogEntity(
                            id = UUID.randomUUID().toString(),
                            tripId = firstTrip.id,
                            actorMemberId = "SYSTEM",
                            actorName = "Hệ thống",
                            action = "RESTORE_BACKUP",
                            description = "Khôi phục thành công bản sao lưu từ ${backupData.metadata.createdAtFormatted}: ${backupData.trips.size} chuyến đi, ${backupData.expenses.size} khoản chi.",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            Result.success(
                RestoreResult(
                    success = true,
                    message = "Khôi phục dữ liệu thành công! Đã nạp ${backupData.trips.size} chuyến đi, ${backupData.members.size} thành viên, ${backupData.expenses.size} khoản chi.",
                    tripsRestored = backupData.trips.size,
                    membersRestored = backupData.members.size,
                    expensesRestored = backupData.expenses.size,
                    splitsRestored = backupData.splits.size,
                    fundsRestored = backupData.fundContributions.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listLocalBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified()}
            ?: emptyList()
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bản sao lưu CSDL TripFinance - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ bản sao lưu TripFinance"))
    }
}
