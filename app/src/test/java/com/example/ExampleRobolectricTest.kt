package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.TripFinanceRepository
import com.example.domain.model.DefaultExchangeRates
import com.example.domain.model.sumOfSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.math.round

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TripFinanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(sqliteDb: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(sqliteDb)
                    sqliteDb.execSQL("PRAGMA foreign_keys = ON;")
                    AppDatabase.createDatabaseCheckConstraints(sqliteDb)
                }
                override fun onOpen(sqliteDb: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(sqliteDb)
                    sqliteDb.execSQL("PRAGMA foreign_keys = ON;")
                    AppDatabase.createDatabaseCheckConstraints(sqliteDb)
                }
            })
            .build()
        db.openHelper.writableDatabase.let {
            it.execSQL("PRAGMA foreign_keys = ON;")
            AppDatabase.createDatabaseCheckConstraints(it)
        }
        repository = TripFinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TripFinance", appName)
    }

    @Test
    fun `launch MainActivity without crash`() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        assertNotNull(scenario)
        scenario.close()
    }

    @Test
    fun `database starts clean without sample seed data`() = runBlocking {
        // AppDatabase should start completely empty when installed/created freshly
        val allTrips = repository.allTrips.first()
        assertTrue("Database should have 0 trips upon fresh start", allTrips.isEmpty())
    }

    @Test
    fun `unified exchange rates match specification`() {
        // EUR 27.600, JPY 168, THB 720, USD 25.450
        assertEquals(27600.0, DefaultExchangeRates.RATE_EUR, 0.001)
        assertEquals(168.0, DefaultExchangeRates.RATE_JPY, 0.001)
        assertEquals(720.0, DefaultExchangeRates.RATE_THB, 0.001)
        assertEquals(25450.0, DefaultExchangeRates.RATE_USD, 0.001)
        assertEquals(27600.0, DefaultExchangeRates.getRate("EUR"), 0.001)
        assertEquals(168.0, DefaultExchangeRates.getRate("JPY"), 0.001)
        assertEquals(720.0, DefaultExchangeRates.getRate("THB"), 0.001)
    }

    @Test
    fun `currency conversion properly rounds decimal inputs such as 12_50 USD`() {
        val amount = 12.50
        val rate = DefaultExchangeRates.RATE_USD // 25450.0
        val converted = round(amount * rate).toLong()
        // 12.50 USD * 25,450 = 318,125 VND (Previously truncated with toLong() if entered as integer or lost)
        assertEquals(318125L, converted)

        // Decimal EUR test: 10.75 EUR * 27,600 = 296,700 VND
        val eurAmount = 10.75
        val eurRate = DefaultExchangeRates.RATE_EUR // 27600.0
        val eurConverted = round(eurAmount * eurRate).toLong()
        assertEquals(296700L, eurConverted)

        // Non-integer rounding test: 12.345 USD * 25,450 = 314,180.25 -> 314,180 VND
        val preciseAmount = 12.345
        val preciseConverted = round(preciseAmount * rate).toLong()
        assertEquals(314180L, preciseConverted)
    }

    @Test
    fun `editExpense preserves createdMemberId and records detailBefore and detailAfter`() = runBlocking {
        // 1. Create a trip
        val tripId = repository.createTrip(
            title = "Chuyến đi Hà Nội",
            description = "Công tác Hà Nội 2026",
            joinCode = "HN2026",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Nguyễn Văn A",
            adminBankName = "Vietcombank",
            adminBankAccount = "1234567890",
            adminBankHolder = "NGUYEN VAN A"
        )
        val members = repository.getMembers(tripId).first()
        val adminMember = members.first()

        // 2. Add second member (Bob)
        val bobId = repository.addMember(
            tripId = tripId,
            name = "Trần Văn B",
            role = "MEMBER",
            bankName = null,
            bankAccount = null,
            bankAccountHolder = null,
            actor = adminMember
        )
        val updatedMembers = repository.getMembers(tripId).first()
        val bobMember = updatedMembers.first { it.id == bobId }

        // 3. Bob creates an expense with decimal amount 12.50 USD
        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Bữa sáng phở",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = bobId,
            totalAmount = 12.50,
            currency = "USD",
            exchangeRate = DefaultExchangeRates.RATE_USD,
            convertedTotalAmount = round(12.50 * DefaultExchangeRates.RATE_USD).toLong(),
            splitType = "EQUAL",
            note = "Ăn sáng ngon",
            timestamp = System.currentTimeMillis(),
            createdMemberId = bobId
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, adminMember.id, 159062L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, bobId, 159063L)
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = splits,
            actor = bobMember
        )

        // Verify createdMemberId is Bob
        val savedExpense = db.expenseDao().getExpenseById(expense.id)
        assertNotNull(savedExpense)
        assertEquals(bobId, savedExpense?.createdMemberId)
        assertEquals(12.50, savedExpense?.totalAmount ?: 0.0, 0.001)

        // 4. Admin edits the expense (changes title and totalAmount to 15.0 USD)
        val updatedExpense = savedExpense!!.copy(
            title = "Bữa sáng phở đặc biệt",
            totalAmount = 15.0,
            convertedTotalAmount = round(15.0 * DefaultExchangeRates.RATE_USD).toLong(),
            createdMemberId = adminMember.id // Intentional attempt by caller to pass editor's id
        )
        val updatedSplits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, adminMember.id, 190875L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, bobId, 190875L)
        )
        repository.updateExpenseWithSplits(
            expense = updatedExpense,
            splits = updatedSplits,
            actor = adminMember
        )

        // 5. Verify createdMemberId is preserved as Bob
        val modifiedExpense = db.expenseDao().getExpenseById(expense.id)
        assertNotNull(modifiedExpense)
        assertEquals("createdMemberId must NOT be overwritten by editor", bobId, modifiedExpense?.createdMemberId)
        assertEquals("Bữa sáng phở đặc biệt", modifiedExpense?.title)

        // 6. Verify audit logs contain detailBefore and detailAfter
        val logs = repository.getAuditLogs(tripId).first()
        val updateLog = logs.find { it.action == "UPDATE_EXPENSE" }
        assertNotNull("Update expense audit log must exist", updateLog)
        assertNotNull("detailBefore must not be null", updateLog?.detailBefore)
        assertNotNull("detailAfter must not be null", updateLog?.detailAfter)
        assertTrue("detailBefore must contain old title", updateLog?.detailBefore?.contains("Bữa sáng phở") == true)
        assertTrue("detailAfter must contain new title", updateLog?.detailAfter?.contains("Bữa sáng phở đặc biệt") == true)
    }

    @Test
    fun `atomic deleteTripCascade removes all trip data`() = runBlocking {
        val tripId = repository.createTrip(
            title = "Chuyến đi test cascade",
            description = "Thử nghiệm cascade",
            joinCode = "DL2026",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Nguyễn Văn A",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val members = repository.getMembers(tripId).first()
        val admin = members.first()

        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Vé xe",
            category = "TRANSPORT",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 250000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 250000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, admin.id, 250000L)),
            actor = admin
        )

        // Delete cascade in atomic transaction
        repository.deleteTripCascade(tripId)

        // Verify everything deleted
        val trips = repository.allTrips.first()
        assertTrue(trips.none { it.id == tripId })
        val remainingMembers = repository.getMembers(tripId).first()
        assertTrue(remainingMembers.isEmpty())
        val remainingExpenses = repository.getExpenses(tripId).first()
        assertTrue(remainingExpenses.isEmpty())
    }

    @Test
    fun `test BackupCryptoUtils encryption and decryption roundtrip`() {
        val sampleJson = """{"version":1,"appName":"TripFinance","data":"secret_account_number_123456"}"""
        val password = "SuperSecretPassword#2026"

        val encrypted = com.example.data.backup.BackupCryptoUtils.encryptBackup(sampleJson, password)
        assertTrue(com.example.data.backup.BackupCryptoUtils.isEncryptedBackup(encrypted))
        assertTrue(com.example.data.backup.BackupCryptoUtils.isEncryptedBackupSnippet(encrypted.take(500)))

        // Decrypt with correct password
        val decryptSuccess = com.example.data.backup.BackupCryptoUtils.decryptBackup(encrypted, password)
        assertTrue(decryptSuccess.isSuccess)
        assertEquals(sampleJson, decryptSuccess.getOrThrow())

        // Decrypt with wrong password
        val decryptFailure = com.example.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "WrongPassword#999")
        assertTrue(decryptFailure.isFailure)
    }

    @Test
    fun `joinCode collision does not replace existing trip or delete expenses`() = runBlocking {
        // Tạo đoàn 1 với joinCode cố định
        val trip1Id = repository.createTrip(
            title = "Đoàn gốc 1",
            description = "Chuyến đi 1",
            joinCode = "TRIP-ORIGINAL",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin 1",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val admin1 = repository.getMembers(trip1Id).first().first()

        // Thêm chi tiêu cho đoàn 1
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = trip1Id,
            title = "Chi tiêu đoàn 1",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin1.id,
            totalAmount = 500000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 500000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin1.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expId, trip1Id, admin1.id, 500000L)),
            actor = admin1
        )

        // Cố tình tạo đoàn 2 với cùng joinCode "TRIP-ORIGINAL" -> Hệ thống phải báo lỗi trùng mã, KHÔNG được đè đoàn 1
        try {
            repository.createTrip(
                title = "Đoàn xâm phạm 2",
                description = "Cố tình trùng mã",
                joinCode = "TRIP-ORIGINAL",
                startDate = System.currentTimeMillis(),
                endDate = System.currentTimeMillis() + 86400000L,
                adminName = "Admin 2",
                adminBankName = null,
                adminBankAccount = null,
                adminBankHolder = null
            )
            fail("Phải ném ngoại lệ khi trùng joinCode do người dùng chỉ định")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("đã được sử dụng") == true)
        }

        // Kiểm tra toàn vẹn: Đoàn 1 và chi tiêu của nó vẫn nguyên vẹn 100%
        val trip1 = repository.getTrip(trip1Id).first()
        assertNotNull(trip1)
        assertEquals("Đoàn gốc 1", trip1?.title)
        val expList = repository.getExpenses(trip1Id).first()
        assertEquals(1, expList.size)
        assertEquals("Chi tiêu đoàn 1", expList[0].title)
    }

    @Test
    fun `restore backup never unseals settled trip`() = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn đã quyết toán",
            description = "Đã khóa sổ",
            joinCode = "SETTLED-01",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin Quyết Toán",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val originalTrip = repository.getTrip(tripId).first()!!

        // Khóa sổ đoàn
        db.tripDao().updateTrip(originalTrip.copy(isSettled = true, settledAt = 1700000000000L))
        val settledTrip = repository.getTrip(tripId).first()!!
        assertTrue(settledTrip.isSettled)

        // Tạo bản backup cũ trong đó đoàn chưa khóa sổ (isSettled = false)
        val backupData = com.example.data.backup.BackupData(
            metadata = com.example.data.backup.BackupMetadata(
                createdAt = 1690000000000L,
                createdAtFormatted = "2026-01-01",
                totalTrips = 1,
                totalExpenses = 0
            ),
            trips = listOf(originalTrip.copy(isSettled = false, settledAt = null))
        )

        // Phục hồi dữ liệu (clearExisting = false)
        val result = com.example.data.backup.BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = false)
        assertTrue(result.isSuccess)

        // Xác nhận trạng thái niêm phong: Đoàn VẪN PHẢI LÀ isSettled = true, không được mở niêm phong
        val afterRestoreTrip = repository.getTrip(tripId).first()!!
        assertTrue("Đoàn đã quyết toán không được phép bị mở niêm phong khi restore bản cũ!", afterRestoreTrip.isSettled)
        assertEquals(1700000000000L, afterRestoreTrip.settledAt)
    }

    @Test
    fun `restore clearExisting handles cascade cleanly without trigger violation`() = runBlocking {
        // Tạo đoàn và thành viên có chi tiêu
        val tripId = repository.createTrip(
            title = "Đoàn cần dọn dẹp",
            description = "Test clearExisting",
            joinCode = "CLEAN-99",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin Clean",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val admin = repository.getMembers(tripId).first().first()

        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Chi phí khách sạn",
            category = "HOTEL",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 1200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 1200000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, admin.id, 1200000L)),
            actor = admin
        )

        // Khôi phục đè toàn bộ (clearExisting = true)
        val backupData = com.example.data.backup.BackupData(
            metadata = com.example.data.backup.BackupMetadata(
                createdAt = System.currentTimeMillis(),
                createdAtFormatted = "2026-09-28",
                totalTrips = 0,
                totalExpenses = 0
            ),
            trips = emptyList()
        )
        val result = com.example.data.backup.BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = true)
        assertTrue("clearExisting = true phải thành công mà không bị trigger chặn", result.isSuccess)

        val trips = repository.allTrips.first()
        assertTrue(trips.isEmpty())
    }

    @Test
    fun `financial input validator parses VND and foreign currency correctly`() {
        // 1. Kiểm tra VND với dấu phân cách hàng nghìn kiểu Việt Nam: 1.500 -> 1500 VND (Không được hiểu là 1.5 VND)
        val res1 = com.example.domain.model.FinancialInputValidator.parseAmount("1.500", "VND")
        assertTrue(res1 is com.example.domain.model.AmountValidationResult.Success)
        assertEquals(1500.0, (res1 as com.example.domain.model.AmountValidationResult.Success).amount, 0.001)

        val res2 = com.example.domain.model.FinancialInputValidator.parseAmount("25.450", "VND")
        assertTrue(res2 is com.example.domain.model.AmountValidationResult.Success)
        assertEquals(25450.0, (res2 as com.example.domain.model.AmountValidationResult.Success).amount, 0.001)

        val res3 = com.example.domain.model.FinancialInputValidator.parseAmount("1.500.000", "VND")
        assertTrue(res3 is com.example.domain.model.AmountValidationResult.Success)
        assertEquals(1500000.0, (res3 as com.example.domain.model.AmountValidationResult.Success).amount, 0.001)

        // 2. Tỷ giá nhập sai như "abc" phải báo lỗi, KHÔNG được âm thầm thay bằng 1.0
        val rateBad = com.example.domain.model.FinancialInputValidator.parseRate("abc", "USD")
        assertTrue("Tỷ giá 'abc' phải trả về lỗi", rateBad is com.example.domain.model.RateValidationResult.Error)

        val rateGood = com.example.domain.model.FinancialInputValidator.parseRate("25450", "USD")
        assertTrue(rateGood is com.example.domain.model.RateValidationResult.Success)
        assertEquals(25450.0, (rateGood as com.example.domain.model.RateValidationResult.Success).rate, 0.001)

        // 3. Ngoại tệ với số thập phân hợp lệ: 12.50 USD
        val usdRes = com.example.domain.model.FinancialInputValidator.parseAmount("12.50", "USD")
        assertTrue(usdRes is com.example.domain.model.AmountValidationResult.Success)
        assertEquals(12.50, (usdRes as com.example.domain.model.AmountValidationResult.Success).amount, 0.001)

        // 4. Giới hạn trên: Số tiền vượt 100 tỷ phải bị chặn
        val overLimit = com.example.domain.model.FinancialInputValidator.parseAmount("200000000000", "VND")
        assertTrue(overLimit is com.example.domain.model.AmountValidationResult.Error)
    }

    @Test
    fun `sumOfSafe prevents Long overflow`() {
        val hugeList = listOf(Long.MAX_VALUE, Long.MAX_VALUE)
        val sum = hugeList.sumOfSafe { it }
        // Thông thường Long.MAX_VALUE * 2 sẽ tràn số thành -2. Với sumOfSafe, nó kẹp an toàn ở Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, sum)
    }

    @Test
    fun `database migration from v1 to v4 produces matching schema without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        // Khởi tạo database phiên bản 1 bằng SQLite trực tiếp
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 1
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `bankName` TEXT, `bankAccount` TEXT, `bankAccountHolder` TEXT, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        // Thêm dữ liệu mẫu vào v1
        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-v1', 'Đoàn v1', 'Mô tả v1', 'V1-CODE', 1000, 2000, 'VND', 0, NULL, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('member-v1', 'trip-v1', 'user-1', 'Thành viên v1', 'ADMIN', 1, NULL, NULL, NULL, 1000)")
        sqlite.execSQL("INSERT INTO `expenses` VALUES('exp-v1', 'trip-v1', 'Chi tiêu v1', 'FOOD', 'MEMBER', 'member-v1', 50000, 'VND', 1.0, 50000, 'EQUAL', NULL, 'Ghi chú', 1000, 'member-v1', 1000, 1)")
        sqlite.close()

        // Mở qua Room Database builder kèm migration 1_2, 2_3, 3_4 (không có destructive fallback)
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        val migratedTrips = runBlocking { migratedDb.tripDao().getAllTripsOnce() }
        assertEquals(1, migratedTrips.size)
        assertEquals("Đoàn v1", migratedTrips[0].title)

        val migratedExpenses = runBlocking { migratedDb.expenseDao().getAllExpensesOnce() }
        assertEquals(1, migratedExpenses.size)
        assertEquals(50000.0, migratedExpenses[0].totalAmount, 0.001)

        // Kiểm tra bảng settlement_snapshots đã tồn tại và có các cột chuẩn v4
        val snapshotList = runBlocking { migratedDb.settlementDao().getAllSnapshotsOnce() }
        assertTrue(snapshotList.isEmpty()) // Chưa có snapshot nào nhưng bảng đã sẵn sàng

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test DatabaseKeyManager never stores plaintext seed in SharedPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trip_finance_db_sec_vault", Context.MODE_PRIVATE)

        // Đảm bảo không tồn tại bất kỳ fallback plaintext nào
        assertFalse(prefs.contains("fallback_db_seed"))

        // Lấy passphrase
        val passphrase = com.example.data.security.DatabaseKeyManager.getOrCreateDatabasePassphrase(context)
        assertNotNull(passphrase)
        assertEquals(32, passphrase.size)

        // Xác nhận sau khi lấy passphrase, SharedPreferences tuyệt đối KHÔNG chứa khóa plaintext
        assertFalse("Tuyệt đối không lưu khóa dự phòng plaintext trong SharedPreferences", prefs.contains("fallback_db_seed"))
    }

    @Test
    fun `test AppDatabase resetDatabase cleans up credentials safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trip_finance_db_sec_vault", Context.MODE_PRIVATE)
        prefs.edit().putString("test_key", "test_value").commit()
        assertTrue(prefs.contains("test_key"))

        AppDatabase.resetDatabase(context)
        assertFalse("resetDatabase phải xóa sạch SharedPreferences bảo mật để tạo mới", prefs.contains("test_key"))
    }

    @Test
    fun `test DatabaseSecurityException error types for failure modes`() {
        val exInit = com.example.data.security.DatabaseSecurityException(
            message = "KeyStore init failed",
            errorType = com.example.data.security.DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED
        )
        assertEquals(com.example.data.security.DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED, exInit.errorType)

        val exCipher = com.example.data.security.DatabaseSecurityException(
            message = "SQLCipher load failed",
            errorType = com.example.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED
        )
        assertEquals(com.example.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED, exCipher.errorType)

        val exCommit = com.example.data.security.DatabaseSecurityException(
            message = "Commit failed",
            errorType = com.example.data.security.DatabaseSecurityErrorType.COMMIT_FAILED
        )
        assertEquals(com.example.data.security.DatabaseSecurityErrorType.COMMIT_FAILED, exCommit.errorType)
    }

    @Test
    fun `migration from legacy v2 with old settlement_snapshots converts data cleanly to v4`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_legacy_snapshot.db")
        dbFile.delete()

        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 2
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `bankName` TEXT, `bankAccount` TEXT, `bankAccountHolder` TEXT, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        // Tạo bảng settlement_snapshots kiểu cũ với các cột snapshotDataJson, totalFund, settledByMemberId
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFund` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settledByMemberId` TEXT, `snapshotDataJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-legacy', 'Đoàn cũ', 'Mô tả', 'LEGACY-CODE', 1000, 2000, 'VND', 1, 2000, 1000, 1)")
        sqlite.execSQL("INSERT INTO `settlement_snapshots` VALUES('snap-old', 'trip-legacy', 2000, 500000, 600000, 100000, 'member-1', '{\"summary\":\"test\"}')")
        sqlite.close()

        // Nâng cấp trực tiếp lên v4
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_legacy_snapshot.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()

        val snapshots = runBlocking { migratedDb.settlementDao().getAllSnapshotsOnce() }
        assertEquals(1, snapshots.size)
        val snap = snapshots[0]
        assertEquals("snap-old", snap.id)
        assertEquals("Bản quyết toán đã lưu", snap.snapshotTitle)
        assertEquals(600000L, snap.totalFundCollected)
        assertEquals(500000L, snap.totalFundSpent)
        assertEquals(100000L, snap.remainingFund)
        assertEquals("{\"summary\":\"test\"}", snap.settlementJson)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test backup and restore roundtrip preserves receiptImageUri, percentage, and trip version`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Chuyến đi Đà Nẵng",
            description = "Du lịch hè",
            joinCode = "DN2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin DN",
            adminBankName = "MBBank",
            adminBankAccount = "0987654321",
            adminBankHolder = "ADMIN DN"
        )
        val adminMember = repository.getMembers(tripId).first().first()

        // Thêm thành viên thứ 2
        val member2Id = repository.addMember(
            tripId = tripId,
            name = "Thành viên 2",
            role = "MEMBER",
            bankName = null,
            bankAccount = null,
            bankAccountHolder = null,
            actor = adminMember
        )

        // Thêm khoản chi có receiptImageUri và split percentage
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = "Khách sạn biển",
            category = "HOTEL",
            payerType = "MEMBER",
            payerMemberId = adminMember.id,
            totalAmount = 1000000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 1000000L,
            splitType = "RATIO",
            receiptImageUri = "content://media/external/images/receipt_123.jpg",
            note = "Biên lai đầy đủ",
            timestamp = 1500L,
            createdMemberId = adminMember.id
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, adminMember.id, 600000L, 60.0),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, member2Id, 400000L, 40.0)
        )
        repository.addExpenseWithSplits(expense, splits, adminMember)

        // Xuất backup dạng JSON (mã hóa mật khẩu để giữ nguyên số tài khoản)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupFile = repository.createLocalBackup(context, password = "SafePassword123")
        assertTrue(backupFile.exists())

        // Nạp lại vào database mới
        val newDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val restoreRes = com.example.data.backup.BackupRestoreManager.restoreFromFileStreaming(
            db = newDb,
            file = backupFile,
            clearExisting = true,
            password = "SafePassword123"
        )
        assertTrue(restoreRes.isSuccess)

        // Kiểm chứng tính nguyên vẹn: receiptImageUri, percentage, và trip version không bị mất
        val restoredExpenses = newDb.expenseDao().getAllExpensesOnce()
        assertEquals(1, restoredExpenses.size)
        assertEquals("content://media/external/images/receipt_123.jpg", restoredExpenses[0].receiptImageUri)

        val restoredSplits = newDb.expenseDao().getAllSplitsOnce()
        assertEquals(2, restoredSplits.size)
        val split1 = restoredSplits.first { it.memberId == adminMember.id }
        assertEquals(60.0, split1.percentage ?: 0.0, 0.001)

        val restoredTrips = newDb.tripDao().getAllTripsOnce()
        assertEquals(1, restoredTrips.size)
        assertEquals(1L, restoredTrips[0].version)

        newDb.close()
        backupFile.delete()
    }

    @Test
    fun `test unencrypted backup masks bank account whereas encrypted backup preserves it`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Test Bảo Mật",
            description = "Kiểm tra bảo mật số tài khoản",
            joinCode = "SEC-2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Thủ quỹ",
            adminBankName = "VietinBank",
            adminBankAccount = "102030405060",
            adminBankHolder = "THU QUY"
        )

        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Tạo bản sao lưu không mã hóa: Phải tự động che mờ số tài khoản
        val unencryptedFile = repository.createLocalBackup(context, password = null)
        val unencryptedJson = unencryptedFile.readText(Charsets.UTF_8)
        assertFalse("Bản sao lưu không mã hóa tuyệt đối không chứa số tài khoản thô", unencryptedJson.contains("102030405060"))
        assertTrue("Bản sao lưu không mã hóa phải che mờ dạng ******5060", unencryptedJson.contains("******5060"))

        // 2. Tạo bản sao lưu có mã hóa mật khẩu: Nội dung tệp không lộ số tài khoản thô (AES-256-GCM)
        val encryptedFile = repository.createLocalBackup(context, password = "StrongPassword789")
        val encryptedContent = encryptedFile.readText(Charsets.UTF_8)
        assertFalse("Ciphertext không được chứa plaintext", encryptedContent.contains("102030405060"))

        // Nhưng sau khi giải mã đúng mật khẩu, số tài khoản đầy đủ được phục hồi nguyên vẹn
        val decrypted = com.example.data.backup.BackupCryptoUtils.decryptBackup(encryptedContent, "StrongPassword789").getOrThrow()
        assertTrue("Sau khi giải mã phải phục hồi đầy đủ số tài khoản", decrypted.contains("102030405060"))

        unencryptedFile.delete()
        encryptedFile.delete()
    }

    @Test
    fun `test backup integrity validation rejects tampered split sums and invalid roles`() {
        val trip = TripEntity("trip-1", "Hà Nội", "Công tác", "HN-01", 1000L, 2000L)
        val memberAdmin = TripMemberEntity("m-1", "trip-1", "u-1", "Admin", "ADMIN")
        val memberUser = TripMemberEntity("m-2", "trip-1", "u-2", "User", "MEMBER")

        val expense = ExpenseEntity(
            id = "exp-1",
            tripId = "trip-1",
            title = "Ăn tối",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = "m-1",
            totalAmount = 500000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 500000L,
            splitType = "EQUAL",
            createdMemberId = "m-1"
        )

        // 1. Phép thử: Tổng tiền chia không khớp với convertedTotalAmount (sửa tay số tiền)
        val tamperedSplits = listOf(
            ExpenseSplitEntity("s-1", "exp-1", "trip-1", "m-1", 200000L),
            ExpenseSplitEntity("s-2", "exp-1", "trip-1", "m-2", 200000L) // Tổng 400k != 500k
        )
        val tamperedData = com.example.data.backup.BackupData(
            metadata = com.example.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, memberUser),
            expenses = listOf(expense),
            splits = tamperedSplits
        )
        val splitCheck = com.example.data.backup.BackupRestoreManager.validateBackupData(tamperedData)
        assertTrue("Phải từ chối bản sao lưu bị sửa lệch tổng tiền chia", splitCheck.isFailure)
        assertTrue(splitCheck.exceptionOrNull()?.message?.contains("không khớp với số tiền quy đổi") == true)

        // 2. Phép thử: Sửa tay vai trò không hợp lệ (ví dụ: HACKER)
        val fakeRoleMember = TripMemberEntity("m-2", "trip-1", "u-2", "User", "HACKER")
        val correctSplits = listOf(
            ExpenseSplitEntity("s-1", "exp-1", "trip-1", "m-1", 250000L),
            ExpenseSplitEntity("s-2", "exp-1", "trip-1", "m-2", 250000L)
        )
        val roleData = com.example.data.backup.BackupData(
            metadata = com.example.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, fakeRoleMember),
            expenses = listOf(expense),
            splits = correctSplits
        )
        val roleCheck = com.example.data.backup.BackupRestoreManager.validateBackupData(roleData)
        assertTrue("Phải từ chối bản sao lưu bị gán vai trò phi pháp", roleCheck.isFailure)
        assertTrue(roleCheck.exceptionOrNull()?.message?.contains("không hợp lệ") == true)

        // 3. Phép thử: Thêm nhật ký giả với thành viên không tồn tại trong đoàn
        val fakeLog = AuditLogEntity(
            id = "log-1",
            tripId = "trip-1",
            actorMemberId = "fake-member-999",
            actorName = "Kẻ mạo danh",
            action = "ADD_EXPENSE",
            description = "Khoản chi ma",
            timestamp = 1200L
        )
        val fakeLogData = com.example.data.backup.BackupData(
            metadata = com.example.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, memberUser),
            expenses = listOf(expense),
            splits = correctSplits,
            auditLogs = listOf(fakeLog)
        )
        val logCheck = com.example.data.backup.BackupRestoreManager.validateBackupData(fakeLogData)
        assertTrue("Phải từ chối bản sao lưu chứa nhật ký kiểm toán giả", logCheck.isFailure)
        assertTrue(logCheck.exceptionOrNull()?.message?.contains("nghi vấn nhật ký giả") == true)
    }

    @Test
    fun `test BackupCryptoUtils password requirements and iteration bounds`() {
        // 1. Mật khẩu ngắn dưới 6 ký tự phải bị từ chối
        assertThrows(IllegalArgumentException::class.java) {
            com.example.data.backup.BackupCryptoUtils.encryptBackup("{}", "12345")
        }

        // 2. Mật khẩu 6 ký tự hợp lệ
        val encrypted = com.example.data.backup.BackupCryptoUtils.encryptBackup("{\"hello\":\"world\"}", "123456")
        assertTrue(com.example.data.backup.BackupCryptoUtils.isEncryptedBackup(encrypted))

        // 3. Giải mã sai mật khẩu phải trả về failure
        val failDec = com.example.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "WrongPassword")
        assertTrue(failDec.isFailure)

        // 4. Giải mã đúng mật khẩu
        val okDec = com.example.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "123456")
        assertTrue(okDec.isSuccess)
        assertEquals("{\"hello\":\"world\"}", okDec.getOrThrow())
    }

    @Test
    fun `test BackupCryptoUtils rejects malicious iterations to prevent CPU DoS`() {
        val validEncrypted = com.example.data.backup.BackupCryptoUtils.encryptBackup("{\"data\":1}", "Password123")
        val root = org.json.JSONObject(validEncrypted)

        // Phép thử 1: Tệp độc hại đặt iterations = 50_000_000 (tấn công DoS CPU treo máy)
        root.put("iterations", 50_000_000)
        val dosResult = com.example.data.backup.BackupCryptoUtils.decryptBackup(root.toString(), "Password123")
        assertTrue("Phải từ chối tệp có số vòng lặp KDF vượt ngưỡng an toàn", dosResult.isFailure)
        assertTrue(dosResult.exceptionOrNull()?.message?.contains("không nằm trong giới hạn an toàn") == true)

        // Phép thử 2: Tệp đặt iterations = 50 (quá yếu)
        root.put("iterations", 50)
        val weakResult = com.example.data.backup.BackupCryptoUtils.decryptBackup(root.toString(), "Password123")
        assertTrue("Phải từ chối tệp có số vòng lặp KDF quá thấp", weakResult.isFailure)
        assertTrue(weakResult.exceptionOrNull()?.message?.contains("không nằm trong giới hạn an toàn") == true)
    }

    @Test
    fun `test escapeCsv neutralizes spreadsheet formula injection and escapes quotes correctly`() {
        // 1. Công thức bắt đầu bằng =
        val formulaEqual = "=HYPERLINK(\"http://evil.com\", \"Click\")"
        val escapedEqual = com.example.domain.export.ReportGenerator.escapeCsv(formulaEqual)
        assertEquals("\"'=HYPERLINK(\"\"http://evil.com\"\", \"\"Click\"\")\"", escapedEqual)

        // 2. Công thức bắt đầu bằng +, -, @, %
        val formulaPlus = "+cmd|' /C calc'!A0"
        val escapedPlus = com.example.domain.export.ReportGenerator.escapeCsv(formulaPlus)
        assertTrue(escapedPlus.startsWith("\"'+"))

        val formulaMinus = "-2+3*cmd|' /C calc'!A0"
        val escapedMinus = com.example.domain.export.ReportGenerator.escapeCsv(formulaMinus)
        assertTrue(escapedMinus.startsWith("\"'-"))

        val formulaAt = "@SUM(A1:A10)"
        val escapedAt = com.example.domain.export.ReportGenerator.escapeCsv(formulaAt)
        assertTrue(escapedAt.startsWith("\"'@"))

        val formulaPercent = "%LOCALAPPDATA%"
        val escapedPercent = com.example.domain.export.ReportGenerator.escapeCsv(formulaPercent)
        assertTrue(escapedPercent.startsWith("\"'%"))

        // 3. Chuỗi có khoảng trắng phía trước công thức
        val spacedFormula = "   =1+1"
        val escapedSpaced = com.example.domain.export.ReportGenerator.escapeCsv(spacedFormula)
        assertTrue(escapedSpaced.startsWith("\"'   ="))

        // 4. Dấu ngoặc kép trong văn bản thường: phải nhân đôi (" -> "") và không làm vỡ cấu trúc cột
        val textWithQuotes = "Khoản chi \"Ăn uống buffet\" tại Đà Nẵng"
        val escapedQuotes = com.example.domain.export.ReportGenerator.escapeCsv(textWithQuotes)
        assertEquals("\"Khoản chi \"\"Ăn uống buffet\"\" tại Đà Nẵng\"", escapedQuotes)

        // 5. Xuống dòng trong ghi chú: phải được làm sạch thành dấu cách
        val textWithNewline = "Dòng 1\nDòng 2\r\nDòng 3"
        val escapedNewline = com.example.domain.export.ReportGenerator.escapeCsv(textWithNewline)
        assertFalse(escapedNewline.contains("\n"))
        assertFalse(escapedNewline.contains("\r"))
        assertEquals("\"Dòng 1 Dòng 2 Dòng 3\"", escapedNewline)
    }

    @Test
    fun `test generateCsvReport outputs UTF-8 BOM and escapes formula injections in ledger`() {
        val trip = TripEntity(
            id = "t-csv",
            title = "=HYPERLINK(\"http://evil.com\", \"Đoàn Hack\")",
            description = "Test CSV",
            joinCode = "CSV01",
            startDate = 1000L,
            endDate = 2000L
        )
        val member = TripMemberEntity(
            id = "m-csv",
            tripId = "t-csv",
            userId = "u-csv",
            name = "@HackerName",
            role = "ADMIN",
            bankName = "+MBBank",
            bankAccount = "=1+1",
            bankAccountHolder = "HACKER"
        )
        val expense = ExpenseEntity(
            id = "e-csv",
            tripId = "t-csv",
            title = "=CMD|' /C calc'!A0",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = "m-csv",
            totalAmount = 100000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 100000L,
            splitType = "EQUAL",
            note = "Ghi chú có dấu \"ngoặc kép\" và\nxuống dòng",
            timestamp = 1500L,
            createdMemberId = "m-csv"
        )
        val split = ExpenseSplitEntity(
            id = "s-csv",
            expenseId = "e-csv",
            tripId = "t-csv",
            memberId = "m-csv",
            amount = 100000L
        )

        val summary = com.example.domain.model.FinancialSummary(
            totalExpenses = 100000L,
            personalPaidExpenses = 100000L,
            fundPaidExpenses = 0L,
            totalFundCollected = 0L,
            remainingFund = 0L,
            isBalanced = true,
            balanceDiscrepancy = 0L
        )

        val status = com.example.domain.model.MemberFinancialStatus(
            member = member,
            totalPaid = 100000L,
            outOfPocketPaid = 100000L,
            fundContributed = 0L,
            totalOwed = 100000L,
            balance = 0L,
            status = com.example.domain.model.BalanceStatus.BALANCED
        )

        val csv = com.example.domain.export.ReportGenerator.generateCsvReport(
            trip = trip,
            members = listOf(member),
            summary = summary,
            statuses = listOf(status),
            settlementTransfers = emptyList(),
            expenses = listOf(expense),
            funds = emptyList(),
            splits = listOf(split)
        )

        // 1. Phải có UTF-8 BOM
        assertTrue("CSV phải có tiền tố UTF-8 BOM", csv.startsWith("\uFEFF"))

        // 2. Không có ô nào bắt đầu thô bằng ký tự công thức mà không được vô hiệu hóa bằng dấu nháy đơn
        assertFalse("Tên đoàn chứa công thức phải bị vô hiệu hóa", csv.contains(",\"=HYPERLINK"))
        assertTrue("Tên đoàn phải có tiền tố dấu nháy đơn", csv.contains(",\"'=HYPERLINK"))

        assertFalse("Tên thành viên chứa @ phải bị vô hiệu hóa", csv.contains(",\"@HackerName\""))
        assertTrue("Tên thành viên phải có dấu nháy đơn", csv.contains(",\"'@HackerName\""))

        assertFalse("Tên khoản chi chứa =CMD phải bị vô hiệu hóa", csv.contains(",\"=CMD"))
        assertTrue("Tên khoản chi phải có dấu nháy đơn", csv.contains(",\"'=CMD"))

        assertFalse("Tài khoản ngân hàng chứa =1+1 phải bị vô hiệu hóa", csv.contains(",\"=1+1\""))
        assertTrue("Tài khoản ngân hàng phải có dấu nháy đơn", csv.contains(",\"'=1+1\""))

        // 3. Dấu ngoặc kép trong ghi chú phải được nhân đôi và xuống dòng được làm phẳng
        assertTrue("Dấu ngoặc kép phải được nhân đôi", csv.contains("\"\"ngoặc kép\"\""))
        assertFalse("Không được có xuống dòng thô phá vỡ dòng CSV trong ô", csv.contains("có dấu \"\"ngoặc kép\"\" và\nxuống dòng"))
    }

    @Test
    fun `test database trigger and repository block expense insertions on settled trip`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Đã Khóa Sổ",
            description = "Test trigger",
            joinCode = "LOCK-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Lock",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val admin = repository.getMembers(tripId).first().first()

        // Thêm 1 khoản chi hợp lệ và chia tiền cân bằng
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = "Ăn trưa",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            createdMemberId = admin.id
        )
        val split = ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, admin.id, 200000L)
        repository.addExpenseWithSplits(expense, listOf(split), admin)

        // Thực hiện khóa sổ
        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        val (summary, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin),
            expenses = listOf(expense),
            splits = listOf(split),
            fundContributions = emptyList()
        )
        val snapshot = repository.finalizeSettlement(
            trip = trip,
            snapshotTitle = "Quyết toán đợt 1",
            summary = summary,
            settlementJson = "",
            actor = admin
        )
        assertNotNull(snapshot)

        // 1. Phép thử Repository: Cố tình thêm khoản chi mới khi đoàn đã khóa sổ -> Bị chặn
        val newExpense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Cố tình thêm khi đã khóa",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 50000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 50000L,
            splitType = "EQUAL",
            createdMemberId = admin.id
        )
        val newSplit = ExpenseSplitEntity(UUID.randomUUID().toString(), newExpense.id, tripId, admin.id, 50000L)

        val repEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.addExpenseWithSplits(newExpense, listOf(newSplit), admin)
            }
        }
        assertTrue(repEx.message?.contains("đã được khóa sổ quyết toán") == true)

        // 2. Phép thử Cơ sở dữ liệu: Gọi trực tiếp DAO insert bỏ qua tầng ViewModel/Repository
        // Trigger SQLite (trg_prevent_expense_insert_when_settled) phải ABORT với lỗi SETTLEMENT LOCKED
        val dbEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.expenseDao().insertExpense(newExpense)
            }
        }
        assertTrue("SQLite Trigger cấp cơ sở dữ liệu phải chặn đứng chèn chi tiêu", dbEx.message?.contains("SETTLEMENT LOCKED") == true)
    }

    @Test
    fun `test finalizeSettlement optimistic locking prevents concurrent overwrite`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Đồng Thời",
            description = "Test Optimistic Lock",
            joinCode = "CONCUR-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Đồng Thời",
            adminBankName = null,
            adminBankAccount = null,
            adminBankHolder = null
        )
        val admin = repository.getMembers(tripId).first().first()

        val staleTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertEquals(1L, staleTrip.version)

        // Giả lập một tác vụ khác đã cập nhật version chuyến đi lên 2
        val updatedTrip = staleTrip.copy(version = 2L, title = "Tên đoàn đã bị sửa đổi")
        db.tripDao().updateTrip(updatedTrip)

        // Phép thử: Dùng TripEntity cũ từ UI (v=1) để khóa sổ -> Phải ném ConcurrentModificationException
        val summary = com.example.domain.model.FinancialSummary(
            totalExpenses = 0L,
            personalPaidExpenses = 0L,
            fundPaidExpenses = 0L,
            totalFundCollected = 0L,
            remainingFund = 0L,
            isBalanced = true,
            balanceDiscrepancy = 0L,
            memberCount = 1
        )

        val ex = assertThrows(java.util.ConcurrentModificationException::class.java) {
            runBlocking {
                repository.finalizeSettlement(
                    trip = staleTrip, // version 1 cũ
                    snapshotTitle = "Quyết toán đồng thời",
                    summary = summary,
                    settlementJson = "",
                    actor = admin
                )
            }
        }
        assertTrue(ex.message?.contains("thay đổi đồng thời") == true)
    }

    @Test
    fun `test finalizeSettlement rejects empty trip and produces structured JSON`(): Unit = runBlocking {
        // Tạo đoàn trống không có thành viên
        val emptyTrip = TripEntity(
            id = "trip-empty",
            title = "Đoàn Trống",
            description = "Không có ai",
            joinCode = "EMPTY-01",
            startDate = 1000L,
            endDate = 2000L
        )
        db.tripDao().insertTrip(emptyTrip)

        val fakeAdmin = TripMemberEntity("m-fake", "trip-empty", "u-fake", "Fake Admin", "ADMIN")
        val summary = com.example.domain.model.FinancialSummary(isBalanced = true, balanceDiscrepancy = 0L)

        // 1. Khóa sổ đoàn không có thành viên -> Bị từ chối
        val emptyEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.finalizeSettlement(emptyTrip, "Khóa đoàn trống", summary, "", fakeAdmin)
            }
        }
        assertTrue(emptyEx.message?.contains("không có thành viên nào") == true)

        // 2. Thêm thành viên nhưng có chênh lệch đối soát -> Bị từ chối
        db.tripMemberDao().insertMember(fakeAdmin)
        val expense = ExpenseEntity(
            id = "exp-unbalanced",
            tripId = "trip-empty",
            title = "Chi chưa phân bổ hết",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = fakeAdmin.id,
            totalAmount = 100000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 100000L,
            splitType = "EQUAL",
            createdMemberId = fakeAdmin.id
        )
        // Split chỉ có 90.000 đ (lệch 10.000 đ)
        val split = ExpenseSplitEntity("s-unbal", expense.id, "trip-empty", fakeAdmin.id, 90000L)
        db.expenseDao().insertExpense(expense)
        db.expenseDao().insertSplits(listOf(split))

        val unbalEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                val freshTrip = db.tripDao().getTripByIdOnce("trip-empty")!!
                repository.finalizeSettlement(freshTrip, "Khóa lệch", summary, "", fakeAdmin)
            }
        }
        assertTrue(unbalEx.message?.contains("chênh lệch đối soát") == true)

        // 3. Sửa split đủ 100.000 đ (chênh lệch = 0) -> Khóa sổ thành công và sinh JSON cấu trúc chuẩn
        db.expenseDao().updateExpenseWithSplits(expense, listOf(split.copy(amount = 100000L)))
        val readyTrip = db.tripDao().getTripByIdOnce("trip-empty")!!
        val snapshot = repository.finalizeSettlement(readyTrip, "Khóa chuẩn", summary, "", fakeAdmin)

        assertNotNull(snapshot)
        val jsonRoot = org.json.JSONObject(snapshot.settlementJson)
        assertTrue(jsonRoot.has("summary"))
        assertTrue(jsonRoot.has("members"))
        assertEquals("trip-empty", jsonRoot.getString("tripId"))
        assertEquals(100000L, jsonRoot.getJSONObject("summary").getLong("totalExpenses"))
        assertEquals(0L, jsonRoot.getJSONObject("summary").getLong("balanceDiscrepancy"))
    }

    @Test
    fun `test SettlementEngine enforces absolute zero discrepancy and rejects small 1-5 VND leaks`() {
        val member = TripMemberEntity("m1", "t1", "u1", "Nguyen Van A", "ADMIN")
        val status = com.example.domain.model.MemberFinancialStatus(
            member = member,
            totalPaid = 100005L,
            outOfPocketPaid = 100005L,
            fundContributed = 0L,
            totalOwed = 100000L,
            balance = 5L, // Lệch 5 VND (trước đây <= 5 VND bị cho qua)
            status = com.example.domain.model.BalanceStatus.RECEIVE
        )

        // 1. Lệch 5 VND với quỹ = 0 -> discrepancy = 5 -> Phải bị từ chối với lỗi đối soát
        val res5 = com.example.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = listOf(status),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNotNull("Dung sai 5 VND không được phép bỏ qua", res5.reconciliationError)
        assertTrue(res5.transfers.isEmpty())
        assertTrue(res5.reconciliationError?.contains("chênh lệch đối soát") == true)

        // 2. Đoàn không có thành viên nào -> Phải bị từ chối
        val resEmpty = com.example.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = emptyList(),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNotNull(resEmpty.reconciliationError)
        assertTrue(resEmpty.reconciliationError?.contains("không có thành viên nào") == true)

        // 3. Cân bằng tuyệt đối (balance = 0) -> Thành công
        val balancedStatus = status.copy(totalPaid = 100000L, balance = 0L, status = com.example.domain.model.BalanceStatus.BALANCED)
        val resBalanced = com.example.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = listOf(balancedStatus),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNull(resBalanced.reconciliationError)
    }

    @Test
    fun `test Database triggers block tampering of trips and funds when settled`(): Unit = runBlocking {
        val trip = TripEntity(
            id = "trip-lock-test",
            title = "Đoàn Đã Khóa",
            description = "Test trigger",
            joinCode = "LOCK-01",
            startDate = 1000L,
            endDate = 2000L,
            isSettled = true,
            settledAt = 3000L
        )
        db.tripDao().insertTrip(trip)

        // 1. Thử xóa chuyến đi đã khóa sổ -> Trigger DB phải chặn đứng
        val delTripEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().deleteTripById("trip-lock-test")
            }
        }
        assertTrue(delTripEx.message?.contains("SETTLEMENT LOCKED") == true)

        // 2. Thử sửa tiêu đề chuyến đi đã khóa sổ mà không mở sổ -> Trigger DB phải chặn đứng
        val updateTripEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().updateTrip(trip.copy(title = "Tên bị giả mạo"))
            }
        }
        assertTrue(updateTripEx.message?.contains("SETTLEMENT LOCKED") == true)
    }
}

