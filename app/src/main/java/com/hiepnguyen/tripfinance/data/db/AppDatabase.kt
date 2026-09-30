package com.hiepnguyen.tripfinance.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hiepnguyen.tripfinance.data.dao.*
import com.hiepnguyen.tripfinance.data.entity.*
import com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        TripEntity::class,
        TripMemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        FundContributionEntity::class,
        ExchangeRateEntity::class,
        SettlementSnapshotEntity::class,
        AuditLogEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripMemberDao(): TripMemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun fundDao(): FundDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun settlementDao(): SettlementDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tạo bảng settlement_snapshots khớp 100% với Room schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `settlement_snapshots` (
                        `id` TEXT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `snapshotTitle` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `totalExpenses` INTEGER NOT NULL,
                        `totalFundCollected` INTEGER NOT NULL,
                        `totalFundSpent` INTEGER NOT NULL,
                        `remainingFund` INTEGER NOT NULL,
                        `settlementJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tái tạo bảng expenses với cột totalAmount dạng REAL hỗ trợ số thập phân (VD: 12.50 USD)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expenses_new` (
                        `id` TEXT NOT NULL,
                        `tripId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `payerType` TEXT NOT NULL,
                        `payerMemberId` TEXT,
                        `totalAmount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `exchangeRate` REAL NOT NULL,
                        `convertedTotalAmount` INTEGER NOT NULL,
                        `splitType` TEXT NOT NULL,
                        `receiptImageUri` TEXT,
                        `note` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `createdMemberId` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `expenses_new` (
                        `id`, `tripId`, `title`, `category`, `payerType`, `payerMemberId`,
                        `totalAmount`, `currency`, `exchangeRate`, `convertedTotalAmount`,
                        `splitType`, `receiptImageUri`, `note`, `timestamp`, `createdMemberId`,
                        `updatedAt`, `isSynced`
                    )
                    SELECT
                        `id`, `tripId`, `title`, `category`, `payerType`, `payerMemberId`,
                        CAST(`totalAmount` AS REAL), `currency`, `exchangeRate`, `convertedTotalAmount`,
                        `splitType`, `receiptImageUri`, `note`, `timestamp`, `createdMemberId`,
                        `updatedAt`, `isSynced`
                    FROM `expenses`
                """.trimIndent())

                db.execSQL("DROP TABLE `expenses`")
                db.execSQL("ALTER TABLE `expenses_new` RENAME TO `expenses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")

                // Đảm bảo đầy đủ tất cả index của schema v4
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_rates_tripId` ON `exchange_rates` (`tripId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_tripId_currencyCode` ON `exchange_rates` (`tripId`, `currencyCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")

                // Bổ sung cột percentage cho bảng expense_splits nếu chưa có
                try {
                    db.execSQL("ALTER TABLE `expense_splits` ADD COLUMN `percentage` REAL")
                } catch (e: Exception) {
                    // Đã có cột percentage
                }

                // Tự động kiểm tra và chuyển đổi nếu bảng settlement_snapshots chứa cột cũ từ bản beta
                try {
                    val cursor = db.query("PRAGMA table_info(`settlement_snapshots`)")
                    var hasSnapshotDataJson = false
                    while (cursor.moveToNext()) {
                        val colName = cursor.getString(1)
                        if (colName == "snapshotDataJson") {
                            hasSnapshotDataJson = true
                            break
                        }
                    }
                    cursor.close()
                    if (hasSnapshotDataJson) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `settlement_snapshots_v4` (
                                `id` TEXT NOT NULL,
                                `tripId` TEXT NOT NULL,
                                `snapshotTitle` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `totalExpenses` INTEGER NOT NULL,
                                `totalFundCollected` INTEGER NOT NULL,
                                `totalFundSpent` INTEGER NOT NULL,
                                `remainingFund` INTEGER NOT NULL,
                                `settlementJson` TEXT NOT NULL,
                                PRIMARY KEY(`id`),
                                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `settlement_snapshots_v4` (
                                `id`, `tripId`, `snapshotTitle`, `createdAt`, `totalExpenses`,
                                `totalFundCollected`, `totalFundSpent`, `remainingFund`, `settlementJson`
                            )
                            SELECT `id`, `tripId`, 'Bản quyết toán đã lưu', `createdAt`, `totalExpenses`,
                                   `totalFund`, `totalFund` - `remainingFund`, `remainingFund`, `snapshotDataJson`
                            FROM `settlement_snapshots`
                        """.trimIndent())
                        db.execSQL("DROP TABLE `settlement_snapshots`")
                        db.execSQL("ALTER TABLE `settlement_snapshots_v4` RENAME TO `settlement_snapshots`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "Legacy migration check on settlement_snapshots: ${e.message}")
                }
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        @Synchronized
        fun resetDatabase(context: Context) {
            try {
                INSTANCE?.close()
            } catch (e: Throwable) {
                android.util.Log.w("AppDatabase", "Error closing database during reset: ${e.message}")
            }
            INSTANCE = null
            try {
                context.deleteDatabase("trip_finance_database")
                val dbFile = context.getDatabasePath("trip_finance_database")
                java.io.File(dbFile.path + "-wal").delete()
                java.io.File(dbFile.path + "-shm").delete()
                java.io.File(dbFile.path + "-journal").delete()
                dbFile.delete()
            } catch (e: Throwable) {
                android.util.Log.w("AppDatabase", "Error deleting database files: ${e.message}")
            }
            com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.clearDatabaseCredentials(context)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            val passphrase = com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.getOrCreateDatabasePassphrase(context)
            val isTest = com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.isTestEnvironment()
            val sqlCipherFactory = try {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (loadErr: Throwable) {
                    if (!isTest) {
                        android.util.Log.w("AppDatabase", "Explicit System.loadLibrary(sqlcipher) note: ${loadErr.message}")
                    }
                }
                SupportOpenHelperFactory(passphrase)
            } catch (e: Throwable) {
                if (isTest) {
                    android.util.Log.w("AppDatabase", "SQLCipher native libraries not available in test runtime (JVM): ${e.message}")
                    null
                } else {
                    // FAIL-CLOSED ARCHITECTURE: Tuyệt đối không mở CSDL không mã hóa trên thiết bị
                    throw com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException(
                        "Không thể nạp thư viện mã hóa SQLCipher trên thiết bị. Để bảo vệ dữ liệu tài chính, ứng dụng dừng khởi tạo CSDL: ${e.message}",
                        e,
                        com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED
                    )
                }
            }

            // Kiểm tra kép: Trên thiết bị Android thật, bắt buộc phải có SupportOpenHelperFactory của SQLCipher
            if (sqlCipherFactory == null && !isTest) {
                throw com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException(
                    "Lỗi bảo mật: SQLCipher SupportOpenHelperFactory bị rỗng. Dừng khởi tạo để tránh tạo DB không mã hóa (Fail-Closed).",
                    null,
                    com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED
                )
            }

            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "trip_finance_database"
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4)

            if (sqlCipherFactory != null) {
                builder.openHelperFactory(sqlCipherFactory)
            }

            return builder
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON;")
                            createDatabaseCheckConstraints(db)
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Warning on database open constraints", e)
                        }
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON;")
                            createDatabaseCheckConstraints(db)
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Warning on database create constraints", e)
                        }
                    }
                })
                .build()
        }

        fun createDatabaseCheckConstraints(db: SupportSQLiteDatabase) {
            // 1. RÀNG BUỘC CƠ SỞ DỮ LIỆU: CHẶN ĐỨNG THAO TÁC XÓA THÀNH VIÊN ĐÃ CÓ PHÁT SINH TÀI CHÍNH / CHI TIÊU
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_delete_member_with_financials
                BEFORE DELETE ON trip_members
                BEGIN
                    SELECT CASE
                        WHEN (SELECT COUNT(*) FROM expenses WHERE payerMemberId = OLD.id OR createdMemberId = OLD.id) > 0
                            THEN RAISE(ABORT, 'CHECK CONSTRAINT VIOLATION: Không thể xóa thành viên đã có phát sinh chi tiêu (người chi hoặc người tạo khoản chi). Hãy chuyển sang trạng thái Vô hiệu hoá (Deactivate) để bảo toàn chứng từ kế toán.')
                        WHEN (SELECT COUNT(*) FROM expense_splits WHERE memberId = OLD.id) > 0
                            THEN RAISE(ABORT, 'CHECK CONSTRAINT VIOLATION: Không thể xóa thành viên đang có dữ liệu phân bổ chia tiền. Hãy chuyển sang trạng thái Vô hiệu hoá (Deactivate) để bảo toàn chứng từ kế toán.')
                        WHEN (SELECT COUNT(*) FROM fund_contributions WHERE memberId = OLD.id OR recordedByMemberId = OLD.id) > 0
                            THEN RAISE(ABORT, 'CHECK CONSTRAINT VIOLATION: Không thể xóa thành viên đã có phát sinh nộp hoặc thu quỹ chung. Hãy chuyển sang trạng thái Vô hiệu hoá (Deactivate).')
                    END;
                END;
            """.trimIndent())

            // 2. RÀNG BUỘC KIỂM TRA SỐ TIỀN CHI TIÊU KHÔNG ĐƯỢC ÂM VÀ TỶ GIÁ DƯƠNG
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_expense_insert
                BEFORE INSERT ON expenses
                BEGIN
                    SELECT CASE
                        WHEN NEW.totalAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: totalAmount cannot be negative')
                        WHEN NEW.convertedTotalAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: convertedTotalAmount cannot be negative')
                        WHEN NEW.exchangeRate <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: exchangeRate must be positive')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_expense_update
                BEFORE UPDATE ON expenses
                BEGIN
                    SELECT CASE
                        WHEN NEW.totalAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: totalAmount cannot be negative')
                        WHEN NEW.convertedTotalAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: convertedTotalAmount cannot be negative')
                        WHEN NEW.exchangeRate <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: exchangeRate must be positive')
                    END;
                END;
            """.trimIndent())

            // 3. RÀNG BUỘC PHÂN BỔ CHI TIÊU KHÔNG ĐƯỢC ÂM
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_split_insert
                BEFORE INSERT ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN NEW.amount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: split amount cannot be negative')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_split_update
                BEFORE UPDATE ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN NEW.amount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: split amount cannot be negative')
                    END;
                END;
            """.trimIndent())

            // 4. RÀNG BUỘC TIỀN ĐÓNG QUÝ KHÔNG ĐƯỢC ÂM
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_fund_insert
                BEFORE INSERT ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN NEW.amount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund amount cannot be negative')
                        WHEN NEW.convertedAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund convertedAmount cannot be negative')
                        WHEN NEW.exchangeRate <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund exchangeRate must be positive')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_fund_update
                BEFORE UPDATE ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN NEW.amount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund amount cannot be negative')
                        WHEN NEW.convertedAmount < 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund convertedAmount cannot be negative')
                        WHEN NEW.exchangeRate <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: fund exchangeRate must be positive')
                    END;
                END;
            """.trimIndent())

            // 5. RÀNG BUỘC TỶ GIÁ NGOẠI TỆ PHẢI DƯƠNG
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_rate_insert
                BEFORE INSERT ON exchange_rates
                BEGIN
                    SELECT CASE
                        WHEN NEW.rateToBase <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: exchange rate must be positive')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_rate_update
                BEFORE UPDATE ON exchange_rates
                BEGIN
                    SELECT CASE
                        WHEN NEW.rateToBase <= 0 THEN RAISE(ABORT, 'CHECK CONSTRAINT FAILED: exchange rate must be positive')
                    END;
                END;
            """.trimIndent())

            // 6. RÀNG BUỘC BẢO VỆ NIÊM PHONG KHÓA SỔ TẠI CƠ SỞ DỮ LIỆU (DATABASE LEVEL LOCKING):
            // Ngăn chặn tuyệt đối mọi hành vi chèn, sửa, xóa chi tiêu, phân bổ, quỹ, thành viên, tỷ giá
            // trên các chuyến đi đã khóa sổ (isSettled = 1), phòng ngừa triệt để lỗi TOCTOU / Race Condition.
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_expense_insert_when_settled
                BEFORE INSERT ON expenses
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = NEW.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thêm khoản chi mới!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_expense_update_when_settled
                BEFORE UPDATE ON expenses
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể sửa khoản chi!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_expense_delete_when_settled
                BEFORE DELETE ON expenses
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa khoản chi!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_split_insert_when_settled
                BEFORE INSERT ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = NEW.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thêm phân bổ chi phí!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_split_update_when_settled
                BEFORE UPDATE ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể sửa phân bổ chi phí!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_split_delete_when_settled
                BEFORE DELETE ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa phân bổ chi phí!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_fund_insert_when_settled
                BEFORE INSERT ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = NEW.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể nộp quỹ mới!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_fund_delete_when_settled
                BEFORE DELETE ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa khoản nộp quỹ!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_fund_update_when_settled
                BEFORE UPDATE ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể sửa khoản nộp quỹ!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_member_insert_when_settled
                BEFORE INSERT ON trip_members
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = NEW.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thêm thành viên mới!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_member_update_when_settled
                BEFORE UPDATE ON trip_members
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1 AND (OLD.isActive != NEW.isActive OR OLD.role != NEW.role)
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thay đổi trạng thái hoặc vai trò thành viên!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_member_delete_when_settled
                BEFORE DELETE ON trip_members
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa thành viên!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_rate_insert_when_settled
                BEFORE INSERT ON exchange_rates
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = NEW.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thêm tỷ giá mới!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_rate_update_when_settled
                BEFORE UPDATE ON exchange_rates
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể thay đổi tỷ giá ngoại tệ!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_rate_delete_when_settled
                BEFORE DELETE ON exchange_rates
                BEGIN
                    SELECT CASE
                        WHEN (SELECT isSettled FROM trips WHERE id = OLD.tripId) = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa tỷ giá ngoại tệ!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_trip_delete_when_settled
                BEFORE DELETE ON trips
                BEGIN
                    SELECT CASE
                        WHEN OLD.isSettled = 1
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể xóa chuyến đi!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_prevent_trip_tamper_when_settled
                BEFORE UPDATE ON trips
                BEGIN
                    SELECT CASE
                        WHEN OLD.isSettled = 1 AND NEW.isSettled = 1 AND (
                            OLD.title != NEW.title OR
                            OLD.baseCurrency != NEW.baseCurrency OR
                            OLD.startDate != NEW.startDate OR
                            OLD.endDate != NEW.endDate OR
                            OLD.joinCode != NEW.joinCode
                        )
                            THEN RAISE(ABORT, 'SETTLEMENT LOCKED: Chuyến đi đã khóa sổ quyết toán. Không thể sửa đổi thông tin chuyến đi!')
                    END;
                END;
            """.trimIndent())

            // 7. RÀNG BUỘC TOÀN VẸN THÀNH VIÊN THEO ĐOÀN (CROSS-TRIP MEMBER INTEGRITY):
            // Ngăn chặn người chi, người tạo khoản chi, người nộp quỹ hoặc người chịu chi trong splits
            // thuộc chuyến đi khác được gán vào chi tiêu/quỹ của chuyến đi này.
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_expense_members_belong_to_same_trip_insert
                BEFORE INSERT ON expenses
                BEGIN
                    SELECT CASE
                        WHEN NEW.payerType = 'MEMBER' AND NEW.payerMemberId IS NOT NULL AND (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.payerMemberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người chi trả không thuộc danh sách thành viên của đoàn này!')
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.createdMemberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người tạo khoản chi không thuộc danh sách thành viên của đoàn này!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_expense_members_belong_to_same_trip_update
                BEFORE UPDATE ON expenses
                BEGIN
                    SELECT CASE
                        WHEN NEW.payerType = 'MEMBER' AND NEW.payerMemberId IS NOT NULL AND (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.payerMemberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người chi trả không thuộc danh sách thành viên của đoàn này!')
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.createdMemberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người tạo khoản chi không thuộc danh sách thành viên của đoàn này!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_split_member_belongs_to_same_trip_insert
                BEFORE INSERT ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.memberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người chịu chi trong phân bổ không thuộc danh sách thành viên của đoàn này!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_split_member_belongs_to_same_trip_update
                BEFORE UPDATE ON expense_splits
                BEGIN
                    SELECT CASE
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.memberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người chịu chi trong phân bổ không thuộc danh sách thành viên của đoàn này!')
                    END;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS trg_check_fund_member_belongs_to_same_trip_insert
                BEFORE INSERT ON fund_contributions
                BEGIN
                    SELECT CASE
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.memberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người nộp quỹ không thuộc danh sách thành viên của đoàn này!')
                        WHEN (
                            SELECT COUNT(*) FROM trip_members WHERE id = NEW.recordedByMemberId AND tripId = NEW.tripId
                        ) = 0
                            THEN RAISE(ABORT, 'VALIDATION FAILED: Người ghi nhận nộp quỹ không thuộc danh sách thành viên của đoàn này!')
                    END;
                END;
            """.trimIndent())
        }
    }
}
