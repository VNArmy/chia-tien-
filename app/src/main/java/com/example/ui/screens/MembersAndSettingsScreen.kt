package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.ExchangeRateEntity
import com.example.data.entity.TripMemberEntity
import com.example.ui.components.RoleBadge
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LocalAppLanguage
import com.example.ui.theme.*
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersAndSettingsScreen(
    uiState: UiState,
    onAddMember: (name: String, role: String, bankName: String?, bankAccount: String?, holder: String?) -> Unit,
    onEditMember: (member: TripMemberEntity, name: String, role: String, bankName: String?, bankAccount: String?, holder: String?) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateRole: (TripMemberEntity, String) -> Unit,
    onRemoveOrDeactivate: (TripMemberEntity) -> Unit,
    onUpdateRate: (currencyCode: String, rate: Double) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onOpenUserGuide: () -> Unit = {},
    onOpenBackupRestore: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val clipboardManager = LocalClipboardManager.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<TripMemberEntity?>(null) }
    var memberToDelete by remember { mutableStateOf<TripMemberEntity?>(null) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var copiedCodeNotice by remember { mutableStateOf<String?>(null) }

    val currentMember = uiState.currentMember
    val isAdmin = currentMember?.role == "ADMIN"
    val isTreasurer = currentMember?.role == "TREASURER"
    val isSettled = uiState.currentTrip?.isSettled == true
    val canManageMembers = !isSettled && (isAdmin || isTreasurer)
    val canViewAuditLogs = isAdmin || isTreasurer // SRS 5: Member KHÔNG được xem Audit Log kỹ thuật

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
    ) {
        // User Guide Quick Link
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenUserGuide() }
                    .testTag("settings_open_user_guide_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (lang == AppLanguage.VI) "Hướng Dẫn Sử Dụng & FAQ" else "User Guide & FAQ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lang == AppLanguage.VI)
                                    "Cẩm nang chi tiết: tạo đoàn, chia tiền, quỹ, quyết toán VietQR, ngoại tệ"
                                else
                                    "Detailed handbook: trip setup, expense split, fund, VietQR, multi-currency",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Language Settings Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == AppLanguage.VI) "NGÔN NGỮ HIỂN THỊ (LANGUAGE)" else "DISPLAY LANGUAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isVi = lang == AppLanguage.VI
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isVi) EmeraldPrimaryContainer else Color(0xFFF8FAFC),
                            border = BorderStroke(if (isVi) 1.5.dp else 1.dp, if (isVi) EmeraldPrimary else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLanguage(AppLanguage.VI) }
                                .testTag("language_vi_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("🇻🇳", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tiếng Việt",
                                    fontWeight = if (isVi) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isVi) EmeraldOnPrimaryContainer else Color(0xFF334155)
                                )
                                if (isVi) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        val isEn = lang == AppLanguage.EN
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEn) EmeraldPrimaryContainer else Color(0xFFF8FAFC),
                            border = BorderStroke(if (isEn) 1.5.dp else 1.dp, if (isEn) EmeraldPrimary else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectLanguage(AppLanguage.EN) }
                                .testTag("language_en_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text("🇬🇧", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "English",
                                    fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isEn) EmeraldOnPrimaryContainer else Color(0xFF334155)
                                )
                                if (isEn) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offline & Data Storage Notice Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == AppLanguage.VI) "CHẾ ĐỘ HOẠT ĐỘNG THUẦN OFFLINE" else "PURE OFFLINE OPERATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (lang == AppLanguage.VI)
                            "Ứng dụng hoạt động hoàn toàn offline, 100% dữ liệu tài chính, chi tiêu và quỹ được lưu trữ cục bộ, bảo mật tuyệt đối trên thiết bị của bạn. Không cần kết nối internet hay tài khoản."
                        else
                            "The app operates completely offline. 100% of your financial, expense, and fund data is stored locally and securely on your device with no internet connection or accounts required.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Backup & Recovery Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoSecondary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, IndigoSecondary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBackupRestore() }
                    .testTag("open_backup_restore_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(IndigoSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (lang == AppLanguage.VI) "Sao Lưu & Khôi Phục CSDL" else "Backup & Data Recovery",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (lang == AppLanguage.VI) "Xuất JSON, phục hồi khi nâng cấp ứng dụng hoặc đổi máy" else "Export JSON, restore on app upgrade or device change",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = IndigoSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Privacy Policy Card (Google Play User Data Policy Compliance)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPrivacyPolicyDialog = true }
                    .testTag("open_privacy_policy_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (lang == AppLanguage.VI) "Chính Sách Quyền Riêng Tư & Bảo Mật" else "Privacy & Data Protection Policy",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (lang == AppLanguage.VI) "Bảo vệ thông tin tài chính & AI theo chuẩn Google Play" else "Financial data & AI security per Google Play policies",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Quick Management Utilities (Exchange Rates & Audit Logs)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showRateDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${if (lang == AppLanguage.VI) "Tỷ giá" else "Exchange Rates"} (${uiState.exchangeRates.size})", fontSize = 12.sp)
                }

                Button(
                    onClick = { showAuditLogsDialog = true },
                    enabled = canViewAuditLogs,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.HistoryEdu, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Audit Log", fontSize = 12.sp)
                }
            }
        }

        // Members Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (lang == AppLanguage.VI) "Danh Sách Thành Viên" else "Trip Members"} (${uiState.members.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (canManageMembers) {
                    FilledTonalButton(
                        onClick = { showAddMemberDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("add_member_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.add(lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Member Cards List
        items(uiState.members) { member ->
            var expandedRoleMenu by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (member.isActive) MaterialTheme.colorScheme.surface else Color(0xFFF8FAFC)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                    .background(if (member.isActive) EmeraldPrimaryContainer else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = if (member.isActive) EmeraldOnPrimaryContainer else Color(0xFF64748B),
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = member.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!member.isActive) {
                                    Text(
                                        text = if (lang == AppLanguage.VI) "• Đã ngừng hoạt động" else "• Deactivated",
                                        fontSize = 10.sp,
                                        color = DangerRed
                                    )
                                }
                            }
                        }

                        // Role Tag / Role Selector
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = isAdmin) { expandedRoleMenu = true }
                            ) {
                                RoleBadge(role = member.role)
                                if (isAdmin) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = expandedRoleMenu,
                                onDismissRequest = { expandedRoleMenu = false }
                            ) {
                                listOf(
                                    "ADMIN" to if (lang == AppLanguage.VI) "Trưởng đoàn (Admin)" else "Admin",
                                    "TREASURER" to if (lang == AppLanguage.VI) "Thủ quỹ (Treasurer)" else "Treasurer",
                                    "MEMBER" to if (lang == AppLanguage.VI) "Thành viên (Member)" else "Member",
                                    "VIEWER" to if (lang == AppLanguage.VI) "Người xem (Viewer)" else "Viewer"
                                ).forEach { (rKey, rLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(rLabel) },
                                        onClick = {
                                            onUpdateRole(member, rKey)
                                            expandedRoleMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Bank Account details & Action buttons (Edit & Delete for Admin)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${if (lang == AppLanguage.VI) "STK" else "Account"}: ${member.bankAccount ?: if (lang == AppLanguage.VI) "Chưa cập nhật" else "Not updated"} (${member.bankName ?: if (lang == AppLanguage.VI) "Chưa có" else "N/A"})",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            if (!member.bankAccountHolder.isNullOrBlank()) {
                                Text(
                                    text = "${if (lang == AppLanguage.VI) "Chủ TK" else "Holder"}: ${member.bankAccountHolder}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        if (isAdmin) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { memberToEdit = member },
                                    modifier = Modifier.size(28.dp).testTag("edit_member_${member.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = AppStrings.edit(lang),
                                        tint = IndigoSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (member.id != currentMember?.id) {
                                    IconButton(
                                        onClick = { memberToDelete = member },
                                        modifier = Modifier.size(28.dp).testTag("delete_member_${member.id}")
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = AppStrings.delete(lang),
                                            tint = DangerRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Member Dialog
    memberToEdit?.let { mem ->
        EditMemberDialog(
            member = mem,
            onDismiss = { memberToEdit = null },
            onSave = { name, role, bankName, bankAccount, holder ->
                onEditMember(mem, name, role, bankName, bankAccount, holder)
                memberToEdit = null
            }
        )
    }

    // Delete Member Confirmation Dialog
    memberToDelete?.let { mem ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(
                    text = if (lang == AppLanguage.VI) "Xác Nhận Xóa / Vô Hiệu Hóa Thành Viên" else "Confirm Remove / Deactivate Member",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (lang == AppLanguage.VI)
                        "Bạn có chắc chắn muốn xử lý thành viên '${mem.name}'? Nếu thành viên đã có lịch sử chi tiêu hoặc nộp quỹ, hệ thống sẽ chuyển sang trạng thái Ngừng hoạt động để bảo toàn số liệu tài chính."
                    else
                        "Are you sure you want to remove '${mem.name}'? If this member has expense or fund records, their status will be set to Deactivated to preserve financial integrity."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveOrDeactivate(mem)
                        memberToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_member_button")
                ) {
                    Text(AppStrings.confirm(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text(AppStrings.cancel(lang))
                }
            }
        )
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        AddMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onSave = { name, role, bankName, bankAccount, holder ->
                onAddMember(name, role, bankName, bankAccount, holder)
                showAddMemberDialog = false
            }
        )
    }

    // Exchange Rates Dialog
    if (showRateDialog) {
        ExchangeRatesDialog(
            rates = uiState.exchangeRates,
            canEdit = canManageMembers,
            onDismiss = { showRateDialog = false },
            onSave = { code, rate ->
                onUpdateRate(code, rate)
            }
        )
    }

    // Audit Logs Dialog
    if (showAuditLogsDialog) {
        AuditLogsDialog(
            logs = uiState.auditLogs,
            onDismiss = { showAuditLogsDialog = false }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }
}

@Composable
fun AddMemberDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, bankName: String?, bankAccount: String?, holder: String?) -> Unit
) {
    val lang = LocalAppLanguage.current
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("MEMBER") }
    var bankName by remember { mutableStateOf("Vietcombank") }
    var bankAccount by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = { 
            Text(
                text = if (lang == AppLanguage.VI) "Thêm Thành Viên Vào Đoàn" else "Add Member To Trip",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (lang == AppLanguage.VI) "Họ và tên thành viên *" else "Full Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (lang == AppLanguage.VI) "Vai trò:" else "Role:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "MEMBER" to if (lang == AppLanguage.VI) "Thành viên" else "Member",
                        "TREASURER" to if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer",
                        "VIEWER" to if (lang == AppLanguage.VI) "Xem" else "Viewer"
                    ).forEach { (rKey, rLabel) ->
                        FilterChip(
                            selected = role == rKey,
                            onClick = { role = rKey },
                            label = { Text(rLabel, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(if (lang == AppLanguage.VI) "Tên Ngân hàng (VD: MBBank, VCB...)" else "Bank Name (e.g. MBBank, Chase...)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bankAccount,
                    onValueChange = { bankAccount = it },
                    label = { Text(if (lang == AppLanguage.VI) "Số tài khoản nhận tiền" else "Bank Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = holder,
                    onValueChange = { holder = it },
                    label = { Text(if (lang == AppLanguage.VI) "Chủ tài khoản (Không dấu)" else "Account Holder Name") },
                    singleLine = true,
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
                    onSave(name, role, bankName.takeIf { it.isNotBlank() }, bankAccount.takeIf { it.isNotBlank() }, holder.takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(AppStrings.add(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancel(lang)) }
        }
    )
}

@Composable
fun ExchangeRatesDialog(
    rates: List<ExchangeRateEntity>,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    val lang = LocalAppLanguage.current
    var selectedCode by remember { mutableStateOf("USD") }
    var rateValueText by remember { mutableStateOf("25450") }

    val rateValidation = com.example.domain.model.FinancialInputValidator.parseRate(rateValueText, selectedCode)
    val isRateValid = rateValidation is com.example.domain.model.RateValidationResult.Success

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLanguage.VI) "Quản Lý Tỷ Giá Ngoại Tệ" else "Exchange Rates Management",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == AppLanguage.VI) "Tỷ giá quy đổi cố định theo đoàn (Base: VND):" else "Trip exchange rates against base currency (VND):",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                rates.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1 ${r.currencyCode}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("= ${r.rateToBase} VND", fontSize = 14.sp, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (canEdit) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = if (lang == AppLanguage.VI) "Cập nhật tỷ giá mới:" else "Update exchange rate:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("USD", "EUR", "JPY", "THB", "SGD", "CNY").forEach { c ->
                            FilterChip(
                                selected = selectedCode == c,
                                onClick = { selectedCode = c },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = rateValueText,
                        onValueChange = { rateValueText = it },
                        isError = rateValueText.isNotEmpty() && !isRateValid,
                        supportingText = {
                            if (rateValueText.isNotEmpty() && rateValidation is com.example.domain.model.RateValidationResult.Error) {
                                Text(rateValidation.message, color = DangerRed, fontSize = 11.sp)
                            }
                        },
                        label = { Text("1 $selectedCode = ? VND", color = Color(0xFFDC2626)) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFDC2626),
                            unfocusedTextColor = Color(0xFFDC2626),
                            focusedBorderColor = Color(0xFFDC2626),
                            unfocusedBorderColor = Color(0xFFF87171),
                            focusedLabelColor = Color(0xFFDC2626),
                            unfocusedLabelColor = Color(0xFFDC2626),
                            cursorColor = Color(0xFFDC2626)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val parsed = (rateValidation as? com.example.domain.model.RateValidationResult.Success)?.rate
                        if (parsed != null && parsed > 0) {
                            onSave(selectedCode, parsed)
                            onDismiss()
                        }
                    },
                    enabled = isRateValid,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(if (lang == AppLanguage.VI) "Lưu tỷ giá" else "Save Rate")
                }
            } else {
                TextButton(onClick = safeDismiss) { Text(AppStrings.close(lang)) }
            }
        },
        dismissButton = {
            if (canEdit) TextButton(onClick = safeDismiss) { Text(AppStrings.close(lang)) }
        }
    )
}

@Composable
fun AuditLogsDialog(
    logs: List<AuditLogEntity>,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = IndigoSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLanguage.VI) "Nhật Ký Kiểm Toán (Audit Logs)" else "Audit Logs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = if (lang == AppLanguage.VI) "Chưa có bản ghi nhật ký nào." else "No audit log entries found.",
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = NeutralLightSurfaceCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoSecondary)
                                    Text(dateFormat.format(Date(log.timestamp)), fontSize = 9.sp, color = Color(0xFF94A3B8))
                                }
                                Text(log.description, fontSize = 12.sp, color = Color(0xFF1E293B))
                                Text(
                                    text = "${if (lang == AppLanguage.VI) "Thực hiện bởi" else "By"}: ${log.actorName}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.close(lang)) }
        }
    )
}

@Composable
fun EditMemberDialog(
    member: TripMemberEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, bankName: String?, bankAccount: String?, holder: String?) -> Unit
) {
    val lang = LocalAppLanguage.current
    var name by remember { mutableStateOf(member.name) }
    var role by remember { mutableStateOf(member.role) }
    var bankName by remember { mutableStateOf(member.bankName ?: "Vietcombank") }
    var bankAccount by remember { mutableStateOf(member.bankAccount ?: "") }
    var holder by remember { mutableStateOf(member.bankAccountHolder ?: member.name) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = IndigoSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLanguage.VI) "Chỉnh Sửa Thông Tin Thành Viên" else "Edit Member Info",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (lang == AppLanguage.VI) "Họ và tên thành viên *" else "Full Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("edit_member_name_input")
                )

                Text(
                    text = if (lang == AppLanguage.VI) "Vai trò:" else "Role:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "ADMIN" to if (lang == AppLanguage.VI) "Trưởng đoàn" else "Admin",
                        "TREASURER" to if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer",
                        "MEMBER" to if (lang == AppLanguage.VI) "Thành viên" else "Member",
                        "VIEWER" to if (lang == AppLanguage.VI) "Xem" else "Viewer"
                    ).forEach { (rKey, rLabel) ->
                        FilterChip(
                            selected = role == rKey,
                            onClick = { role = rKey },
                            label = { Text(rLabel, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(if (lang == AppLanguage.VI) "Tên Ngân hàng" else "Bank Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bankAccount,
                    onValueChange = { bankAccount = it },
                    label = { Text(if (lang == AppLanguage.VI) "Số tài khoản nhận tiền" else "Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = holder,
                    onValueChange = { holder = it },
                    label = { Text(if (lang == AppLanguage.VI) "Chủ tài khoản (Không dấu)" else "Account Holder") },
                    singleLine = true,
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
                    onSave(name, role, bankName.takeIf { it.isNotBlank() }, bankAccount.takeIf { it.isNotBlank() }, holder.takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("save_edit_member_button")
            ) {
                Text(AppStrings.save(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancel(lang)) }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        icon = {
            Icon(
                Icons.Filled.Security,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (lang == AppLanguage.VI) "Chính Sách Quyền Riêng Tư & An Toàn Dữ Liệu" else "Privacy & Data Safety Policy",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (lang == AppLanguage.VI) "1. Kiến trúc Sandbox Cục bộ (Local-First)" else "1. Local-First Sandbox Architecture",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (lang == AppLanguage.VI)
                            "Ứng dụng TripFinance lưu trữ 100% dữ liệu chi tiêu, danh sách thành viên và quỹ đoàn trong cơ sở dữ liệu SQLite cục bộ được mã hóa theo phân vùng bảo mật riêng của ứng dụng (Android App Sandbox). Ứng dụng đã vô hiệu hóa sao lưu đám mây tự động (allowBackup=false) để bảo vệ toàn vẹn dữ liệu cá nhân."
                        else
                            "TripFinance stores 100% of expense, member, and fund records inside an encrypted local SQLite database within the Android Application Sandbox. Automated cloud backup is explicitly disabled (allowBackup=false) to protect user data privacy.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = if (lang == AppLanguage.VI) "2. Dữ liệu Tài chính & Số Tài Khoản Ngân Hàng" else "2. Financial Data & Bank Account Numbers",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (lang == AppLanguage.VI)
                            "Thông tin số tài khoản và ngân hàng do người dùng nhập chỉ được sử dụng nội bộ nhằm tạo lệnh chuyển khoản quyết toán giữa các thành viên. Hệ thống cam kết không bao giờ thu thập, bán hay chia sẻ số tài khoản cho bất kỳ bên thứ ba hay mạng quảng cáo nào."
                        else
                            "Bank account numbers and bank names entered by users are strictly used locally to generate peer-to-peer settlement transfer instructions. TripFinance never transmits, sells, or monetizes this financial info to third parties or ad networks.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = if (lang == AppLanguage.VI) "3. Tính năng Cố Vấn Tài Chính Gemini AI" else "3. Gemini AI Financial Advisor Feature",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (lang == AppLanguage.VI)
                            "Khi bạn sử dụng tính năng 'Cố vấn chi tiêu AI', hệ thống chỉ gửi dữ liệu số học tổng hợp ẩn danh (tổng chi, danh mục chi, số dư tương đối) qua kết nối HTTPS bảo mật đến Google Gemini API. Dữ liệu gửi đi TUYỆT ĐỐI KHÔNG chứa số tài khoản ngân hàng hay thông tin định danh nhạy cảm. Khuyến nghị AI chỉ mang tính tham khảo."
                        else
                            "When using the 'AI Spending Advisor', only aggregated, anonymized financial metrics (total spend, categories, relative balances) are securely sent over HTTPS to the Google Gemini API. Bank account numbers and personal identities are NEVER sent to the AI service. AI insights are advisory only.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                item {
                    Text(
                        text = if (lang == AppLanguage.VI) "4. Quyền Hạn Thiết Bị & Lưu Trữ (Zero-Permission SAF)" else "4. Permissions & Storage Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (lang == AppLanguage.VI)
                            "TripFinance tuân thủ chính sách bảo mật Google Play: Không yêu cầu quyền đọc/ghi bộ nhớ ngoài diện rộng (READ_EXTERNAL_STORAGE). Mọi thao tác xuất báo cáo PDF/Excel và sao lưu JSON đều sử dụng khung lưu trữ an toàn tiêu chuẩn Android Storage Access Framework (SAF)."
                        else
                            "TripFinance adheres to Google Play policy: No broad external storage permissions requested. All PDF/Excel export and JSON backup operations rely on the standard Android Storage Access Framework (SAF).",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(AppStrings.close(lang))
            }
        }
    )
}
