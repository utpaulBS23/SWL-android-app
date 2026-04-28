package com.phq.swl.pioms.presentation.screens.incident_report

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentScreen(
    incidentReportId: Int? = null,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val viewModel: IncidentViewModel = koinViewModel()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isPageLoading by viewModel.isPageLoadingState
    val isSaving by viewModel.isSavingState
    val saveSuccess by viewModel.saveSuccessState
    val saveMessage by viewModel.saveMessageState
    val reportStatus by viewModel.reportStatusState
    val reportFlowLabel by viewModel.reportFlowLabelState
    
    val thanaList by viewModel.thanaListState
    val selectedThanaId by viewModel.selectedThanaIdState
    
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var showConfirmSaveDialog by remember { mutableStateOf(false) }

    val isSearchingAgents by viewModel.isSearchingAgentsState
    val agentSearchResults by viewModel.agentSearchResultsState
    val agentSearchQuery by viewModel.agentSearchQueryState
    val agentReportRelations by viewModel.agentReportRelationsState
    val currentUserId by viewModel.currentUserIdState

    var agentRemarksDialogIndex by remember { mutableStateOf<Int?>(null) }
    var agentRemarksInput by remember { mutableStateOf("") }
    var agentAcknowledgedInput by remember { mutableStateOf(false) }

    LaunchedEffect(incidentReportId) {
        incidentReportId?.takeIf { it > 0 }?.let {
            viewModel.loadIncidentReport(it)
        }
    }

    LaunchedEffect(saveSuccess, saveMessage) {
        saveMessage?.let { msg ->
            launch {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short,
                    withDismissAction = true
                )
            }
            if (saveSuccess == true) {
                delay(1500)
                onSaveSuccess()
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        viewModel.attachmentUrisState.value = viewModel.attachmentUrisState.value + uris
    }

    FaceNetAndroidTheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    snackbar = { snackbarData ->
                        val backgroundColor = if (saveSuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            containerColor = backgroundColor,
                            contentColor = Color.White,
                            dismissAction = {
                                snackbarData.dismiss()
                            }
                        ) {
                            Text(
                                text = snackbarData.visuals.message,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                )
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Incident Report Entry", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF0B4AA2)
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Thana Dropdown
                    LabelText("থানা")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedThana = thanaList.find { it.thanaID == selectedThanaId }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedThana?.thanaName ?: "Select Thana",
                                color = if (selectedThana == null) Color.Gray else Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = LocalContentColor.current)
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            thanaList.forEach { thana ->
                                DropdownMenuItem(
                                    text = { Text(thana.thanaName) },
                                    onClick = {
                                        viewModel.selectedThanaIdState.value = thana.thanaID
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Date and Time Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            LabelText("ঘটনার তারিখ")
                            OutlinedTextField(
                                value = viewModel.dateState.value,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp).clickable {
                                            val date = LocalDate.parse(viewModel.dateState.value, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                            DatePickerDialog(context, { _, year, month, day ->
                                                viewModel.dateState.value = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                                        }
                                    )
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            LabelText("সময়")
                            OutlinedTextField(
                                value = viewModel.timeState.value,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp).clickable {
                                            val time = LocalTime.parse(viewModel.timeState.value, DateTimeFormatter.ofPattern("hh:mm a"))
                                            TimePickerDialog(context, { _, hour, minute ->
                                                viewModel.timeState.value = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("hh:mm a"))
                                            }, time.hour, time.minute, false).show()
                                        }
                                    )
                                }
                            )
                        }
                    }

                    // Subject
                    LabelText("বিষয়")
                    OutlinedTextField(
                        value = viewModel.subjectState.value,
                        onValueChange = { viewModel.subjectState.value = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Description
                    LabelText("ঘটনার বিবরণ")
                    OutlinedTextField(
                        value = viewModel.descriptionState.value,
                        onValueChange = { viewModel.descriptionState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    // Attachments
                    LabelText("সংযুক্তি")
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE), contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose File:")
                    }

                    // Attachment List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.attachmentUrisState.value.forEach { uri ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color.LightGray)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = uri.lastPathSegment ?: "File",
                                        fontSize = 12.sp,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp).clickable {
                                            viewModel.attachmentUrisState.value = viewModel.attachmentUrisState.value - uri
                                        },
                                        tint = Color.Red
                                    )
                                }
                            }
                        }
                    }

                    // Agent Search and Relations
                    SectionHeader("তদন্তকারী সদস্যগণ")
                    
                    // Search Bar
                    OutlinedTextField(
                        value = agentSearchQuery,
                        onValueChange = { viewModel.agentSearchQueryState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by BP or Civ...") },
                        trailingIcon = {
                            if (isSearchingAgents) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { viewModel.searchAgents() }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                        },
                        singleLine = true
                    )

                    // Search Results
                    if (agentSearchResults.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Column {
                                agentSearchResults.forEach { result ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(result.userFullName, fontWeight = FontWeight.Bold)
                                                Text("BP: ${result.bpNumber} | Code: ${result.userCode}", fontSize = 12.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = { viewModel.addAgentFromSearch(result) }
                                    )
                                    HorizontalDivider(color = Color.LightGray)
                                }
                            }
                        }
                    }

                    // Selected Agents List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        agentReportRelations.forEachIndexed { index, rel ->
                            AgentRelationItemView(
                                index = index,
                                relation = rel,
                                onRemove = { viewModel.removeAgentRelation(index) },
                                onClick = {
                                    agentRemarksDialogIndex = index
                                    agentRemarksInput = rel.remarks ?: ""
                                    agentAcknowledgedInput = rel.isAcknowledged
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    val isAcknowledgmentMode = reportStatus == 0 && reportFlowLabel == 0 && incidentReportId != null && incidentReportId != 0
                    val isAcknowledgePending = reportStatus == 2 && reportFlowLabel == 0
                    
                    val allAcknowledged = agentReportRelations.isNotEmpty() && agentReportRelations.all { it.isAcknowledged }
                    val isLeadAgent = agentReportRelations.any { it.isLead && it.agentId == currentUserId }
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
                                viewModel.submitReport(isForwarding = true)
                            } else if (isAcknowledgmentMode || isAcknowledgePending) {
                                showConfirmSubmitDialog = true 
                            } else {
                                showConfirmSaveDialog = true 
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isForwardingMode || isAcknowledgmentMode || isAcknowledgePending) Color(0xFF689F38) else Color(0xFF3F51B5)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
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

                    Spacer(Modifier.height(32.dp))
                }

                if (isPageLoading) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (showConfirmSubmitDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmSubmitDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.Green)
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm Submit", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = { Text("Are you sure you want to submit this incident report?") },
                    confirmButton = {
                        val isAcknowledgmentMode = reportStatus == 0 && reportFlowLabel == 0 && incidentReportId != null && incidentReportId != 0
                        Button(onClick = {
                            showConfirmSubmitDialog = false
                            viewModel.submitReport(isAcknowledgment = isAcknowledgmentMode)
                        }, colors = ButtonDefaults.buttonColors(containerColor = if (isAcknowledgmentMode) Color(0xFF689F38) else Color(0xFF3F51B5))) {
                            Text(if (isAcknowledgmentMode) "ACKNOWLEDGE" else "SAVE")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSubmitDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
            }

            if (showConfirmSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmSaveDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFF3F51B5))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm Save", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = { Text("Do you want to save this incident report as a draft?") },
                    confirmButton = {
                        Button(onClick = {
                            showConfirmSaveDialog = false
                            viewModel.submitReport(isAcknowledgment = false)
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))) {
                            Text("SAVE")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSaveDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                )
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
                                label = { Text("Remarks") }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = agentAcknowledgedInput,
                                    onCheckedChange = { agentAcknowledgedInput = it }
                                )
                                Text("Is Acknowledged")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val idx = agentRemarksDialogIndex
                                if (idx != null) {
                                    val list = viewModel.agentReportRelationsState.value.toMutableList()
                                    if (idx in list.indices) {
                                        list[idx] = list[idx].copy(
                                            remarks = agentRemarksInput.ifBlank { null },
                                            isAcknowledged = agentAcknowledgedInput
                                        )
                                        viewModel.agentReportRelationsState.value = list
                                    }
                                }
                                agentRemarksDialogIndex = null
                            }
                        ) {
                            Text("SAVE")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { agentRemarksDialogIndex = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (viewModel.showNoAgentFoundDialogState.value) {
                AlertDialog(
                    onDismissRequest = { viewModel.showNoAgentFoundDialogState.value = false },
                    title = { Text("Search Result") },
                    text = { Text("No agent found") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.showNoAgentFoundDialogState.value = false }) { Text("OK") }
                    },
                )
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
fun AgentRelationItemView(
    index: Int,
    relation: AgentRelationItem,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
        border = BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${index + 1}. ${relation.agentName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = "BP: ${relation.bpNumber}", fontSize = 12.sp, color = Color.Gray)
                if (relation.isAcknowledged) {
                    Text(text = "Acknowledged", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(20.dp), tint = Color.Red)
            }
        }
    }
}

@Composable
fun LabelText(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
}
