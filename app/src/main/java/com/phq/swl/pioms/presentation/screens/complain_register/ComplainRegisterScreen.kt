package com.phq.swl.pioms.presentation.screens.complain_register

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplainRegisterScreen(
    onNavigateBack: () -> Unit,
    onOpenSpecialReport: (Int, String, String) -> Unit,
) {
    val viewModel: ComplainRegisterViewModel = koinViewModel()

    val snackbarHostState = remember { SnackbarHostState() }

    val applicantLoading by viewModel.applicantLoadingState
    val complainTypesLoading by viewModel.complainTypesLoadingState
    val complainTypes by viewModel.complainTypesState
    val complainList by viewModel.complainListState
    val rewardsLoading by viewModel.rewardsLoadingState
    val rewards by viewModel.rewardsState
    val punishmentsLoading by viewModel.punishmentsLoadingState
    val punishments by viewModel.punishmentsState
    val showExtraSections by viewModel.showExtraSectionsState
    val saving by viewModel.savingState
    val saveSuccess by viewModel.saveSuccessState
    val saveMessage by viewModel.saveMessageState
    val savedComplainId by viewModel.savedComplainIdState
    val savedComplainNo by viewModel.savedComplainNoState
    val photoBytes by viewModel.applicantPhotoBytesState
    val complainRefNo by viewModel.complainRefNoState

    val name by viewModel.nameState
    val rank by viewModel.rankState
    val mainUnit by viewModel.mainUnitState
    val currentPosition by viewModel.currentPositionState

    val isApplicantReady =
        name.trim().isNotBlank() &&
            rank.trim().isNotBlank() &&
            mainUnit.trim().isNotBlank()

    var showAddComplainDialog by remember { mutableStateOf(false) }
    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(saveSuccess, saveMessage, savedComplainId, savedComplainNo) {
        val msg = saveMessage?.trim().orEmpty()
        if (saveSuccess == true && msg.isNotBlank()) {
            // Show snackbar in a separate job within this scope so it doesn't block navigation
            launch {
                snackbarHostState.showSnackbar(msg)
            }
            
            if (savedComplainId != null && savedComplainNo != null) {
                val data = org.json.JSONObject().apply {
                    put("name", viewModel.nameState.value)
                    put("banglaName", viewModel.banglaNameState.value)
                    put("rank", viewModel.rankState.value)
                    put("bpNumber", viewModel.bpNumberState.value)
                    put("mainUnit", viewModel.mainUnitState.value)
                    put("currentPosition", viewModel.currentPositionState.value)
                    put("mobile", viewModel.mobileState.value)
                    put("homeDistrict", viewModel.homeDistrictState.value)
                    put("isMain", viewModel.isMainState.value)
                    put("recordDate", viewModel.recordDateUiState.value)
                    put("date_of_joining_at_present_rank", viewModel.applicantInfoState.value?.optString("date_of_joining_at_present_rank")?.substringBefore("T") ?: "")
                    put("notes", viewModel.remarksState.value)
                    put("complainList", org.json.JSONArray(complainList))

                    val relations = org.json.JSONArray()
                    for (c in complainList) {
                        val t = complainTypes.find { it.title == c }
                        relations.put(
                            org.json.JSONObject()
                                .put("complainTypeId", t?.id ?: 0)
                                .put("complain_Type_Details", c)
                        )
                    }
                    put("complain_ComplainType_Relations", relations)

                    val extraPeopleArr = org.json.JSONArray()
                    viewModel.extraPeopleState.value.forEach { p ->
                        extraPeopleArr.put(
                            org.json.JSONObject()
                                .put("specialReportAccusedId", p.specialReportAccusedId)
                                .put("englishName", p.englishName)
                                .put("banglaName", p.banglaName)
                                .put("bpNumber", p.bpNumber)
                                .put("designation", p.designation)
                                .put("currentWorkingPlace", p.currentWorkingPlace)
                                .put("joiningDateTime", p.joiningDateTime)
                                .put("previousWorkPlace", p.previousWorkPlace)
                                .put("homeDistrict", p.homeDistrict)
                                .put("phoneNumber", p.phoneNumber)
                                .put("punishment", p.punishment)
                                .put("reward", p.reward)
                                .put("previousIrregularity", p.previousIrregularity)
                                .put("specialReportId", p.specialReportId)
                                .put("isMain", p.isMain)
                        )
                    }
                    put("extraPeople", extraPeopleArr)

                    val accusedListArr = org.json.JSONArray()
                    viewModel.extraPeopleState.value.forEach { p ->
                        accusedListArr.put(
                            org.json.JSONObject()
                                .put("specialReportAccusedId", p.specialReportAccusedId)
                                .put("englishName", p.englishName)
                                .put("banglaName", p.banglaName)
                                .put("bpNumber", p.bpNumber)
                                .put("designation", p.designation)
                                .put("currentWorkingPlace", p.currentWorkingPlace)
                                .put("joiningDateTime", p.joiningDateTime)
                                .put("previousWorkPlace", p.previousWorkPlace)
                                .put("homeDistrict", p.homeDistrict)
                                .put("phoneNumber", p.phoneNumber)
                                .put("punishment", p.punishment)
                                .put("reward", p.reward)
                                .put("previousIrregularity", p.previousIrregularity)
                                .put("specialReportId", p.specialReportId)
                                .put("isMain", p.isMain)
                        )
                    }
                    put("accusedList", accusedListArr)
                }
                onOpenSpecialReport(savedComplainId!!, savedComplainNo!!, data.toString())
            } else {
                onNavigateBack()
            }
        } else if (saveSuccess == false && msg.isNotBlank()) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val primaryBlue = Color(0xFF0B4AA2)
    val darkTile = Color(0xFF34495E)
    val screenBg = Color.White

    FaceNetAndroidTheme {
        Scaffold(
            snackbarHost = { 
                SnackbarHost(snackbarHostState) { data ->
                    val isSuccess = saveSuccess == true
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        containerColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                        contentColor = Color.White
                    )
                }
            },
            topBar = {
                TopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = primaryBlue,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                    title = { Text(text = "Special Report", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::clearAll, enabled = !saving) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    },
                )
            },
            containerColor = screenBg,
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GradientHeader(title = "Special Report Entry", backgroundColor = darkTile)
                    DemoComplainDetailsSection(
                        viewModel = viewModel,
                        bpNumber = viewModel.bpNumberState.value,
                        onBpChange = { viewModel.bpNumberState.value = it },
                        onSearch = viewModel::fetchApplicantByBp,
                        searching = applicantLoading,
                        complainRefNo = complainRefNo,
                        photoBytes = photoBytes,
                        name = viewModel.nameState.value,
                        rank = viewModel.rankState.value,
                        mainUnit = viewModel.mainUnitState.value,
                        currentPosition = viewModel.currentPositionState.value,
                        mobile = viewModel.mobileState.value,
                        recordDate = viewModel.recordDateUiState.value,
                        onOpenDatePicker = { showDatePickerDialog = true },
                        notes = viewModel.remarksState.value,
                        onNotesChange = {
                            viewModel.remarksState.value = it
                            viewModel.complainDetailsState.value = it
                        },
                        onAddComplains = { showAddComplainDialog = true },
                        complainList = complainList,
                        onRemoveComplain = viewModel::removeComplain,
                        primaryBlue = primaryBlue,
                        enabled = !saving,
                    )


                    Button(
                        onClick = { showConfirmSaveDialog = true },
                        enabled = isApplicantReady && complainList.isNotEmpty() && !saving,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = primaryBlue,
                                contentColor = Color.White,
                                disabledContainerColor = Color.LightGray,
                                disabledContentColor = Color.White,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Saving…")
                        } else {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Save")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (showAddComplainDialog) {
                    AddComplainDialog(
                        isLoadingTemplates = complainTypesLoading,
                        templates = complainTypes,
                        currentItems = complainList,
                        onAdd = viewModel::addComplain,
                        onRemove = viewModel::removeComplain,
                        onDismiss = { showAddComplainDialog = false },
                    )
                }

                if (showConfirmSaveDialog) {
                    ConfirmSaveDialog(
                        onConfirm = {
                            showConfirmSaveDialog = false
                            viewModel.saveComplain()
                        },
                        onDismiss = { showConfirmSaveDialog = false },
                    )
                }

                if (showDatePickerDialog) {
                    val formatter = remember { DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH) }
                    val initial =
                        remember(viewModel.recordDateUiState.value) {
                            runCatching { LocalDate.parse(viewModel.recordDateUiState.value.trim(), formatter) }
                                .getOrNull()
                                ?: LocalDate.now()
                        }
                    val initialMillis =
                        remember(initial) {
                            initial
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                    val datePickerState =
                        androidx.compose.material3.rememberDatePickerState(
                            initialSelectedDateMillis = initialMillis,
                        )

                    androidx.compose.material3.DatePickerDialog(
                        onDismissRequest = { showDatePickerDialog = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val millis = datePickerState.selectedDateMillis ?: initialMillis
                                    val date =
                                        Instant
                                            .ofEpochMilli(millis)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                                    viewModel.recordDateUiState.value = date.format(formatter)
                                    showDatePickerDialog = false
                                },
                            ) {
                                Text(text = "OK")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePickerDialog = false }) { Text(text = "Cancel") }
                        },
                    ) {
                        androidx.compose.material3.DatePicker(state = datePickerState)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientHeader(
    title: String,
    backgroundColor: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DemoComplainDetailsSection(
    viewModel: ComplainRegisterViewModel,
    bpNumber: String,
    onBpChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    complainRefNo: Int,
    photoBytes: ByteArray?,
    name: String,
    rank: String,
    mainUnit: String,
    currentPosition: String,
    mobile: String,
    recordDate: String,
    onOpenDatePicker: () -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onAddComplains: () -> Unit,
    complainList: List<String>,
    onRemoveComplain: (String) -> Unit,
    primaryBlue: Color,
    enabled: Boolean,
) {
    val extraPeople by viewModel.extraPeopleState

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                    .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "BP/CIV", fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(45.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = bpNumber,
                        onValueChange = onBpChange,
                        enabled = enabled && !searching,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                        cursorBrush = SolidColor(primaryBlue),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (bpNumber.isEmpty()) {
                                    Text(text = "Enter BP/CIV", color = Color.Gray, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier =
                        Modifier
                            .size(width = 40.dp, height = 35.dp)
                            .background(primaryBlue, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSearch, enabled = enabled && bpNumber.trim().isNotBlank()) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "Ref. No : $complainRefNo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.W500,
                color = Color.Black,
            )
        }

        if (photoBytes != null) {
            val bitmap =
                remember(photoBytes) { runCatching { BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size) }.getOrNull() }
            if (bitmap != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo",
                        modifier =
                            Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDEDED)),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val context = LocalContext.current
            Button(
                onClick = {
                    viewModel.addCurrentApplicantToAccused()
                    Toast.makeText(context, "Added", Toast.LENGTH_SHORT).show()
                },
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Add")
            }
        }

        
        DemoLabel("Name")
        DemoReadOnlyField(viewModel.banglaNameState.value.ifBlank { name })

        DemoLabel("Present Rank")
        DemoReadOnlyField(rank)

        DemoLabel("Main Unit")
        DemoReadOnlyField(mainUnit)

        DemoLabel("Current Position")
        DemoReadOnlyField(currentPosition)

        DemoLabel("Mobile")
        DemoReadOnlyField(mobile)

        if (extraPeople.isNotEmpty()) {
            ComplainExtraPeopleEditor(
                items = extraPeople,
                enabled = enabled,
                onRemove = { viewModel.removeExtraPerson(it) },
                onUpdate = { id, item -> viewModel.updateExtraPerson(id, item) },
                primaryBlue = primaryBlue,
            )
        }

        DemoLabel("Report Date")
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = recordDate,
                color = Color.Black,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onOpenDatePicker, enabled = enabled) {
                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pick date", tint = Color.Gray)
            }
        }

        DemoLabel("Notes")
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp))
                    .background(Color.White, RoundedCornerShape(4.dp)),
        ) {
            androidx.compose.material3.TextField(
                value = notes,
                onValueChange = onNotesChange,
                enabled = enabled,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.Black),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = primaryBlue,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        disabledTextColor = Color.Black,
                    ),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Button(
                onClick = onAddComplains,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Add Complains")
            }
        }

        Text(text = "List of Complains", style = MaterialTheme.typography.labelSmall, color = Color.Black)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            complainList.forEach { complain ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = complain,
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemoveComplain(complain) }, enabled = enabled) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                    }
                }
            }
            if (complainList.isEmpty()) {
                Text(text = "No complains added", color = Color.Black)
            }
        }
    }
}

@Composable
private fun DemoLabel(text: String) {
    Text(text = text, color = Color.Black, fontSize = 13.sp)
}

@Composable
private fun DemoReadOnlyField(value: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(45.dp)
                .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { "-" },
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComplainExtraPeopleEditor(
    items: List<ComplainExtraPersonItem>,
    enabled: Boolean,
    onRemove: (Long) -> Unit,
    onUpdate: (Long, ComplainExtraPersonItem) -> Unit,
    primaryBlue: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFD0D0D0), RoundedCornerShape(4.dp))
                .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "Accused List", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.W600)
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DemoLabel("Name")
                    DemoReadOnlyField(item.banglaName.ifBlank { item.englishName })
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Main", fontSize = 12.sp, color = Color.Black)
                    Checkbox(
                        checked = item.isMain,
                        enabled = enabled,
                        onCheckedChange = { checked ->
                            onUpdate(item.id, item.copy(isMain = checked))
                        },
                    )
                }

                IconButton(
                    onClick = { onRemove(item.id) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color(0xFFE53935),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicantSearchCard(
    bpNumber: String,
    onBpChange: (String) -> Unit,
    recordDate: String,
    onRecordDateChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
) {
    SectionCard(
        title = "BP & Date",
        accent = Color(0xFF34495E),
    ) {
        OutlinedTextField(
            value = bpNumber,
            onValueChange = onBpChange,
            enabled = !isSearching,
            label = { Text(text = "BP Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = recordDate,
            onValueChange = onRecordDateChange,
            enabled = !isSearching,
            label = { Text(text = "Record Date (dd-MMM-yyyy)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSearch,
            enabled = !isSearching && bpNumber.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Searching…")
            } else {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Load Applicant Info")
            }
        }
    }
}

@Composable
private fun ApplicantInfoCard(
    name: String,
    rank: String,
    mainUnit: String,
    currentPosition: String,
    mobile: String,
    photoBytes: ByteArray?,
) {
    SectionCard(
        title = "Applicant Profile",
        accent = Color(0xFF34495E),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bitmap =
                remember(photoBytes) {
                    photoBytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
                }
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8EAF6)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Applicant photo",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFF5C6BC0),
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name.ifBlank { "Name: -" },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "Rank: ${rank.ifBlank { "-" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Unit: ${mainUnit.ifBlank { "-" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Posting: ${currentPosition.ifBlank { "-" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mobile.isNotBlank()) {
                    Text(text = "Mobile: $mobile", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(accent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun FlowRowChips(
    items: List<String>,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = remember(items) {
            val out = mutableListOf<List<String>>()
            var current = mutableListOf<String>()
            for (i in items) {
                current.add(i)
                if (current.size == 2) {
                    out.add(current)
                    current = mutableListOf()
                }
            }
            if (current.isNotEmpty()) out.add(current)
            out
        }

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (chip in row) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = chip,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { onRemove(chip) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Remove")
                            }
                        },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFF1F8E9),
                                labelColor = Color(0xFF1B5E20),
                                trailingIconContentColor = Color(0xFFB71C1C),
                            ),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddComplainDialog(
    isLoadingTemplates: Boolean,
    templates: List<ComplainType>,
    currentItems: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<ComplainType?>(null) }
    var customText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(customText)
                    customText = ""
                    selectedTemplate = null
                    expanded = false
                },
                enabled = customText.trim().isNotBlank(),
            ) {
                Text(text = "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        },
        title = { Text(text = "Add Complains") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isLoadingTemplates) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                    ) {
                        OutlinedTextField(
                            value = selectedTemplate?.title.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(text = "Select from Template") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            templates.forEach { t ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(text = t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        selectedTemplate = t
                                        customText = t.title
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customText,
                    onValueChange = {
                        customText = it
                        if (it.trim().isNotBlank() && selectedTemplate != null && it.trim() != selectedTemplate?.title) {
                            selectedTemplate = null
                        }
                    },
                    label = { Text(text = "Complain text") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (currentItems.isNotEmpty()) {
                    Text(text = "Added:", fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentItems.take(6).forEach { c ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF7F7FB), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = c, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onRemove(c) }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                                }
                            }
                        }
                        if (currentItems.size > 6) {
                            Text(text = "+${currentItems.size - 6} more", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmSaveDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E4BC6),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
        title = { Text(text = "Confirm Save") },
        text = { Text(text = "Are you sure you want to save this complain?") },
    )
}

@Composable
private fun CompactJsonList(
    items: List<org.json.JSONObject>,
    primaryKey: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(8).forEachIndexed { index, obj ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7FB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val title =
                        obj.optString(primaryKey, "")
                            .trim()
                            .ifBlank { obj.optString("title", "").trim() }
                            .ifBlank { obj.optString("name", "").trim() }
                            .ifBlank { "Item ${index + 1}" }
                    Text(text = title, fontWeight = FontWeight.SemiBold)
                    val date =
                        obj.optString("date", "").trim()
                            .ifBlank { obj.optString("rewardDate", "").trim() }
                            .ifBlank { obj.optString("punishmentDate", "").trim() }
                    if (date.isNotBlank()) {
                        Text(text = date, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (items.size > 8) {
            Text(text = "+${items.size - 8} more", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
