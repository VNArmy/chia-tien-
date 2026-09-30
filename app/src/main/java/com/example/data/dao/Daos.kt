package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    suspend fun getAllTripsOnce(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun getTripById(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripByIdOnce(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE joinCode = :code LIMIT 1")
    suspend fun getTripByJoinCode(code: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("UPDATE trips SET isSettled = 1, settledAt = :settledAt, version = version + 1 WHERE id = :tripId AND version = :expectedVersion AND isSettled = 0")
    suspend fun lockSettlementWithOptimisticLock(tripId: String, expectedVersion: Long, settledAt: Long): Int

    @Query("UPDATE trips SET isSettled = 0, settledAt = NULL, version = version + 1 WHERE id = :tripId AND version = :expectedVersion AND isSettled = 1")
    suspend fun reopenSettlementWithOptimisticLock(tripId: String, expectedVersion: Long): Int

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)
}

@Dao
interface TripMemberDao {
    @Query("SELECT * FROM trip_members ORDER BY joinedAt ASC")
    suspend fun getAllMembersOnce(): List<TripMemberEntity>

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId ORDER BY joinedAt ASC")
    fun getMembersByTrip(tripId: String): Flow<List<TripMemberEntity>>

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId ORDER BY joinedAt ASC")
    suspend fun getMembersByTripOnce(tripId: String): List<TripMemberEntity>

    @Query("SELECT * FROM trip_members WHERE id = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: String): TripMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: TripMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<TripMemberEntity>)

    @Update
    suspend fun updateMember(member: TripMemberEntity)

    @Query("UPDATE trip_members SET isActive = :isActive WHERE id = :memberId")
    suspend fun setMemberActiveStatus(memberId: String, isActive: Boolean)

    @Query("DELETE FROM trip_members WHERE id = :memberId")
    suspend fun deleteMember(memberId: String)

    @Query("DELETE FROM trip_members WHERE tripId = :tripId")
    suspend fun deleteMembersByTrip(tripId: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAllExpensesOnce(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun getExpensesByTrip(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY timestamp DESC")
    suspend fun getExpensesByTripOnce(tripId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    @Query("DELETE FROM expenses WHERE tripId = :tripId")
    suspend fun deleteExpensesByTrip(tripId: String)

    // Splits
    @Query("SELECT * FROM expense_splits")
    suspend fun getAllSplitsOnce(): List<ExpenseSplitEntity>

    @Query("SELECT * FROM expense_splits WHERE tripId = :tripId")
    fun getAllSplitsByTrip(tripId: String): Flow<List<ExpenseSplitEntity>>

    @Query("SELECT * FROM expense_splits WHERE tripId = :tripId")
    suspend fun getAllSplitsByTripOnce(tripId: String): List<ExpenseSplitEntity>

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    fun getSplitsByExpense(expenseId: String): Flow<List<ExpenseSplitEntity>>

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getSplitsByExpenseOnce(expenseId: String): List<ExpenseSplitEntity>

    @Query("SELECT * FROM expense_splits WHERE id = :splitId LIMIT 1")
    suspend fun getSplitById(splitId: String): ExpenseSplitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsByExpense(expenseId: String)

    @Query("DELETE FROM expense_splits WHERE tripId = :tripId")
    suspend fun deleteSplitsByTrip(tripId: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE payerMemberId = :memberId")
    suspend fun countExpensesByPayer(memberId: String): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE createdMemberId = :memberId")
    suspend fun countExpensesCreatedByMember(memberId: String): Int

    @Query("SELECT COUNT(*) FROM expense_splits WHERE memberId = :memberId")
    suspend fun countSplitsByMember(memberId: String): Int

    @Transaction
    suspend fun insertExpenseWithSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>) {
        insertExpense(expense)
        insertSplits(splits)
    }

    @Transaction
    suspend fun updateExpenseWithSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>) {
        updateExpense(expense)
        deleteSplitsByExpense(expense.id)
        insertSplits(splits)
    }

    @Transaction
    suspend fun deleteExpenseWithSplits(expenseId: String) {
        deleteSplitsByExpense(expenseId)
        deleteExpenseById(expenseId)
    }
}

@Dao
interface FundDao {
    @Query("SELECT * FROM fund_contributions ORDER BY timestamp DESC")
    suspend fun getAllFundsOnce(): List<FundContributionEntity>

    @Query("SELECT * FROM fund_contributions WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun getFundContributions(tripId: String): Flow<List<FundContributionEntity>>

    @Query("SELECT * FROM fund_contributions WHERE tripId = :tripId ORDER BY timestamp DESC")
    suspend fun getFundContributionsOnce(tripId: String): List<FundContributionEntity>

    @Query("SELECT * FROM fund_contributions WHERE id = :fundId LIMIT 1")
    suspend fun getFundById(fundId: String): FundContributionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFundContribution(contribution: FundContributionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFundContributions(contributions: List<FundContributionEntity>)

    @Query("DELETE FROM fund_contributions WHERE id = :id")
    suspend fun deleteFundContribution(id: String)

    @Query("DELETE FROM fund_contributions WHERE tripId = :tripId")
    suspend fun deleteFundsByTrip(tripId: String)

    @Query("SELECT COUNT(*) FROM fund_contributions WHERE memberId = :memberId")
    suspend fun countFundContributionsByMember(memberId: String): Int
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates")
    suspend fun getAllExchangeRatesOnce(): List<ExchangeRateEntity>

    @Query("SELECT * FROM exchange_rates WHERE tripId = :tripId")
    fun getExchangeRates(tripId: String): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates WHERE tripId = :tripId")
    suspend fun getExchangeRatesOnce(tripId: String): List<ExchangeRateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRates(rates: List<ExchangeRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchangeRate(rate: ExchangeRateEntity)

    @Query("DELETE FROM exchange_rates WHERE tripId = :tripId")
    suspend fun deleteExchangeRatesByTrip(tripId: String)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlement_snapshots")
    suspend fun getAllSnapshotsOnce(): List<SettlementSnapshotEntity>

    @Query("SELECT * FROM settlement_snapshots WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun getSnapshotsByTrip(tripId: String): Flow<List<SettlementSnapshotEntity>>

    @Query("SELECT * FROM settlement_snapshots WHERE id = :snapshotId LIMIT 1")
    suspend fun getSnapshotById(snapshotId: String): SettlementSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SettlementSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<SettlementSnapshotEntity>)

    @Query("DELETE FROM settlement_snapshots WHERE id = :snapshotId")
    suspend fun deleteSnapshot(snapshotId: String)

    @Query("DELETE FROM settlement_snapshots WHERE tripId = :tripId")
    suspend fun deleteSnapshotsByTrip(tripId: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAllAuditLogsOnce(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE tripId = :tripId ORDER BY timestamp DESC")
    fun getAuditLogsByTrip(tripId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE id = :logId LIMIT 1")
    suspend fun getLogById(logId: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs WHERE tripId = :tripId")
    suspend fun deleteAuditLogsByTrip(tripId: String)
}
