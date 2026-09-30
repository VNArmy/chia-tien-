package com.hiepnguyen.tripfinance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.ui.components.*
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: UiState,
    onNavigateToExpenses: () -> Unit,
    onNavigateToFund: () -> Unit,
    onNavigateToSettlement: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenAddFund: () -> Unit,
    onOpenExportReport: () -> Unit,
    onOpenCreateTrip: () -> Unit,
    onOpenUserGuide: () -> Unit = {},
    onRequestAiInsight: () -> Unit = {},
    onSwitchUser: (String) -> Unit,
    onSelectTrip: (String) -> Unit = {},
    onEditTrip: (TripEntity, String, String, Long, Long) -> Unit = { _, _, _, _, _ -> },
    onDeleteTrip: (TripEntity) -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val isSettled = uiState.currentTrip?.isSettled == true
    var showUserPicker by remember { mutableStateOf(false) }
    var tripToEdit by remember { mutableStateOf<TripEntity?>(null) }
    var tripToDelete by remember { mutableStateOf<TripEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // Trip Banner & Persona Switcher
        item {
            TripHeaderCard(
                uiState = uiState,
                onOpenCreateTrip = onOpenCreateTrip,
                onOpenUserPicker = { showUserPicker = true },
                onSelectTrip = onSelectTrip
            )
        }

        if (uiState.allTrips.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.CardTravel,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chưa Có Đoàn Công Tác Nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ứng dụng sạch sẽ và sẵn sàng sử dụng. Bắt đầu bằng cách tạo đoàn công tác mới để quản lý chi tiêu và chia tiền.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onOpenCreateTrip,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tạo Đoàn Công Tác Mới")
                        }
                    }
                }
            }
        }

        if (isSettled) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sổ Chuyến Đi Đã Được Khóa Quyết Toán",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Mọi khoản chi tiêu và nộp quỹ đã được niêm phong an toàn để đối soát và chuyển khoản.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        // User Guide Banner Link
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenUserGuide() }
                    .testTag("dashboard_user_guide_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (lang == AppLanguage.VI) "Cẩm Nang Hướng Dẫn Sử Dụng" else "User Guide & Handbook",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                text = if (lang == AppLanguage.VI)
                                    "Xem quy trình 4 bước, cách chia tiền, quét VietQR & ngoại tệ"
                                else
                                    "Learn workflows, split methods, VietQR & currency rates",
                                fontSize = 11.sp,
                                color = Color(0xFF3B82F6),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF3B82F6)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == AppLanguage.VI) "Xem ngay" else "Read",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.currentTrip != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF5F3FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRequestAiInsight() }
                        .testTag("dashboard_ai_advisor_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == AppLanguage.VI) "AI Cố Vấn Chi Tiêu (Gemini)" else "Gemini Financial Advisor",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4C1D95)
                                )
                                Text(
                                    text = if (lang == AppLanguage.VI) "Phân tích ngân sách, cơ cấu chi tiêu và sức khỏe quỹ" else "Analyze budget, category split & fund health",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6D28D9)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF7C3AED)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.VI) "Phân tích" else "Analyze",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Financial Overview Cards
        item {
            Text(
                text = "Tổng Quan Tài Chính",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 1: Total Expenses & Sources
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = EmeraldOnPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "TỔNG CHI TIÊU ĐOÀN",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatVnd(uiState.financialSummary.totalExpenses),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onNavigateToExpenses,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Chi tiết", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Breakdown of expense source: Member paid vs Fund paid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Thành viên chi hộ (Paid)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = NumberFormatUtils.formatVnd(uiState.financialSummary.personalPaidExpenses),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Chi từ Quỹ chung",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = NumberFormatUtils.formatVnd(uiState.financialSummary.fundPaidExpenses),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberTertiary
                                )
                            }
                        }
                    }
                }

                // Card 2: Group Fund (Quỹ đoàn)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AmberTertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Savings,
                                        contentDescription = null,
                                        tint = AmberOnTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SỐ DƯ QUỸ CHUNG",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatVnd(uiState.financialSummary.remainingFund),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberTertiary
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onNavigateToFund,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Quỹ đoàn", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Đã thu quỹ: ${NumberFormatUtils.formatVnd(uiState.financialSummary.totalFundCollected)}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Đã chi quỹ: ${NumberFormatUtils.formatVnd(uiState.financialSummary.fundPaidExpenses)}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Card 3: Single Source of Truth Reconciliation Badge (SRS Section 2.4)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.financialSummary.isBalanced) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.financialSummary.isBalanced) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (uiState.financialSummary.isBalanced) Color(0xFF059669) else Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (uiState.financialSummary.isBalanced) {
                                        "Đối soát hợp lệ: Tổng Balance = 0đ"
                                    } else {
                                        "Cảnh báo: Chênh lệch ${NumberFormatUtils.formatVnd(uiState.financialSummary.balanceDiscrepancy)}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.financialSummary.isBalanced) Color(0xFF065F46) else Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Thuật toán kiểm tra liên tục đảm bảo không thất thoát",
                                    fontSize = 10.sp,
                                    color = if (uiState.financialSummary.isBalanced) Color(0xFF047857) else Color(0xFFB91C1C)
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSettlement,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                "Quyết toán",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.financialSummary.isBalanced) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onOpenAddExpense,
                    enabled = !isSettled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_add_expense_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (lang == AppLanguage.VI) "Thêm Chi" else "Expense",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenAddFund,
                    enabled = !isSettled && (uiState.currentMember?.role == "ADMIN" || uiState.currentMember?.role == "TREASURER"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmberTertiaryContainer,
                        contentColor = AmberOnTertiaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_add_fund_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (lang == AppLanguage.VI) "Nộp Quỹ" else "Fund",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenExportReport,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = IndigoSecondaryContainer,
                        contentColor = IndigoOnSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_export_report_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (lang == AppLanguage.VI) "Báo Cáo" else "Report",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onOpenUserGuide,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFEFF6FF),
                        contentColor = Color(0xFF1D4ED8)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp)
                        .testTag("quick_user_guide_button")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (lang == AppLanguage.VI) "Hướng Dẫn" else "Guide",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Category Breakdown
        if (uiState.categoryBreakdowns.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == AppLanguage.VI) "Phân Loại Chi Tiêu" else "Category Breakdown",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.expenses.size} ${if (lang == AppLanguage.VI) "khoản chi" else "expenses"}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.categoryBreakdowns.forEach { cat ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIcon(category = cat.category, size = 16)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cat.labelVi,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = " (${cat.count})",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = "${NumberFormatUtils.formatVnd(cat.totalAmount)} (${cat.percentage.toInt()}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (cat.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = EmeraldPrimary,
                                    trackColor = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Member Balances Summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bảng Số Dư Thành Viên",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = onNavigateToSettlement) {
                    Text("Xem quyết toán", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    uiState.memberStatuses.forEach { status ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = status.member.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BalanceChip(balance = status.balance, status = status.status)
                                Text(
                                    text = "Đã chi: ${NumberFormatUtils.formatVnd(status.totalPaid)} • Phải trả: ${NumberFormatUtils.formatVnd(status.totalOwed)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        if (status != uiState.memberStatuses.last()) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }
    }

    // Role / Persona Switcher Dialog
    if (showUserPicker) {
        AlertDialog(
            onDismissRequest = { showUserPicker = false },
            title = {
                Text(
                    text = "Chuyển Đổi Người Dùng / Vai Trò",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Chọn nhân vật để trải nghiệm phân quyền đầy đủ (Admin, Thủ quỹ, Thành viên, Người xem):",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    uiState.members.forEach { m ->
                        val isSelected = m.id == uiState.currentMember?.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) EmeraldPrimaryContainer else NeutralLightSurfaceCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchUser(m.id)
                                    showUserPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = m.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) EmeraldOnPrimaryContainer else Color(0xFF0F172A)
                                    )
                                    RoleBadge(role = m.role)
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserPicker = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}

@Composable
fun TripHeaderCard(
    uiState: UiState,
    onOpenCreateTrip: () -> Unit,
    onOpenUserPicker: () -> Unit,
    onSelectTrip: (String) -> Unit
) {
    val lang = LocalAppLanguage.current
    var showTripDropdown by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Top: Offline Badge + Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (lang == AppLanguage.VI) "DỮ LIỆU OFFLINE CỤC BỘ" else "LOCAL OFFLINE STORAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onOpenCreateTrip,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0x33FFFFFF),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tạo đoàn mới", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prominent Dropdown Selector for Active Business Trip
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x26000000),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTripDropdown = true }
                        .testTag("dashboard_trip_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.FlightTakeoff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lang == AppLanguage.VI) "ĐOÀN CÔNG TÁC ĐANG CHỌN" else "SELECTED TRIP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCCFBF1)
                                )
                                Text(
                                    text = uiState.currentTrip?.title ?: if (lang == AppLanguage.VI) "Chưa chọn đoàn nào" else "No trip selected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = uiState.currentTrip?.description ?: if (lang == AppLanguage.VI) "Chạm để đổi đoàn công tác" else "Tap to switch trip",
                                    fontSize = 11.sp,
                                    color = Color(0xCCFFFFFF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Dropdown Arrow Indicator Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.VI) "Đổi đoàn" else "Switch",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Danh sách xổ xuống",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Dropdown Menu List
                DropdownMenu(
                    expanded = showTripDropdown,
                    onDismissRequest = { showTripDropdown = false },
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Header of the dropdown list
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == AppLanguage.VI) "Chọn Đoàn Công Tác" else "Select Business Trip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.allTrips.size} ${if (lang == AppLanguage.VI) "đoàn" else "trips"}",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (uiState.allTrips.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (lang == AppLanguage.VI) "Chưa có đoàn nào trong danh sách" else "No trips available",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            onClick = { showTripDropdown = false }
                        )
                    } else {
                        uiState.allTrips.forEach { trip ->
                            val isSelected = trip.id == uiState.currentTrip?.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = trip.title,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (trip.description.isNotBlank()) trip.description else if (lang == AppLanguage.VI) "Lưu trữ cục bộ" else "Local storage",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Đang chọn",
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectTrip(trip.id)
                                    showTripDropdown = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.FlightTakeoff else Icons.Outlined.WorkOutline,
                                        contentDescription = null,
                                        tint = if (isSelected) EmeraldPrimary else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Quick Actions in dropdown menu
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (lang == AppLanguage.VI) "+ Tạo đoàn công tác mới" else "+ Create New Trip",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showTripDropdown = false
                            onOpenCreateTrip()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0x33FFFFFF))
            Spacer(modifier = Modifier.height(8.dp))

            // User Persona switcher bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenUserPicker() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bạn: ${uiState.currentMember?.name ?: "Khách"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Đổi vai trò",
                        fontSize = 11.sp,
                        color = Color(0xFFCCFBF1),
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFCCFBF1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
