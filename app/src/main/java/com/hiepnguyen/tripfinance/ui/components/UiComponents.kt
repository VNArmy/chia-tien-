package com.hiepnguyen.tripfinance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.BalanceStatus
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.ui.theme.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage

object NumberFormatUtils {
    private val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val formatter = DecimalFormat("#,###", symbols)
    private val decimalFormatter = DecimalFormat("#,##0.##", symbols)

    fun formatVnd(amount: Long): String {
        return "${formatter.format(amount)} đ"
    }

    fun formatVnd(amount: Double): String {
        return "${formatter.format(kotlin.math.round(amount).toLong())} đ"
    }

    fun formatCurrency(amount: Long, currency: String): String {
        return if (currency == "VND") formatVnd(amount) else "${formatter.format(amount)} $currency"
    }

    fun formatCurrency(amount: Double, currency: String): String {
        return if (currency == "VND") {
            formatVnd(kotlin.math.round(amount).toLong())
        } else {
            "${decimalFormatter.format(amount)} $currency"
        }
    }
}

@Composable
fun CategoryIcon(category: String, modifier: Modifier = Modifier, size: Int = 20) {
    val (icon, bgColor, tintColor) = when (category) {
        "FOOD" -> Triple(Icons.Filled.Restaurant, Color(0xFFFEF3C7), Color(0xFFD97706))
        "TRANSPORT" -> Triple(Icons.Filled.DirectionsCar, Color(0xFFDBEAFE), Color(0xFF2563EB))
        "HOTEL" -> Triple(Icons.Filled.Hotel, Color(0xFFF3E8FF), Color(0xFF7E22CE))
        "SIGHTSEEING" -> Triple(Icons.Filled.ConfirmationNumber, Color(0xFFDCFCE7), Color(0xFF16A34A))
        "ENTERTAINMENT" -> Triple(Icons.Filled.Celebration, Color(0xFFFFE4E6), Color(0xFFE11D48))
        "SHOPPING" -> Triple(Icons.Filled.ShoppingBag, Color(0xFFEDE9FE), Color(0xFF6D28D9))
        else -> Triple(Icons.Filled.ReceiptLong, Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category,
            tint = tintColor,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    val lang = LocalAppLanguage.current
    val (text, bg, textColor) = when (role) {
        "ADMIN" -> Triple(if (lang == AppLanguage.VI) "Trưởng đoàn" else "Admin", Color(0xFFFEE2E2), Color(0xFF991B1B))
        "TREASURER" -> Triple(if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer", Color(0xFFCCFBF1), Color(0xFF115E59))
        "MEMBER" -> Triple(if (lang == AppLanguage.VI) "Thành viên" else "Member", Color(0xFFE0E7FF), Color(0xFF3730A3))
        else -> Triple(if (lang == AppLanguage.VI) "Người xem" else "Viewer", Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BalanceChip(balance: Long, status: BalanceStatus, modifier: Modifier = Modifier) {
    val lang = LocalAppLanguage.current
    val (text, bg, fg) = when (status) {
        BalanceStatus.RECEIVE -> Triple(
            "+${NumberFormatUtils.formatVnd(balance)} " + if (lang == AppLanguage.VI) "(Nhận lại)" else "(To receive)",
            Color(0xFFDCFCE7),
            Color(0xFF15803D)
        )
        BalanceStatus.PAY -> Triple(
            "${NumberFormatUtils.formatVnd(balance)} " + if (lang == AppLanguage.VI) "(Cần nộp)" else "(To pay)",
            Color(0xFFFEE2E2),
            Color(0xFFB91C1C)
        )
        BalanceStatus.BALANCED -> Triple(
            "0 đ " + if (lang == AppLanguage.VI) "(Cân bằng)" else "(Balanced)",
            Color(0xFFF1F5F9),
            Color(0xFF475569)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun VietQrTransferDialog(
    transfer: SettlementTransfer,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val clipboardManager = LocalClipboardManager.current
    var copiedText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == AppLanguage.VI) "Thông Tin Chuyển Khoản" else "Transfer Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Transfer Amount Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (lang == AppLanguage.VI) "Số tiền thanh toán" else "Payment Amount",
                            fontSize = 12.sp,
                            color = EmeraldOnPrimaryContainer
                        )
                        Text(
                            text = NumberFormatUtils.formatVnd(transfer.amount),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldOnPrimaryContainer
                        )
                    }
                }

                // Recipient Info
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeutralLightSurfaceCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.VI) "NGƯỜI NHẬN TIỀN" else "RECIPIENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = transfer.toMember.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        Text(
                            text = "${if (lang == AppLanguage.VI) "NGÂN HÀNG" else "BANK"}: ${transfer.toMember.bankName ?: if (lang == AppLanguage.VI) "Chưa cập nhật" else "Not updated"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${if (lang == AppLanguage.VI) "SỐ TÀI KHOẢN" else "ACCOUNT NUMBER"}: ${transfer.toMember.bankAccount ?: if (lang == AppLanguage.VI) "Chưa cập nhật" else "Not updated"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoSecondary
                        )
                        Text(
                            text = "${if (lang == AppLanguage.VI) "CHỦ TÀI KHOẢN" else "ACCOUNT HOLDER"}: ${transfer.toMember.bankAccountHolder ?: transfer.toMember.name.uppercase()}",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }

                // Transfer Content / Note
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (lang == AppLanguage.VI) "NỘI DUNG CHUYỂN KHOẢN CHUẨN ĐỐI SOÁT:" else "TRANSFER DESCRIPTION / MEMO:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = transfer.transferNote,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F)
                        )
                    }
                }

                if (copiedText != null) {
                    Text(
                        text = "✓ $copiedText",
                        color = Color(0xFF16A34A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val copyContent = "Nguoi nhan: ${transfer.toMember.name}\n" +
                            "Ngan hang: ${transfer.toMember.bankName ?: ""}\n" +
                            "STK: ${transfer.toMember.bankAccount ?: ""}\n" +
                            "So tien: ${transfer.amount}\n" +
                            "Noi dung: ${transfer.transferNote}"
                    clipboardManager.setText(AnnotatedString(copyContent))
                    copiedText = if (lang == AppLanguage.VI) "Đã sao chép thông tin chuyển khoản!" else "Copied transfer details!"
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("copy_transfer_info_button")
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == AppLanguage.VI) "Sao chép thông tin" else "Copy Info")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == AppLanguage.VI) "Đóng" else "Close")
            }
        }
    )
}

