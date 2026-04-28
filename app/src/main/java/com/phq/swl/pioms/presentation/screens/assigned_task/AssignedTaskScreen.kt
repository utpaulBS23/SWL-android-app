package com.phq.swl.pioms.presentation.screens.assigned_task

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phq.swl.pioms.presentation.screens.dashboard.DashboardScreenViewModel
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.phq.swl.pioms.R
import com.phq.swl.pioms.presentation.screens.dashboard.AssignedTaskInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignedTaskScreen(
    onNavigateBack: () -> Unit,
    onOpenTaskReportRoute: (String) -> Unit,
) {
    val viewModel: DashboardScreenViewModel = koinViewModel()
    val tasks by viewModel.assignedTasksState
    val userAutoId by viewModel.userAutoIdState
    val taskInfoItems by viewModel.taskInfoItemsState
    val taskInfoLoading by viewModel.taskInfoLoadingState
    val taskInfoError by viewModel.taskInfoErrorState

    var showTaskInfoDialog by remember { mutableStateOf(false) }
    var openingTaskRoute by remember { mutableStateOf(false) }

    LaunchedEffect(userAutoId) {
        val id = userAutoId ?: return@LaunchedEffect
        viewModel.loadAssignedTasks(id)
    }

    val sorted =
        tasks.sortedByDescending { parseAssignDateMillis(it.assignDate) }

    FaceNetAndroidTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.screen),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        colors =
                            TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        title = { Text(text = "Assigned Task", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                if (sorted.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "No assigned tasks.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sorted, key = { it.assignedTaskId }) { task ->
                            AssignedTaskCard(
                                taskId = task.assignedTaskId,
                                bpNumber = task.bpNumber,
                                unit = task.unit,
                                assignDate = formatAssignDate(task.assignDate),
                                onClick = {
                                    viewModel.loadTaskInfoForAssignedTask(task.assignedTaskId)
                                    showTaskInfoDialog = true
                                },
                            )
                        }
                    }
                }

                if (showTaskInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showTaskInfoDialog = false },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.Default.Description, contentDescription = null)
                                    }
                                }
                                Column {
                                    Text(text = "Task Info", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (taskInfoItems.isEmpty()) "" else "${taskInfoItems.size} item(s)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        text = {
                            when {
                                taskInfoLoading -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                !taskInfoError.isNullOrBlank() -> {
                                    Text(text = taskInfoError ?: "Failed")
                                }
                                openingTaskRoute -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                taskInfoItems.isEmpty() -> {
                                    Text(text = "No task info found.")
                                }
                                else -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        taskInfoItems.forEachIndexed { index, item ->
                                            if (index > 0) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                            }
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = MaterialTheme.shapes.medium,
                                                colors =
                                                    CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                                    ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                                onClick = {
                                                    openingTaskRoute = true
                                                    viewModel.resolveAssignedTaskRoute(item) { route ->
                                                        openingTaskRoute = false
                                                        if (route != null) {
                                                            showTaskInfoDialog = false
                                                            onOpenTaskReportRoute(route)
                                                        }
                                                    }
                                                },
                                            ) {
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                Brush.linearGradient(
                                                                    colors =
                                                                        listOf(
                                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                                                            Color.Transparent,
                                                                        ),
                                                                ),
                                                            )
                                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.size(40.dp),
                                                        shape = MaterialTheme.shapes.extraLarge,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        contentColor = MaterialTheme.colorScheme.primary,
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(imageVector = Icons.Default.Description, contentDescription = null)
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = "Task Ref: ${item.complainNo}",
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                        Text(
                                                            text = "Task Type: ${item.reportTypeName}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            OutlinedButton(onClick = { showTaskInfoDialog = false }) {
                                Text(text = "Close")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignedTaskCard(
    taskId: Int,
    bpNumber: String,
    unit: String,
    assignDate: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Assignment, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Task ID: $taskId",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            text = "BP: $bpNumber",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            text = assignDate,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun parseAssignDateMillis(raw: String): Long {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return 0L
    return runCatching {
        LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

private fun formatAssignDate(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return runCatching {
        val dt = LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
    }.getOrDefault(trimmed)
}
