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

    // Launcher for exporting backup to a user-selected URI (SAF)
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri)
        }
    }

    // Launcher for opening/restoring backup from a user-selected URI (SAF)
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            pendingRestoreFile = null
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
                        onClick = { viewModel.createLocalBackup(context) },
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
                            val defaultName = "tripfinance_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                            exportDocumentLauncher.launch(defaultName)
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
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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
                                            onClick = { viewModel.shareBackupFile(context, file) },
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

    // Confirmation dialog before restoring
    if (showConfirmRestoreDialog) {
        val targetName = pendingRestoreFile?.name ?: pendingRestoreUri?.lastPathSegment ?: "Tệp sao lưu"
        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            icon = {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD97706))
            },
            title = {
                Text("Xác nhận khôi phục CSDL", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bạn sắp nạp dữ liệu từ: $targetName.")
                    Text("Dữ liệu các chuyến đi, thành viên, quỹ và khoản chi sẽ được tích hợp vào ứng dụng.")

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
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreDialog = false
                        if (pendingRestoreFile != null) {
                            viewModel.restoreBackupFromFile(context, pendingRestoreFile!!, isClearExistingSelected)
                        } else if (pendingRestoreUri != null) {
                            viewModel.restoreBackupFromUri(context, pendingRestoreUri!!, isClearExistingSelected)
                        }
                    },
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
}
