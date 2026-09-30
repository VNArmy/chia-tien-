package com.hiepnguyen.tripfinance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.SettlementSnapshotEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.ui.components.BalanceChip
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import com.hiepnguyen.tripfinance.ui.components.RoleBadge
import com.hiepnguyen.tripfinance.ui.components.VietQrTransferDialog
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    uiState: UiState,
    onFinalizeSettlement: (String) -> Unit,
    onReopenSettlement: () -> Unit = {},
    onOpenExportReport: () -> Unit
) {
    var selectedTransferForQr by remember { mutableStateOf<SettlementTransfer?>(null) }
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var showReopenDialog by remember { mutableStateOf(false) }
    var selectedSnapshotForView by remember { mutableStateOf<SettlementSnapshotEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val isAdmin = uiState.currentMember?.role == "ADMIN"
    val isBalanced = uiState.financialSummary.isBalanced
    val isSettled = uiState.currentTrip?.isSettled == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // Settlement Algorithm Overview Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quyết Toán Tối Ưu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Text(
                                text = "${uiState.settlementTransfers.size}",
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "tối ưu hóa chuyển khoản giữa các thành viên đoàn.",
                        fontSize = 11.sp,
                        color = Color(0xEEFFFFFF)
                    )
                }
            }
        }

        // Finalize Settlement & Export Report Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Khóa Sổ & Xuất Báo Cáo Đoàn", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (isSettled) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = "ĐÃ KHÓA SỔ",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isSettled)
                                    "Chuyến đi đã khóa sổ quyết toán. Dữ liệu chi tiêu & quỹ được niêm phong."
                                else
                                    "Xuất báo cáo tiếng Việt (Times New Roman / Excel CSV) và lưu snapshot quyết toán",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = onOpenExportReport,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = IndigoSecondaryContainer,
                                    contentColor = IndigoOnSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("export_report_screen_button")
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Báo cáo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (!isSettled) {
                                Button(
                                    onClick = { showFinalizeDialog = true },
                                    enabled = isAdmin && isBalanced && uiState.members.isNotEmpty() && uiState.financialSummary.balanceDiscrepancy == 0L,
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("finalize_settlement_button")
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Khóa sổ", fontSize = 11.sp)
                                }
                            } else if (isAdmin) {
                                OutlinedButton(
                                    onClick = { showReopenDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reopen_settlement_button")
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mở khóa", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (!isSettled) {
                        if (!isAdmin) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Chỉ Trưởng đoàn (Admin) mới có quyền khóa sổ chuyến đi", fontSize = 10.sp, color = Color(0xFFEF4444))
                        }
                        if (uiState.members.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Đoàn chưa có thành viên nào. Không thể khóa sổ!", fontSize = 10.sp, color = Color(0xFFEF4444))
                        } else if (!isBalanced || uiState.financialSummary.balanceDiscrepancy != 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val msg = if (uiState.financialSummary.balanceDiscrepancy > 0) {
                                "• Phát hiện chênh lệch đối soát (${NumberFormatUtils.formatVnd(uiState.financialSummary.balanceDiscrepancy)}). Khoản chi phân bổ chưa đủ hoặc có sai lệch dữ liệu. Không thể khóa sổ!"
                            } else {
                                "• Tổng số dư đoàn bị lệch hoặc dữ liệu chưa hợp lệ. Không thể khóa sổ!"
                            }
                            Text(msg, fontSize = 10.sp, color = Color(0xFFEF4444))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "🔒 Sổ chi tiêu đã được khóa. Người dùng không thể thêm, sửa, xóa các khoản chi và nộp quỹ.",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Reconciliation Error Banner (Requested for Settlement precision)
        if (uiState.reconciliationError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reconciliation_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lệch Đối Soát Quyết Toán",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = uiState.reconciliationError!!,
                                fontSize = 12.sp,
                                color = Color(0xFFB91C1C),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Simplified Transfer Instructions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kế Hoạch Chuyển Khoản Chi Tiết",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (copiedNotice != null) {
                    Text(
                        text = copiedNotice!!,
                        fontSize = 11.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (uiState.settlementTransfers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.reconciliationError != null) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.reconciliationError!!,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Chênh lệch đối soát: ${NumberFormatUtils.formatVnd(uiState.financialSummary.balanceDiscrepancy)}. Vui lòng kiểm tra lại các khoản chi để phân bổ đủ tiền cho các thành viên.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else if (!isBalanced) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Chưa thể tạo quyết toán!", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Phát hiện chênh lệch đối soát (${NumberFormatUtils.formatVnd(uiState.financialSummary.balanceDiscrepancy)}). Vui lòng kiểm tra lại các khoản chi chưa phân bổ đủ.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tất cả thành viên đã cân bằng!", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            Text("Không cần chuyển khoản bổ sung thêm", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
        } else {
            itemsIndexed(uiState.settlementTransfers) { index, transfer ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GIAO DỊCH #${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = NumberFormatUtils.formatVnd(transfer.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Transfer Flow: Debtor -> Creditor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Debtor
                            Column(modifier = Modifier.weight(1f)) {
                                val isFundSender = transfer.fromMember.id == "FUND_ORGANIZATION"
                                Text(
                                    text = if (isFundSender) "Xuất từ Quỹ chung" else "Người chuyển (Cần nộp)",
                                    fontSize = 10.sp,
                                    color = if (isFundSender) IndigoSecondary else DangerRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transfer.fromMember.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )

                            // Creditor
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                val isFundRecipient = transfer.toMember.id == "FUND_ORGANIZATION"
                                Text(
                                    text = if (isFundRecipient) "Nộp bù Quỹ chung" else "Người nhận (Được nhận)",
                                    fontSize = 10.sp,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transfer.toMember.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bank Details & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "STK: ${transfer.toMember.bankAccount ?: "Chưa có"} (${transfer.toMember.bankName ?: "Chưa rõ ngân hàng"})",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(transfer.transferNote))
                                        copiedNotice = "Đã chép nội dung CK!"
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Chép ND", fontSize = 10.sp)
                                }

                                Button(
                                    onClick = { selectedTransferForQr = transfer },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("VietQR", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full Balance Summary Table
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bảng Tổng Hợp Thu Chi Từng Người",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.memberStatuses.forEach { status ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(status.member.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    RoleBadge(role = status.member.role)
                                }
                                Text(
                                    text = "Chi hộ: ${NumberFormatUtils.formatVnd(status.outOfPocketPaid)} | Quỹ: ${NumberFormatUtils.formatVnd(status.fundContributed)} | Chịu chi: ${NumberFormatUtils.formatVnd(status.totalOwed)}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            BalanceChip(balance = status.balance, status = status.status)
                        }
                        if (status != uiState.memberStatuses.last()) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }

        // Snapshots History Section (SRS Section 4 & 2.4)
        if (uiState.snapshots.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Lịch Sử Snapshot Quyết Toán (Bất Biến)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(uiState.snapshots) { snap ->
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(snap.snapshotTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Khóa lúc: ${dateFormat.format(Date(snap.createdAt))}", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("Tổng chi: ${NumberFormatUtils.formatVnd(snap.totalExpenses)}", fontSize = 11.sp, color = EmeraldPrimary)
                        }

                        OutlinedButton(
                            onClick = { selectedSnapshotForView = snap },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Xem chi tiết", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // QR Dialog
    selectedTransferForQr?.let { tr ->
        VietQrTransferDialog(transfer = tr, onDismiss = { selectedTransferForQr = null })
    }

    // Finalize Confirmation Dialog
    if (showFinalizeDialog) {
        var snapshotTitle by remember { mutableStateOf("Quyết toán kết thúc chuyến đi ${uiState.currentTrip?.title}") }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val safeDismiss = {
            focusManager.clearFocus()
            keyboardController?.hide()
            showFinalizeDialog = false
        }

        AlertDialog(
            onDismissRequest = safeDismiss,
            properties = DialogProperties(decorFitsSystemWindows = true),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = IndigoSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xác Nhận Khóa Sổ Quyết Toán", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sau khi khóa sổ, hệ thống sẽ tạo một bản Snapshot kế toán bất biến. Dữ liệu quyết toán sẽ được bảo toàn chính xác.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = snapshotTitle,
                        onValueChange = { snapshotTitle = it },
                        label = { Text("Tên bản Snapshot") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onFinalizeSettlement(snapshotTitle)
                        showFinalizeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary)
                ) {
                    Text("Lưu Snapshot & Khóa")
                }
            },
            dismissButton = {
                TextButton(onClick = safeDismiss) {
                    Text("Hủy")
                }
            }
        )
    }

    // Reopen Settlement Dialog
    if (showReopenDialog) {
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null, tint = IndigoSecondary) },
            title = { Text("Mở Lại Sổ Chuyến Đi", fontWeight = FontWeight.Bold) },
            text = {
                Text("Bạn có chắc chắn muốn mở khóa sổ chuyến đi '${uiState.currentTrip?.title}'? Thao tác này sẽ cho phép Trưởng đoàn tiếp tục thêm, sửa, xóa các khoản chi và nộp quỹ.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReopenSettlement()
                        showReopenDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary)
                ) {
                    Text("Mở khóa sổ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Snapshot View Dialog
    selectedSnapshotForView?.let { snap ->
        val parsedJson = remember(snap.settlementJson) {
            try {
                org.json.JSONObject(snap.settlementJson)
            } catch (_: Exception) {
                null
            }
        }

        AlertDialog(
            onDismissRequest = { selectedSnapshotForView = null },
            properties = DialogProperties(decorFitsSystemWindows = true),
            title = {
                Column {
                    Text(snap.snapshotTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val dateFormatted = remember(snap.createdAt) {
                        java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("vi", "VN")).format(java.util.Date(snap.createdAt))
                    }
                    Text("Niêm phong lúc: $dateFormatted", fontSize = 11.sp, color = Color(0xFF64748B))
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (parsedJson != null) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("TỔNG QUAN TÀI CHÍNH NIÊM PHONG", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF475569))
                                    Text("• Tổng chi tiêu: ${NumberFormatUtils.formatVnd(snap.totalExpenses)}", fontSize = 12.sp)
                                    Text("• Tổng quỹ đã thu: ${NumberFormatUtils.formatVnd(snap.totalFundCollected)}", fontSize = 12.sp)
                                    Text("• Quỹ đã chi: ${NumberFormatUtils.formatVnd(snap.totalFundSpent)}", fontSize = 12.sp)
                                    Text("• Quỹ còn lại: ${NumberFormatUtils.formatVnd(snap.remainingFund)}", fontSize = 12.sp)
                                }
                            }
                        }

                        val membersArray = parsedJson.optJSONArray("members")
                        if (membersArray != null && membersArray.length() > 0) {
                            item {
                                Text("SỐ DƯ TỪNG THÀNH VIÊN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                            items(membersArray.length()) { idx ->
                                val memObj = membersArray.getJSONObject(idx)
                                val name = memObj.optString("name", "TV")
                                val role = memObj.optString("role", "MEMBER")
                                val balance = memObj.optLong("balance", 0L)
                                val balText = if (balance > 0) "+${NumberFormatUtils.formatVnd(balance)} (Nhận lại)"
                                    else if (balance < 0) "${NumberFormatUtils.formatVnd(balance)} (Cần nộp)"
                                    else "0 đ (Đã cân bằng)"
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$name ($role)", fontSize = 11.5.sp, color = Color(0xFF334155))
                                    Text(
                                        balText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (balance > 0) EmeraldPrimary else if (balance < 0) Color(0xFFEF4444) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        val transfersArray = parsedJson.optJSONArray("transfers")
                        if (transfersArray != null && transfersArray.length() > 0) {
                            item {
                                Text("KẾ HOẠCH CHUYỂN KHOẢN QUYẾT TOÁN", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E293B))
                            }
                            items(transfersArray.length()) { idx ->
                                val trObj = transfersArray.getJSONObject(idx)
                                val from = trObj.optString("fromMemberName")
                                val to = trObj.optString("toMemberName")
                                val amount = trObj.optLong("amount", 0L)
                                val note = trObj.optString("transferNote")
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("$from ➔ $to: ${NumberFormatUtils.formatVnd(amount)}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF1E40AF))
                                        if (note.isNotBlank()) {
                                            Text("Nội dung: $note", fontSize = 10.5.sp, color = Color(0xFF3B82F6))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(snap.settlementJson, fontSize = 12.sp, color = Color(0xFF1E293B))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSnapshotForView = null }) {
                    Text("Đóng")
                }
            }
        )
    }
}
