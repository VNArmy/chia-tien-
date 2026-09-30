package com.hiepnguyen.tripfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.screens.*
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.TripFinanceViewModel

enum class NavigationTab(val tabKey: String, val defaultTitle: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    DASHBOARD("DASHBOARD", "Tổng quan", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    EXPENSES("EXPENSES", "Chi tiêu", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    FUND("FUND", "Quỹ đoàn", Icons.Filled.Savings, Icons.Outlined.Savings),
    SETTLEMENT("SETTLEMENT", "Quyết toán", Icons.Filled.Payments, Icons.Outlined.Payments),
    MEMBERS("SETTINGS", "Thành viên", Icons.Filled.Group, Icons.Outlined.Group)
}

class MainActivity : ComponentActivity() {

    private val viewModel: TripFinanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TripFinanceTheme {
                val dbState by viewModel.databaseInitState.collectAsStateWithLifecycle()
                when (val state = dbState) {
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Initializing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EmeraldPrimary)
                        }
                    }
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Error -> {
                        EmergencyDatabaseRecoveryScreen(
                            error = state,
                            onRetry = { viewModel.retryDatabaseInit() },
                            onRestoreBackup = { json, password ->
                                viewModel.restoreFromEmergencyBackup(json, password)
                            },
                            onResetFresh = { viewModel.resetDatabaseFresh() }
                        )
                    }
                    is com.hiepnguyen.tripfinance.ui.viewmodel.DatabaseInitState.Ready -> {
                        TripFinanceApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFinanceApp(viewModel: TripFinanceViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiInsight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val isLoadingAi by viewModel.isLoadingAi.collectAsStateWithLifecycle()
    val showAiConsentDialog by viewModel.showAiConsentDialog.collectAsStateWithLifecycle()
    val lang = uiState.language
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    // Dialog state controllers
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddFundDialog by remember { mutableStateOf(false) }
    var showCreateTripDialog by remember { mutableStateOf(false) }
    var showExportReportDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showUserGuideScreen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessages()
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides lang) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TripFinance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Language Switcher Button in TopAppBar
                        Surface(
                            onClick = { viewModel.toggleLanguage() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (lang == AppLanguage.VI) EmeraldPrimaryContainer else Color(0xFFEDE9FE),
                            border = BorderStroke(1.dp, if (lang == AppLanguage.VI) EmeraldPrimary else Color(0xFF7C3AED)),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("toggle_language_top_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.VI) "🇻🇳 VI" else "🇬🇧 EN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lang == AppLanguage.VI) EmeraldOnPrimaryContainer else Color(0xFF5B21B6)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Export Report Button in TopAppBar
                        IconButton(
                            onClick = { showExportReportDialog = true },
                            modifier = Modifier.testTag("export_report_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = AppStrings.exportReport(lang),
                                tint = IndigoSecondary
                            )
                        }

                        // User Guide Button in TopAppBar
                        IconButton(
                            onClick = { showUserGuideScreen = true },
                            modifier = Modifier.testTag("user_guide_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = if (lang == AppLanguage.VI) "Hướng dẫn sử dụng" else "User Guide",
                                tint = Color(0xFF2563EB)
                            )
                        }

                        // Backup & Recovery Button in TopAppBar
                        IconButton(
                            onClick = { showBackupRestoreDialog = true },
                            modifier = Modifier.testTag("backup_restore_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudSync,
                                contentDescription = if (lang == AppLanguage.VI) "Sao lưu & Khôi phục" else "Backup & Recover",
                                tint = EmeraldPrimary
                            )
                        }

                        // Quick trip selector dropdown if user has multiple trips
                        var tripMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { tripMenuExpanded = true },
                                modifier = Modifier.testTag("switch_trip_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = AppStrings.selectTrip(lang),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = tripMenuExpanded,
                                onDismissRequest = { tripMenuExpanded = false }
                            ) {
                                Text(
                                    text = AppStrings.selectTrip(lang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                uiState.allTrips.forEach { trip ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = trip.title,
                                                fontWeight = if (trip.id == uiState.currentTrip?.id) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectTrip(trip.id)
                                            tripMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (trip.id == uiState.currentTrip?.id) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary)
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(AppStrings.createNewTrip(lang)) },
                                    onClick = {
                                        showCreateTripDialog = true
                                        tripMenuExpanded = false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabTitle = AppStrings.getTabName(tab.name, lang)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tabTitle
                                )
                            },
                            label = {
                                Text(
                                    text = tabTitle,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldPrimary,
                                selectedTextColor = EmeraldPrimary,
                                indicatorColor = EmeraldPrimaryContainer
                            )
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Content per selected tab
                when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        onNavigateToExpenses = { currentTab = NavigationTab.EXPENSES },
                        onNavigateToFund = { currentTab = NavigationTab.FUND },
                        onNavigateToSettlement = { currentTab = NavigationTab.SETTLEMENT },
                        onRequestAiInsight = { viewModel.requestAiSpendingInsight() },
                        onOpenAddExpense = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddExpenseDialog = true
                            }
                        },
                        onOpenAddFund = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddFundDialog = true
                            }
                        },
                        onOpenExportReport = { showExportReportDialog = true },
                        onOpenCreateTrip = { showCreateTripDialog = true },
                        onOpenUserGuide = { showUserGuideScreen = true },
                        onSwitchUser = { memberId -> viewModel.switchUserPersona(memberId) },
                        onSelectTrip = { tripId -> viewModel.selectTrip(tripId) },
                        onEditTrip = { trip, title, desc, start, end ->
                            viewModel.editTrip(trip.id, title, desc, start, end)
                        },
                        onDeleteTrip = { trip -> viewModel.deleteTrip(trip.id) }
                    )
                    NavigationTab.EXPENSES -> ExpensesScreen(
                        uiState = uiState,
                        onOpenAddExpense = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddExpenseDialog = true
                            }
                        },
                        onEditExpense = { expId, title, cat, payerType, payerId, amount, curr, rate, splitType, splits, note, time ->
                            viewModel.editExpense(expId, title, cat, payerType, payerId, amount, curr, rate, splitType, splits, note, time)
                        },
                        onDeleteExpense = { exp -> viewModel.deleteExpense(exp) },
                        onCategoryFilterChange = { cat -> viewModel.setCategoryFilter(cat) },
                        onSearchChange = { q -> viewModel.setSearchQuery(q) }
                    )
                    NavigationTab.FUND -> FundScreen(
                        uiState = uiState,
                        onOpenAddFund = {
                            if (uiState.currentTrip?.isSettled != true) {
                                showAddFundDialog = true
                            }
                        }
                    )
                    NavigationTab.SETTLEMENT -> SettlementScreen(
                        uiState = uiState,
                        onFinalizeSettlement = { title -> viewModel.finalizeSettlement(title) },
                        onReopenSettlement = { viewModel.reopenSettlement() },
                        onOpenExportReport = { showExportReportDialog = true }
                    )
                    NavigationTab.MEMBERS -> MembersAndSettingsScreen(
                        uiState = uiState,
                        onAddMember = { name, role, bank, acc, holder ->
                            viewModel.addMember(name, role, bank, acc, holder)
                        },
                        onEditMember = { member, name, role, bank, acc, holder ->
                            viewModel.editMember(member, name, role, bank, acc, holder)
                        },
                        onUpdateRole = { member, newRole ->
                            viewModel.updateMemberRole(member, newRole)
                        },
                        onRemoveOrDeactivate = { member ->
                            viewModel.removeOrDeactivateMember(member)
                        },
                        onUpdateRate = { code, rate ->
                            viewModel.updateExchangeRate(code, rate)
                        },
                        onSelectLanguage = { newLang ->
                            viewModel.setLanguage(newLang)
                        },
                        onOpenUserGuide = { showUserGuideScreen = true },
                        onOpenBackupRestore = { showBackupRestoreDialog = true }
                    )
                }
            }
        }
    }

    // Modal Dialogs
    if (showBackupRestoreDialog) {
        BackupRestoreDialog(
            viewModel = viewModel,
            onDismiss = { showBackupRestoreDialog = false }
        )
    }

    if (showExportReportDialog) {
        ExportReportDialog(
            uiState = uiState,
            onDismiss = { showExportReportDialog = false }
        )
    }

    // Modal Dialogs
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            members = uiState.members,
            exchangeRates = uiState.exchangeRates,
            currentMemberId = uiState.currentMember?.id,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, category, payerType, payerMemberId, amount, currency, rate, splitType, splits, note, timestamp ->
                viewModel.addExpense(
                    title, category, payerType, payerMemberId, amount, currency, rate, splitType, splits, note, timestamp
                )
            }
        )
    }

    if (showAddFundDialog) {
        AddFundContributionDialog(
            members = uiState.members,
            onDismiss = { showAddFundDialog = false },
            onSave = { memberId, amount, currency, rate, note ->
                viewModel.addFundContribution(memberId, amount, currency, rate, note)
            }
        )
    }

    if (showCreateTripDialog) {
        CreateTripDialog(
            onDismiss = { showCreateTripDialog = false },
            onConfirm = { title, desc, code, start, end, admin, bName, bAcc, bHolder ->
                viewModel.createTrip(title, desc, code, start, end, admin, bName, bAcc, bHolder)
                showCreateTripDialog = false
            }
        )
    }

    if (showUserGuideScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showUserGuideScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                UserGuideScreen(
                    onBack = { showUserGuideScreen = false }
                )
            }
        }
    }

    if (showAiConsentDialog) {
        com.hiepnguyen.tripfinance.ui.components.AiConsentDialog(
            onConfirmCloudAi = { viewModel.onUserConfirmAiCloudConsent() },
            onChooseOfflineOnly = { viewModel.onUserChooseOfflineAiInsight() },
            onDismiss = { viewModel.dismissAiConsentDialog() }
        )
    }

    if (aiInsight != null || isLoadingAi) {
        com.hiepnguyen.tripfinance.ui.components.AiSpendingAdvisorDialog(
            insight = aiInsight,
            isLoading = isLoadingAi,
            onDismiss = { viewModel.clearAiInsight() }
        )
    }
}

