package com.phq.swl.pioms.presentation.screens.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import android.graphics.Bitmap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.phq.swl.pioms.presentation.screens.detect_screen.DetectScreenViewModel
import com.phq.swl.pioms.presentation.components.FaceDetectionOverlay
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: AttendanceScreenViewModel = koinViewModel()
    val detectViewModel: DetectScreenViewModel = koinViewModel()
    val yearMonth by viewModel.yearMonthState
    val presentDates by viewModel.presentDatesState
    val absentDates by viewModel.absentDatesState
    val entries by viewModel.entriesState
    val presentCount by viewModel.presentCountState
    val absentCount by viewModel.absentCountState
    val loading by viewModel.loadingState
    val faceReady by viewModel.faceReferenceReadyState
    val avatarUrl by viewModel.faceReferenceUrlState
    val userId by viewModel.userIdState
    val attendanceSaving by viewModel.attendanceSaveLoadingState
    val attendanceSaveSuccess by viewModel.attendanceSaveSuccessState
    val attendanceSaveMessage by viewModel.attendanceSaveMessageState
    val capturedImage by viewModel.capturedImageState
    val faceVerificationLoading by viewModel.faceVerificationLoadingState
    val similarityScore by viewModel.similarityScoreState

    LaunchedEffect(yearMonth) {
        try {
            viewModel.loadMonthStats(yearMonth)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to load stats: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            viewModel.prepareFaceReference(context)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to prepare face reference: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(attendanceSaveSuccess) {
        if (attendanceSaveSuccess == true) {
            // Refresh the calendar and attendance data after successful check-in
            try {
                viewModel.loadMonthStats(yearMonth)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to refresh stats: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val primaryBlue = Color(0xFF0B4AA2)
    val darkBlue = Color(0xFF083A7A)
    val green = Color(0xFF1B8D2B)
    val red = Color(0xFFE53935)

    val todayDateString = LocalDate.now().toString()
    val todayEntry = entries.lastOrNull { it.attendanceDate == todayDateString }
    val isCheckedInToday = presentDates.contains(todayDateString)
    val isCheckedOutToday = todayEntry?.time2?.isNotBlank() == true

    val checkedInText = todayEntry?.time1?.ifBlank { "-" } ?: "-"
    val checkedOutText = todayEntry?.time2?.ifBlank { "-" } ?: "-"
    
    var lastVerificationResult by remember { mutableStateOf<Boolean?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var isManualMode by remember { mutableStateOf(false) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            cameraPermissionGranted = it
            if (it) {
                showCamera = true
            } else {
                resultTitle = "Camera Permission"
                resultMessage = "Camera permission is required for face verification."
                showResultDialog = true
            }
        }

    LaunchedEffect(similarityScore) {
        val score = similarityScore ?: return@LaunchedEffect
        if (score >= 0.80) {
            // Recognized
            lastVerificationResult = true
            showDetailsDialog = true
        } else {
            // Not Recognized
            lastVerificationResult = false
            resultTitle = "Ops!"
            resultMessage = "Face is not Recognized"
            showResultDialog = true
        }
    }

    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var dutyPlace by remember { mutableStateOf<String?>(null) }

    var locationPermissionGranted by remember {
        val fine =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        mutableStateOf(fine || coarse)
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            locationPermissionGranted = fine || coarse
        }

    LaunchedEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) return@LaunchedEffect
        try {
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        currentLatLng = LatLng(loc.latitude, loc.longitude)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(currentLatLng) {
        val ll = currentLatLng ?: return@LaunchedEffect
        try {
            dutyPlace = "" // getDutyPlaceFromLatLng(context, ll.latitude, ll.longitude) ?: "current location"
        } catch (e: Exception) {
            dutyPlace = "current location"
            Toast.makeText(context, "Failed to get duty place: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val faceVerified = (lastVerificationResult == true)

    FaceNetAndroidTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            var processed by remember(showCamera) { mutableStateOf(false) }
            var matchStreak by remember(showCamera) { mutableStateOf(0) }
            var overlayInstance: FaceDetectionOverlay? by remember { mutableStateOf(null) }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(0xFFF2F5FA),
                topBar = {
                    CenterAlignedTopAppBar(
                        colors =
                            TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = primaryBlue,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White,
                            ),
                        title = { Text(text = "Attendance Dashboard", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (!locationPermissionGranted) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                    return@IconButton
                                }
                                if (cameraPermissionGranted) {
                                    isManualMode = true
                                    showCamera = true
                                    // Pre-fetch stored image silently
                                    viewModel.prefetchStoredImage(context)
                                    // Reset similarity score when opening manual camera
                                    viewModel.similarityScoreState.value = null
                                    viewModel.capturedImageState.value = null
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateBack,
                            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Dashboard") },
                            label = { Text(text = "Dashboard") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = green,
                                    selectedTextColor = green,
                                    unselectedIconColor = primaryBlue,
                                    unselectedTextColor = primaryBlue,
                                    indicatorColor = Color.Transparent,
                                ),
                        )
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Attendance") },
                            label = { Text(text = "Attendance") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = green,
                                    selectedTextColor = green,
                                    unselectedIconColor = primaryBlue,
                                    unselectedTextColor = primaryBlue,
                                    indicatorColor = Color.Transparent,
                                ),
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateBack,
                            icon = { Icon(imageVector = Icons.Default.Description, contentDescription = "Reports") },
                            label = { Text(text = "Reports") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = green,
                                    selectedTextColor = green,
                                    unselectedIconColor = primaryBlue,
                                    unselectedTextColor = primaryBlue,
                                    indicatorColor = Color.Transparent,
                                ),
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "More") },
                            label = { Text(text = "More") },
                            colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = green,
                                    selectedTextColor = green,
                                    unselectedIconColor = primaryBlue,
                                    unselectedTextColor = primaryBlue,
                                    indicatorColor = Color.Transparent,
                                ),
                        )
                    }
                },
            ) { innerPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AttendanceTopCard(
                        primaryBlue = primaryBlue,
                        darkBlue = darkBlue,
                        green = green,
                        red = red,
                        faceVerified = faceVerified,
                        checkedInText = checkedInText,
                        checkedOutText = checkedOutText,
                        totalPresent = presentCount,
                        gpsVerified = locationPermissionGranted && currentLatLng != null,
                        avatarUrl = avatarUrl,
                        capturedImage = capturedImage,
                        isCheckedInToday = isCheckedInToday,
                        isCheckedOutToday = isCheckedOutToday,
                        onCheckIn = {
                            lastVerificationResult = null
                            isManualMode = false
                            // Request location permission if not granted
                            if (!locationPermissionGranted) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                                return@AttendanceTopCard
                            }
                            scope.launch {
                                try {
                                    val latestLatLng = getCurrentLatLng(context, fusedLocationClient)
                                    currentLatLng = latestLatLng
                                    /*dutyPlace =
                                        latestLatLng?.let {
                                            getDutyPlaceFromLatLng(context, it.latitude, it.longitude)
                                        } ?: dutyPlace*/

                                    if (latestLatLng == null) {
                                        resultTitle = "Ops!"
                                        resultMessage = "Location not found. Please turn on location & permission."
                                        showResultDialog = true
                                        return@launch
                                    }

                                    if (cameraPermissionGranted) {
                                        showCamera = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error preparing check-in: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )

                    MonthHeader(
                        primaryBlue = primaryBlue,
                        yearMonth = yearMonth,
                        onPrev = { viewModel.yearMonthState.value = yearMonth.minusMonths(1) },
                        onNext = { viewModel.yearMonthState.value = yearMonth.plusMonths(1) },
                    )

                    CalendarPanel(
                        primaryBlue = primaryBlue,
                        yearMonth = yearMonth,
                        presentDates = presentDates,
                        absentDates = absentDates,
                        green = green,
                        red = red,
                    )

                    TotalLine(
                        presentCount = presentCount,
                        absentCount = absentCount,
                        green = green,
                        red = red,
                    )

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Monthly Attendance Record",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CheckCircle,
                            iconColor = green,
                            title = "$presentCount Days Present",
                            subtitle = "This Month",
                        )
                        SummaryCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Close,
                            iconColor = red,
                            title = "$absentCount Days Absent",
                            subtitle = "This Month",
                        )
                    }

                    if (loading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }

            if (showCamera) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            FaceDetectionOverlay(
                                lifecycleOwner = lifecycleOwner,
                                context = context,
                                viewModel = detectViewModel,
                                onFaceRecognitionResults = { results ->
                                    if (isManualMode) return@FaceDetectionOverlay
                                    if (processed) return@FaceDetectionOverlay
                                    if (results.isEmpty()) return@FaceDetectionOverlay
                                    val target = userId.trim().ifEmpty { "agent1" }
                                    val spoofDetected = results.any { it.spoofResult?.isSpoof == true }
                                    if (spoofDetected) {
                                        processed = true
                                        lastVerificationResult = false
                                        resultTitle = "Ops!"
                                        resultMessage = "Face is not Recognized"
                                        showCamera = false
                                        showResultDialog = true
                                        return@FaceDetectionOverlay
                                    }
                                    val matchedFrame = results.any { it.personName == target }
                                    matchStreak = if (matchedFrame) matchStreak + 1 else 0
                                    if (matchStreak < 5) return@FaceDetectionOverlay
                                    processed = true
                                    lastVerificationResult = true
                                    val latLng = currentLatLng
                                    val lat = latLng?.latitude?.toString().orEmpty()
                                    val lng = latLng?.longitude?.toString().orEmpty()
                                    if (lat.isBlank() || lng.isBlank()) {
                                        lastVerificationResult = false
                                        resultTitle = "Ops!"
                                        resultMessage = "Location not found. Please check location permission and GPS from Dashboard first."
                                        showCamera = false
                                        showResultDialog = true
                                        return@FaceDetectionOverlay
                                    }
                                    showCamera = false
                                    showDetailsDialog = true
                                },
                                onManualCapture = { bitmap ->
                                    viewModel.capturedImageState.value = bitmap
                                    scope.launch {
                                        val storedBase64 = viewModel.storedFaceBase64State.value
                                            ?: viewModel.getStoredImageBase64(context)
                                        if (storedBase64 == null) {
                                            resultTitle = "Error"
                                            resultMessage = "Your profile image not found. Please register face first."
                                            showResultDialog = true
                                            showCamera = false
                                            return@launch
                                        }
                                        val capturedBase64 = viewModel.convertBitmapToBase64(bitmap)
                                        viewModel.verifyFaceSimilarity(storedBase64, capturedBase64)
                                        showCamera = false
                                    }
                                },
                            ).also { overlayInstance = it }
                        },
                        update = {
                            it.initializeCamera(androidx.camera.core.CameraSelector.LENS_FACING_FRONT)
                        },
                    )

                    IconButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        onClick = {
                            showCamera = false
                            isManualMode = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }

                    if (isManualMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                                .size(80.dp)
                                .background(Color.White, CircleShape)
                                .padding(4.dp)
                                .background(Color.Black, CircleShape)
                                .padding(2.dp)
                                .background(Color.White, CircleShape)
                                .clickable { overlayInstance?.capture() },
                        )
                    }
                }
            }

            if (faceVerificationLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                }
            }

            if (showResultDialog) {
                val success =
                    when (lastVerificationResult) {
                        true -> (attendanceSaveSuccess == true || attendanceSaving)
                        false -> false
                        null -> null
                    }
                val titleText =
                    if (resultTitle.isNotBlank()) {
                        resultTitle
                    } else if (lastVerificationResult == true) {
                        "Success!"
                    } else {
                        "Ops!"
                    }
                val messageText =
                    if (resultMessage.isNotBlank()) {
                        resultMessage
                    } else if (lastVerificationResult == true) {
                        when {
                            attendanceSaving -> "Submitting attendance..."
                            attendanceSaveSuccess == true -> "Attendance Successful"
                            !attendanceSaveMessage.isNullOrBlank() -> attendanceSaveMessage ?: "Attendance failed"
                            else -> "Attendance failed"
                        }
                    } else {
                        "Face is not Recognized"
                    }

                AlertDialog(
                    onDismissRequest = { showResultDialog = false },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                color =
                                    when {
                                        lastVerificationResult == true && attendanceSaveSuccess == true -> Color(0xFF1B8D2B).copy(alpha = 0.14f)
                                        lastVerificationResult == true && attendanceSaving -> Color(0xFF1B8D2B).copy(alpha = 0.14f)
                                        else -> Color(0xFFE53935).copy(alpha = 0.14f)
                                    },
                                contentColor =
                                    when {
                                        lastVerificationResult == true -> Color(0xFF1B8D2B)
                                        else -> Color(0xFFE53935)
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector =
                                            if (lastVerificationResult == true) Icons.Default.CheckCircle else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (lastVerificationResult == true) Color(0xFF1B8D2B) else Color(0xFFE53935),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = titleText,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (lastVerificationResult == true) Color(0xFF1B8D2B) else Color(0xFFE53935),
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                    text = {
                        Text(
                            text = messageText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            val failed = lastVerificationResult == false
                            showResultDialog = false
                            resultTitle = ""
                            resultMessage = ""
                            lastVerificationResult = null
                            if (failed) onNavigateBack()
                        }) {
                            Text(text = "OK")
                        }
                    },
                )
            }
            if (showDetailsDialog) {
                AlertDialog(
                    onDismissRequest = { showDetailsDialog = false },
                    title = { Text("Duty Details") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dutyPlace ?: "",
                                onValueChange = { dutyPlace = it },
                                label = { Text("Duty Place") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = remarks,
                                onValueChange = { remarks = it },
                                label = { Text("Remarks") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showDetailsDialog = false
                            val lat = currentLatLng?.latitude?.toString().orEmpty()
                            val lng = currentLatLng?.longitude?.toString().orEmpty()
                            val place = dutyPlace?.trim().takeUnless { it.isNullOrBlank() } ?: "currentPosition"
                            try {
                                viewModel.saveAttendanceCheckIn(
                                    dutyPlace = place,
                                    latitude = lat,
                                    longitude = lng,
                                    remarks = remarks
                                )
                                resultTitle = "Success!"
                                resultMessage = "Attendance Successful"
                            } catch (e: Exception) {
                                lastVerificationResult = false
                                resultTitle = "Error"
                                resultMessage = e.message ?: "Failed to save attendance"
                            }
                            showResultDialog = true
                        }) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDetailsDialog = false
                            onNavigateBack()
                        }) {
                            Text("Cancel")
                        }
                    }
                )
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

private suspend fun getDutyPlaceFromLatLng(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
): String? {
    val geocoder = Geocoder(context, Locale.getDefault())
    val address =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    },
                )
            }
        } else {
            runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
        }

    if (address == null) return null
    val subLocality = address.subLocality?.trim().orEmpty()
    val locality = address.locality?.trim().orEmpty()
    val admin = address.adminArea?.trim().orEmpty()
    return when {
        subLocality.isNotEmpty() -> subLocality
        locality.isNotEmpty() -> locality
        admin.isNotEmpty() -> admin
        else -> address.getAddressLine(0)?.trim()
    }?.takeIf { it.isNotBlank() }
}

@Composable
private fun AttendanceTopCard(
    primaryBlue: Color,
    darkBlue: Color,
    green: Color,
    red: Color,
    faceVerified: Boolean,
    checkedInText: String,
    checkedOutText: String,
    totalPresent: Int,
    gpsVerified: Boolean,
    avatarUrl: String?,
    capturedImage: Bitmap?,
    isCheckedInToday: Boolean,
    isCheckedOutToday: Boolean,
    onCheckIn: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(darkBlue, primaryBlue),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when {
                    isCheckedOutToday -> "Today's Checkout - Completed"
                    isCheckedInToday -> "Today's Check In - Completed"
                    else -> "Ready for Today's Check In"
                },
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A2F63)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .clip(RoundedCornerShape(11.dp)),
                    ) {
                        val corner = Color(0xFF2AF5F9)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    corner,
                                                    Color.Transparent,
                                                ),
                                        ),
                                    )
                                    .padding(2.dp)
                                    .background(Color(0xFF0A2F63), RoundedCornerShape(11.dp)),
                        )
                        if (capturedImage != null) {
                            androidx.compose.foundation.Image(
                                bitmap = capturedImage.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.align(Alignment.Center).size(50.dp),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = (if (isCheckedInToday || isCheckedOutToday || faceVerified) green else red).copy(alpha = 0.12f),
                                contentColor = if (isCheckedInToday || isCheckedOutToday || faceVerified) green else red,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isCheckedInToday || isCheckedOutToday || faceVerified) Icons.Default.CheckCircle else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isCheckedInToday || isCheckedOutToday || faceVerified) green else red,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isCheckedOutToday -> "Today - Checked Out"
                                        isCheckedInToday -> "Today - Checked In"
                                        else -> "Today - Not Checked In"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = when {
                                        isCheckedOutToday -> green
                                        isCheckedInToday -> Color(0xFFF57C00)
                                        else -> red
                                    },
                                )
                                Text(
                                    text = buildString {
                                        append("Check In: $checkedInText")
                                        if (isCheckedOutToday) {
                                            append(" | Check Out: $checkedOutText")
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        onClick = onCheckIn,
                        enabled = true,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isCheckedOutToday -> Color(0xFF888888)
                                    isCheckedInToday -> Color(0xFFF57C00)
                                    else -> green
                                },
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF888888),
                                disabledContentColor = Color.White,
                            ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when {
                                    isCheckedInToday || isCheckedOutToday -> Icons.Default.CheckCircle
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isCheckedOutToday -> "CHECKED OUT"
                                    isCheckedInToday -> "CHECK OUT"
                                    else -> "CHECK IN"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = green,
                    )
                    Text(
                        text = if (gpsVerified) "GPS Verified" else "GPS Not Verified",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Total: $totalPresent Present",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    primaryBlue: Color,
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = primaryBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Prev", tint = Color.White)
            }
            Text(
                modifier = Modifier.weight(1f),
                text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CalendarPanel(
    primaryBlue: Color,
    yearMonth: YearMonth,
    presentDates: Set<String>,
    absentDates: Set<String>,
    green: Color,
    red: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { h ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = h,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val firstDay = LocalDate.of(yearMonth.year, yearMonth.monthValue, 1)
            val offset = firstDay.dayOfWeek.value % 7
            val totalCells = offset + yearMonth.lengthOfMonth()
            val rows = (totalCells + 6) / 7

            var day = 1
            for (r in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0..6) {
                        val cellIndex = r * 7 + c
                        if (cellIndex < offset || day > yearMonth.lengthOfMonth()) {
                            Spacer(modifier = Modifier.weight(1f).height(38.dp))
                        } else {
                            val dateKey = LocalDate.of(yearMonth.year, yearMonth.monthValue, day).toString()
                            val isPresent = presentDates.contains(dateKey)
                            val isAbsent = absentDates.contains(dateKey)
                            val bg =
                                when {
                                    isPresent -> green
                                    isAbsent -> red
                                    else -> null
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bg != null) {
                                    Surface(
                                        modifier = Modifier.size(30.dp),
                                        shape = RoundedCornerShape(40),
                                        color = bg,
                                    ) {}
                                }
                                Text(
                                    text = day.toString(),
                                    color = if (bg != null) Color.White else primaryBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            day++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalLine(
    presentCount: Int,
    absentCount: Int,
    green: Color,
    red: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Total: $presentCount Present", fontWeight = FontWeight.Bold, color = green)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "|", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "$absentCount Absent", fontWeight = FontWeight.Bold, color = red)
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconColor.copy(alpha = 0.15f),
                contentColor = iconColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.ExtraBold, color = iconColor)
                Text(text = subtitle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
