package com.hiepnguyen.tripfinance.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hiepnguyen.tripfinance.data.db.AppDatabase
import com.hiepnguyen.tripfinance.data.entity.*
import com.hiepnguyen.tripfinance.data.repository.TripFinanceRepository
import com.hiepnguyen.tripfinance.domain.engine.SettlementEngine
import com.hiepnguyen.tripfinance.domain.engine.SplitCalculator
import com.hiepnguyen.tripfinance.domain.model.*
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import android.content.Context
import android.net.Uri
import java.io.File

data class UiState(
    val currentTrip: TripEntity? = null,
    val allTrips: List<TripEntity> = emptyList(),
    val members: List<TripMemberEntity> = emptyList(),
    val currentMember: TripMemberEntity? = null,
    val financialSummary: FinancialSummary = FinancialSummary(),
    val memberStatuses: List<MemberFinancialStatus> = emptyList(),
    val settlementTransfers: List<SettlementTransfer> = emptyList(),
    val reconciliationError: String? = null,
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val allSplits: List<ExpenseSplitEntity> = emptyList(),
    val fundContributions: List<FundContributionEntity> = emptyList(),
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val snapshots: List<SettlementSnapshotEntity> = emptyList(),
    val selectedCategoryFilter: String? = null,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val language: AppLanguage = AppLanguage.VI
)

sealed class DatabaseInitState {
    object Initializing : DatabaseInitState()
    object Ready : DatabaseInitState()
    data class Error(
        val errorType: com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType,
        val message: String,
        val details: String? = null
    ) : DatabaseInitState()
}

class TripFinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val _databaseInitState = MutableStateFlow<DatabaseInitState>(DatabaseInitState.Initializing)
    val databaseInitState: StateFlow<DatabaseInitState> = _databaseInitState.asStateFlow()

    private val _repositoryFlow = MutableStateFlow<TripFinanceRepository?>(null)
    private val repository: TripFinanceRepository
        get() = _repositoryFlow.value ?: error("Cơ sở dữ liệu chưa sẵn sàng")

    private val sharedPrefs = application.getSharedPreferences("trip_finance_prefs", Context.MODE_PRIVATE)

    private val _currentTripId = MutableStateFlow<String?>(null)
    private val _currentMemberId = MutableStateFlow<String?>(null)
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _aiInsight = MutableStateFlow<String?>(null)
    private val _isLoadingAi = MutableStateFlow(false)
    private val _showAiConsentDialog = MutableStateFlow(false)
    val aiInsight: StateFlow<String?> = _aiInsight.asStateFlow()
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()
    val showAiConsentDialog: StateFlow<Boolean> = _showAiConsentDialog.asStateFlow()
    private val _language = MutableStateFlow(
        if (sharedPrefs.getString("app_language", "vi") == "en") AppLanguage.EN else AppLanguage.VI
    )

    val uiState: StateFlow<UiState>

    private data class FilterState(
        val categoryFilter: String?,
        val query: String,
        val currentMemberId: String?,
        val language: AppLanguage
    )

    private data class TripCoreData(
        val members: List<TripMemberEntity>,
        val expenses: List<ExpenseEntity>,
        val splits: List<ExpenseSplitEntity>,
        val funds: List<FundContributionEntity>,
        val rates: List<ExchangeRateEntity>
    )

    private data class TripAuxData(
        val logs: List<AuditLogEntity>,
        val snapshots: List<SettlementSnapshotEntity>,
        val financialPair: Pair<FinancialSummary, List<MemberFinancialStatus>>
    )

    init {
        val filterFlow = combine(
            _selectedCategoryFilter,
            _searchQuery,
            combine(_currentMemberId, _language) { mId, lang -> mId to lang }
        ) { cat, q, (mId, lang) ->
            FilterState(cat, q, mId, lang)
        }

        uiState = _repositoryFlow.flatMapLatest { repo ->
            if (repo == null) {
                flowOf(
                    UiState(
                        errorMessage = _errorMessage.value,
                        successMessage = _successMessage.value,
                        language = _language.value
                    )
                )
            } else {
                combine(
                    repo.allTrips,
                    _currentTripId,
                    filterFlow,
                    _errorMessage,
                    _successMessage
                ) { trips, activeTripId, filters, error, success ->
                    val trip = trips.find { it.id == activeTripId } ?: trips.firstOrNull()
                    Triple(trips, trip, filters) to (error to success)
                }.flatMapLatest { (triple, messagePair) ->
                    val (trips, currentTrip, filters) = triple
                    val (errorMsg, successMsg) = messagePair

                    if (currentTrip == null) {
                        flowOf(
                            UiState(
                                allTrips = trips,
                                errorMessage = errorMsg,
                                successMessage = successMsg,
                                language = filters.language
                            )
                        )
                    } else {
                        val tripId = currentTrip.id

                        val coreFlow = combine(
                            repo.getMembers(tripId),
                            repo.getExpenses(tripId),
                            repo.getSplitsForTrip(tripId),
                            repo.getFundContributions(tripId),
                            repo.getExchangeRates(tripId)
                        ) { members, expenses, splits, funds, rates ->
                            TripCoreData(members, expenses, splits, funds, rates)
                        }

                        val auxFlow = combine(
                            repo.getAuditLogs(tripId),
                            repo.getSnapshots(tripId),
                            repo.observeFinancialStatus(tripId)
                        ) { logs, snapshots, financialPair ->
                            TripAuxData(logs, snapshots, financialPair)
                        }

                combine(coreFlow, auxFlow) { core, aux ->
                    val (summary, statuses) = aux.financialPair
                    val currentMember = core.members.find { it.id == filters.currentMemberId }
                        ?: core.members.firstOrNull()

                    val totalSpent = summary.totalExpenses.coerceAtLeast(1L)
                    val catMapVi = mapOf(
                        "FOOD" to "Ăn uống",
                        "TRANSPORT" to "Di chuyển",
                        "HOTEL" to "Lưu trú/Khách sạn",
                        "SIGHTSEEING" to "Vé tham quan",
                        "ENTERTAINMENT" to "Vui chơi/Giải trí",
                        "SHOPPING" to "Mua sắm",
                        "OTHER" to "Chi phí khác"
                    )
                    val catMapEn = mapOf(
                        "FOOD" to "Food & Dining",
                        "TRANSPORT" to "Transportation",
                        "HOTEL" to "Accommodation",
                        "SIGHTSEEING" to "Sightseeing & Tours",
                        "ENTERTAINMENT" to "Entertainment",
                        "SHOPPING" to "Shopping",
                        "OTHER" to "Other Expenses"
                    )

                    val breakdowns = core.expenses.groupBy { it.category }.map { (cat, list) ->
                        val amount = list.sumOfSafe { it.convertedTotalAmount }
                        val label = if (filters.language == AppLanguage.VI) {
                            catMapVi[cat] ?: cat
                        } else {
                            catMapEn[cat] ?: cat
                        }
                        CategoryBreakdown(
                            category = cat,
                            labelVi = label,
                            iconName = cat,
                            totalAmount = amount,
                            percentage = (amount.toDouble() / totalSpent.toDouble()) * 100.0,
                            count = list.size
                        )
                    }.sortedByDescending { it.totalAmount }

                    val fundHolder = core.members.find { it.role == "TREASURER" && it.isActive }
                        ?: core.members.find { it.role == "ADMIN" && it.isActive }
                        ?: core.members.firstOrNull()

                    val settlementResult = SettlementEngine.computeSettlementWithStatus(
                        memberStatuses = statuses,
                        tripJoinCode = currentTrip.joinCode,
                        remainingFund = summary.remainingFund,
                        fundHolder = fundHolder
                    )

                    val filteredExpenses = core.expenses.filter { exp ->
                        val matchesCat = filters.categoryFilter == null || exp.category == filters.categoryFilter
                        val matchesSearch = filters.query.isBlank() ||
                                exp.title.contains(filters.query, ignoreCase = true) ||
                                exp.note.contains(filters.query, ignoreCase = true)
                        matchesCat && matchesSearch
                    }

                    UiState(
                        currentTrip = currentTrip,
                        allTrips = trips,
                        members = core.members,
                        currentMember = currentMember,
                        financialSummary = summary,
                        memberStatuses = statuses,
                        settlementTransfers = settlementResult.transfers,
                        reconciliationError = settlementResult.reconciliationError,
                        categoryBreakdowns = breakdowns,
                        expenses = filteredExpenses,
                        allSplits = core.splits,
                        fundContributions = core.funds,
                        exchangeRates = core.rates,
                        auditLogs = aux.logs,
                        snapshots = aux.snapshots,
                        selectedCategoryFilter = filters.categoryFilter,
                        searchQuery = filters.query,
                        errorMessage = errorMsg,
                        successMessage = successMsg,
                        language = filters.language
                    )
                }
            }
        }
    }
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UiState()
)

        initDatabase()
    }

    fun retryDatabaseInit() {
        initDatabase()
    }

    private fun initDatabase() {
        _databaseInitState.value = DatabaseInitState.Initializing
        try {
            val db = AppDatabase.getDatabase(getApplication())
            val repo = TripFinanceRepository(db)
            _repositoryFlow.value = repo
            _databaseInitState.value = DatabaseInitState.Ready
        } catch (e: com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException) {
            _repositoryFlow.value = null
            _databaseInitState.value = DatabaseInitState.Error(
                errorType = e.errorType,
                message = e.message ?: "Lỗi bảo mật khi mở cơ sở dữ liệu",
                details = e.cause?.message
            )
        } catch (e: Throwable) {
            _repositoryFlow.value = null
            _databaseInitState.value = DatabaseInitState.Error(
                errorType = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.UNKNOWN,
                message = "Không thể khởi tạo cơ sở dữ liệu: ${e.message}",
                details = e.message
            )
        }
    }

    fun resetDatabaseFresh() {
        viewModelScope.launch {
            try {
                AppDatabase.resetDatabase(getApplication())
                initDatabase()
                showSuccess("Đã xóa và tạo mới cơ sở dữ liệu an toàn.")
            } catch (e: Throwable) {
                showError("Lỗi khi tạo mới cơ sở dữ liệu: ${e.message}")
            }
        }
    }

    fun restoreFromEmergencyBackup(jsonContent: String, password: String?) {
        viewModelScope.launch {
            try {
                val parseResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.parseAndValidateBackup(jsonContent, password)
                if (parseResult.isFailure) {
                    val err = parseResult.exceptionOrNull()?.message ?: "Tệp sao lưu không hợp lệ hoặc sai mật khẩu"
                    showError("Khôi phục thất bại: $err")
                    return@launch
                }
                val backupData = parseResult.getOrThrow()

                // Đặt lại CSDL và KeyStore sạch sẽ
                AppDatabase.resetDatabase(getApplication())

                // Mở CSDL mới và nạp dữ liệu
                val newDb = AppDatabase.getDatabase(getApplication())
                val repo = TripFinanceRepository(newDb)
                _repositoryFlow.value = repo

                val restoreResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromBackupData(
                    db = newDb,
                    backupData = backupData,
                    clearExisting = false
                )

                if (restoreResult.isSuccess) {
                    _databaseInitState.value = DatabaseInitState.Ready
                    showSuccess("Khôi phục thành công ${backupData.trips.size} đoàn từ bản sao lưu!")
                } else {
                    val err = restoreResult.exceptionOrNull()?.message ?: "Lỗi khi nạp dữ liệu"
                    showError("Lỗi nạp dữ liệu: $err")
                }
            } catch (e: Throwable) {
                showError("Lỗi khôi phục khẩn cấp: ${e.message}")
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        sharedPrefs.edit().putString("app_language", lang.code).apply()
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.VI) AppLanguage.EN else AppLanguage.VI
        setLanguage(next)
    }

    fun selectTrip(tripId: String) {
        _currentTripId.value = tripId
    }

    fun switchUserPersona(memberId: String) {
        _currentMemberId.value = memberId
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun showError(msg: String) {
        _errorMessage.value = msg
    }

    fun showSuccess(msg: String) {
        _successMessage.value = msg
    }

    fun hasUserConsentedToAiCloud(): Boolean {
        return sharedPrefs.getBoolean("user_consented_ai_cloud", false)
    }

    fun requestAiSpendingInsight() {
        if (!hasUserConsentedToAiCloud()) {
            _showAiConsentDialog.value = true
        } else {
            executeAiAnalysis(userHasConsented = true)
        }
    }

    fun onUserConfirmAiCloudConsent() {
        sharedPrefs.edit().putBoolean("user_consented_ai_cloud", true).apply()
        _showAiConsentDialog.value = false
        executeAiAnalysis(userHasConsented = true)
    }

    fun onUserChooseOfflineAiInsight() {
        _showAiConsentDialog.value = false
        executeAiAnalysis(userHasConsented = false)
    }

    fun dismissAiConsentDialog() {
        _showAiConsentDialog.value = false
    }

    private fun executeAiAnalysis(userHasConsented: Boolean) {
        val state = uiState.value
        val currentTrip = state.currentTrip ?: return
        viewModelScope.launch {
            _isLoadingAi.value = true
            try {
                val insight = com.hiepnguyen.tripfinance.domain.ai.GeminiSpendingAdvisor.analyzeTripFinances(
                    tripTitle = currentTrip.title,
                    financialSummary = state.financialSummary,
                    categories = state.categoryBreakdowns,
                    members = state.memberStatuses,
                    languageCode = state.language.code,
                    userHasConsented = userHasConsented
                )
                _aiInsight.value = insight
            } catch (e: Exception) {
                _aiInsight.value = "Lỗi khi phân tích: ${e.message}"
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    fun clearAiInsight() {
        _aiInsight.value = null
    }

    // Trip operations
    fun createTrip(
        title: String,
        description: String,
        joinCode: String,
        startDate: Long,
        endDate: Long,
        adminName: String,
        adminBankName: String?,
        adminBankAccount: String?,
        adminBankHolder: String?
    ) {
        viewModelScope.launch {
            try {
                val newTripId = repository.createTrip(
                    title = title,
                    description = description,
                    joinCode = joinCode,
                    startDate = startDate,
                    endDate = endDate,
                    adminName = adminName,
                    adminBankName = adminBankName,
                    adminBankAccount = adminBankAccount,
                    adminBankHolder = adminBankHolder
                )
                _currentTripId.value = newTripId
                showSuccess("Tạo đoàn '$title' thành công!")
            } catch (e: Exception) {
                showError("Lỗi khi tạo đoàn: ${e.message}")
            }
        }
    }

    fun editTrip(
        tripId: String,
        title: String,
        description: String,
        startDate: Long,
        endDate: Long
    ) {
        val currentMember = uiState.value.currentMember ?: return
        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa thông tin đoàn!")
            return
        }

        viewModelScope.launch {
            try {
                repository.updateTripDetails(
                    tripId = tripId,
                    title = title,
                    description = description,
                    startDate = startDate,
                    endDate = endDate,
                    actor = currentMember
                )
                showSuccess("Đã cập nhật thông tin đoàn '$title' thành công!")
            } catch (e: Exception) {
                showError("Lỗi cập nhật đoàn: ${e.message}")
            }
        }
    }

    fun deleteTrip(tripId: String) {
        val currentMember = uiState.value.currentMember ?: return
        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền xóa đoàn!")
            return
        }

        viewModelScope.launch {
            try {
                val trips = uiState.value.allTrips
                repository.deleteTripCascade(tripId)
                // If deleting current active trip, switch to another
                if (_currentTripId.value == tripId) {
                    val remaining = trips.filter { it.id != tripId }
                    _currentTripId.value = remaining.firstOrNull()?.id
                }
                showSuccess("Đã xóa đoàn thành công!")
            } catch (e: Exception) {
                showError("Lỗi khi xóa đoàn: ${e.message}")
            }
        }
    }

    // Expense operations
    fun addExpense(
        title: String,
        category: String,
        payerType: String,
        payerMemberId: String?,
        totalAmount: Double,
        currency: String,
        exchangeRate: Double,
        splitType: String,
        splits: List<Pair<String, Long>>,
        note: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể thêm khoản chi mới!")
            return
        }

        if (!totalAmount.isFinite() || totalAmount.isNaN() || totalAmount <= 0.0 || totalAmount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError("Số tiền chi tiêu không hợp lệ hoặc vượt quá hạn mức cho phép.")
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError("Tỷ giá quy đổi không hợp lệ.")
            return
        }

        val convertedTotal = FinancialInputValidator.convertToVnd(totalAmount, exchangeRate)
        val totalSplitSum = splits.sumOfSafe { it.second }
        if (totalSplitSum != convertedTotal) {
            showError("Tổng tiền phân bổ ($totalSplitSum VND) không bằng tổng khoản chi ($convertedTotal VND). Vui lòng kiểm tra lại!")
            return
        }

        if (splits.any { it.second < 0 }) {
            showError("Số tiền phân bổ không được âm. Vui lòng kiểm tra lại tỷ lệ chia!")
            return
        }

        viewModelScope.launch {
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    tripId = currentTrip.id,
                    title = title,
                    category = category,
                    payerType = payerType,
                    payerMemberId = if (payerType == "MEMBER") payerMemberId else null,
                    totalAmount = totalAmount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedTotalAmount = convertedTotal,
                    splitType = splitType,
                    note = note,
                    timestamp = timestamp,
                    createdMemberId = currentMember.id,
                    isSynced = true
                )

                val splitEntities = splits.map { (memberId, amount) ->
                    ExpenseSplitEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        tripId = currentTrip.id,
                        memberId = memberId,
                        amount = amount
                    )
                }

                repository.addExpenseWithSplits(expense, splitEntities, currentMember)
                showSuccess("Đã thêm khoản chi '$title' thành công!")
            } catch (e: Exception) {
                showError("Lỗi thêm chi tiêu: ${e.message}")
            }
        }
    }

    fun editExpense(
        expenseId: String,
        title: String,
        category: String,
        payerType: String,
        payerMemberId: String?,
        totalAmount: Double,
        currency: String,
        exchangeRate: Double,
        splitType: String,
        splits: List<Pair<String, Long>>,
        note: String,
        timestamp: Long
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể chỉnh sửa khoản chi!")
            return
        }

        // Strict RBAC: Only Admin can edit expenses
        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa các khoản chi!")
            return
        }

        if (!totalAmount.isFinite() || totalAmount.isNaN() || totalAmount <= 0.0 || totalAmount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError("Số tiền chi tiêu không hợp lệ hoặc vượt quá hạn mức cho phép.")
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError("Tỷ giá quy đổi không hợp lệ.")
            return
        }

        val convertedTotal = FinancialInputValidator.convertToVnd(totalAmount, exchangeRate)
        val totalSplitSum = splits.sumOfSafe { it.second }
        if (totalSplitSum != convertedTotal) {
            showError("Tổng tiền phân bổ ($totalSplitSum VND) không bằng tổng khoản chi ($convertedTotal VND). Vui lòng kiểm tra lại!")
            return
        }

        if (splits.any { it.second < 0 }) {
            showError("Số tiền phân bổ không được âm. Vui lòng kiểm tra lại tỷ lệ chia!")
            return
        }

        // CRITICAL FIX: Bảo lưu người tạo ban đầu (createdMemberId), không ghi đè bằng id của người sửa
        val existingExpense = uiState.value.expenses.find { it.id == expenseId }
        val preservedCreatedMemberId = existingExpense?.createdMemberId ?: currentMember.id

        viewModelScope.launch {
            try {
                val updatedExpense = ExpenseEntity(
                    id = expenseId,
                    tripId = currentTrip.id,
                    title = title,
                    category = category,
                    payerType = payerType,
                    payerMemberId = if (payerType == "MEMBER") payerMemberId else null,
                    totalAmount = totalAmount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedTotalAmount = convertedTotal,
                    splitType = splitType,
                    note = note,
                    timestamp = timestamp,
                    createdMemberId = preservedCreatedMemberId,
                    isSynced = true
                )

                val splitEntities = splits.map { (memberId, amount) ->
                    ExpenseSplitEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        tripId = currentTrip.id,
                        memberId = memberId,
                        amount = amount
                    )
                }

                repository.updateExpenseWithSplits(updatedExpense, splitEntities, currentMember)
                showSuccess("Đã cập nhật khoản chi '$title' thành công!")
            } catch (e: Exception) {
                showError("Lỗi cập nhật chi tiêu: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể xóa khoản chi!")
            return
        }

        // Strict RBAC: Only Admin can delete expenses
        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền xóa các khoản chi!")
            return
        }

        viewModelScope.launch {
            try {
                repository.deleteExpense(expense, currentMember)
                showSuccess("Đã xóa khoản chi '${expense.title}'")
            } catch (e: Exception) {
                showError("Lỗi xóa khoản chi: ${e.message}")
            }
        }
    }

    // Fund operations
    fun addFundContribution(
        memberId: String,
        amount: Long,
        currency: String,
        exchangeRate: Double,
        note: String
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể nộp thêm quỹ!")
            return
        }

        val members = uiState.value.members
        val contributor = members.find { it.id == memberId }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError("Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền ghi nhận thu quỹ!")
            return
        }

        if (amount <= 0L || amount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError("Số tiền nộp quỹ không hợp lệ (phải từ 1 đến 100 tỷ).")
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError("Tỷ giá quy đổi không hợp lệ.")
            return
        }

        val convertedAmount = FinancialInputValidator.convertToVnd(amount.toDouble(), exchangeRate)
        val contribution = FundContributionEntity(
            id = UUID.randomUUID().toString(),
            tripId = currentTrip.id,
            memberId = memberId,
            amount = amount,
            currency = currency,
            exchangeRate = exchangeRate,
            convertedAmount = convertedAmount,
            note = note,
            timestamp = System.currentTimeMillis(),
            recordedByMemberId = currentMember.id
        )

        viewModelScope.launch {
            try {
                repository.addFundContribution(contribution, contributor?.name ?: "Thành viên", currentMember)
                showSuccess("Đã ghi nhận nộp quỹ $convertedAmount VND cho ${contributor?.name}!")
            } catch (e: Exception) {
                showError("Lỗi nộp quỹ: ${e.message}")
            }
        }
    }

    fun deleteFundContribution(contribution: FundContributionEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể xóa khoản nộp quỹ!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền xóa khoản đóng góp quỹ!")
            return
        }

        val contributorName = uiState.value.members.find { it.id == contribution.memberId }?.name ?: "Thành viên"
        viewModelScope.launch {
            try {
                repository.deleteFundContribution(contribution, contributorName, currentMember)
                showSuccess("Đã xóa khoản nộp quỹ của $contributorName")
            } catch (e: Exception) {
                showError("Lỗi xóa khoản nộp quỹ: ${e.message}")
            }
        }
    }

    // Members & Roles
    fun addMember(
        name: String,
        role: String,
        bankName: String?,
        bankAccount: String?,
        bankAccountHolder: String?
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể thêm thành viên mới!")
            return
        }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError("Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền thêm thành viên!")
            return
        }

        viewModelScope.launch {
            try {
                repository.addMember(
                    tripId = currentTrip.id,
                    name = name,
                    role = role,
                    bankName = bankName,
                    bankAccount = bankAccount,
                    bankAccountHolder = bankAccountHolder,
                    actor = currentMember
                )
                showSuccess("Đã thêm thành viên '$name' ($role)")
            } catch (e: Exception) {
                showError("Lỗi thêm thành viên: ${e.message}")
            }
        }
    }

    fun editMember(
        member: TripMemberEntity,
        newName: String,
        newRole: String,
        newBankName: String?,
        newBankAccount: String?,
        newBankAccountHolder: String?
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể sửa thông tin thành viên!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa thông tin thành viên!")
            return
        }

        viewModelScope.launch {
            try {
                val updated = member.copy(
                    name = newName,
                    role = newRole,
                    bankName = newBankName,
                    bankAccount = newBankAccount,
                    bankAccountHolder = newBankAccountHolder
                )
                repository.updateMember(updated, currentMember)
                showSuccess("Đã cập nhật thông tin thành viên '$newName' thành công!")
            } catch (e: Exception) {
                showError("Lỗi cập nhật thành viên: ${e.message}")
            }
        }
    }

    fun updateMemberRole(member: TripMemberEntity, newRole: String) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể thay đổi vai trò thành viên!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền thay đổi vai trò thành viên!")
            return
        }

        viewModelScope.launch {
            try {
                repository.updateMember(member.copy(role = newRole), currentMember)
                showSuccess("Đã đổi vai trò của ${member.name} thành $newRole")
            } catch (e: Exception) {
                showError("Lỗi cập nhật vai trò: ${e.message}")
            }
        }
    }

    fun removeOrDeactivateMember(member: TripMemberEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể xóa hoặc ngừng hoạt động thành viên!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền xóa/ngừng hoạt động thành viên!")
            return
        }

        viewModelScope.launch {
            try {
                repository.removeOrDeactivateMember(member, currentMember)
                showSuccess("Đã xử lý trạng thái thành viên ${member.name}")
            } catch (e: Exception) {
                showError("Lỗi xóa/vô hiệu hóa thành viên: ${e.message}")
            }
        }
    }

    // Exchange rates
    fun updateExchangeRate(currencyCode: String, rateToBase: Double) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError("Chuyến đi '${currentTrip.title}' đã được khóa sổ quyết toán. Không thể thay đổi tỷ giá!")
            return
        }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError("Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền cập nhật tỷ giá!")
            return
        }

        val rateEntity = ExchangeRateEntity(
            id = UUID.randomUUID().toString(),
            tripId = currentTrip.id,
            currencyCode = currencyCode,
            rateToBase = rateToBase,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                repository.updateExchangeRate(rateEntity, currentMember)
                showSuccess("Đã cập nhật tỷ giá 1 $currencyCode = $rateToBase VND")
            } catch (e: Exception) {
                showError("Lỗi cập nhật tỷ giá: ${e.message}")
            }
        }
    }

    // Settlement & Snapshot Finalization
    fun finalizeSettlement(snapshotTitle: String) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return
        val state = uiState.value

        if (currentTrip.isSettled) {
            showError("Chuyến đi này đã được khóa sổ quyết toán từ trước!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền khóa sổ và quyết toán chuyến đi!")
            return
        }

        if (state.members.isEmpty()) {
            showError("Đoàn không có thành viên nào! Không thể thực hiện quyết toán và khóa sổ.")
            return
        }

        if (!state.financialSummary.isBalanced || state.financialSummary.balanceDiscrepancy != 0L) {
            showError("Hệ thống phát hiện chênh lệch đối soát (${state.financialSummary.balanceDiscrepancy} VND). Toàn bộ chi tiêu phải được phân bổ cân bằng tuyệt đối để khóa sổ!")
            return
        }

        val settlementJsonObj = org.json.JSONObject().apply {
            put("tripId", currentTrip.id)
            put("tripTitle", currentTrip.title)
            put("joinCode", currentTrip.joinCode)
            put("settledAt", System.currentTimeMillis())
            put("settledByMemberId", currentMember.id)
            put("settledByMemberName", currentMember.name)
            put("summary", org.json.JSONObject().apply {
                put("totalExpenses", state.financialSummary.totalExpenses)
                put("personalPaidExpenses", state.financialSummary.personalPaidExpenses)
                put("fundPaidExpenses", state.financialSummary.fundPaidExpenses)
                put("totalFundCollected", state.financialSummary.totalFundCollected)
                put("remainingFund", state.financialSummary.remainingFund)
                put("balanceDiscrepancy", state.financialSummary.balanceDiscrepancy)
                put("isBalanced", state.financialSummary.isBalanced)
            })
            put("members", org.json.JSONArray().apply {
                state.memberStatuses.forEach { st ->
                    put(org.json.JSONObject().apply {
                        put("memberId", st.member.id)
                        put("name", st.member.name)
                        put("role", st.member.role)
                        put("totalPaid", st.totalPaid)
                        put("outOfPocketPaid", st.outOfPocketPaid)
                        put("fundContributed", st.fundContributed)
                        put("totalOwed", st.totalOwed)
                        put("balance", st.balance)
                        put("status", st.status.name)
                        put("bankAccount", st.member.bankAccount ?: org.json.JSONObject.NULL)
                        put("bankName", st.member.bankName ?: org.json.JSONObject.NULL)
                    })
                }
            })
            put("transfers", org.json.JSONArray().apply {
                state.settlementTransfers.forEach { tr ->
                    put(org.json.JSONObject().apply {
                        put("fromMemberId", tr.fromMember.id)
                        put("fromMemberName", tr.fromMember.name)
                        put("toMemberId", tr.toMember.id)
                        put("toMemberName", tr.toMember.name)
                        put("amount", tr.amount)
                        put("bankName", tr.toMember.bankName ?: org.json.JSONObject.NULL)
                        put("bankAccount", tr.toMember.bankAccount ?: org.json.JSONObject.NULL)
                        put("bankAccountHolder", tr.toMember.bankAccountHolder ?: org.json.JSONObject.NULL)
                        put("transferNote", tr.transferNote)
                    })
                }
            })
        }
        val settlementJson = settlementJsonObj.toString(2)

        viewModelScope.launch {
            try {
                repository.finalizeSettlement(
                    trip = currentTrip,
                    snapshotTitle = snapshotTitle.ifBlank { "Quyết toán ngày ${System.currentTimeMillis()}" },
                    summary = state.financialSummary,
                    settlementJson = settlementJson,
                    actor = currentMember
                )
                showSuccess("Đã khóa sổ và lưu Snapshot quyết toán thành công!")
            } catch (e: Exception) {
                showError("Lỗi khóa sổ quyết toán: ${e.message}")
            }
        }
    }

    fun reopenSettlement() {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (!currentTrip.isSettled) {
            showError("Chuyến đi hiện chưa khóa sổ!")
            return
        }

        if (currentMember.role != "ADMIN") {
            showError("Chỉ Trưởng đoàn (Admin) mới có quyền mở khóa sổ chuyến đi!")
            return
        }

        viewModelScope.launch {
            try {
                repository.reopenSettlement(currentTrip, currentMember)
                showSuccess("Đã mở khóa sổ chuyến đi '${currentTrip.title}' thành công! Bạn có thể tiếp tục cập nhật dữ liệu.")
            } catch (e: Exception) {
                showError("Lỗi mở khóa sổ: ${e.message}")
            }
        }
    }

    // ==========================================
    // BACKUP & RECOVERY OPERATIONS
    // ==========================================

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    fun refreshLocalBackups(context: Context) {
        viewModelScope.launch {
            _localBackups.value = repository.listLocalBackups(context)
        }
    }

    fun createLocalBackup(context: Context, password: String? = null, onCompleted: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val file = repository.createLocalBackup(context, password)
                refreshLocalBackups(context)
                val msg = if (!password.isNullOrBlank()) {
                    "Đã tạo bản sao lưu mã hóa AES-256-GCM: ${file.name}"
                } else {
                    "Đã tạo bản sao lưu an toàn: ${file.name}"
                }
                showSuccess(msg)
                onCompleted?.invoke(file)
            } catch (e: Exception) {
                showError("Lỗi tạo sao lưu: ${e.message}")
            }
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.exportBackupToUri(context, uri, password)
                if (result.isSuccess) {
                    val msg = if (!password.isNullOrBlank()) {
                        "Đã xuất tệp sao lưu mã hóa AES-256-GCM thành công!"
                    } else {
                        "Đã xuất tệp sao lưu thành công!"
                    }
                    showSuccess(msg)
                } else {
                    showError("Lỗi xuất tệp: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("Lỗi xuất sao lưu: ${e.message}")
            }
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri, clearExisting: Boolean = false, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.restoreBackupFromUri(context, uri, clearExisting, password)
                if (result.isSuccess) {
                    val report = result.getOrThrow()
                    showSuccess(report.message)
                    refreshLocalBackups(context)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Lỗi không xác định"
                    val userMsg = if (err.contains("ENCRYPTED_BACKUP_PASSWORD_REQUIRED") || err.contains("mật khẩu")) {
                        "Tệp sao lưu yêu cầu mật khẩu giải mã chính xác."
                    } else err
                    showError("Lỗi khôi phục sao lưu: $userMsg")
                }
            } catch (e: Exception) {
                showError("Lỗi khôi phục: ${e.message}")
            }
        }
    }

    fun restoreBackupFromFile(context: Context, file: File, clearExisting: Boolean = false, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.restoreBackupFromFile(file, clearExisting, password)
                if (result.isSuccess) {
                    val report = result.getOrThrow()
                    showSuccess(report.message)
                    refreshLocalBackups(context)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Lỗi không xác định"
                    val userMsg = if (err.contains("ENCRYPTED_BACKUP_PASSWORD_REQUIRED") || err.contains("mật khẩu")) {
                        "Tệp sao lưu yêu cầu mật khẩu giải mã chính xác."
                    } else err
                    showError("Lỗi khôi phục tệp: $userMsg")
                }
            } catch (e: Exception) {
                showError("Lỗi khôi phục: ${e.message}")
            }
        }
    }

    fun isEncryptedBackupFile(file: File): Boolean = _repositoryFlow.value?.isEncryptedBackupFile(file) ?: com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.isEncryptedBackupFile(file)
    fun isEncryptedBackupUri(context: Context, uri: Uri): Boolean = _repositoryFlow.value?.isEncryptedBackupUri(context, uri) ?: com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.isEncryptedBackupUri(context, uri)

    fun shareBackupFile(context: Context, file: File) {
        try {
            repository.shareBackupFile(context, file)
        } catch (e: Exception) {
            showError("Lỗi chia sẻ tệp sao lưu: ${e.message}")
        }
    }
}
