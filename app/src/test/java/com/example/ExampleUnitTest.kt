package com.example

import com.example.data.entity.TripMemberEntity
import com.example.domain.engine.SettlementEngine
import com.example.domain.model.BalanceStatus
import com.example.domain.model.MemberFinancialStatus
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    private fun createMember(id: String, name: String, role: String = "MEMBER"): TripMemberEntity {
        return TripMemberEntity(
            id = id,
            tripId = "trip_test",
            userId = "user_$id",
            name = name,
            role = role,
            isActive = true,
            bankName = "Bank $name",
            bankAccount = "123456789",
            bankAccountHolder = name.uppercase()
        )
    }

    @Test
    fun `test settlement with remaining fund positive - user exact scenario`() {
        // User example:
        // Quỹ thu 2.000.000đ, chi 650.000đ, còn 1.350.000đ
        // Hiệp được nhận lại 1.883.000đ
        // Quang được nhận lại 501.000đ
        // Mai phải nộp 517.000đ
        // Lan phải nộp 517.000đ
        val hiep = createMember("member_hiep", "Nguyễn Hồng Hiệp", "ADMIN")
        val mai = createMember("member_mai", "Trần Tuyết Mai", "TREASURER")
        val quang = createMember("member_quang", "Lê Nhật Quang", "MEMBER")
        val lan = createMember("member_lan", "Phạm Hương Lan", "MEMBER")

        val statuses = listOf(
            MemberFinancialStatus(hiep, totalPaid = 2900000L, outOfPocketPaid = 2400000L, fundContributed = 500000L, totalOwed = 1017000L, balance = 1883000L, status = BalanceStatus.RECEIVE),
            MemberFinancialStatus(quang, totalPaid = 1518000L, outOfPocketPaid = 1018000L, fundContributed = 500000L, totalOwed = 1017000L, balance = 501000L, status = BalanceStatus.RECEIVE),
            MemberFinancialStatus(mai, totalPaid = 500000L, outOfPocketPaid = 0L, fundContributed = 500000L, totalOwed = 1017000L, balance = -517000L, status = BalanceStatus.PAY),
            MemberFinancialStatus(lan, totalPaid = 500000L, outOfPocketPaid = 0L, fundContributed = 500000L, totalOwed = 1017000L, balance = -517000L, status = BalanceStatus.PAY)
        )

        val remainingFund = 1350000L
        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "DN2026",
            remainingFund = remainingFund,
            fundHolder = mai
        )

        assertFalse("Transfers should not be empty when there is remaining fund and non-zero balances", transfers.isEmpty())

        // Verify total money received by Hiệp = 1,883,000 VND
        val hiepReceived = transfers.filter { it.toMember.id == "member_hiep" }.sumOf { it.amount }
        assertEquals(1883000L, hiepReceived)

        // Verify total money received by Quang = 501,000 VND
        val quangReceived = transfers.filter { it.toMember.id == "member_quang" }.sumOf { it.amount }
        assertEquals(501000L, quangReceived)

        // Verify total money paid by Mai = 517,000 VND
        val maiPaid = transfers.filter { it.fromMember.id == "member_mai" }.sumOf { it.amount }
        assertEquals(517000L, maiPaid)

        // Verify total money paid by Lan = 517,000 VND
        val lanPaid = transfers.filter { it.fromMember.id == "member_lan" }.sumOf { it.amount }
        assertEquals(517000L, lanPaid)

        // Verify total money disbursed from Fund = 1,350,000 VND
        val fundPaid = transfers.filter { it.fromMember.id == "FUND_ORGANIZATION" }.sumOf { it.amount }
        assertEquals(1350000L, fundPaid)

        // Total transfers amount
        val totalTransfers = transfers.sumOf { it.amount }
        assertEquals(2384000L, totalTransfers)
    }

    @Test
    fun `test settlement with overspent fund negative`() {
        // Quỹ bị chi vượt: remainingFund = -500,000 VND
        // Quỹ chi 1.500.000đ nhưng chỉ thu 1.000.000đ
        // Thành viên A chi 0, chịu 500k -> balance = -500k
        val memA = createMember("mem_a", "Thành viên A")
        val treasurer = createMember("treasurer", "Thủ quỹ", "TREASURER")

        val statuses = listOf(
            MemberFinancialStatus(memA, totalPaid = 0L, outOfPocketPaid = 0L, fundContributed = 0L, totalOwed = 500000L, balance = -500000L, status = BalanceStatus.PAY)
        )

        val remainingFund = -500000L
        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "TRIP1",
            remainingFund = remainingFund,
            fundHolder = treasurer
        )

        assertEquals(1, transfers.size)
        val transfer = transfers[0]
        assertEquals("mem_a", transfer.fromMember.id)
        assertEquals("FUND_ORGANIZATION", transfer.toMember.id)
        assertEquals(500000L, transfer.amount)
        assertTrue(transfer.transferNote.contains("nop bu thieu hut Quy doan"))
    }

    @Test
    fun `test settlement when all balanced and remaining fund zero`() {
        val hiep = createMember("member_hiep", "Hiệp")
        val statuses = listOf(
            MemberFinancialStatus(hiep, totalPaid = 100000L, outOfPocketPaid = 100000L, fundContributed = 0L, totalOwed = 100000L, balance = 0L, status = BalanceStatus.BALANCED)
        )

        val transfers = SettlementEngine.computeSimplifiedTransfers(
            memberStatuses = statuses,
            tripJoinCode = "TRIP0",
            remainingFund = 0L
        )

        assertTrue(transfers.isEmpty())
    }

    @Test
    fun `test SplitCalculator equal split remainder distribution`() {
        val members = listOf("user_1", "user_2", "user_3")
        // 100,000 VND / 3 = 33,333 per person with remainder 1 VND
        val splits = com.example.domain.engine.SplitCalculator.calculateEqualSplit(
            totalAmount = 100000L,
            memberIds = members,
            primaryMemberId = "user_2"
        )
        assertEquals(3, splits.size)
        val user1 = splits.find { it.first == "user_1" }?.second
        val user2 = splits.find { it.first == "user_2" }?.second
        val user3 = splits.find { it.first == "user_3" }?.second

        assertEquals(33333L, user1)
        assertEquals(33334L, user2) // primaryMemberId gets remainder 1 VND
        assertEquals(33333L, user3)
        assertEquals(100000L, splits.sumOf { it.second })
    }

    @Test
    fun `test SplitCalculator ratio split validation and exact rounding`() {
        // Test 1: Exactly 100%
        val ratiosValid = listOf("user_1" to 33.33, "user_2" to 33.33, "user_3" to 33.34)
        val (splitsValid, isValid) = com.example.domain.engine.SplitCalculator.calculateRatioSplit(
            totalAmount = 100000L,
            memberRatios = ratiosValid
        )
        assertTrue("Total 100% ratio should be valid", isValid)
        assertEquals(100000L, splitsValid.sumOf { it.second })

        // Test 2: Incomplete ratio (< 100%)
        val ratiosIncomplete = listOf("user_1" to 50.0, "user_2" to 30.0)
        val (_, isInvalid) = com.example.domain.engine.SplitCalculator.calculateRatioSplit(
            totalAmount = 100000L,
            memberRatios = ratiosIncomplete
        )
        assertFalse("80% total ratio should NOT be valid", isInvalid)
    }
}
