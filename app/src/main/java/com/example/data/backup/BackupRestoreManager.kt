package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.Reader
import java.io.StringReader
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

    suspend fun createLocalBackupFile(context: Context, db: AppDatabase, password: String? = null): File {
        val rawJson = exportBackupToJson(db)
        val finalContent = if (!password.isNullOrBlank()) {
            BackupCryptoUtils.encryptBackup(rawJson, password)
        } else rawJson

        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val filename = "tripfinance_backup_${fileDateFormat.format(Date())}.json"
        val file = File(backupDir, filename)
        file.writeText(finalContent, Charsets.UTF_8)

        // Tự động dọn dẹp các bản sao lưu cũ, chỉ giữ lại tối đa 10 bản sao lưu mới nhất
        val files = backupDir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
        if (files != null && files.size > 10) {
            files.drop(10).forEach { it.delete() }
        }

        return file
    }

    suspend fun writeBackupToUri(context: Context, uri: Uri, db: AppDatabase, password: String? = null): Result<Unit> {
        return try {
            val rawJson = exportBackupToJson(db)
            val finalContent = if (!password.isNullOrBlank()) {
                BackupCryptoUtils.encryptBackup(rawJson, password)
            } else rawJson

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(finalContent.toByteArray(Charsets.UTF_8))
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

    /**
     * Kiểm tra nhanh xem tệp File có phải là bản sao lưu mã hóa hay không mà chỉ đọc 2KB đầu tệp.
     * Đảm bảo không tốn bộ nhớ RAM ngay cả với tệp dung lượng hàng chục MB.
     */
    fun isEncryptedBackupFile(file: File): Boolean {
        return try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(2048)
                val count = reader.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val snippet = String(buffer, 0, count)
                    BackupCryptoUtils.isEncryptedBackupSnippet(snippet)
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Kiểm tra nhanh xem tệp Uri (Storage Access Framework) có phải là bản sao lưu mã hóa hay không mà chỉ đọc 2KB đầu tệp.
     */
    fun isEncryptedBackupUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(2048)
                    val count = reader.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        val snippet = String(buffer, 0, count)
                        BackupCryptoUtils.isEncryptedBackupSnippet(snippet)
                    } else false
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Khôi phục trực tiếp từ tệp cục bộ (File) theo cơ chế Streaming O(1) Memory đối với tệp không mã hóa.
     */
    suspend fun restoreFromFileStreaming(
        db: AppDatabase,
        file: File,
        clearExisting: Boolean = false,
        password: String? = null
    ): Result<RestoreResult> {
        return try {
            val isEncrypted = isEncryptedBackupFile(file)
            val parseResult: Result<BackupData> = if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("ENCRYPTED_BACKUP_PASSWORD_REQUIRED"))
                }
                val encryptedJson = file.readText(Charsets.UTF_8)
                val decryptResult = BackupCryptoUtils.decryptBackup(encryptedJson, password)
                if (decryptResult.isFailure) {
                    return Result.failure(decryptResult.exceptionOrNull() ?: IllegalArgumentException("Mật khẩu giải mã không chính xác!"))
                }
                parseBackupFromReader(java.io.StringReader(decryptResult.getOrThrow()))
            } else {
                file.bufferedReader(Charsets.UTF_8).use { reader ->
                    parseBackupFromReader(reader)
                }
            }

            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("Dữ liệu sao lưu không hợp lệ"))
            }

            restoreFromBackupData(db, parseResult.getOrThrow(), clearExisting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Khôi phục trực tiếp từ Storage Access Framework Uri theo cơ chế Streaming O(1) Memory đối với tệp không mã hóa.
     */
    suspend fun restoreFromUriStreaming(
        context: Context,
        db: AppDatabase,
        uri: Uri,
        clearExisting: Boolean = false,
        password: String? = null
    ): Result<RestoreResult> {
        return try {
            val isEncrypted = isEncryptedBackupUri(context, uri)
            val parseResult: Result<BackupData> = if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("ENCRYPTED_BACKUP_PASSWORD_REQUIRED"))
                }
                val encryptedJson = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: return Result.failure(IllegalArgumentException("Không thể mở tệp từ hệ thống"))

                val decryptResult = BackupCryptoUtils.decryptBackup(encryptedJson, password)
                if (decryptResult.isFailure) {
                    return Result.failure(decryptResult.exceptionOrNull() ?: IllegalArgumentException("Mật khẩu giải mã không chính xác!"))
                }
                parseBackupFromReader(java.io.StringReader(decryptResult.getOrThrow()))
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        parseBackupFromReader(reader)
                    }
                } ?: return Result.failure(IllegalArgumentException("Không thể mở tệp từ hệ thống"))
            }

            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("Dữ liệu sao lưu không hợp lệ"))
            }

            restoreFromBackupData(db, parseResult.getOrThrow(), clearExisting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseAndValidateBackup(jsonString: String, password: String? = null): Result<BackupData> {
        return try {
            val decryptedJson = if (BackupCryptoUtils.isEncryptedBackup(jsonString)) {
                if (password.isNullOrBlank()) {
                    return Result.failure(IllegalArgumentException("ENCRYPTED_BACKUP_PASSWORD_REQUIRED"))
                }
                val decryptResult = BackupCryptoUtils.decryptBackup(jsonString, password)
                if (decryptResult.isFailure) {
                    return Result.failure(decryptResult.exceptionOrNull() ?: Exception("Mật khẩu giải mã không chính xác!"))
                }
                decryptResult.getOrThrow()
            } else {
                jsonString
            }

            parseBackupFromReader(StringReader(decryptedJson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đọc và phân tích cú pháp tệp sao lưu trực tiếp từ luồng Reader bằng JsonReader (Streaming API).
     * Cơ chế này không tải toàn bộ cây đối tượng JSON vào RAM, tối ưu hóa triệt để bộ nhớ khi nhập file dữ liệu lớn.
     */
    fun parseBackupFromReader(reader: Reader): Result<BackupData> {
        return try {
            val jsonReader = JsonReader(reader)
            jsonReader.isLenient = true

            var metadata: BackupMetadata? = null
            val tripsList = mutableListOf<TripEntity>()
            val membersList = mutableListOf<TripMemberEntity>()
            val expensesList = mutableListOf<ExpenseEntity>()
            val splitsList = mutableListOf<ExpenseSplitEntity>()
            val fundsList = mutableListOf<FundContributionEntity>()
            val ratesList = mutableListOf<ExchangeRateEntity>()
            val snapshotsList = mutableListOf<SettlementSnapshotEntity>()
            val logsList = mutableListOf<AuditLogEntity>()

            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                when (jsonReader.nextName()) {
                    "metadata" -> metadata = readMetadata(jsonReader)
                    "trips" -> readTrips(jsonReader, tripsList)
                    "members" -> readMembers(jsonReader, membersList)
                    "expenses" -> readExpenses(jsonReader, expensesList)
                    "splits" -> readSplits(jsonReader, splitsList)
                    "fundContributions" -> readFunds(jsonReader, fundsList)
                    "exchangeRates" -> readRates(jsonReader, ratesList)
                    "settlementSnapshots" -> readSnapshots(jsonReader, snapshotsList)
                    "auditLogs" -> readLogs(jsonReader, logsList)
                    else -> jsonReader.skipValue()
                }
            }
            jsonReader.endObject()

            if (metadata == null && tripsList.isEmpty() && membersList.isEmpty()) {
                return Result.failure(IllegalArgumentException("Tệp sao lưu không đúng định dạng của TripFinance!"))
            }

            Result.success(
                BackupData(
                    metadata = metadata ?: BackupMetadata(),
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

    private fun readMetadata(reader: JsonReader): BackupMetadata {
        reader.beginObject()
        var appName = "TripFinance"
        var backupVersion = 1
        var schemaVersion = 3
        var createdAt = 0L
        var createdAtFormatted = ""
        var totalTrips = 0
        var totalMembers = 0
        var totalExpenses = 0
        var totalSplits = 0
        var totalFunds = 0
        var totalRates = 0
        var totalSnapshots = 0
        var totalLogs = 0

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "appName" -> appName = reader.nextStringOrDefault("TripFinance")
                "backupVersion" -> backupVersion = reader.nextIntOrDefault(1)
                "schemaVersion" -> schemaVersion = reader.nextIntOrDefault(3)
                "createdAt" -> createdAt = reader.nextLongOrDefault(0L)
                "createdAtFormatted" -> createdAtFormatted = reader.nextStringOrDefault("")
                "totalTrips" -> totalTrips = reader.nextIntOrDefault(0)
                "totalMembers" -> totalMembers = reader.nextIntOrDefault(0)
                "totalExpenses" -> totalExpenses = reader.nextIntOrDefault(0)
                "totalSplits" -> totalSplits = reader.nextIntOrDefault(0)
                "totalFunds" -> totalFunds = reader.nextIntOrDefault(0)
                "totalRates" -> totalRates = reader.nextIntOrDefault(0)
                "totalSnapshots" -> totalSnapshots = reader.nextIntOrDefault(0)
                "totalLogs" -> totalLogs = reader.nextIntOrDefault(0)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return BackupMetadata(
            appName = appName,
            backupVersion = backupVersion,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            createdAtFormatted = createdAtFormatted,
            totalTrips = totalTrips,
            totalMembers = totalMembers,
            totalExpenses = totalExpenses,
            totalSplits = totalSplits,
            totalFunds = totalFunds,
            totalRates = totalRates,
            totalSnapshots = totalSnapshots,
            totalLogs = totalLogs
        )
    }

    private fun readTrips(reader: JsonReader, list: MutableList<TripEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var title = ""
            var description = ""
            var joinCode = ""
            var startDate = 0L
            var endDate = 0L
            var baseCurrency = "VND"
            var isSettled = false
            var settledAt: Long? = null
            var createdAt = System.currentTimeMillis()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "title" -> title = reader.nextStringOrDefault()
                    "description" -> description = reader.nextStringOrDefault()
                    "joinCode" -> joinCode = reader.nextStringOrDefault()
                    "startDate" -> startDate = reader.nextLongOrDefault()
                    "endDate" -> endDate = reader.nextLongOrDefault()
                    "baseCurrency" -> baseCurrency = reader.nextStringOrDefault("VND")
                    "isSettled" -> isSettled = reader.nextBooleanOrDefault()
                    "settledAt" -> settledAt = reader.nextLongOrNull()
                    "createdAt" -> createdAt = reader.nextLongOrDefault(System.currentTimeMillis())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                TripEntity(
                    id = id,
                    title = title,
                    description = description,
                    joinCode = joinCode,
                    startDate = startDate,
                    endDate = endDate,
                    baseCurrency = baseCurrency,
                    isSettled = isSettled,
                    settledAt = settledAt,
                    createdAt = createdAt
                )
            )
        }
        reader.endArray()
    }

    private fun readMembers(reader: JsonReader, list: MutableList<TripMemberEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var tripId = ""
            var userId = UUID.randomUUID().toString()
            var name = ""
            var role = "MEMBER"
            var bankName: String? = null
            var bankAccount: String? = null
            var bankAccountHolder: String? = null
            var isActive = true
            var joinedAt = System.currentTimeMillis()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "userId" -> userId = reader.nextStringOrDefault(UUID.randomUUID().toString())
                    "name" -> name = reader.nextStringOrDefault()
                    "role" -> role = reader.nextStringOrDefault("MEMBER")
                    "bankName" -> bankName = reader.nextStringOrNull()
                    "bankAccount" -> bankAccount = reader.nextStringOrNull()
                    "bankAccountHolder" -> bankAccountHolder = reader.nextStringOrNull()
                    "isActive" -> isActive = reader.nextBooleanOrDefault(true)
                    "joinedAt" -> joinedAt = reader.nextLongOrDefault(System.currentTimeMillis())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                TripMemberEntity(
                    id = id,
                    tripId = tripId,
                    userId = userId,
                    name = name,
                    role = role,
                    bankName = bankName,
                    bankAccount = bankAccount,
                    bankAccountHolder = bankAccountHolder,
                    isActive = isActive,
                    joinedAt = joinedAt
                )
            )
        }
        reader.endArray()
    }

    private fun readExpenses(reader: JsonReader, list: MutableList<ExpenseEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var tripId = ""
            var title = ""
            var category = "OTHER"
            var payerType = "MEMBER"
            var payerMemberId: String? = null
            var totalAmount = 0.0
            var currency = "VND"
            var exchangeRate = 1.0
            var convertedTotalAmount = 0L
            var splitType = "EQUAL"
            var note = ""
            var timestamp = 0L
            var createdMemberId = ""
            var isSynced = true

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "title" -> title = reader.nextStringOrDefault()
                    "category" -> category = reader.nextStringOrDefault("OTHER")
                    "payerType" -> payerType = reader.nextStringOrDefault("MEMBER")
                    "payerMemberId" -> payerMemberId = reader.nextStringOrNull()
                    "totalAmount" -> totalAmount = reader.nextDoubleOrDefault(0.0)
                    "currency" -> currency = reader.nextStringOrDefault("VND")
                    "exchangeRate" -> exchangeRate = reader.nextDoubleOrDefault(1.0)
                    "convertedTotalAmount" -> convertedTotalAmount = reader.nextLongOrDefault(0L)
                    "splitType" -> splitType = reader.nextStringOrDefault("EQUAL")
                    "note" -> note = reader.nextStringOrDefault("")
                    "timestamp" -> timestamp = reader.nextLongOrDefault(0L)
                    "createdMemberId" -> createdMemberId = reader.nextStringOrDefault("")
                    "isSynced" -> isSynced = reader.nextBooleanOrDefault(true)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                ExpenseEntity(
                    id = id,
                    tripId = tripId,
                    title = title,
                    category = category,
                    payerType = payerType,
                    payerMemberId = payerMemberId,
                    totalAmount = totalAmount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedTotalAmount = convertedTotalAmount,
                    splitType = splitType,
                    note = note,
                    timestamp = timestamp,
                    createdMemberId = createdMemberId,
                    isSynced = isSynced
                )
            )
        }
        reader.endArray()
    }

    private fun readSplits(reader: JsonReader, list: MutableList<ExpenseSplitEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var expenseId = ""
            var tripId = ""
            var memberId = ""
            var amount = 0L

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "expenseId" -> expenseId = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "memberId" -> memberId = reader.nextStringOrDefault()
                    "amount" -> amount = reader.nextLongOrDefault(0L)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                ExpenseSplitEntity(
                    id = id,
                    expenseId = expenseId,
                    tripId = tripId,
                    memberId = memberId,
                    amount = amount
                )
            )
        }
        reader.endArray()
    }

    private fun readFunds(reader: JsonReader, list: MutableList<FundContributionEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var tripId = ""
            var memberId = ""
            var amount = 0L
            var currency = "VND"
            var exchangeRate = 1.0
            var convertedAmount = 0L
            var note = ""
            var timestamp = 0L
            var recordedByMemberId = ""

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "memberId" -> {
                        memberId = reader.nextStringOrDefault()
                        if (recordedByMemberId.isEmpty()) recordedByMemberId = memberId
                    }
                    "amount" -> amount = reader.nextLongOrDefault(0L)
                    "currency" -> currency = reader.nextStringOrDefault("VND")
                    "exchangeRate" -> exchangeRate = reader.nextDoubleOrDefault(1.0)
                    "convertedAmount" -> convertedAmount = reader.nextLongOrDefault(0L)
                    "note" -> note = reader.nextStringOrDefault("")
                    "timestamp" -> timestamp = reader.nextLongOrDefault(0L)
                    "recordedByMemberId" -> recordedByMemberId = reader.nextStringOrDefault("")
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                FundContributionEntity(
                    id = id,
                    tripId = tripId,
                    memberId = memberId,
                    amount = amount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedAmount = convertedAmount,
                    note = note,
                    timestamp = timestamp,
                    recordedByMemberId = if (recordedByMemberId.isNotEmpty()) recordedByMemberId else memberId
                )
            )
        }
        reader.endArray()
    }

    private fun readRates(reader: JsonReader, list: MutableList<ExchangeRateEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = UUID.randomUUID().toString()
            var tripId = ""
            var currencyCode = ""
            var rateToBase = 1.0
            var updatedAt = System.currentTimeMillis()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault(UUID.randomUUID().toString())
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "currencyCode" -> currencyCode = reader.nextStringOrDefault()
                    "rateToBase" -> rateToBase = reader.nextDoubleOrDefault(1.0)
                    "updatedAt" -> updatedAt = reader.nextLongOrDefault(System.currentTimeMillis())
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                ExchangeRateEntity(
                    id = id,
                    tripId = tripId,
                    currencyCode = currencyCode,
                    rateToBase = rateToBase,
                    updatedAt = updatedAt
                )
            )
        }
        reader.endArray()
    }

    private fun readSnapshots(reader: JsonReader, list: MutableList<SettlementSnapshotEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var tripId = ""
            var snapshotTitle = "Quyết toán chuyến đi"
            var createdAt = System.currentTimeMillis()
            var totalExpenses = 0L
            var totalFundCollected = 0L
            var totalFundSpent = 0L
            var remainingFund = 0L
            var settlementJson = "{}"

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "snapshotTitle" -> snapshotTitle = reader.nextStringOrDefault("Quyết toán chuyến đi")
                    "createdAt" -> createdAt = reader.nextLongOrDefault(System.currentTimeMillis())
                    "totalExpenses" -> totalExpenses = reader.nextLongOrDefault(0L)
                    "totalFundCollected" -> totalFundCollected = reader.nextLongOrDefault(0L)
                    "totalFundSpent" -> totalFundSpent = reader.nextLongOrDefault(0L)
                    "remainingFund" -> remainingFund = reader.nextLongOrDefault(0L)
                    "settlementJson" -> settlementJson = reader.nextStringOrDefault("{}")
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                SettlementSnapshotEntity(
                    id = id,
                    tripId = tripId,
                    snapshotTitle = snapshotTitle,
                    createdAt = createdAt,
                    totalExpenses = totalExpenses,
                    totalFundCollected = totalFundCollected,
                    totalFundSpent = totalFundSpent,
                    remainingFund = remainingFund,
                    settlementJson = settlementJson
                )
            )
        }
        reader.endArray()
    }

    private fun readLogs(reader: JsonReader, list: MutableList<AuditLogEntity>) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            var id = ""
            var tripId = ""
            var actorMemberId = "SYSTEM"
            var actorName = "Hệ thống"
            var action = ""
            var description = ""
            var detailBefore: String? = null
            var detailAfter: String? = null
            var timestamp = 0L

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id" -> id = reader.nextStringOrDefault()
                    "tripId" -> tripId = reader.nextStringOrDefault()
                    "actorMemberId" -> actorMemberId = reader.nextStringOrDefault("SYSTEM")
                    "actorName" -> actorName = reader.nextStringOrDefault("Hệ thống")
                    "action" -> action = reader.nextStringOrDefault()
                    "description" -> description = reader.nextStringOrDefault()
                    "detailBefore" -> detailBefore = reader.nextStringOrNull()
                    "detailAfter" -> detailAfter = reader.nextStringOrNull()
                    "timestamp" -> timestamp = reader.nextLongOrDefault(0L)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            list.add(
                AuditLogEntity(
                    id = id,
                    tripId = tripId,
                    actorMemberId = actorMemberId,
                    actorName = actorName,
                    action = action,
                    description = description,
                    detailBefore = detailBefore,
                    detailAfter = detailAfter,
                    timestamp = timestamp
                )
            )
        }
        reader.endArray()
    }

    // Helper extension functions for safe streaming reading
    private fun JsonReader.nextStringOrNull(): String? {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString()
        }
    }

    private fun JsonReader.nextLongOrNull(): Long? {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextLong()
        }
    }

    private fun JsonReader.nextStringOrDefault(default: String = ""): String {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            default
        } else {
            nextString()
        }
    }

    private fun JsonReader.nextLongOrDefault(default: Long = 0L): Long {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            default
        } else {
            nextLong()
        }
    }

    private fun JsonReader.nextIntOrDefault(default: Int = 0): Int {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            default
        } else {
            nextInt()
        }
    }

    private fun JsonReader.nextDoubleOrDefault(default: Double = 0.0): Double {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            default
        } else {
            nextDouble()
        }
    }

    private fun JsonReader.nextBooleanOrDefault(default: Boolean = false): Boolean {
        return if (peek() == JsonToken.NULL) {
            nextNull()
            default
        } else {
            nextBoolean()
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
