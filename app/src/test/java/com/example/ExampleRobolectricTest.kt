package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.entity.*
import com.example.data.repository.TripFinanceRepository
import com.example.domain.model.DefaultExchangeRates
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
            .build()
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
}
