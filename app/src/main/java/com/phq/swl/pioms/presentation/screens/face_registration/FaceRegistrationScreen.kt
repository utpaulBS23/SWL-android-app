package com.phq.swl.pioms.presentation.screens.face_registration

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.phq.swl.pioms.R
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import com.phq.swl.pioms.service.LocationHistoryForegroundService
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: FaceRegistrationViewModel = koinViewModel()

    val userId by viewModel.userIdState
    val agentCode by viewModel.agentCodeState
    val agentName by viewModel.agentNameState
    val departmentId by viewModel.departmentIdState
    val mobile by viewModel.mobileState
    val status by viewModel.statusState
    val selectedImages by viewModel.selectedImagesState
    val uploading by viewModel.uploadingState
    val uploadMessage by viewModel.uploadMessageState
    val uploadSuccess by viewModel.uploadSuccessState
    val logoutRequested by viewModel.logoutRequestedState

    var showResultDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uploadSuccess, uploadMessage) {
        if (uploadSuccess != null || !uploadMessage.isNullOrBlank()) {
            showResultDialog = true
        }
    }

    var logoutHandled by remember { mutableStateOf(false) }
    LaunchedEffect(logoutRequested, uploadSuccess) {
        if (!logoutHandled && logoutRequested && uploadSuccess == true) {
            logoutHandled = true
            delay(900)
            LocationHistoryForegroundService.stop(context)
            viewModel.performLogout()
            onLogout()
        }
    }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermissionLauncher =
        rememberLauncherPermission { granted ->
            cameraPermissionGranted = granted
        }

    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val executor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }

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
                        title = { Text(text = "Face Registration", fontWeight = FontWeight.SemiBold) },
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!cameraPermissionGranted) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Camera permission is required",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { cameraPermissionLauncher.request(Manifest.permission.CAMERA) },
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(text = "Enable Camera", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }
                                    },
                                    update = { previewView ->
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                        cameraProviderFuture.addListener(
                                            {
                                                val cameraProvider = cameraProviderFuture.get()
                                                val preview =
                                                    Preview.Builder().build().also {
                                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                                    }
                                                val capture =
                                                    ImageCapture.Builder()
                                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                                        .build()
                                                imageCapture.value = capture
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                                    preview,
                                                    capture,
                                                )
                                            },
                                            executor,
                                        )
                                    },
                                )

                                Surface(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.35f),
                                    contentColor = Color.White,
                                ) {
                                    IconButton(
                                        modifier = Modifier.size(64.dp),
                                        onClick = {
                                            val capture = imageCapture.value ?: return@IconButton
                                            val file = File(context.cacheDir, "face_${System.currentTimeMillis()}.jpg")
                                            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                                            capture.takePicture(
                                                outputOptions,
                                                executor,
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        val uri = outputFileResults.savedUri ?: Uri.fromFile(file)
                                                        viewModel.addCapturedImage(uri)
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        viewModel.uploadSuccessState.value = false
                                                        viewModel.uploadMessageState.value = exception.message ?: "Capture failed"
                                                    }
                                                },
                                            )
                                        },
                                    ) {
                                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Capture")
                                    }
                                }
                            }
                        }
                    }

                    if (selectedImages.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(selectedImages, key = { it.toString() }) { uri ->
                                Box(
                                    modifier =
                                        Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.45f),
                                        contentColor = Color.White,
                                    ) {
                                        Icon(
                                            modifier =
                                                Modifier
                                                    .size(22.dp)
                                                    .padding(2.dp)
                                                    .clickable { viewModel.removeImage(uri) },
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = agentName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Agent Name") },
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    onClick = { viewModel.submit(context) },
                                    enabled = !uploading,
                                    shape = RoundedCornerShape(12.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (uploading) "Submitting..." else "Upload", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            if (showResultDialog && (uploadSuccess != null || !uploadMessage.isNullOrBlank())) {
                AlertDialog(
                    onDismissRequest = {
                        showResultDialog = false
                        viewModel.uploadMessageState.value = null
                        viewModel.uploadSuccessState.value = null
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    if (uploadSuccess == true) Color(0xFF1B8D2B).copy(alpha = 0.14f) else Color(0xFFE53935).copy(alpha = 0.14f),
                                contentColor =
                                    if (uploadSuccess == true) Color(0xFF1B8D2B) else Color(0xFFE53935),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (uploadSuccess == true) Icons.Default.CameraAlt else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (uploadSuccess == true) Color(0xFF1B8D2B) else Color(0xFFE53935),
                                    )
                                }
                            }
                            Text(
                                text = if (uploadSuccess == true) "Success" else "Failed",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    text = { Text(text = uploadMessage ?: "") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResultDialog = false
                                viewModel.uploadMessageState.value = null
                                viewModel.uploadSuccessState.value = null
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = "OK", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberLauncherPermission(onResult: (Boolean) -> Unit): PermissionRequester {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return remember { PermissionRequester { perm -> launcher.launch(perm) } }
}

private class PermissionRequester(val request: (String) -> Unit)
