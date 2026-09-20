package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.entity.*
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
                db.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `snapshotDataJson` TEXT NOT NULL, `settledAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `settledByMemberId` TEXT NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFund` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
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
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trip_finance_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Bật ràng buộc khóa ngoại chặt chẽ ở cấp độ SQLite engine
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON;")
                            createDatabaseCheckConstraints(db)
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Warning on database open constraints", e)
                        }
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Kích hoạt khóa ngoại & ràng buộc kiểm tra
                        try {
                            db.execSQL("PRAGMA foreign_keys = ON;")
                            createDatabaseCheckConstraints(db)
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Warning on database create constraints", e)
                        }
                        // Cài mới: Không nạp dữ liệu mẫu - cơ sở dữ liệu hoàn toàn sạch sẽ
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun createDatabaseCheckConstraints(db: SupportSQLiteDatabase) {
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
        }
    }
}
