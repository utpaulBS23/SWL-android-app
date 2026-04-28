package com.phq.swl.pioms.presentation.screens.submitted_reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmittedReportsScreen(
    reportType: ReportType,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (ReportItem, ReportType) -> Unit
) {
    val viewModel: SubmittedReportsViewModel = koinViewModel()
    
    val isLoading by viewModel.isLoadingState
    val filteredReports by viewModel.filteredReportsState
    val selectedStatus by viewModel.selectedStatusState
    val bpFilter by viewModel.bpFilterState
    val allReports by viewModel.reportsState
    

    var showBpFilterDialog by remember { mutableStateOf(false) }
    var bpFilterInput by remember { mutableStateOf(viewModel.bpFilterState.value) }
    var fromDateMillis by remember { mutableStateOf(viewModel.startDateState.value) }
    var toDateMillis by remember { mutableStateOf(viewModel.endDateState.value) }
    var deleteConfirmationVisible by remember { mutableStateOf(false) }
    var selectedReportForDelete by remember { mutableStateOf<ReportItem?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val statusCounts by viewModel.statusCountsState

    LaunchedEffect(reportType) {
        viewModel.fetchReports(reportType)
    }

    FaceNetAndroidTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(reportType.displayName, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp), tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            bpFilterInput = viewModel.bpFilterState.value
                            fromDateMillis = viewModel.startDateState.value
                            toDateMillis = viewModel.endDateState.value
                            showBpFilterDialog = true 
                        }) {
                            Icon(Icons.Default.FilterAlt, contentDescription = "Filter", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF0B4AA2))
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
                    StatusTabsRow(
                        viewModel = viewModel,
                        selectedStatus = selectedStatus,
                        onStatusChange = { viewModel.updateStatusFilter(it) },
                        statusCounts = statusCounts
                    )

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredReports.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No reports found", fontSize = 16.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredReports) { report ->
                                ReportListCard(
                                    report = report,
                                    status = viewModel.getStatusForReport(report),
                                    onEdit = { onNavigateToEdit(report, reportType) },
                                    onDelete = {
                                        selectedReportForDelete = report
                                        deleteConfirmationVisible = true
                                    },
                                    onSubmit = {
                                        isSubmitting = true
                                        viewModel.submitDraftReport(report, reportType) { success ->
                                            isSubmitting = false
                                            if (success) viewModel.fetchReports(reportType)
                                        }
                                    },
                                    onClick = { onNavigateToEdit(report, reportType) }
                                )
                            }
                        }
                    }
                }

                if (isSubmitting) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            if (showBpFilterDialog) {
                val context = LocalContext.current
                val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

                AlertDialog(
                    onDismissRequest = { showBpFilterDialog = false },
                    title = { Text("Filter Reports", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = bpFilterInput,
                                onValueChange = { bpFilterInput = it },
                                label = { Text("BP Number") },
                                placeholder = { Text("Enter BP number") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Text("Date Range", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    fromDateMillis?.let { calendar.timeInMillis = it }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            calendar.set(year, month, dayOfMonth, 0, 0, 0)
                                            fromDateMillis = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6))
                            ) {
                                Text(
                                    text = if (fromDateMillis != null) "From: " + dateFormatter.format(Date(fromDateMillis!!)) else "Select From Date",
                                    color = Color.Black
                                )
                            }
                            
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    toDateMillis?.let { calendar.timeInMillis = it }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            calendar.set(year, month, dayOfMonth, 23, 59, 59)
                                            toDateMillis = calendar.timeInMillis
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6))
                            ) {
                                Text(
                                    text = if (toDateMillis != null) "To: " + dateFormatter.format(Date(toDateMillis!!)) else "Select To Date",
                                    color = Color.Black
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateBpFilter(bpFilterInput)
                                viewModel.updateDateRange(fromDateMillis, toDateMillis)
                                showBpFilterDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4AA2))
                        ) { Text("Apply", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                bpFilterInput = ""
                                fromDateMillis = null
                                toDateMillis = null
                                viewModel.updateBpFilter("")
                                viewModel.updateDateRange(null, null)
                                showBpFilterDialog = false
                            }
                        ) { Text("Clear", color = Color.Gray) }
                    }
                )
            }

            if (deleteConfirmationVisible && selectedReportForDelete != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirmationVisible = false },
                    title = { Text("Confirm Delete", fontWeight = FontWeight.Bold) },
                    text = { Text("Do you want to delete this report?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                selectedReportForDelete?.let {
                                    viewModel.deleteReport(it, reportType) { success ->
                                        deleteConfirmationVisible = false
                                        selectedReportForDelete = null
                                        if (success) viewModel.fetchReports(reportType)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("DELETE", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmationVisible = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StatusTabsRow(
    viewModel: SubmittedReportsViewModel,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    statusCounts: Map<String, Int>
) {
    val statuses = viewModel.statusTabsState.value
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { status ->
            val isSelected = selectedStatus == status
            val badgeColor = when (status) {
                "Draft" -> Color(0xFF6B7280)
                "Submitted", "Ready to submit", "Completed", "Forwarded", "Acknowledge Completed" -> Color(0xFF1B8D2B)
                "Acknowledge Pending", "Resend", "Resend to Agent" -> Color(0xFFD97706)
                else -> Color(0xFF6B7280)
            }
            val containerColor = if (isSelected) Color(0xFFEAF2FF) else Color.White
            val contentColor = if (isSelected) Color(0xFF0B4AA2) else Color(0xFF374151)

            Surface(
                modifier = Modifier
                    .clickable { onStatusChange(status) }
                    .border(1.dp, if (isSelected) Color(0xFF0B4AA2) else Color(0xFFE5E7EB), RoundedCornerShape(999.dp)),
                shape = RoundedCornerShape(999.dp),
                color = containerColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = status, fontSize = 12.sp, fontWeight = FontWeight.W700, color = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(20.dp).background(badgeColor, RoundedCornerShape(50.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = (statusCounts[status] ?: 0).toString(), fontSize = 10.sp, fontWeight = FontWeight.W900, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportListCard(
    report: ReportItem,
    status: String,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSubmit: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val statusColor = when (status) {
        "Submitted", "Ready to submit", "Completed", "Forwarded", "Acknowledge Completed" -> Color(0xFF1B8D2B)
        "Cancel" -> Color(0xFFDC2626)
        "Acknowledge Pending", "Resend", "Resend to Agent" -> Color(0xFFD97706)
        else -> Color(0xFF6B7280)
    }
    val statusIcon = if (status == "Cancel") Icons.Default.Close else Icons.Default.Check
    val cardBackgroundColor = when (status) {
        "Submitted", "Ready to submit", "Completed", "Forwarded", "Acknowledge Completed" -> Color(0xFFF1FBF5)
        "Cancel" -> Color(0xFFFFF3F3)
        "Acknowledge Pending", "Resend", "Resend to Agent" -> Color(0xFFFFFBEB)
        else -> Color(0xFFF7F7F7)
    }
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp, 10.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "BP No: ${report.bpNo}", fontWeight = FontWeight.W900, fontSize = 14.sp, color = Color.Black)
                Text(text = "Name: ${report.name}", fontWeight = FontWeight.W700, fontSize = 12.sp, color = Color.Black)
                Text(text = "Updated At: ${formatDate(report.updatedAt)}", fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            if (status == "Draft") {
                Box {
                    IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp), Color(0xFF6B7280))
                    }
                    DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }, modifier = Modifier.background(Color.White)) {
                        DropdownMenuItem(
                            text = { MenuItemRow(Icons.Default.Edit, "Edit", Color(0xFFFFA500)) },
                            onClick = { expandedMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { MenuItemRow(Icons.Default.Delete, "Delete", Color.Red) },
                            onClick = { expandedMenu = false; onDelete() }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.background(statusColor, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(statusIcon, null, Modifier.size(16.dp), Color.White)
                        Text(status, fontWeight = FontWeight.W900, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItemRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(18.dp), color)
        Text(text, color = Color.Black, fontSize = 14.sp)
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateStr)
            ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).parse(dateStr)
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date ?: Date())
    } catch (e: Exception) { dateStr }
}
