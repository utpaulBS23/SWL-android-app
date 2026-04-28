package com.phq.swl.pioms.presentation.screens.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.phq.swl.pioms.R
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import com.phq.swl.pioms.service.LocationHistoryForegroundService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import androidx.activity.result.IntentSenderRequest
import android.content.IntentSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenAttendanceEntry: () -> Unit,
    onOpenFaceRegistration: () -> Unit,
    onOpenSpecialReport: () -> Unit,
    onOpenVrReport: () -> Unit,
    onOpenIncidentReport: () -> Unit,
    onOpenComplainRegister: () -> Unit,
    onOpenAssignedTask: () -> Unit,
    onOpenDraftReports: () -> Unit,
    onOpenSubmittedReports: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: DashboardScreenViewModel = koinViewModel()
    val unseenTaskCount = viewModel.unseenTaskCountState.intValue
    val userAutoId by viewModel.userAutoIdState
    val authToken by viewModel.authTokenState

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showPasswordSheet by remember { mutableStateOf(false) }
    var showReportTypeSheet by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var lastBackPressedAt by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        viewModel.onDashboardOpened()
    }

    LaunchedEffect(userAutoId, authToken) {
        viewModel.onDashboardOpened()
    }

    var locationServicePermissionsGranted by remember {
        val fine =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val camera =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val notifications =
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        mutableStateOf((fine || coarse) && notifications && camera)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val camera = result[Manifest.permission.CAMERA] == true
            val notifications =
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    result[Manifest.permission.POST_NOTIFICATIONS] == true
                } else {
                    true
                }
            locationServicePermissionsGranted = (fine || coarse) && notifications && camera
        }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (locationServicePermissionsGranted) {
                LocationHistoryForegroundService.start(context)
            }
        }
    }

    LaunchedEffect(userAutoId, authToken, locationServicePermissionsGranted) {
        val hasSession = (userAutoId != null) && authToken.isNotBlank()
        if (!hasSession) return@LaunchedEffect
        if (locationServicePermissionsGranted) {
            checkLocationSettings(
                context = context,
                onEnabled = {
                    LocationHistoryForegroundService.start(context)
                },
                onDisabled = { intentSenderRequest ->
                    settingsLauncher.launch(intentSenderRequest)
                }
            )
            return@LaunchedEffect
        }

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        
        permissionLauncher.launch(permissions)
    }

    val faceRegHasObj by viewModel.faceRegHasResponseObjState
    val faceRegStatus by viewModel.faceRegStatusState
    val hideNavigationBar =
        (faceRegHasObj == false) || (faceRegStatus == 0) || (faceRegStatus == 2)

    BackHandler(enabled = !showLogoutDialog && !showPasswordSheet && !showReportTypeSheet && !showExitDialog) {
        if (selectedTab != 0) {
            selectedTab = 0
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPressedAt < 2000L) {
            showExitDialog = true
        } else {
            lastBackPressedAt = now
        }
    }

    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors =
                        TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    title = { Text(text = "Dashboard", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        NotificationAction(
                            count = unseenTaskCount,
                            onClick = { selectedTab = 2 },
                        )
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Change Password") },
                                onClick = {
                                    menuExpanded = false
                                    showPasswordSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = "Logout") },
                                onClick = {
                                    menuExpanded = false
                                    showLogoutDialog = true
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!hideNavigationBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                            label = { Text(text = "Home") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                ),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = onOpenSubmittedReports,
                            icon = { Icon(imageVector = Icons.Default.Description, contentDescription = "Reports") },
                            label = { Text(text = "Reports") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                ),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = onOpenAssignedTask,
                            icon = {
                                if (unseenTaskCount > 0) {
                                    BadgeBox(count = unseenTaskCount) {
                                        Icon(imageVector = Icons.Default.Assignment, contentDescription = "Tasks")
                                    }
                                } else {
                                    Icon(imageVector = Icons.Default.Assignment, contentDescription = "Tasks")
                                }
                            },
                            label = { Text(text = "Tasks") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                ),
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = {  }, //selectedTab = 3
                            icon = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More") },
                            label = { Text(text = "More") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                DashboardBackground()

                when (selectedTab) {
                    0 ->
                        DashboardHomeTab(
                            viewModel = viewModel,
                            unseenTaskCount = unseenTaskCount,
                            onOpenAttendanceEntry = onOpenAttendanceEntry,
                            onOpenFaceRegistration = onOpenFaceRegistration,
                            onOpenSpecialReport = onOpenSpecialReport,
                            onOpenVrReport = onOpenVrReport,
                            onOpenIncidentReport = onOpenIncidentReport,
                            onOpenComplainRegister = onOpenComplainRegister,
                            onOpenAssignedTask = onOpenAssignedTask,
                            onOpenSubmittedReports = onOpenSubmittedReports,
                        )

                    1 -> ReportsTab(onOpenDraftReports = onOpenDraftReports, onOpenSubmittedReports = onOpenSubmittedReports)
                    2 -> TasksTab(unseenTaskCount = unseenTaskCount, onOpenAssignedTask = onOpenAssignedTask)
                    else -> MoreTab(onChangePassword = { showPasswordSheet = true }, onLogout = { showLogoutDialog = true })
                }
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Logout, contentDescription = null)
                                }
                            }
                            Column {
                                Text(text = "Confirm Logout", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Your duty will be stopped",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    text = { Text(text = "Are you sure you want to logout?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                LocationHistoryForegroundService.stop(context)
                                viewModel.performLogout {
                                    onLogout()
                                }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = "Logout", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                }
                            }
                            Column {
                                Text(text = "Exit App", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Do you want to close the app?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    text = {},
                    confirmButton = {
                        Button(
                            onClick = {
                                showExitDialog = false
                                (context as? android.app.Activity)?.finishAffinity()
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = "Exit", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showExitDialog = false },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }

            if (showPasswordSheet) {
                ChangePasswordSheet(
                    viewModel = viewModel,
                    onDismiss = { showPasswordSheet = false },
                    onPasswordChanged = {
                        showPasswordSheet = false
                        LocationHistoryForegroundService.stop(context)
                        viewModel.performLogout {
                            onLogout()
                        }
                    },
                )
            }

            if (showReportTypeSheet) {
                ReportTypeSheet(
                    onDismiss = { showReportTypeSheet = false },
                    onDraftReport = onOpenDraftReports,
                    onSubmittedReports = onOpenSubmittedReports,
                )
            }
        }
    }
}

@Composable
private fun DashboardBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.82f)),
                        ),
                    ),
        )
    }
}

@Composable
private fun NotificationAction(
    count: Int,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
        }
        if (count > 0) {
            Badge(
                modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                containerColor = BadgeDefaults.containerColor,
            ) {
                Text(text = if (count > 99) "99+" else count.toString())
            }
        }
    }
}

@Composable
private fun BadgeBox(
    count: Int,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.TopEnd) {
        content()
        Badge(modifier = Modifier.padding(start = 10.dp, bottom = 10.dp)) {
            Text(text = if (count > 99) "99+" else count.toString())
        }
    }
}

@Composable
private fun DashboardHomeTab(
    viewModel: DashboardScreenViewModel,
    unseenTaskCount: Int,
    onOpenAttendanceEntry: () -> Unit,
    onOpenFaceRegistration: () -> Unit,
    onOpenSpecialReport: () -> Unit,
    onOpenVrReport: () -> Unit,
    onOpenIncidentReport: () -> Unit,
    onOpenComplainRegister: () -> Unit,
    onOpenAssignedTask: () -> Unit,
    onOpenSubmittedReports: () -> Unit,
) {
    var isScrollEnabled by remember { mutableStateOf(true) }

    val todayText = remember {
        val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
        "Today : " + LocalDate.now().format(formatter)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = isScrollEnabled)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        DateChip(text = todayText)

        val faceRegHasObj by viewModel.faceRegHasResponseObjState
        val faceRegStatus by viewModel.faceRegStatusState
        if (!faceRegHasObj) {
            Spacer(modifier = Modifier.height(8.dp))
            DashboardListItem(
                icon = Icons.Default.Assignment,
                title = "New Face Registration",
                badgeCount = unseenTaskCount,
                onClick = onOpenFaceRegistration,
            )
            return@Column
        } else if (faceRegStatus == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Face Approval Pending",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            return@Column
        } else if (faceRegStatus == 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your face rejected, Try Again",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            DashboardListItem(
                icon = Icons.Default.Assignment,
                title = "New Face Registration",
                badgeCount = unseenTaskCount,
                onClick = onOpenFaceRegistration,
            )
            return@Column
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 360.dp) 1 else 2
            val compact = maxWidth < 420.dp
            val spacing = if (compact) 8.dp else 12.dp

            if (columns == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Attendance Entry",
                            icon = Icons.Default.AccessTime,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenAttendanceEntry,
                        )
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Special Report",
                            icon = Icons.Default.Star,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenSpecialReport,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "VR Report",
                            icon = Icons.Default.Assignment,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenVrReport,
                        )
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Incident Report",
                            icon = Icons.Default.Report,
                            backgroundColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            compact = compact,
                            onClick = onOpenIncidentReport,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Attendance Entry",
                            icon = Icons.Default.AccessTime,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenAttendanceEntry,
                        )
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Special Report",
                            icon = Icons.Default.Star,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenSpecialReport,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "VR Report",
                            icon = Icons.Default.Assignment,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            compact = compact,
                            onClick = onOpenVrReport,
                        )
                        DashboardActionCard(
                            modifier = Modifier.weight(1f),
                            title = "Incident Report",
                            icon = Icons.Default.Report,
                            backgroundColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            compact = compact,
                            onClick = onOpenIncidentReport,
                        )
                    }
                }
            }
        }

        DashboardListItem(
            icon = Icons.Default.Assignment,
            title = "Assigned Task",
            badgeCount = unseenTaskCount,
            onClick = onOpenAssignedTask,
        )

        DashboardListItem(
            icon = Icons.Default.Description,
            title = "My Reports",
            onClick = onOpenSubmittedReports,
        )

        GeofenceMapSection(
            viewModel = viewModel,
            onMapInteract = { isScrollEnabled = it }
        )

        //Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun checkLocationSettings(
    context: android.content.Context,
    onEnabled: () -> Unit,
    onDisabled: (androidx.activity.result.IntentSenderRequest) -> Unit
) {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
        .setMinUpdateIntervalMillis(2000)
        .build()
    
    val builder = LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
        .setAlwaysShow(true)
        
    val client: SettingsClient = LocationServices.getSettingsClient(context)
    val task = client.checkLocationSettings(builder.build())

    task.addOnSuccessListener {
        onEnabled()
    }

    task.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) {
            try {
                val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                onDisabled(intentSenderRequest)
            } catch (sendEx: IntentSender.SendIntentException) {
                // Ignore the error
            }
        }
    }
}

@Composable
private fun GeofenceMapSection(
    viewModel: DashboardScreenViewModel,
    onMapInteract: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    val userAutoId by viewModel.userAutoIdState

    var permissionGranted by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionGranted =
                (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        }

    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }

    val geofencePolygons by viewModel.geofencePolygonsState
    val geofenceId by viewModel.geofenceIdState
    val inOutStatus by viewModel.inOutStatusState

    val polygonLatLngs =
        remember(geofencePolygons) {
            geofencePolygons.map { poly ->
                poly.points.map { LatLng(it.first, it.second) }
            }
        }
    val allPolygonPoints = remember(polygonLatLngs) { polygonLatLngs.flatten() }
    val cameraPositionState = rememberCameraPositionState()
    var locationCameraInitialized by remember { mutableStateOf(false) }
    var polygonCameraInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.reloadSessionFromStore()
        if (!permissionGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(userAutoId) {
        val id = userAutoId ?: return@LaunchedEffect
        viewModel.loadGeofenceData(id)
    }

    LaunchedEffect(permissionGranted, userAutoId) {
        if (!permissionGranted) return@LaunchedEffect

        while (isActive) {
            val id = userAutoId
            if (id != null) {
                viewModel.loadGeofenceData(id)
            }
            getCurrentLatLng(context, fusedLocationClient)?.let { currentLatLng = it }

            val loc = currentLatLng
            if (loc != null && geofencePolygons.isNotEmpty()) {
                viewModel.refreshInOutStatusAndNotifyIfNeeded(loc.latitude, loc.longitude)
            }

            delay(5 * 60 * 1000L)
        }
    }

    LaunchedEffect(allPolygonPoints, currentLatLng) {
        runCatching {
            if (allPolygonPoints.size >= 3) {
                if (polygonCameraInitialized) return@runCatching
                val boundsBuilder = LatLngBounds.Builder()
                allPolygonPoints.forEach { boundsBuilder.include(it) }
                val bounds = boundsBuilder.build()
                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                polygonCameraInitialized = true
                return@runCatching
            }

            val loc = currentLatLng ?: return@runCatching
            if (!locationCameraInitialized) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(loc, 15f))
                locationCameraInitialized = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Map & Geofence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        if (geofencePolygons.size > 1) {
                            append("Geofences: ").append(geofencePolygons.size)
                        } else if (geofenceId != null) {
                            append("Geofence: ").append(geofenceId)
                        }
                        if (!inOutStatus.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(inOutStatus)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!permissionGranted) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) {
                    Text(text = "Enable Location")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            onMapInteract(false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val anyPressed = event.changes.any { it.pressed }
                            } while (anyPressed)
                            onMapInteract(true)
                        }
                    }
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = permissionGranted),
                    uiSettings =
                        MapUiSettings(
                            myLocationButtonEnabled = permissionGranted,
                            zoomControlsEnabled = false,
                        ),
                ) {
                    geofencePolygons.forEach { poly ->
                        if (poly.points.isNotEmpty()) {
                            val pts = poly.points.map { LatLng(it.first, it.second) }
                            Polygon(
                                points = pts,
                                strokeColor = Color(poly.strokeArgb ?: 0xFFF9A825.toInt()),
                                strokeWidth = 3f,
                                fillColor = Color(poly.fillArgb ?: 0x4D1565C0),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun getCurrentLatLng(
    context: android.content.Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
): LatLng? =
    suspendCancellableCoroutine { cont ->
        val fine =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                cont.resume(location?.let { LatLng(it.latitude, it.longitude) })
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }

@Composable
private fun DateChip(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardActionCard(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.heightIn(min = if (compact) 72.dp else 84.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor.copy(alpha = 0.92f),
                contentColor = contentColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compact) 10.dp else 14.dp,
                        vertical = if (compact) 10.dp else 12.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (compact) 38.dp else 44.dp),
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 20.dp else 24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(if (compact) 8.dp else 12.dp))
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun DashboardListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badgeCount: Int? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (badgeCount != null && badgeCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(text = if (badgeCount > 99) "99+" else badgeCount.toString())
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportsTab(
    onOpenDraftReports: () -> Unit,
    onOpenSubmittedReports: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        DashboardListItem(icon = Icons.Default.Description, title = "Draft Report", onClick = onOpenDraftReports)
        DashboardListItem(icon = Icons.Default.Description, title = "Submitted Reports", onClick = onOpenSubmittedReports)
    }
}

@Composable
private fun TasksTab(
    unseenTaskCount: Int,
    onOpenAssignedTask: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Tasks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        DashboardListItem(
            icon = Icons.Default.Assignment,
            title = "Assigned Task",
            badgeCount = unseenTaskCount,
            onClick = onOpenAssignedTask,
        )
    }
}

@Composable
private fun MoreTab(
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "More", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        DashboardListItem(icon = Icons.Default.AccessTime, title = "Change Password", onClick = onChangePassword)
        DashboardListItem(icon = Icons.Default.Report, title = "Logout", onClick = onLogout)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(
    viewModel: DashboardScreenViewModel,
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val passwordChangeLoading by viewModel.passwordChangeLoadingState
    val passwordChangeSuccess by viewModel.passwordChangeSuccessState
    val passwordChangeMessage by viewModel.passwordChangeMessageState

    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        Unit
    }

    LaunchedEffect(Unit) {
        viewModel.resetPasswordChangeState()
        sheetState.show()
    }

    LaunchedEffect(passwordChangeSuccess) {
        if (passwordChangeSuccess == true) {
            onPasswordChanged()
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Change Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = currentPass,
                onValueChange = { currentPass = it },
                label = { Text(text = "Current Password") },
                singleLine = true,
                visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { currentVisible = !currentVisible }) {
                        Icon(
                            imageVector = if (currentVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = newPass,
                onValueChange = { newPass = it },
                label = { Text(text = "New Password") },
                singleLine = true,
                visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { newVisible = !newVisible }) {
                        Icon(
                            imageVector = if (newVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = { Text(text = "Confirm Password") },
                singleLine = true,
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            imageVector = if (confirmVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )

            val feedbackMessage = validationMessage ?: passwordChangeMessage
            if (feedbackMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (passwordChangeSuccess == false && validationMessage == null) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    contentColor =
                        if (passwordChangeSuccess == false && validationMessage == null) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                ) {
                    Text(
                        text = feedbackMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.resetPasswordChangeState()
                        dismiss()
                    },
                    enabled = !passwordChangeLoading,
                ) {
                    Text(text = "Cancel")
                }
                androidx.compose.material3.Button(
                    modifier = Modifier.weight(1f),
                    enabled = !passwordChangeLoading,
                    onClick = {
                        val current = currentPass.trim()
                        val newP = newPass.trim()
                        val confirm = confirmPass.trim()
                        validationMessage =
                            when {
                                current.isEmpty() -> "Please insert your current password"
                                newP.isEmpty() -> "Please insert your new password"
                                confirm.isEmpty() -> "Please insert your confirm password"
                                confirm != newP -> "New password and Confirm password not matched"
                                else -> null
                            }
                        if (validationMessage == null) {
                            viewModel.changePassword(
                                oldPassword = current,
                                newPassword = newP,
                                confirmPassword = confirm,
                            )
                        }
                    },
                ) {
                    Text(text = if (passwordChangeLoading) "Saving..." else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTypeSheet(
    onDismiss: () -> Unit,
    onDraftReport: () -> Unit,
    onSubmittedReports: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val dismissAndAction: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    LaunchedEffect(Unit) {
        sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Select Report Type",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            DashboardListItem(
                icon = Icons.Default.Description,
                title = "Draft Report",
                onClick = { dismissAndAction(onDraftReport) },
            )
            DashboardListItem(
                icon = Icons.Default.Description,
                title = "Submitted Reports",
                onClick = { dismissAndAction(onSubmittedReports) },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    onNavigateBack: () -> Unit,
) {
    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text = title) },
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
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "This page is wired from the dashboard. Hook up API + UI when ready.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
