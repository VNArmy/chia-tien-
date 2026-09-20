package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExpenseSplitEntity
import com.example.data.entity.TripMemberEntity
import com.example.ui.components.CategoryIcon
import com.example.ui.components.NumberFormatUtils
import com.example.ui.theme.*
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    uiState: UiState,
    onOpenAddExpense: () -> Unit,
    onEditExpense: (
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
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    var selectedExpenseForDetail by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isAdmin = uiState.currentMember?.role == "ADMIN"
    val isSettled = uiState.currentTrip?.isSettled == true

    Scaffold(
        floatingActionButton = {
            if (!isSettled) {
                FloatingActionButton(
                    onClick = onOpenAddExpense,
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 72.dp)
                        .testTag("fab_add_expense")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Thêm khoản chi")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isSettled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chuyến đi đã khóa sổ quyết toán. Dữ liệu chi tiêu được bảo vệ và không thể thêm/sửa/xóa.",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Tìm kiếm khoản chi, ghi chú...") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF64748B))
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchChange("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Xóa tìm kiếm")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_search_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Chips
            val categories = listOf(
                null to "Tất cả",
                "FOOD" to "Ăn uống",
                "TRANSPORT" to "Di chuyển",
                "HOTEL" to "Khách sạn",
                "SIGHTSEEING" to "Vé tham quan",
                "ENTERTAINMENT" to "Giải trí",
                "SHOPPING" to "Mua sắm",
                "OTHER" to "Khác"
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { (catKey, catLabel) ->
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == catKey,
                        onClick = { onCategoryFilterChange(catKey) },
                        label = { Text(catLabel, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expense List
            if (uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chưa có khoản chi nào",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Nhấn nút '+' bên dưới để tạo khoản chi mới",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(uiState.expenses) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            members = uiState.members,
                            isAdmin = isAdmin,
                            isSettled = isSettled,
                            onClick = { selectedExpenseForDetail = expense },
                            onEdit = { expenseToEdit = expense },
                            onDelete = { expenseToDelete = expense }
                        )
                    }
                }
            }
        }
    }

    // Expense Detail Dialog
    selectedExpenseForDetail?.let { exp ->
        val splitsForExp = uiState.allSplits.filter { it.expenseId == exp.id }
        ExpenseDetailDialog(
            expense = exp,
            members = uiState.members,
            splits = splitsForExp,
            isAdmin = isAdmin,
            isSettled = isSettled,
            onDismiss = { selectedExpenseForDetail = null },
            onEdit = {
                selectedExpenseForDetail = null
                expenseToEdit = exp
            },
            onDelete = {
                selectedExpenseForDetail = null
                expenseToDelete = exp
            }
        )
    }

    // Edit Expense Dialog
    expenseToEdit?.let { exp ->
        val splitsForExp = uiState.allSplits.filter { it.expenseId == exp.id }
        AddExpenseDialog(
            members = uiState.members,
            exchangeRates = uiState.exchangeRates,
            currentMemberId = uiState.currentMember?.id,
            initialExpense = exp,
            initialSplits = splitsForExp,
            onDismiss = { expenseToEdit = null },
            onSave = { title, category, payerType, payerMemberId, totalAmount, currency, exchangeRate, splitType, splits, note, timestamp ->
                onEditExpense(
                    exp.id, title, category, payerType, payerMemberId, totalAmount, currency, exchangeRate, splitType, splits, note, timestamp
                )
                expenseToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { exp ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Xác Nhận Xóa Khoản Chi", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Bạn có chắc chắn muốn xóa khoản chi '${exp.title}' (${NumberFormatUtils.formatVnd(exp.convertedTotalAmount)})? Thao tác này chỉ dành cho Trưởng đoàn.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteExpense(exp)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_expense_button")
                ) {
                    Text("Xóa khoản chi")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseEntity,
    members: List<TripMemberEntity>,
    isAdmin: Boolean,
    isSettled: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val payerName = if (expense.payerType == "FUND") {
        "Quỹ chung đoàn"
    } else {
        members.find { it.id == expense.payerMemberId }?.name ?: "Thành viên"
    }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(expense.timestamp))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(category = expense.category, size = 22)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Thời gian chi đặt trực tiếp dưới tên khoản chi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Người chi hộ đặt ở phía dưới thời gian chi
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (expense.payerType == "FUND") Color(0xFFFEF3C7) else Color(0xFFE0E7FF)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (expense.payerType == "FUND") Icons.Filled.Savings else Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (expense.payerType == "FUND") Color(0xFF92400E) else Color(0xFF3730A3),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (expense.payerType == "FUND") "Quỹ chi" else "Chi hộ: $payerName",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (expense.payerType == "FUND") Color(0xFF92400E) else Color(0xFF3730A3)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatUtils.formatVnd(expense.convertedTotalAmount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                if (expense.currency != "VND") {
                    Text(
                        text = "${expense.totalAmount} ${expense.currency}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (isAdmin && !isSettled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(28.dp).testTag("edit_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Sửa khoản chi",
                                tint = IndigoSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Xóa khoản chi",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailDialog(
    expense: ExpenseEntity,
    members: List<TripMemberEntity>,
    splits: List<ExpenseSplitEntity> = emptyList(),
    isAdmin: Boolean = false,
    isSettled: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val payerMember = members.find { it.id == expense.payerMemberId }
    val payerName = if (expense.payerType == "FUND") {
        "Quỹ chung của đoàn (Trừ thẳng vào quỹ)"
    } else {
        "${payerMember?.name ?: "Thành viên"} (${when (payerMember?.role) {
            "ADMIN" -> "Trưởng đoàn"
            "TREASURER" -> "Thủ quỹ"
            else -> "Thành viên"
        }})"
    }

    val categoryLabel = when (expense.category) {
        "FOOD" -> "Ăn uống"
        "TRANSPORT" -> "Di chuyển"
        "HOTEL" -> "Khách sạn / Lưu trú"
        "SIGHTSEEING" -> "Vé tham quan"
        "ENTERTAINMENT" -> "Giải trí"
        "SHOPPING" -> "Mua sắm"
        else -> "Chi phí khác"
    }

    val splitTypeLabel = when (expense.splitType) {
        "EQUAL" -> "Chia đều cho tất cả thành viên"
        "CUSTOM_PARTICIPANT" -> "Chọn danh sách người tham gia"
        "RATIO" -> "Chia theo tỷ lệ %"
        "CUSTOM_AMOUNT" -> "Tùy nhập số tiền từng người"
        else -> expense.splitType
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = expense.category, size = 26)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(expense.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Thời gian: ${dateFormat.format(Date(expense.timestamp))}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Tổng số tiền quy đổi (VND)", fontSize = 11.sp, color = EmeraldOnPrimaryContainer)
                            Text(
                                text = NumberFormatUtils.formatVnd(expense.convertedTotalAmount),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldOnPrimaryContainer
                            )
                            if (expense.currency != "VND") {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tiền gốc: ${expense.totalAmount} ${expense.currency} • Tỷ giá: ${expense.exchangeRate}",
                                    fontSize = 12.sp,
                                    color = EmeraldOnPrimaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "• Danh mục: $categoryLabel",
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "• Người xuất tiền: $payerName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "• Cách phân bổ: $splitTypeLabel",
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                        if (expense.note.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = "• Ghi chú: ${expense.note}",
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }

                // Participant splits breakdown list
                if (splits.isNotEmpty()) {
                    item {
                        Text(
                            text = "Danh Sách Người Tham Gia (${splits.size} người):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            splits.forEach { sp ->
                                val memName = members.find { it.id == sp.memberId }?.name ?: "Thành viên"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = memName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatVnd(sp.amount),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSettled) {
                    item {
                        Text(
                            text = "🔒 Chuyến đi đã khóa sổ. Dữ liệu chi tiêu được niêm phong, không thể sửa hoặc xóa.",
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (!isAdmin) {
                    item {
                        Text(
                            text = "ℹ️ Chỉ Trưởng đoàn (Admin) mới có quyền sửa hoặc xóa khoản chi này.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isAdmin && !isSettled) {
                    OutlinedButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoSecondary)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sửa")
                    }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xóa")
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Đóng")
                }
            }
        }
    )
}
