package com.phq.swl.pioms.presentation.screens.login

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.phq.swl.pioms.R
import com.phq.swl.pioms.presentation.components.AppAlertDialog
import com.phq.swl.pioms.presentation.components.AppProgressDialog
import com.phq.swl.pioms.presentation.components.FaceDetectionOverlay
import com.phq.swl.pioms.presentation.components.createAlertDialog
import com.phq.swl.pioms.presentation.screens.detect_screen.DetectScreenViewModel
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel

private lateinit var cameraPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
) {
    val loginViewModel: LoginScreenViewModel = koinViewModel()
    val detectViewModel: DetectScreenViewModel = koinViewModel()

    var showCamera by remember { mutableStateOf(false) }
    var pendingOpenCamera by remember { mutableStateOf(false) }

    val isEnrolled by remember { loginViewModel.isEnrolledState }
    val otpVisible by remember { loginViewModel.otpDialogVisibleState }
    val requestNewIdVisible by remember { loginViewModel.requestNewIdDialogVisibleState }
    val requestNewIdOtpVisible by remember { loginViewModel.requestNewIdOtpDialogVisibleState }
    val loginCompleted by remember { loginViewModel.loginCompletedState }
    val isProcessing by remember { loginViewModel.isProcessingState }
    val loginError by remember { loginViewModel.loginErrorState }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isEnrolled, isProcessing, pendingOpenCamera) {
        if (pendingOpenCamera && isEnrolled) {
            showCamera = true
            pendingOpenCamera = false
        }
        if (!isProcessing && !isEnrolled) {
            pendingOpenCamera = false
        }
    }
    LaunchedEffect(loginCompleted) {
        if (loginCompleted) {
            loginViewModel.loginCompletedState.value = false
            onLoginSuccess()
        }
    }

    LaunchedEffect(loginError) {
        loginError?.let {
            snackbarHostState.showSnackbar(it)
            loginViewModel.loginErrorState.value = null
        }
    }

    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 16.dp),
                    snackbar = { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                PiomsLoginBackground()

                if (showCamera) {
                    FaceLoginCamera(
                        detectViewModel = detectViewModel,
                        loginViewModel = loginViewModel,
                        onLoginSuccess = {
                            loginViewModel.setLoggedInUser(loginViewModel.userIdState.value.trim())
                            onLoginSuccess()
                        },
                    )
                } else {
                    PiomsLoginForm(
                        viewModel = loginViewModel,
                        onLoginClick = {
                            loginViewModel.loginWithPassword()
                        },
                        onBiometricClick = {
                            pendingOpenCamera = true
                            loginViewModel.downloadAndEnroll()
                        },
                    )
                }

                if (otpVisible) {
                    OtpDialog(
                        otp = loginViewModel.otpState.value,
                        onOtpChange = { loginViewModel.otpState.value = it },
                        onCancel = { loginViewModel.otpDialogVisibleState.value = false },
                        onVerify = { loginViewModel.verifyOtpForLogin() },
                        verifyEnabled = loginViewModel.otpState.value.trim().isNotEmpty() && !isProcessing,
                    )
                }

                if (requestNewIdVisible) {
                    RequestNewIdDialog(
                        mobile = loginViewModel.requestNewIdMobileState.value,
                        onMobileChange = { loginViewModel.requestNewIdMobileState.value = it },
                        onCancel = { loginViewModel.requestNewIdDialogVisibleState.value = false },
                        onSubmit = { loginViewModel.requestNewUserId() },
                        submitEnabled = loginViewModel.requestNewIdMobileState.value.trim().isNotEmpty() && !isProcessing,
                    )
                }

                if (requestNewIdOtpVisible) {
                    VerifyNewIdOtpDialog(
                        mobile = loginViewModel.requestNewIdMobileState.value.trim(),
                        otp = loginViewModel.requestNewIdOtpState.value,
                        onOtpChange = { loginViewModel.requestNewIdOtpState.value = it },
                        onCancel = { loginViewModel.requestNewIdOtpDialogVisibleState.value = false },
                        onVerify = { loginViewModel.verifyOtpForNewId() },
                        verifyEnabled = loginViewModel.requestNewIdOtpState.value.trim().isNotEmpty() && !isProcessing,
                    )
                }

                if (loginViewModel.recoverDialogVisibleState.value) {
                    RecoverPasswordDialog(
                        userId = loginViewModel.recoverUserIdState.value,
                        onUserIdChange = { loginViewModel.recoverUserIdState.value = it },
                        mobile = loginViewModel.recoverMobileState.value,
                        onMobileChange = { loginViewModel.recoverMobileState.value = it },
                        onClose = { loginViewModel.recoverDialogVisibleState.value = false },
                        onSubmit = { loginViewModel.recoverPasswordRequest() },
                        submitEnabled =
                            loginViewModel.recoverUserIdState.value.trim().isNotEmpty() &&
                                loginViewModel.recoverMobileState.value.trim().isNotEmpty() &&
                                !isProcessing,
                    )
                }

                if (loginViewModel.recoverOtpDialogVisibleState.value) {
                    RecoverPasswordOtpDialog(
                        userId = loginViewModel.recoverUserIdState.value.trim(),
                        otp = loginViewModel.recoverOtpState.value,
                        onOtpChange = { loginViewModel.recoverOtpState.value = it },
                        onClose = { loginViewModel.recoverOtpDialogVisibleState.value = false },
                        onVerify = { loginViewModel.verifyOtpForPasswordRecovery() },
                        verifyEnabled = loginViewModel.recoverOtpState.value.trim().isNotEmpty() && !isProcessing,
                    )
                }

                AppProgressDialog()
                AppAlertDialog()
            }
        }
    }
}

@Composable
private fun OtpDialog(
    otp: String,
    onOtpChange: (String) -> Unit,
    onCancel: () -> Unit,
    onVerify: () -> Unit,
    verifyEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                    }
                }
                Column {
                    Text(text = "OTP Verification", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Enter OTP to continue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Please enter the OTP sent to your mobile number.")
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = otp,
                    onValueChange = onOtpChange,
                    label = { Text(text = "OTP") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onVerify,
                enabled = verifyEnabled,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text = "Verify", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text = "Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

@Composable
private fun RequestNewIdDialog(
    mobile: String,
    onMobileChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Request for New ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        "Please enter your registered mobile number. If your mobile number is found, we will send an OTP for verification.",
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = mobile,
                    onValueChange = onMobileChange,
                    label = { Text(text = "Mobile Number") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = submitEnabled,
            ) {
                Text(text = "Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun VerifyNewIdOtpDialog(
    mobile: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onCancel: () -> Unit,
    onVerify: () -> Unit,
    verifyEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Verify OTP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        "Please enter the OTP sent to $mobile. After successful verification, your User Code and Password will be sent to your mobile.",
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = otp,
                    onValueChange = onOtpChange,
                    label = { Text(text = "OTP") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onVerify,
                enabled = verifyEnabled,
            ) {
                Text(text = "Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun RecoverPasswordDialog(
    userId: String,
    onUserIdChange: (String) -> Unit,
    mobile: String,
    onMobileChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Recover Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = userId,
                    onValueChange = onUserIdChange,
                    label = { Text(text = "User ID") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = mobile,
                    onValueChange = onMobileChange,
                    label = { Text(text = "Mobile Number") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = submitEnabled,
            ) {
                Text(text = "Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
private fun RecoverPasswordOtpDialog(
    userId: String,
    otp: String,
    onOtpChange: (String) -> Unit,
    onClose: () -> Unit,
    onVerify: () -> Unit,
    verifyEnabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Verify OTP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = otp,
                    onValueChange = onOtpChange,
                    label = { Text(text = "OTP") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onVerify,
                enabled = verifyEnabled,
            ) {
                Text(text = "Verify")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
private fun PiomsLoginForm(
    viewModel: LoginScreenViewModel,
    onLoginClick: () -> Unit,
    onBiometricClick: () -> Unit,
) {
    val context = LocalContext.current
    var apiBaseUrl by remember { viewModel.apiBaseUrlState }
    var token by remember { viewModel.apiTokenState }
    var userId by remember { viewModel.userIdState }
    var password by remember { viewModel.passwordState }
    val isProcessing by remember { viewModel.isProcessingState }
    val versionName = remember(context) { getAppVersionName(context) }

    var rememberMe by remember { viewModel.rememberMeState }
    var passwordVisible by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(text = "API Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = apiBaseUrl,
                        onValueChange = { apiBaseUrl = it },
                        label = { Text(text = "API base URL") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(text = "Token") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(text = "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.safeDrawing.union(WindowInsets.ime).asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Image(
            painter = painterResource(id = R.drawable.pioms_logo),
            contentDescription = "PIOMS Logo",
            modifier = Modifier.size(100.dp),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = "PIOMS",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "Police Internal Oversight Management System",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 8.dp,
            color = colorScheme.surface.copy(alpha = 0.92f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Text(
                //     text = "Welcome Back",
                //     style = MaterialTheme.typography.titleMedium,
                //     fontWeight = FontWeight.SemiBold,
                //     color = Color(0xFF0F1D3A),
                // )
                // Text(
                //     text = "Login to continue to your account",
                //     style = MaterialTheme.typography.labelMedium,
                //     color = Color(0xFF6A7896),
                // )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    value = userId,
                    onValueChange = { userId = it },
                    singleLine = true,
                    label = { Text(text = "User Id") },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Person, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(imageVector = Icons.Outlined.Person, contentDescription = "User Id")
                        }
                    },
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(text = "Password") },
                    placeholder = { Text(text = "Enter your password") },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle password visibility",
                            )
                        }
                    },
                    visualTransformation =
                        if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                    )
                    Text(
                        text = "Remember Me",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface,
                    )
                }

                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = userId.trim().isNotEmpty() && !isProcessing,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        ),
                    shape = RoundedCornerShape(12.dp),
                    onClick = onLoginClick,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Divider(modifier = Modifier.weight(1f), color = colorScheme.outlineVariant)
            Text(
                text = "OR",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Divider(modifier = Modifier.weight(1f), color = colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = userId.trim().isNotEmpty() && !isProcessing,
            onClick = onBiometricClick,
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(imageVector = Icons.Outlined.Face, contentDescription = null, tint = colorScheme.primary)
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Use Face ID",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Forgot Password?", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
            Text(text = "  |  ", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
            TextButton(
                onClick = {
                    viewModel.recoverUserIdState.value = userId.trim()
                    viewModel.recoverMobileState.value = ""
                    viewModel.recoverOtpState.value = ""
                    viewModel.recoverDialogVisibleState.value = true
                },
                modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = "Recover It",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(70.dp))

        Text(
            text = "Secured by Bangladesh Police • v$versionName",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(5.dp))
    }
}

@Suppress("DEPRECATION")
private fun getAppVersionName(context: Context): String {
    val pm = context.packageManager
    val pkg = context.packageName
    val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(pkg, 0)
        }
    return info.versionName?.trim().orEmpty()
}

@Composable
private fun PiomsLoginBackground() {
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
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f)),
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun FaceLoginCamera(
    detectViewModel: DetectScreenViewModel,
    loginViewModel: LoginScreenViewModel,
    onLoginSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var navigated by remember { mutableStateOf(false) }

    cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            cameraPermissionGranted = it
            if (!cameraPermissionGranted) {
                createAlertDialog(
                    dialogTitle = "Camera Permission",
                    dialogText = "Camera permission is required for face login.",
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }
        }

    if (!cameraPermissionGranted) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Allow Camera Permissions\nThe app cannot login without the camera permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(text = "Allow")
            }
        }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            FaceDetectionOverlay(
                lifecycleOwner = lifecycleOwner,
                context = context,
                viewModel = detectViewModel,
                onFaceRecognitionResults = { results ->
                    if (navigated) return@FaceDetectionOverlay
                    val target = loginViewModel.userIdState.value.trim()
                    if (target.isEmpty()) return@FaceDetectionOverlay
                    val matched =
                        results.any {
                            it.personName == target && (it.spoofResult?.isSpoof != true)
                        }
                    if (matched) {
                        navigated = true
                        onLoginSuccess()
                    }
                },
            )
        },
        update = {
            it.initializeCamera(CameraSelector.LENS_FACING_FRONT)
        },
    )
}
