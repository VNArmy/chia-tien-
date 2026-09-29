package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.TripEntity
import com.example.ui.theme.EmeraldPrimary

@Composable
fun CreateTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        joinCode: String,
        startDate: Long,
        endDate: Long,
        adminName: String,
        adminBankName: String?,
        adminBankAccount: String?,
        adminBankHolder: String?
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var joinCodeInput by remember { mutableStateOf(com.example.domain.model.TripCodeGenerator.generateCode("TRIP-")) }
    var adminBankName by remember { mutableStateOf("Vietcombank") }
    var adminBankAccount by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    val isValid = title.isNotBlank() && adminName.isNotBlank()

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tạo Chuyến Đi / Đoàn Mới", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên đoàn (VD: Du lịch Đà Nẵng 2026) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_trip_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả / Mục đích chuyến đi") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = adminName,
                    onValueChange = { adminName = it },
                    label = { Text("Họ tên bạn (Trưởng đoàn / Người ghi chép) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { input ->
                        val filtered = input.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        if (filtered.length <= 16) joinCodeInput = filtered
                    },
                    label = { Text("Mã đoàn (Dùng để chia sẻ / nhập đoàn)") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            joinCodeInput = com.example.domain.model.TripCodeGenerator.generateCode("TRIP-")
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Tạo mã ngẫu nhiên mới", tint = EmeraldPrimary)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = adminBankName,
                    onValueChange = { adminBankName = it },
                    label = { Text("Ngân hàng nhận tiền quyết toán") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = adminBankAccount,
                    onValueChange = { adminBankAccount = it },
                    label = { Text("Số tài khoản nhận tiền") },
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
                    val now = System.currentTimeMillis()
                    val finalCode = joinCodeInput.trim().ifEmpty {
                        com.example.domain.model.TripCodeGenerator.generateCode("TRIP-")
                    }
                    onConfirm(
                        title.trim(),
                        description.trim(),
                        finalCode,
                        now,
                        now + 86400000L * 5,
                        adminName.trim(),
                        adminBankName.trim().takeIf { it.isNotBlank() },
                        adminBankAccount.trim().takeIf { it.isNotBlank() },
                        adminName.trim()
                    )
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("submit_create_trip_button")
            ) {
                Text("Tạo đoàn")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text("Hủy") }
        }
    )
}

@Composable
fun EditTripDialog(
    trip: TripEntity,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, startDate: Long, endDate: Long) -> Unit
) {
    var title by remember { mutableStateOf(trip.title) }
    var description by remember { mutableStateOf(trip.description) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    val isValid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chỉnh Sửa Thông Tin Đoàn", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên đoàn *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("edit_trip_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả / Mục đích") },
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
                    onConfirm(title.trim(), description.trim(), trip.startDate, trip.endDate)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("submit_edit_trip_button")
            ) {
                Text("Lưu thay đổi")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text("Hủy") }
        }
    )
}

@Composable
fun DeleteTripDialog(
    tripTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = {
            Text("Xác Nhận Xóa Đoàn", fontWeight = FontWeight.Bold)
        },
        text = {
            Text("Bạn có chắc chắn muốn xóa đoàn '$tripTitle'? Toàn bộ chi tiêu, nộp quỹ, thành viên và dữ liệu thuộc đoàn này sẽ bị xóa vĩnh viễn khỏi thiết bị.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_trip_button")
            ) {
                Text("Xóa vĩnh viễn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
