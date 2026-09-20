package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["joinCode"], unique = true)
    ]
)
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val joinCode: String,
    val startDate: Long,
    val endDate: Long,
    val baseCurrency: String = "VND",
    val isSettled: Boolean = false,
    val settledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Long = 1L
) {
    init {
        require(title.isNotBlank()) { "Trip title cannot be blank" }
        require(joinCode.isNotBlank()) { "Trip join code cannot be blank" }
    }
}

@Entity(
    tableName = "trip_members",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tripId", "name"])
    ]
)
data class TripMemberEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val userId: String,
    val name: String,
    val role: String, // ADMIN, TREASURER, MEMBER, VIEWER
    val isActive: Boolean = true,
    val bankName: String? = null,
    val bankAccount: String? = null,
    val bankAccountHolder: String? = null,
    val joinedAt: Long = System.currentTimeMillis()
) {
    init {
        require(name.isNotBlank()) { "Member name cannot be blank" }
        require(tripId.isNotBlank()) { "Trip ID cannot be blank" }
    }
}

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerMemberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TripMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdMemberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["payerMemberId"]),
        Index(value = ["createdMemberId"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val category: String, // FOOD, TRANSPORT, HOTEL, SIGHTSEEING, ENTERTAINMENT, SHOPPING, OTHER
    val payerType: String, // MEMBER, FUND
    val payerMemberId: String?, // null if FUND
    val totalAmount: Double, // in original currency >= 0 (supports decimal e.g. 12.50 USD or 500000 VND)
    val currency: String = "VND",
    val exchangeRate: Double = 1.0, // > 0
    val convertedTotalAmount: Long, // totalAmount * exchangeRate >= 0
    val splitType: String, // EQUAL, RATIO, CUSTOM_AMOUNT, CUSTOM_PARTICIPANT
    val receiptImageUri: String? = null,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createdMemberId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "Expense title cannot be blank" }
        require(totalAmount >= 0.0) { "Total expense amount cannot be negative ($totalAmount)" }
        require(exchangeRate > 0.0) { "Exchange rate must be greater than 0 ($exchangeRate)" }
        require(convertedTotalAmount >= 0) { "Converted total amount cannot be negative ($convertedTotalAmount)" }
    }
}

@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["expenseId"]),
        Index(value = ["tripId"]),
        Index(value = ["memberId"])
    ]
)
data class ExpenseSplitEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val tripId: String,
    val memberId: String,
    val amount: Long, // in base currency >= 0
    val percentage: Double? = null
) {
    init {
        require(amount >= 0) { "Split amount cannot be negative ($amount)" }
        if (percentage != null) {
            require(percentage >= 0.0) { "Split percentage cannot be negative ($percentage)" }
        }
    }
}

@Entity(
    tableName = "fund_contributions",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TripMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordedByMemberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["memberId"]),
        Index(value = ["recordedByMemberId"])
    ]
)
data class FundContributionEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val memberId: String,
    val amount: Long, // >= 0
    val currency: String = "VND",
    val exchangeRate: Double = 1.0, // > 0
    val convertedAmount: Long, // >= 0
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val recordedByMemberId: String
) {
    init {
        require(amount >= 0) { "Fund contribution amount cannot be negative ($amount)" }
        require(exchangeRate > 0.0) { "Exchange rate must be greater than 0 ($exchangeRate)" }
        require(convertedAmount >= 0) { "Converted fund amount cannot be negative ($convertedAmount)" }
    }
}

@Entity(
    tableName = "exchange_rates",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tripId", "currencyCode"], unique = true)
    ]
)
data class ExchangeRateEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val currencyCode: String,
    val rateToBase: Double, // e.g. 1 USD = 25450 VND (> 0)
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(currencyCode.isNotBlank()) { "Currency code cannot be blank" }
        require(rateToBase > 0.0) { "Exchange rate to base currency must be greater than 0 ($rateToBase)" }
    }
}

@Entity(
    tableName = "settlement_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"])
    ]
)
data class SettlementSnapshotEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val snapshotTitle: String,
    val createdAt: Long = System.currentTimeMillis(),
    val totalExpenses: Long, // >= 0
    val totalFundCollected: Long, // >= 0
    val totalFundSpent: Long, // >= 0
    val remainingFund: Long,
    val settlementJson: String // Full serialized audit snapshot
) {
    init {
        require(totalExpenses >= 0) { "Total expenses cannot be negative ($totalExpenses)" }
        require(totalFundCollected >= 0) { "Total fund collected cannot be negative ($totalFundCollected)" }
        require(totalFundSpent >= 0) { "Total fund spent cannot be negative ($totalFundSpent)" }
    }
}

@Entity(
    tableName = "audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val actorMemberId: String,
    val actorName: String,
    val action: String, // CREATE_EXPENSE, UPDATE_EXPENSE, DELETE_EXPENSE, CONTRIBUTE_FUND, SETTLE_TRIP, UPDATE_MEMBER, SYNC_DATA
    val description: String,
    val detailBefore: String? = null,
    val detailAfter: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
