package com.hiepnguyen.tripfinance.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.domain.export.PdfReportExporter
import com.hiepnguyen.tripfinance.domain.export.ReportGenerator
import com.hiepnguyen.tripfinance.domain.export.ReportType
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary
import com.hiepnguyen.tripfinance.ui.theme.IndigoSecondary
import com.hiepnguyen.tripfinance.ui.theme.AppFontFamily
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportReportDialog(
    uiState: UiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lang = LocalAppLanguage.current
    var selectedReportType by remember { mutableStateOf(ReportType.FULL_SETTLEMENT) }
    var exportFormat by remember { mutableStateOf("PDF") } // PDF, TEXT, CSV
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    var reportTypeDropdownExpanded by remember { mutableStateOf(false) }

    val reportText = remember(selectedReportType, exportFormat, uiState) {
        if (exportFormat == "CSV") {
            ReportGenerator.generateCsvReport(
                trip = uiState.currentTrip,
                members = uiState.members,
                summary = uiState.financialSummary,
                statuses = uiState.memberStatuses,
                settlementTransfers = uiState.settlementTransfers,
                expenses = uiState.expenses,
                funds = uiState.fundContributions,
                splits = uiState.allSplits
            )
        } else {
            ReportGenerator.generateTextReport(
                trip = uiState.currentTrip,
                members = uiState.members,
                financialSummary = uiState.financialSummary,
                memberStatuses = uiState.memberStatuses,
                settlementTransfers = uiState.settlementTransfers,
                expenses = uiState.expenses,
                fundContributions = uiState.fundContributions,
                splits = uiState.allSplits,
                reportType = selectedReportType,
                creatorName = uiState.currentMember?.name ?: if (lang == AppLanguage.VI) "Thành viên" else "Member"
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = IndigoSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == AppLanguage.VI) "Xuất Báo Cáo Tài Chính" else "Export Financial Report",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppFontFamily
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Format Selector Chips (PDF, Văn bản, CSV)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = exportFormat == "PDF",
                        onClick = { exportFormat = "PDF" },
                        label = {
                            Text(
                                "PDF chuẩn in",
                                fontSize = 11.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f).testTag("format_pdf_chip")
                    )
                    FilterChip(
                        selected = exportFormat == "TEXT",
                        onClick = { exportFormat = "TEXT" },
                        label = {
                            Text(
                                if (lang == AppLanguage.VI) "Văn bản" else "Text",
                                fontSize = 11.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(0.9f).testTag("format_text_chip")
                    )
                    FilterChip(
                        selected = exportFormat == "CSV",
                        onClick = { exportFormat = "CSV" },
                        label = {
                            Text(
                                "Excel/CSV",
                                fontSize = 11.sp,
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(0.95f).testTag("format_csv_chip")
                    )
                }

                // If in PDF mode, show banner explaining PDF structure with Bank accounts included
                if (exportFormat == "PDF") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Báo cáo PDF chuẩn A4 đầy đủ: Tổng quan thu chi, Bảng quyết toán & số dư từng người, Kế hoạch chuyển khoản, BẢN KÊ CHI TIẾT CÁC KHOẢN CHI TIÊU & THU QUỸ và BẢNG SỐ TÀI KHOẢN NGÂN HÀNG các thành viên.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E40AF),
                                lineHeight = 15.sp,
                                fontFamily = AppFontFamily
                            )
                        }
                    }
                }

                // If in TEXT mode, compact dropdown selector for report type
                if (exportFormat == "TEXT") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportTypeDropdownExpanded = true }
                                .testTag("report_type_dropdown_trigger")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Assessment,
                                    contentDescription = null,
                                    tint = IndigoSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedReportType.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = AppFontFamily,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = selectedReportType.description,
                                        fontSize = 10.sp,
                                        fontFamily = AppFontFamily,
                                        color = Color(0xFF64748B),
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = if (reportTypeDropdownExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                    contentDescription = "Chọn loại báo cáo",
                                    tint = Color(0xFF475569)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = reportTypeDropdownExpanded,
                            onDismissRequest = { reportTypeDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = if (lang == AppLanguage.VI) "CHỌN LOẠI BÁO CÁO:" else "SELECT REPORT TYPE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            ReportType.values().forEach { rType ->
                                val isSelected = selectedReportType == rType
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = rType.title,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = AppFontFamily,
                                                color = if (isSelected) IndigoSecondary else Color(0xFF1E293B)
                                            )
                                            Text(
                                                text = rType.description,
                                                fontSize = 10.sp,
                                                fontFamily = AppFontFamily,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedReportType = rType
                                        reportTypeDropdownExpanded = false
                                    },
                                    modifier = Modifier.background(if (isSelected) Color(0xFFEEF2FF) else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // Copy Notification
                if (copiedNotice != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = copiedNotice!!,
                                fontSize = 12.sp,
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFontFamily
                            )
                        }
                    }
                }

                // Live Preview Canvas
                Text(
                    text = if (exportFormat == "PDF") "Xem trước nội dung văn bản trong báo cáo:" else (if (lang == AppLanguage.VI) "Xem trước nội dung báo cáo:" else "Report Preview:"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppFontFamily,
                    color = Color(0xFF475569)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFAFBFD),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp)
                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                ) {
                    val horizontalScrollState = rememberScrollState()
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            item {
                                Text(
                                    text = reportText,
                                    fontFamily = AppFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exportFormat == "PDF") {
                    // Xuất & Chia sẻ file PDF
                    Button(
                        onClick = {
                            val creator = uiState.currentMember?.name ?: if (lang == AppLanguage.VI) "Thành viên" else "Member"
                            PdfReportExporter.exportAndSharePdf(
                                context = context,
                                trip = uiState.currentTrip,
                                members = uiState.members,
                                summary = uiState.financialSummary,
                                statuses = uiState.memberStatuses,
                                settlementTransfers = uiState.settlementTransfers,
                                expenses = uiState.expenses,
                                funds = uiState.fundContributions,
                                splits = uiState.allSplits,
                                creatorName = creator
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xuất & Gửi File PDF", fontSize = 12.sp, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Copy Button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText))
                            copiedNotice = if (lang == AppLanguage.VI) "Đã sao chép toàn bộ báo cáo vào bộ nhớ tạm!" else "Copied full report to clipboard!"
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("copy_report_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == AppLanguage.VI) "Sao chép" else "Copy", fontSize = 12.sp, fontFamily = AppFontFamily)
                    }

                    // Share Button
                    Button(
                        onClick = {
                            shareReport(context, reportText, uiState.currentTrip?.title ?: "Báo cáo đoàn", exportFormat)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("share_report_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == AppLanguage.VI) "Chia sẻ" else "Share", fontSize = 12.sp, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == AppLanguage.VI) "Đóng" else "Close", fontFamily = AppFontFamily)
            }
        }
    )
}

private fun shareReport(context: Context, reportContent: String, tripTitle: String, format: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_SUBJECT, "Báo cáo quyết toán tài chính - $tripTitle")
        putExtra(Intent.EXTRA_TEXT, reportContent)
        type = if (format == "CSV") "text/comma-separated-values" else "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ báo cáo tài chính qua...")
    context.startActivity(shareIntent)
}
