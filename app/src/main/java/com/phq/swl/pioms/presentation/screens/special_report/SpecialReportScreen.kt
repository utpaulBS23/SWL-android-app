package com.phq.swl.pioms.presentation.screens.special_report

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.io.File

private fun normalizeInput(text: String): String = text.replace("+", " ")

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialReportScreen(
    complainId: Int,
    complainNo: String,
    applicantData: String? = null,
    specialReportId: Int? = null,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val viewModel: SpecialReportViewModel = koinViewModel()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(complainId, complainNo, applicantData, specialReportId) {
        if (specialReportId != null && specialReportId > 0) {
            // Load existing report from API
            viewModel.loadSpecialReportData(specialReportId)
        } else {
            // Initialize new report with applicant data
            viewModel.initData(complainId, complainNo, applicantData)
        }
    }

    val saveSuccess by viewModel.saveSuccessState
    val saveMessage by viewModel.saveMessageState
    val reportStatus by viewModel.reportStatusState
    val reportFlowLabel by viewModel.reportFlowLabelState
    val isSaving by viewModel.isSavingState
    val isLoadingReport by viewModel.isLoadingReportState
    val complainTypes by viewModel.complainTypesState
    val isLoadingComplainTypes by viewModel.isLoadingComplainTypesState
    
    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var showComplainTypeDialog by remember { mutableStateOf(false) }
    var agentRemarksDialogIndex by remember { mutableStateOf<Int?>(null) }
    var agentRemarksInput by remember { mutableStateOf("") }
    var agentAcknowledgedInput by remember { mutableStateOf(false) }

    LaunchedEffect(saveSuccess, saveMessage) {
        saveMessage?.let { msg ->
            if (saveSuccess == true) {
                launch { snackbarHostState.showSnackbar(msg) }
                delay(1500)
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
                        containerColor = if (isSuccess) Color(0xFF388E3C) else Color(0xFFD32F2F),
                        contentColor = Color.White,
                        snackbarData = data
                    )
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("বিশেষ প্রতিবেদন", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF0B4AA2),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            if (isLoadingReport) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                SectionHeader("প্রতিবেদনের শিরোনাম")
                OutlinedTextField(
                    value = viewModel.reportTitleState.value,
                    onValueChange = { viewModel.reportTitleState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                SectionHeader("পরিচিতি")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    val bpDisplay = remember(viewModel.bpNumberState.value) {
                        val raw = viewModel.bpNumberState.value.trim()
                        when {
                            raw.isBlank() -> ""
                            raw.startsWith("BP", ignoreCase = true) || raw.startsWith("CIV", ignoreCase = true) -> raw
                            else -> "BP$raw"
                        }
                    }
                    Column {
                        IdentityRow("নাম", viewModel.banglaNameState)
                        IdentityRow("পদবি", viewModel.designationState)
                        IdentityRow("বিপি/সিআইভি", mutableStateOf(bpDisplay), readOnly = true)
                        IdentityRow("বর্তমান কর্মস্থল", viewModel.currentWorkplaceState)
                        IdentityRow("যোগদানের তারিখ", viewModel.joiningDateState)
                        IdentityRow("পূর্ববর্তী কর্মস্থল", viewModel.previousWorkplaceState)
                        IdentityRow("নিজ জেলা", viewModel.homeDistrictState)
                        IdentityRow("মোবাইল নম্বর", viewModel.mobileState)
                        IdentityRow("পূর্ববর্তী অনিয়ম", viewModel.irregularitiesState)
                        IdentityRow("অন্যান্য তথ্য", viewModel.othersInfoState)
                    }
                }

                SectionHeader("প্রতিবেদনের সারসংক্ষেপ")
                OutlinedTextField(
                    value = viewModel.reportSummaryState.value,
                    onValueChange = { viewModel.reportSummaryState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )

                SectionHeader("Multiple Agent (if needed)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.agentSearchQueryState.value,
                        onValueChange = { viewModel.agentSearchQueryState.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("বিপি/সিআইভি খুঁজুন") },
                        singleLine = true
                    )
                    IconButton(
                        onClick = { viewModel.searchAgents() },
                        enabled = !viewModel.isSearchingAgentsState.value
                    ) {
                        if (viewModel.isSearchingAgentsState.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF1976D2),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search agents", tint = Color(0xFF1976D2))
                        }
                    }
                }
                if (viewModel.agentSearchResultsState.value.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("ফলাফল — যোগ করতে ট্যাপ করুন", fontSize = 12.sp, color = Color.DarkGray)
                            viewModel.agentSearchResultsState.value.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.addAgentFromSearch(result) }
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(result.userFullName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                if (viewModel.showNoAgentFoundDialogState.value) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissNoAgentFoundDialog() },
                        title = { Text("Search Result") },
                        text = { Text("No agent found") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.dismissNoAgentFoundDialog() }) {
                                Text("OK")
                            }
                        }
                    )
                }
                viewModel.agentReportRelationsState.value.forEachIndexed { index, rel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                agentRemarksDialogIndex = index
                                agentRemarksInput = rel.remarks.orEmpty()
                                agentAcknowledgedInput = rel.isAcknowledged
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FF)),
                        border = BorderStroke(0.5.dp, Color(0xFFBBDEFB))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                if (agentRemarksDialogIndex != null) {
                    AlertDialog(
                        onDismissRequest = { agentRemarksDialogIndex = null },
                        title = { Text("Agent details") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = agentRemarksInput,
                                    onValueChange = { agentRemarksInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Remarks") },
                                    minLines = 3
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = agentAcknowledgedInput,
                                        onCheckedChange = { checked -> agentAcknowledgedInput = checked }
                                    )
                                    Text("isAcknowledged")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    agentRemarksDialogIndex?.let { idx ->
                                        viewModel.updateAgentRelation(
                                            index = idx,
                                            remarks = agentRemarksInput,
                                            isAcknowledged = agentAcknowledgedInput
                                        )
                                    }
                                    agentRemarksDialogIndex = null
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { agentRemarksDialogIndex = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                SectionHeader("অভিযোগ সমূহ")
                viewModel.allegationsState.value.forEachIndexed { index: Int, item: AllegationItem ->
                    AllegationItemView(
                        index = index,
                        item = item,
                        viewModel = viewModel,
                        complainTypes = complainTypes,
                        onUpdate = { updatedItem: AllegationItem -> viewModel.updateAllegation(index, updatedItem) },
                        onRemove = { viewModel.removeAllegation(index) },
                        onAddAttachment = { title: String, uri: Uri, file: java.io.File? ->
                            viewModel.addAttachmentToAllegation(index, title, uri, file)
                        },
                        onRemoveAttachment = { attachmentIndex: Int ->
                            viewModel.removeAttachmentFromAllegation(index, attachmentIndex)
                        }
                    )
                }

                Button(
                    onClick = {
                        viewModel.loadComplainTypes()
                        showComplainTypeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF8E1), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ আরো অভিযোগ অ্যাড করুন")
                }

                SectionHeader("সুপারিশ")
                OutlinedTextField(
                    value = viewModel.agentRecommendationState.value,
                    onValueChange = { viewModel.agentRecommendationState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )

                val isAcknowledgmentMode = reportStatus == 0 && reportFlowLabel == 0 && specialReportId != null && specialReportId != 0
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
                    shape = RoundedCornerShape(8.dp)
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
                
                if (showConfirmSaveDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmSaveDialog = false },
                        title = { Text("Confirm Save") },
                        text = { Text("Are you sure you want to save this report as draft?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showConfirmSaveDialog = false
                                viewModel.saveReport(isAcknowledgment = false)
                            }) { Text("Confirm") }
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
                            }) { Text(if (isPending) "UPDATE" else "CONFIRM") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmSubmitDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showComplainTypeDialog) {
                    AlertDialog(
                        onDismissRequest = { showComplainTypeDialog = false },
                        title = { Text("অভিযোগের ধরন নির্বাচন করুন", fontWeight = FontWeight.Bold) },
                        text = {
                            if (isLoadingComplainTypes) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            } else if (complainTypes.isEmpty()) {
                                Text("No complain types found")
                            } else {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 420.dp)
                                            .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    complainTypes.forEach { type ->
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.addAllegation(type)
                                                showComplainTypeDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text(text = type.title, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showComplainTypeDialog = false }) {
                                Text("Close")
                            }
                        },
                    )
                }

                Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFBBDEFB))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
fun IdentityRow(label: String, state: MutableState<String>, readOnly: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .border(0.5.dp, Color.LightGray)
    ) {
        Box(
            modifier = Modifier
                .weight(4f)
                .fillMaxHeight()
                .background(Color(0xFFE3F2FD))
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(label, fontSize = 14.sp, color = Color.Black)
        }
        Box(
            modifier = Modifier
                .weight(6f)
                .fillMaxHeight()
                .padding(4.dp)
        ) {
            BasicTextField(
                value = state.value,
                onValueChange = { if (!readOnly) state.value = normalizeInput(it) },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.Black)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllegationItemView(
    index: Int,
    item: AllegationItem,
    viewModel: SpecialReportViewModel,
    complainTypes: List<SpecialReportComplainType>,
    onUpdate: (AllegationItem) -> Unit,
    onRemove: () -> Unit,
    onAddAttachment: (String, Uri, File?) -> Unit,
    onRemoveAttachment: (Int) -> Unit
) {
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = viewModel.copyUriToFile(context, uri)
            onAddAttachment("Attachment ${item.attachments.size + 1}", uri, file)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        border = BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("অভিযোগ ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(0.5.dp, Color.LightGray)
            ) {
                Box(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight()
                        .background(Color(0xFFE3F2FD))
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("অভিযোগের ধরন", fontSize = 14.sp, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(":", fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = item.title,
                            onValueChange = {
                                val normalized = normalizeInput(it)
                                val matchedType = complainTypes.firstOrNull { type ->
                                    type.title.equals(normalized.trim(), ignoreCase = true)
                                }
                                onUpdate(
                                    item.copy(
                                        title = normalized,
                                        complainTypeId = matchedType?.id,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.Black),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(0.5.dp, Color.LightGray)
            ) {
                Box(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight()
                        .background(Color(0xFFE3F2FD))
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("অভিযোগের বিস্তারিত", fontSize = 14.sp, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(":", fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = item.details,
                            onValueChange = { onUpdate(item.copy(details = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.Black)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .border(0.5.dp, Color.LightGray)
            ) {
                Box(
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight()
                        .background(Color(0xFFE3F2FD))
                        .padding(8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("সাক্ষী/প্রমাণ", fontSize = 14.sp, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .weight(6f)
                        .fillMaxHeight()
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(":", fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = item.witness,
                            onValueChange = { onUpdate(item.copy(witness = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.Black)
                        )
                    }
                }
            }

            // Attachment section
            Text("সংযুক্তি", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            item.attachments.forEachIndexed { attIndex, attachment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(attachment.title, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    IconButton(onClick = { onRemoveAttachment(attIndex) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            TextButton(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("ফাইল যোগ করুন", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CircularProgressIndicator(size: androidx.compose.ui.unit.Dp, color: Color) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp
    )
}
