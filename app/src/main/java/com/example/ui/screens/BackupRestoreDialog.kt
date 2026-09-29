package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoSecondary
import com.example.ui.viewmodel.TripFinanceViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BackupRestoreDialog(
    viewModel: TripFinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localBackups by viewModel.localBackups.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var isClearExistingSelected by remember { mutableStateOf(false) }

    // Encryption states for backup creation & export
    var showCreateOptionsDialog by remember { mutableStateOf(false) }
    var isCreateExportMode by remember { mutableStateOf(false) }
    var encryptBackupCheck by remember { mutableStateOf(true) }
    var backupPasswordField by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var activeExportPassword by remember { mutableStateOf<String?>(null) }

    // Encryption states for restore
    var isPendingEncrypted by remember { mutableStateOf(false) }
    var restorePasswordField by remember { mutableStateOf("") }
    var restorePasswordVisible by remember { mutableStateOf(false) }

    // Share warning for unencrypted backup
    var pendingShareFile by remember { mutableStateOf<File?>(null) }
    var showShareWarningDialog by remember { mutableStateOf(false) }

    // Launcher for exporting backup to a user-selected URI (SAF)
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri, activeExportPassword)
        }
    }

    // Launcher for opening/restoring backup from a user-selected URI (SAF)
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            pendingRestoreFile = null
            isPendingEncrypted = viewModel.isEncryptedBackupUri(context, uri)
            restorePasswordField = ""
            showConfirmRestoreDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalBackups(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(IndigoSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CloudSync,
                                contentDescription = null,
                                tint = IndigoSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sao Lưu & Phục Hồi",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bảo vệ toàn vẹn dữ liệu khi nâng cấp ứng dụng",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Information banner
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CSDL đã được bảo vệ nâng cấp (Anti-destructive migration). Bạn có thể sao lưu ra tệp JSON để gửi qua Google Drive, Zalo hoặc lưu trữ dự phòng.",
                            fontSize = 11.sp,
                            color = Color(0xFF166534),
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: Create local, Export to file, Import from file
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isCreateExportMode = false
                            encryptBackupCheck = true
                            backupPasswordField = ""
                            showCreateOptionsDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tạo sao lưu", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isCreateExportMode = true
                            encryptBackupCheck = true
                            backupPasswordField = ""
                            showCreateOptionsDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xuất tệp", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { pickDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nhập tệp", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of local backup files
                Text(
                    text = "BẢN SAO LƯU TRÊN THIẾT BỊ (${localBackups.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (localBackups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.FolderZip,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Chưa có bản sao lưu nội bộ nào",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                "Nhấn 'Tạo sao lưu' ở trên để lưu snapshot toàn bộ CSDL",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localBackups) { file ->
                            val fileSizeKb = (file.length() / 1024.0)
                            val modifiedDate = dateFormat.format(Date(file.lastModified()))
                            val isEncrypted = viewModel.isEncryptedBackupFile(file)

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = file.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isEncrypted) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFEFF6FF)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Lock,
                                                            contentDescription = "Mã hóa AES-256",
                                                            tint = Color(0xFF2563EB),
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            "AES-256",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2563EB)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$modifiedDate • ${String.format(Locale.US, "%.1f KB", fileSizeKb)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Share
                                        IconButton(
                                            onClick = {
                                                if (isEncrypted) {
                                                    viewModel.shareBackupFile(context, file)
                                                } else {
                                                    pendingShareFile = file
                                                    showShareWarningDialog = true
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Share,
                                                contentDescription = "Chia sẻ",
                                                tint = IndigoSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Restore
                                        Button(
                                            onClick = {
                                                pendingRestoreFile = file
                                                pendingRestoreUri = null
                                                isPendingEncrypted = isEncrypted
                                                restorePasswordField = ""
                                                showConfirmRestoreDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Nạp lại", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom close button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đóng", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Dialog for creating / exporting backup with optional AES-GCM password encryption
    if (showCreateOptionsDialog) {
        val titleText = if (isCreateExportMode) "Tùy Chọn Xuất Tệp Sao Lưu" else "Tùy Chọn Tạo Bản Sao Lưu"
        AlertDialog(
            onDismissRequest = { showCreateOptionsDialog = false },
            icon = {
                Icon(
                    if (encryptBackupCheck) Icons.Filled.Lock else Icons.Filled.Security,
                    contentDescription = null,
                    tint = if (encryptBackupCheck) Color(0xFF2563EB) else EmeraldPrimary
                )
            },
            title = {
                Text(titleText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Bản sao lưu sẽ đóng gói toàn bộ danh sách đoàn, thành viên, các khoản chi, quỹ và lịch sử đối soát.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = encryptBackupCheck,
                                    onCheckedChange = { encryptBackupCheck = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "Mã hóa bảo vệ bằng mật khẩu",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Chuẩn AES-256-GCM + PBKDF2 (Khuyến nghị)",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (encryptBackupCheck) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Mã hóa giúp bảo vệ an toàn tuyệt đối cho số tài khoản ngân hàng và dữ liệu tài chính cá nhân của các thành viên đoàn.",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = backupPasswordField,
                                    onValueChange = { backupPasswordField = it },
                                    label = { Text("Mật khẩu bảo vệ sao lưu (tối thiểu 6 ký tự)", fontSize = 12.sp) },
                                    placeholder = { Text("Nhập mật khẩu tự chọn (>= 6 ký tự)", fontSize = 11.sp) },
                                    singleLine = true,
                                    isError = backupPasswordField.isNotEmpty() && backupPasswordField.length < 6,
                                    supportingText = {
                                        if (backupPasswordField.isNotEmpty() && backupPasswordField.length < 6) {
                                            Text("Mật khẩu phải có tối thiểu 6 ký tự", color = Color(0xFFDC2626), fontSize = 11.sp)
                                        }
                                    },
                                    visualTransformation = if (backupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                            Icon(
                                                if (backupPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Cảnh báo: Bản sao lưu không mã hóa sẽ tự động ẩn số tài khoản ngân hàng (******1234) để bảo vệ an toàn khi chia sẻ qua mạng.",
                                        color = Color(0xFF92400E),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isConfirmEnabled = !encryptBackupCheck || backupPasswordField.trim().length >= 6
                Button(
                    onClick = {
                        showCreateOptionsDialog = false
                        val pwd = if (encryptBackupCheck) backupPasswordField.trim() else null
                        if (isCreateExportMode) {
                            activeExportPassword = pwd
                            val defaultName = "tripfinance_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                            exportDocumentLauncher.launch(defaultName)
                        } else {
                            viewModel.createLocalBackup(context, pwd)
                        }
                    },
                    enabled = isConfirmEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(if (isCreateExportMode) "Tiếp tục xuất" else "Tạo sao lưu ngay")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateOptionsDialog = false }) {
                    Text("Hủy bỏ")
                }
            }
        )
    }

    // Confirmation dialog before restoring
    if (showConfirmRestoreDialog) {
        val targetName = pendingRestoreFile?.name ?: pendingRestoreUri?.lastPathSegment ?: "Tệp sao lưu"
        val canConfirm = !isPendingEncrypted || restorePasswordField.trim().length >= 6

        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            icon = {
                Icon(
                    if (isPendingEncrypted) Icons.Filled.Lock else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isPendingEncrypted) Color(0xFF2563EB) else Color(0xFFD97706)
                )
            },
            title = {
                Text(
                    text = if (isPendingEncrypted) "Nhập Mật Khẩu Khôi Phục" else "Xác nhận khôi phục CSDL",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bạn sắp nạp dữ liệu từ: $targetName.", fontSize = 12.5.sp)

                    if (isPendingEncrypted) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Tệp sao lưu này được mã hóa bảo vệ (AES-256-GCM). Vui lòng nhập đúng mật khẩu để giải mã.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = restorePasswordField,
                            onValueChange = { restorePasswordField = it },
                            label = { Text("Mật khẩu giải mã (tối thiểu 6 ký tự)", fontSize = 12.sp) },
                            singleLine = true,
                            isError = restorePasswordField.isNotEmpty() && restorePasswordField.trim().length < 6,
                            supportingText = {
                                if (restorePasswordField.isNotEmpty() && restorePasswordField.trim().length < 6) {
                                    Text("Mật khẩu phải có tối thiểu 6 ký tự", color = Color(0xFFDC2626), fontSize = 11.sp)
                                }
                            },
                            visualTransformation = if (restorePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { restorePasswordVisible = !restorePasswordVisible }) {
                                    Icon(
                                        if (restorePasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Dữ liệu các chuyến đi, thành viên, quỹ và khoản chi sẽ được tích hợp vào ứng dụng.", fontSize = 11.5.sp, color = Color(0xFF64748B))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = isClearExistingSelected,
                            onCheckedChange = { isClearExistingSelected = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Xóa sạch dữ liệu hiện tại trước khi khôi phục (Khuyến nghị nếu chuyển máy mới)",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreDialog = false
                        val pwd = if (isPendingEncrypted) restorePasswordField.trim().ifBlank { null } else null
                        if (pendingRestoreFile != null) {
                            viewModel.restoreBackupFromFile(context, pendingRestoreFile!!, isClearExistingSelected, pwd)
                        } else if (pendingRestoreUri != null) {
                            viewModel.restoreBackupFromUri(context, pendingRestoreUri!!, isClearExistingSelected, pwd)
                        }
                    },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Tiến hành khôi phục")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmRestoreDialog = false }) {
                    Text("Hủy bỏ")
                }
            }
        )
    }

    // Warning dialog when sharing unencrypted backup file
    if (showShareWarningDialog && pendingShareFile != null) {
        val fileToShare = pendingShareFile!!
        AlertDialog(
            onDismissRequest = {
                showShareWarningDialog = false
                pendingShareFile = null
            },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706)
                )
            },
            title = {
                Text("Chia Sẻ Bản Sao Lưu Không Mã Hóa", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tệp '${fileToShare.name}' không được bảo vệ bằng mật khẩu mã hóa.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF1E293B)
                    )
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Lưu ý an toàn: Để bảo vệ tài chính cá nhân của các thành viên đoàn, hệ thống đã tự động che mờ số tài khoản ngân hàng (dạng ******1234) trong tệp không mã hóa này.",
                            color = Color(0xFF92400E),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        "Bạn có muốn tiếp tục gửi tệp này qua ứng dụng khác không?",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShareWarningDialog = false
                        viewModel.shareBackupFile(context, fileToShare)
                        pendingShareFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Tiếp tục chia sẻ")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showShareWarningDialog = false
                        pendingShareFile = null
                    }
                ) {
                    Text("Hủy bỏ")
                }
            }
        )
    }
}
