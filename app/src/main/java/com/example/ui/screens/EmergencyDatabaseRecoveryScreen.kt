package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.DatabaseInitState

@Composable
fun EmergencyDatabaseRecoveryScreen(
    error: DatabaseInitState.Error,
    onRetry: () -> Unit,
    onRestoreBackup: (jsonContent: String, password: String?) -> Unit,
    onResetFresh: () -> Unit
) {
    val context = LocalContext.current
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var isReadingFile by remember { mutableStateOf(false) }
    var fileReadError by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            fileReadError = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isBlank()) {
                    fileReadError = "Tệp sao lưu rỗng hoặc không thể đọc."
                } else {
                    pendingBackupJson = content
                    // Kiểm tra xem tệp có mã hóa mật khẩu không
                    val isEncrypted = com.example.data.backup.BackupCryptoUtils.isEncryptedBackup(content)
                    if (isEncrypted) {
                        showPasswordDialog = true
                    } else {
                        onRestoreBackup(content, null)
                    }
                }
            } catch (e: Exception) {
                fileReadError = "Lỗi khi đọc tệp sao lưu: ${e.message}"
            } finally {
                isReadingFile = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Icon Shield / Security Error
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0xFFFEE2E2), CircleShape)
                    .border(2.dp, DangerRed.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = DangerRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Title & Subtitle
            Text(
                text = "Bảo Vệ An Toàn Dữ Liệu",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Hệ thống bảo mật phần cứng Android KeyStore hoặc thư viện mã hóa SQLCipher không thể mở khóa cơ sở dữ liệu.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Diagnostic Box
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chi tiết sự cố bảo mật (Fail-Closed):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }

                    Text(
                        text = "Mã lỗi: ${error.errorType.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed,
                        fontFamily = FontFamily.Monospace
                    )

                    Text(
                        text = error.message,
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )

                    Text(
                        text = "Ứng dụng tuân thủ nguyên tắc bảo mật đóng (Fail-Closed), tuyệt đối không mở CSDL không mã hóa để ngăn ngừa rò rỉ thông tin tài chính của bạn.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // File read error if any
            AnimatedVisibility(visible = fileReadError != null) {
                fileReadError?.let {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = DangerRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action 1: Retry
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thử Lại Mở Khóa", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Action 2: Restore from Backup
            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7))
            ) {
                if (isReadingFile) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Khôi Phục Từ Tệp Sao Lưu (.json)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // Action 3: Reset database fresh
            TextButton(
                onClick = { showResetConfirmDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Xóa CSDL Hỏng & Tạo Mới Từ Đầu", fontSize = 13.sp)
            }
        }
    }

    // Password Prompt Dialog for Encrypted Backup
    if (showPasswordDialog && pendingBackupJson != null) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                pendingBackupJson = null
                backupPassword = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mật Khẩu Sao Lưu", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Bản sao lưu này đã được mã hóa bảo mật. Vui lòng nhập mật khẩu bạn đã đặt khi sao lưu để giải mã dữ liệu:",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Mật khẩu bảo vệ") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingBackupJson
                        val pass = backupPassword
                        showPasswordDialog = false
                        pendingBackupJson = null
                        backupPassword = ""
                        if (json != null) {
                            onRestoreBackup(json, pass.ifBlank { null })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Mở Khóa & Phục Hồi")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    pendingBackupJson = null
                    backupPassword = ""
                }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Confirmation Dialog for Resetting Database
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xác Nhận Xóa Dữ Liệu?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Thao tác này sẽ xóa sạch CSDL hiện tại trên máy và tạo mới khóa bảo mật KeyStore hoàn toàn sạch sẽ. " +
                            "Toàn bộ dữ liệu chưa được sao lưu sẽ bị mất vĩnh viễn. Bạn có chắc chắn muốn tiếp tục?",
                    fontSize = 13.sp,
                    color = Color(0xFF334155),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetFresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Xác Nhận Xóa & Tạo Mới")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Hủy Bỏ")
                }
            }
        )
    }
}
