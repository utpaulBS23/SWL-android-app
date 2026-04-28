package com.phq.swl.pioms.presentation.screens.vr_report

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkplaceScreen(
    reportId: Int,
    complainId: Int,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val viewModel: WorkplaceViewModel = koinViewModel()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val primaryBlue = Color(0xFF0B4AA2)

    LaunchedEffect(reportId, complainId) {
        viewModel.initData(reportId, complainId)
    }

    val saveSuccess by viewModel.saveSuccessState
    val saveMessage by viewModel.saveMessageState
    val isSaving by viewModel.isSavingState
    val reportStatus by viewModel.reportStatusState
    val reportFlowLabel by viewModel.reportFlowLabelState
    val isPageLoading by viewModel.isPageLoadingState
    val isSearching by viewModel.isSearchingState
    val applicantPicture by viewModel.applicantPictureState
    val selectedWorkplaceType by viewModel.selectedWorkplaceTypeState
    val isSubmitted by viewModel.isSubmittedState

    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var agentDialogIndex by remember { mutableStateOf<Int?>(null) }
    var agentRemarksInput by remember { mutableStateOf("") }
    var agentAcknowledgedInput by remember { mutableStateOf(false) }

    LaunchedEffect(saveSuccess, saveMessage) {
        saveMessage?.let { msg ->
            if (saveSuccess == true) {
                launch { snackbarHostState.showSnackbar(msg) }
                kotlinx.coroutines.delay(1500)
                onSaveSuccess()
            } else {
                launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    FaceNetAndroidTheme {
        Scaffold(
            snackbarHost = { 
                SnackbarHost(snackbarHostState) { data ->
                    val isSuccess = saveSuccess == true
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                        contentColor = Color.White
                    )
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("কর্মস্থলের রিপোর্ট", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = primaryBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    modifier = Modifier.border(width = 0.5.dp, color = Color.LightGray)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search Bar Section
                    Surface(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
                        color = Color(0xFFE1F5FE)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("BP/CIV", fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = viewModel.bpNumberState.value,
                                onValueChange = { viewModel.bpNumberState.value = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(35.dp)
                                    .background(Color.White)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                                singleLine = true
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 35.dp)
                                    .background(Color(0xFF008CBA))
                                    .clickable { viewModel.fetchApplicantInfoByBP() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                                }
                            }
                        }
                    }
                    // Applicant Picture
                    applicantPicture?.let { base64 ->
                        val bitmap = remember(base64) {
                            try {
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        bitmap?.let {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Applicant",
                                    modifier = Modifier
                                        .size(width = 100.dp, height = 120.dp)
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Workplace Type Radio Selection
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).clickable { viewModel.onWorkplaceTypeChanged("Present") }
                        ) {
                            RadioButton(
                                selected = selectedWorkplaceType == "Present",
                                onClick = { viewModel.onWorkplaceTypeChanged("Present") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color.Blue)
                            )
                            Text("বর্তমান কর্মস্থল", fontSize = 14.sp, color = Color.Black)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).clickable { viewModel.onWorkplaceTypeChanged("Past") }
                        ) {
                            RadioButton(
                                selected = selectedWorkplaceType == "Past",
                                onClick = { viewModel.onWorkplaceTypeChanged("Past") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color.Blue)
                            )
                            Text("পূর্ববর্তী কর্মস্থল", fontSize = 14.sp, color = Color.Black)
                        }
                    }

                    if (selectedWorkplaceType == "Present") {
                        // District/Unit Header
                        TableIdentityRow("জেলা/ইউনিটের নাম", viewModel.districtOrUnitNameState, isHeader = true)

                        Spacer(Modifier.height(5.dp))
                        SectionHeader("ব্যক্তিগত পরিচিতি (কর্মস্থলের জন্য প্রযোজ্য)")
                        
                        Column(modifier = Modifier.border(1.dp, Color.Gray)) {
                            TableIdentityRow("নাম", viewModel.nameState)
                            TableIdentityRow("বিপি/সিআইভি", viewModel.bpNumberState)
                            TableIdentityRow("পদবী", viewModel.designationState)
                            TableIdentityRow("নিজ জেলা", viewModel.homeDistrictState)
                            TableIdentityRow("মাতৃ ইউনিট (প্রেষণ/সংযুক্ত থাকলে উল্লেখ করুন)", viewModel.mainUnitState)
                            TableIdentityRow("বর্তমান কর্মস্থল (থানা, ফাঁড়ি, কোর্ট, শাখা, দপ্তরের নাম লিখুন)", viewModel.currentWorkingPlaceState)
                            TableIdentityRow("বর্তমান কর্মস্থলে যোগদান", viewModel.joiningDateTimeState)
                            TableIdentityRow("উৎকোচ গ্রহণের মাত্রা (চরম, মধ্যম, সীমিত মাত্রায় লিখুন)", viewModel.briberyLevelState)
                            TableIdentityRow("বিশেষ মন্তব্য", viewModel.specialCommentState)
                        }

                    } else {
                        SectionHeader("পূর্ববর্তী চাকুরীর বৃত্তান্ত")
                        viewModel.jobHistoryListState.value.forEachIndexed { index, item ->
                            Column(
                                modifier = Modifier
                                    .border(1.dp, Color.Gray)
                            ) {
                                TableIdentityRow("কর্মস্থল-${index + 1} এর নাম ও ঠিকানা", mutableStateOf(item.nameAndAddress), onValueChange = { viewModel.updateJobHistory(index, item.copy(nameAndAddress = it)) })
                                TableIdentityRow("যোগদানকারী পদের নাম", mutableStateOf(item.rank), onValueChange = { viewModel.updateJobHistory(index, item.copy(rank = it)) })
                                TableIdentityRow("যোগদানের তারিখ", mutableStateOf(item.joiningDate), onValueChange = { viewModel.updateJobHistory(index, item.copy(joiningDate = it)) })
                                TableIdentityRow("প্রস্থানের তারিখ", mutableStateOf(item.leavingDate), onValueChange = { viewModel.updateJobHistory(index, item.copy(leavingDate = it)) })
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xFF0B4AA2))
                                        .clickable { viewModel.currentEditingJobIndexState.value = index }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Workplace Details (Click to Add/View)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { viewModel.addJobHistory() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(35.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("ADD", fontSize = 12.sp)
                            }
                        }
                    }

                    SectionHeader("Multiple agent (if needed)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = viewModel.agentSearchQueryState.value,
                            onValueChange = { viewModel.agentSearchQueryState.value = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("BP/CIV search") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.searchAgents() },
                                    enabled = !viewModel.isSearchingAgentsState.value,
                                ) {
                                    if (viewModel.isSearchingAgentsState.value) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF1976D2),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = "Search agents", tint = Color(0xFF1976D2))
                                    }
                                }
                            },
                        )
                    }
                    if (viewModel.agentSearchResultsState.value.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            border = BorderStroke(1.dp, Color.LightGray),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .padding(8.dp)
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Tap to add", fontSize = 12.sp, color = Color.DarkGray)
                                viewModel.agentSearchResultsState.value.forEach { result ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.addAgentFromSearch(result) }
                                                .background(Color.White, RoundedCornerShape(4.dp))
                                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                                .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(result.userFullName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    viewModel.agentReportRelationsState.value.forEachIndexed { index, rel ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    agentDialogIndex = index
                                    agentRemarksInput = rel.remarks ?: ""
                                    agentAcknowledgedInput = rel.isAcknowledged
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FF)),
                            border = BorderStroke(1.dp, Color(0xFF64B5F6))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "${index + 1}. ${rel.agentName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Black)
                                    Text(text = "BP: ${rel.bpNumber}", fontSize = 12.sp, color = Color.DarkGray)
                                    if (rel.isAcknowledged) {
                                        Text(text = "Acknowledged", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(onClick = { viewModel.removeAgentRelation(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    SectionHeader("প্রাপ্ত তথ্য")
                    
                    viewModel.fieldTypesState.value.forEachIndexed { idx, fieldType ->
                        val banglaIdx = toBanglaNumber(idx + 1)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "$banglaIdx. ${fieldType.name}",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = Color.Black,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            OutlinedTextField(
                                value = viewModel.questionResponsesState.value[fieldType.workingPlaceReceivedFieldTypeId] ?: "",
                                onValueChange = { viewModel.updateQuestionResponse(fieldType.workingPlaceReceivedFieldTypeId, it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                textStyle = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }

                    val isAcknowledgmentMode = reportStatus == 0 && reportFlowLabel == 0 && reportId != 0
                    val isAcknowledgePending = reportStatus == 2 && reportFlowLabel == 0
                    
                    val relations by viewModel.agentReportRelationsState
                    val currentUserId by viewModel.currentUserIdState
                    val allAcknowledged = relations.isNotEmpty() && relations.all { it.isAcknowledged }
                    val isLeadAgent = relations.any { it.isLead && it.agentId == currentUserId }
                    val isForwardingMode = isAcknowledgePending && allAcknowledged && isLeadAgent

                    val buttonText = when {
                        isForwardingMode -> "Forward to Supervisor"
                        isAcknowledgePending -> "Update"
                        isAcknowledgmentMode -> "Send for Acknowledgment"
                        else -> "Save"
                    }

                    if (isSubmitted) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = if (reportFlowLabel == 1) "Report Forwarded to Supervisor" else "Report Submitted",
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Button(
                            onClick = { 
                                if (isForwardingMode) {
                                    viewModel.saveReport(isForwarding = true)
                                } else if (isAcknowledgmentMode || isAcknowledgePending) {
                                    showConfirmSubmitDialog = true 
                                } else {
                                    showConfirmSaveDialog = true 
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = if (isForwardingMode || isAcknowledgmentMode || isAcknowledgePending) Color(0xFF689F38) else Color(0xFF3F51B5)),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isAcknowledgmentMode || isAcknowledgePending) Icons.Default.Send else Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(buttonText)
                            }
                        }
                    }

                    Spacer(Modifier.height(30.dp))
                }

                if (isPageLoading) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Black Page Overlay for Individual Workplace Data
                val editingJobIndex by viewModel.currentEditingJobIndexState
                if (editingJobIndex != null) {
                    val jobIndex = editingJobIndex!!
                    val job = viewModel.jobHistoryListState.value.getOrNull(jobIndex)
                    
                    if (job != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .clickable { /* Consume clicks */ }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "কর্মস্থল-${jobIndex + 1} এর প্রাপ্ত তথ্য",
                                        color = Color.Black,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { viewModel.currentEditingJobIndexState.value = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                                    }
                                }
                                
                                Text(
                                    text = job.nameAndAddress,
                                    color = Color.DarkGray,
                                    fontSize = 14.sp
                                )
                                
                                HorizontalDivider(color = Color.LightGray)

                                viewModel.fieldTypesState.value.forEachIndexed { idx, fieldType ->
                                    val banglaIdx = toBanglaNumber(idx + 1)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "$banglaIdx. ${fieldType.name}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = Color.Black,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        OutlinedTextField(
                                            value = job.questionResponses[fieldType.workingPlaceReceivedFieldTypeId] ?: "",
                                            onValueChange = { viewModel.updateJobQuestionResponse(jobIndex, fieldType.workingPlaceReceivedFieldTypeId, it) },
                                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)),
                                            minLines = 2,
                                            shape = RoundedCornerShape(4.dp),
                                            textStyle = TextStyle(color = Color.Black),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = Color.Gray,
                                                focusedBorderColor = Color(0xFF0B4AA2),
                                                cursorColor = Color.Black
                                            )
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = { viewModel.currentEditingJobIndexState.value = null },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4AA2)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("DONE", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(Modifier.height(50.dp))
                            }
                        }
                    }
                }
            }

            if (showConfirmSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmSaveDialog = false },
                    title = { Text("Confirm Save") },
                    text = { Text("Are you sure you want to save this report?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmSaveDialog = false
                            viewModel.saveReport(isAcknowledgment = false)
                        }) { Text("SAVE") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSaveDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showConfirmSubmitDialog) {
                val isPending = reportStatus == 2 && reportFlowLabel == 0
                AlertDialog(
                    onDismissRequest = { showConfirmSubmitDialog = false },
                    title = { Text(if (isPending) "Update Report" else "Confirm Submit") },
                    text = { Text(if (isPending) "Are you sure you want to update the acknowledgment status/remarks?" else "Are you sure you want to send this report for acknowledgment?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmSubmitDialog = false
                            viewModel.saveReport(isAcknowledgment = true)
                        }) { Text(if (isPending) "UPDATE" else "SUBMIT") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSubmitDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (viewModel.showNoAgentFoundDialogState.value) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissNoAgentFoundDialog() },
                    title = { Text("Search Result") },
                    text = { Text("No agent found") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissNoAgentFoundDialog() }) { Text("OK") }
                    },
                )
            }

            if (agentDialogIndex != null) {
                AlertDialog(
                    onDismissRequest = { agentDialogIndex = null },
                    title = { Text("Agent details") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = agentRemarksInput,
                                onValueChange = { agentRemarksInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Remarks") },
                                minLines = 3,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = agentAcknowledgedInput,
                                    onCheckedChange = { checked -> agentAcknowledgedInput = checked },
                                )
                                Text("isAcknowledged")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                agentDialogIndex?.let { idx ->
                                    viewModel.updateAgentRelation(
                                        index = idx,
                                        remarks = agentRemarksInput,
                                        isAcknowledged = agentAcknowledgedInput,
                                    )
                                }
                                agentDialogIndex = null
                            },
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { agentDialogIndex = null }) { Text("Cancel") }
                    },
                )
            }
        }
    }
}

@Composable
fun TableIdentityRow(
    label: String,
    state: MutableState<String>,
    onValueChange: (String) -> Unit = { state.value = it },
    isHeader: Boolean = false
) {
    val backgroundColor = if (isHeader) Color(0xFFE3F2FD) else Color.White
    val valueBackgroundColor = if (!isHeader && state.value.isNotEmpty()) Color(0xFFEEEEEE) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(backgroundColor)
            .border(0.5.dp, Color.Gray)
    ) {
        Box(
            modifier = Modifier
                .weight(4f)
                .fillMaxHeight()
                .background(if (isHeader) Color(0xFFE3F2FD) else Color(0xFFE3F2FD))
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = label, fontSize = 13.sp, color = Color.Black)
        }
        
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Gray))
        
        Row(
            modifier = Modifier
                .weight(6f)
                .fillMaxHeight()
                .background(valueBackgroundColor)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isHeader) {
                Text(":", fontWeight = FontWeight.Bold, color = Color.Black)
            }
            BasicTextField(
                value = state.value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                textStyle = TextStyle(color = Color.Black, fontSize = 13.sp)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF90CAF9))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
    }
}

fun toBanglaNumber(number: Int): String {
    val banglaDigits = arrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    return number.toString().map { banglaDigits[it - '0'] }.joinToString("")
}
