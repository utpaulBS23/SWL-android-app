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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
fun PersonalScreen(
    userInfoId: Int,
    complainId: Int,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val viewModel: PersonalViewModel = koinViewModel()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val primaryBlue = Color(0xFF0B4AA2)

    LaunchedEffect(userInfoId, complainId) {
        viewModel.initData(userInfoId, complainId)
    }

    val saveSuccess by viewModel.saveSuccessState
    val saveMessage by viewModel.saveMessageState
    val isSaving by viewModel.isSavingState
    val isPageLoading by viewModel.isPageLoadingState
    val isSearching by viewModel.isSearchingState
    val applicantPicture by viewModel.applicantPictureState
    val reportStatus by viewModel.reportStatusState
    val reportFlowLabel by viewModel.reportFlowLabelState
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
                    title = { Text("ব্যক্তিগত তথ্যাদি", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

                    SectionHeader("ব্যক্তিগত পরিচিতি")
                    
                    Column(modifier = Modifier.border(1.dp, Color.Gray)) {
                        TableIdentityRow("নাম", viewModel.nameState)
                        TableIdentityRow("বিপি/সিআইভি", viewModel.bpNumberState)
                        TableIdentityRow("পদবী (বর্তমান)", viewModel.designationState)
                        TableIdentityRow("যোগদানের তারিখ", viewModel.joiningDateTimeState)
                        TableIdentityRow("বর্তমান কর্মস্থল", viewModel.currentWorkingPlaceState)
                        TableIdentityRow("স্থায়ী ঠিকানা", viewModel.permanentAddressState)
                        TableIdentityRow("নিজ জেলা", viewModel.homeDistrictState)
                        TableIdentityRow("জন্ম তারিখ", viewModel.dateOfBirthState)
                        TableIdentityRow("ফোন নম্বর", viewModel.phoneNumberState)
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
                        )
                        IconButton(
                            onClick = { viewModel.searchAgents() },
                            enabled = !viewModel.isSearchingAgentsState.value,
                        ) {
                            if (viewModel.isSearchingAgentsState.value) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF1976D2), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search agents", tint = Color(0xFF1976D2))
                            }
                        }
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
                                    agentRemarksInput = rel.remarks.orEmpty()
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

                    SectionHeader("পরিবার সংক্রান্ত তথ্যাদি")
                    
                    viewModel.familyInfoListState.value.forEachIndexed { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            border = BorderStroke(0.5.dp, Color.Gray)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "সদস্য-${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    IconButton(onClick = { viewModel.removeFamilyInfo(index) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                
                                // Relation Dropdown
                                Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(0.5.dp, Color.Gray).background(Color.White)) {
                                    var expanded by remember { mutableStateOf(false) }
                                    val selectedRelation = viewModel.relationTypeListState.value.find { it.relationTypeId == item.relationTypeId }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "সম্পর্ক: ${selectedRelation?.name ?: "নির্বাচন করুন"}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.heightIn(max = 240.dp)
                                        ) {
                                            viewModel.relationTypeListState.value.forEach { type ->
                                                DropdownMenuItem(
                                                    text = { Text(type.name) },
                                                    onClick = {
                                                        viewModel.updateFamilyInfo(index, item.copy(relationTypeId = type.relationTypeId))
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                TableIdentityRow("বিস্তারিত তথ্য (নাম/ঠিকানা)", mutableStateOf(item.relationshipDetails), onValueChange = { viewModel.updateFamilyInfo(index, item.copy(relationshipDetails = it)) })
                                TableIdentityRow("পেশা", mutableStateOf(item.occupationDetails), onValueChange = { viewModel.updateFamilyInfo(index, item.copy(occupationDetails = it)) })
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = item.isPoliticalInvolvement,
                                        onCheckedChange = { viewModel.updateFamilyInfo(index, item.copy(isPoliticalInvolvement = it)) }
                                    )
                                    Text("রাজনৈতিক সংশ্লিষ্টতা আছে?", fontSize = 13.sp)
                                }
                                
                                if (item.isPoliticalInvolvement) {
                                    TableIdentityRow("রাজনৈতিক পদবী", mutableStateOf(item.politicalDesignation), onValueChange = { viewModel.updateFamilyInfo(index, item.copy(politicalDesignation = it)) })
                                    TableIdentityRow("বিস্তারিত রাজনৈতিক তথ্য", mutableStateOf(item.politicalDetailsInfo), onValueChange = { viewModel.updateFamilyInfo(index, item.copy(politicalDetailsInfo = it)) })
                                }
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Button(
                            onClick = { viewModel.addFamilyInfo() },
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

                    SectionHeader("বিগত ৫ বছরের আবাসিক ঠিকানা")
                    
                    viewModel.residentialAddressListState.value.forEachIndexed { index, item ->
                        Column(modifier = Modifier.border(1.dp, Color.Gray).background(Color.White).padding(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ঠিকানা-${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (viewModel.residentialAddressListState.value.size > 1) {
                                    IconButton(onClick = { viewModel.removeResidentialAddress(index) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            TableIdentityRow("ঠিকানা", mutableStateOf(item.address), onValueChange = { viewModel.updateResidentialAddress(index, item.copy(address = it)) })
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TableIdentityRow("হতে", mutableStateOf(item.startDateTime), onValueChange = { viewModel.updateResidentialAddress(index, item.copy(startDateTime = it)) })
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    TableIdentityRow("পর্যন্ত", mutableStateOf(item.endDateTime), onValueChange = { viewModel.updateResidentialAddress(index, item.copy(endDateTime = it)) })
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = { viewModel.addResidentialAddress() },
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

                    Spacer(Modifier.height(10.dp))

                    val isAcknowledgmentMode = reportStatus == 0 && reportFlowLabel == 0 && userInfoId != 0
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
                                    imageVector = if (isAcknowledgmentMode) Icons.Default.Send else Icons.Default.Save,
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
            }

            if (showConfirmSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmSaveDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = { Text("Confirm Save", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to save this report?", color = Color.DarkGray) },
                    containerColor = Color.White,
                    confirmButton = {
                        Button(
                            onClick = {
                            showConfirmSaveDialog = false
                            viewModel.saveReport(isAcknowledgment = false)
                        },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) { Text("SAVE", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSaveDialog = false }) { Text("Cancel", color = Color.Gray) }
                    }
                )
            }

            if (showConfirmSubmitDialog) {
                val isPending = reportStatus == 2 && reportFlowLabel == 0
                AlertDialog(
                    onDismissRequest = { showConfirmSubmitDialog = false },
                    title = { Text(if (isPending) "Update Report" else "Confirm Submit") },
                    text = { Text(if (isPending) "Are you sure you want to update the acknowledgment status/remarks?" else "Are you sure you want to send this report for acknowledgment?") },
                    containerColor = Color.White,
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmSubmitDialog = false
                                viewModel.saveReport(isAcknowledgment = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text(if (isPending) "UPDATE" else "SUBMIT", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmSubmitDialog = false }) { Text("Cancel", color = Color.Gray) }
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
