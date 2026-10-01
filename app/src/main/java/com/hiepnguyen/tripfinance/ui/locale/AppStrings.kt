package com.hiepnguyen.tripfinance.ui.locale

object AppStrings {
    fun getCategoryName(category: String, lang: AppLanguage): String {
        return when (category) {
            "FOOD" -> if (lang == AppLanguage.VI) "Ăn uống" else "Food & Dining"
            "TRANSPORT" -> if (lang == AppLanguage.VI) "Di chuyển" else "Transportation"
            "HOTEL" -> if (lang == AppLanguage.VI) "Lưu trú" else "Accommodation"
            "SIGHTSEEING" -> if (lang == AppLanguage.VI) "Vé & Tham quan" else "Tickets & Tours"
            "ENTERTAINMENT" -> if (lang == AppLanguage.VI) "Giải trí" else "Entertainment"
            "SHOPPING" -> if (lang == AppLanguage.VI) "Mua sắm" else "Shopping"
            "OTHER" -> if (lang == AppLanguage.VI) "Khác" else "Others"
            else -> category
        }
    }

    fun getRoleName(role: String, lang: AppLanguage): String {
        return when (role) {
            "ADMIN" -> if (lang == AppLanguage.VI) "Trưởng đoàn" else "Admin (Leader)"
            "TREASURER" -> if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer"
            "MEMBER" -> if (lang == AppLanguage.VI) "Thành viên" else "Member"
            "VIEWER" -> if (lang == AppLanguage.VI) "Người xem" else "Viewer"
            else -> role
        }
    }

    fun getSplitTypeName(splitType: String, lang: AppLanguage): String {
        return when (splitType) {
            "EQUAL" -> if (lang == AppLanguage.VI) "Chia đều cho tất cả" else "Split Equally"
            "CUSTOM" -> if (lang == AppLanguage.VI) "Tùy chỉnh số tiền" else "Custom Amount"
            "EXACT" -> if (lang == AppLanguage.VI) "Số tiền chính xác" else "Exact Amount"
            "PERCENTAGE" -> if (lang == AppLanguage.VI) "Theo tỷ lệ %" else "By Percentage %"
            "SHARES" -> if (lang == AppLanguage.VI) "Theo phần/suất" else "By Shares / Ratio"
            else -> splitType
        }
    }

    fun getTabName(tabId: String, lang: AppLanguage): String {
        return when (tabId) {
            "DASHBOARD" -> if (lang == AppLanguage.VI) "Tổng Quan" else "Dashboard"
            "EXPENSES" -> if (lang == AppLanguage.VI) "Chi Tiêu" else "Expenses"
            "FUND" -> if (lang == AppLanguage.VI) "Quỹ Đoàn" else "Group Fund"
            "SETTLEMENT" -> if (lang == AppLanguage.VI) "Quyết Toán" else "Settlement"
            "SETTINGS" -> if (lang == AppLanguage.VI) "Thành Viên" else "Members"
            else -> tabId
        }
    }

    // Top Bar & Global
    fun selectTrip(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chọn chuyến đi / đoàn:" else "Select trip / group:"
    fun createNewTrip(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tạo đoàn mới..." else "Create new group..."
    fun offlineMode(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chế độ Ngoại Tuyến" else "Offline Mode"
    fun languageSettings(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cài Đặt Ngôn Ngữ" else "Language Settings"
    fun languageName(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiếng Việt" else "English"
    fun switchLanguagePrompt(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chuyển sang English" else "Chuyển sang Tiếng Việt"

    // Dashboard
    fun totalExpenses(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng Chi Tiêu Của Đoàn" else "Total Group Expenses"
    fun fundBalance(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ Đoàn Hiện Có" else "Group Fund Balance"
    fun fundDeficit(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi Tiêu Vượt Quỹ" else "Fund Deficit"
    fun fundSurplus(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ Dư Sau Chi" else "Remaining Fund"
    fun fundProgress(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiến Độ Thu Quỹ Đoàn" else "Fund Collection Progress"
    fun targetPerPerson(lang: AppLanguage) = if (lang == AppLanguage.VI) "Mục tiêu" else "Target"
    fun quickActions(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thao Tác Nhanh" else "Quick Actions"
    fun addExpense(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thêm Khoản Chi" else "Add Expense"
    fun addFund(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nộp Quỹ Đoàn" else "Add Fund"
    fun settleUp(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quyết Toán Ngay" else "Settle Up"
    fun exportReport(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xuất Báo Cáo" else "Export Report"
    fun switchPersona(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đổi Tài Khoản" else "Switch User"
    fun createdTripsList(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Danh Sách Các Đoàn Đã Tạo ($count)" else "Created Groups List ($count)"
    fun currentlySelected(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đang chọn" else "Selected"
    fun selectTripBtn(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chọn đoàn" else "Select"
    fun editTrip(lang: AppLanguage) = if (lang == AppLanguage.VI) "Sửa đoàn" else "Edit Group"
    fun deleteTrip(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xóa đoàn" else "Delete Group"
    fun createTripBtn(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tạo đoàn mới" else "Create group"
    fun categoryBreakdown(lang: AppLanguage) = if (lang == AppLanguage.VI) "Phân Bổ Chi Phí Theo Danh Mục" else "Category Cost Breakdown"
    fun memberFinancialStatus(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Tình Hình Tài Chính Từng Thành Viên ($count)" else "Member Financial Status ($count)"
    fun contributed(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã nộp quỹ" else "Contributed"
    fun paidOnBehalf(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã chi hộ" else "Paid on behalf"
    fun shareOfCost(lang: AppLanguage) = if (lang == AppLanguage.VI) "Phải chịu" else "Share of cost"
    fun toReceive(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cần nhận lại" else "To receive"
    fun toPay(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cần đóng thêm" else "To pay"
    fun balanced(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đã cân bằng" else "Balanced"

    // Expenses Screen
    fun searchExpensesPlaceholder(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tìm kiếm khoản chi, người chi..." else "Search expenses, payers..."
    fun all(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tất cả" else "All"
    fun noExpensesTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chưa có khoản chi nào được ghi lại" else "No expenses recorded yet"
    fun noExpensesSubtitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nhấn nút (+) bên dưới để thêm khoản chi đầu tiên cho đoàn" else "Tap the (+) button below to add the first expense"
    fun paidFromFundBadge(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xuất từ Quỹ" else "Paid from Fund"
    fun paidByMemberBadge(name: String, lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi hộ: $name" else "Paid by: $name"
    fun participantsLabel(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Người tham gia ($count)" else "Participants ($count)"
    fun expenseDetail(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chi Tiết Khoản Chi" else "Expense Details"
    fun convertedTotalVnd(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng số tiền quy đổi (VND)" else "Converted Total (VND)"
    fun originalAmount(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiền gốc" else "Original Amount"
    fun exchangeRate(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tỷ giá" else "Exchange Rate"
    fun payer(lang: AppLanguage) = if (lang == AppLanguage.VI) "Người xuất tiền" else "Payer"
    fun splitMethod(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cách phân bổ" else "Split Method"
    fun participantsBreakdown(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Danh Sách Người Tham Gia ($count người):" else "Participants Breakdown ($count members):"
    fun noteLabel(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ghi chú" else "Note"
    fun adminOnlyNotice(lang: AppLanguage) = if (lang == AppLanguage.VI) "ℹ️ Chỉ Trưởng đoàn (Admin) mới có quyền sửa hoặc xóa khoản chi này." else "ℹ️ Only the Group Leader (Admin) can edit or delete this expense."
    fun add(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thêm" else "Add"
    fun edit(lang: AppLanguage) = if (lang == AppLanguage.VI) "Sửa" else "Edit"
    fun delete(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xóa" else "Delete"
    fun close(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đóng" else "Close"
    fun confirmDeleteExpenseTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xác Nhận Xóa Khoản Chi" else "Confirm Delete Expense"
    fun confirmDeleteExpenseDesc(title: String, amountStr: String, lang: AppLanguage) = if (lang == AppLanguage.VI) 
        "Bạn có chắc chắn muốn xóa khoản chi '$title' ($amountStr)? Thao tác này chỉ dành cho Trưởng đoàn."
    else
        "Are you sure you want to delete expense '$title' ($amountStr)? This action is restricted to the Group Leader."
    fun cancel(lang: AppLanguage) = if (lang == AppLanguage.VI) "Hủy" else "Cancel"
    fun confirm(lang: AppLanguage) = if (lang == AppLanguage.VI) "Xác nhận" else "Confirm"
    fun save(lang: AppLanguage) = if (lang == AppLanguage.VI) "Lưu" else "Save"
    fun saveChanges(lang: AppLanguage) = if (lang == AppLanguage.VI) "Lưu thay đổi" else "Save changes"

    // Fund Screen
    fun fundOverview(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quỹ Đoàn Chung" else "Group Fund Overview"
    fun totalCollected(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng tiền quỹ đã thu" else "Total Fund Collected"
    fun targetPerPersonFull(lang: AppLanguage) = if (lang == AppLanguage.VI) "Mục tiêu mỗi người" else "Target per member"
    fun totalTargetGroup(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tổng mục tiêu cả đoàn" else "Total group target"
    fun collectionProgress(lang: AppLanguage) = if (lang == AppLanguage.VI) "Tiến độ hoàn thành" else "Completion progress"
    fun remainingNeeded(lang: AppLanguage) = if (lang == AppLanguage.VI) "Còn thiếu" else "Remaining"
    fun contributeToFund(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nộp Quỹ Đoàn" else "Contribute to Fund"
    fun contributionHistory(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Lịch Sử Đóng Quỹ ($count)" else "Contribution History ($count)"
    fun noContributions(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chưa có lượt nộp quỹ nào" else "No contributions recorded yet"

    // Settlement Screen
    fun settlementTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quyết Toán Đoàn Tối Ưu" else "Optimized Group Settlement"
    fun settlementSubtitle(lang: AppLanguage) = if (lang == AppLanguage.VI) 
        "Thuật toán tính toán bù trừ thông minh giúp tối thiểu hóa số lượt chuyển khoản giữa các thành viên."
    else
        "Smart debt netting algorithm minimizes the total number of peer-to-peer bank transfers."
    fun allBalancedTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Đoàn Đã Cân Bằng Tài Chính! 🎉" else "All Members Are Settled! 🎉"
    fun allBalancedDesc(lang: AppLanguage) = if (lang == AppLanguage.VI) 
        "Tất cả các thành viên hiện không có nợ nần hoặc các khoản chi đã được thanh toán đầy đủ."
    else
        "All debts have been cleared and everyone is financially balanced."
    fun transfersNeeded(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Danh Sách Chuyển Khoản Cần Thực Hiện ($count lượt)" else "Required Bank Transfers ($count transfers)"
    fun transferTo(name: String, lang: AppLanguage) = if (lang == AppLanguage.VI) "Chuyển cho $name" else "Transfer to $name"
    fun scanVietQr(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quét VietQR" else "Scan VietQR"
    fun snapshotSettlement(lang: AppLanguage) = if (lang == AppLanguage.VI) "Lưu Ảnh Quyết Toán" else "Snapshot Settlement"
    fun snapshotHistory(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Lịch Sử Chụp Quyết Toán ($count)" else "Settlement Snapshots ($count)"

    // Members & Settings Screen
    fun membersAndSettings(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quản Lý Thành Viên & Cài Đặt" else "Members & Settings"
    fun memberListTitle(count: Int, lang: AppLanguage) = if (lang == AppLanguage.VI) "Danh Sách Thành Viên ($count)" else "Member List ($count)"
    fun addMemberBtn(lang: AppLanguage) = if (lang == AppLanguage.VI) "Thêm thành viên" else "Add member"
    fun bankAccountLabel(lang: AppLanguage) = if (lang == AppLanguage.VI) "STK" else "Account"
    fun roleLabel(lang: AppLanguage) = if (lang == AppLanguage.VI) "Vai trò" else "Role"
    fun exchangeRateTable(lang: AppLanguage) = if (lang == AppLanguage.VI) "Bảng Tỷ Giá Ngoại Tệ" else "Foreign Exchange Rates"
    fun updateRateBtn(lang: AppLanguage) = if (lang == AppLanguage.VI) "Cập nhật tỷ giá" else "Update rate"
    fun quickConverter(lang: AppLanguage) = if (lang == AppLanguage.VI) "Quy đổi nhanh ngoại tệ sang VND" else "Quick Foreign Currency Converter"
    fun auditLogs(lang: AppLanguage) = if (lang == AppLanguage.VI) "Nhật Ký Hoạt Động (Audit Logs)" else "Activity Audit Logs"
    fun languageCardTitle(lang: AppLanguage) = if (lang == AppLanguage.VI) "Ngôn Ngữ Giao Diện / Language" else "Interface Language / Ngôn Ngữ"
    fun selectLanguage(lang: AppLanguage) = if (lang == AppLanguage.VI) "Chọn ngôn ngữ hiển thị:" else "Select display language:"
}

