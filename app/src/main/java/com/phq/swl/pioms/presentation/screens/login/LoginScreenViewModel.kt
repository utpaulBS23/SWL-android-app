package com.phq.swl.pioms.presentation.screens.login

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import com.phq.swl.pioms.domain.ImageVectorUseCase
import com.phq.swl.pioms.domain.PersonUseCase
import com.phq.swl.pioms.presentation.components.createAlertDialog
import com.phq.swl.pioms.presentation.components.hideProgressDialog
import com.phq.swl.pioms.presentation.components.setProgressDialogText
import com.phq.swl.pioms.presentation.components.showProgressDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@KoinViewModel
class LoginScreenViewModel(
    private val context: Context,
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_API_BASE_URL = "login_api_base_url"
    private val KEY_API_TOKEN = "login_api_token"
    private val KEY_USER_ID = "login_user_id"
    private val KEY_PASSWORD = "login_password"
    private val KEY_REMEMBER_ME = "login_remember_me"
    private val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_USER_CODE = "user_code"
    private val KEY_USER_FULL_NAME = "user_full_name"
    private val KEY_MOBILE_NO = "mobile_no"
    private val KEY_BP_NUMBER = "bp_number"
    private val KEY_UNIT = "unit"
    private val KEY_DEPARTMENT_ID = "department_id"

    val apiBaseUrlState: MutableState<String> =
        mutableStateOf(
            settingsStore.get(KEY_API_BASE_URL)
                ?: (ApiEndpoints.API_BASE_ROOT + "/AgentFaceRegistration/getRegistrationByUserIDWithImages/"),
        )
    val apiTokenState: MutableState<String> =
        mutableStateOf(settingsStore.get(KEY_API_TOKEN) ?: "")
    val userIdState: MutableState<String> = mutableStateOf("")
    val passwordState: MutableState<String> = mutableStateOf("")
    val rememberMeState: MutableState<Boolean> = mutableStateOf(settingsStore.getBoolean(KEY_REMEMBER_ME, false))

    val otpState: MutableState<String> = mutableStateOf("")
    val otpDialogVisibleState: MutableState<Boolean> = mutableStateOf(false)
    val loginCompletedState: MutableState<Boolean> = mutableStateOf(false)
    val loginErrorState: MutableState<String?> = mutableStateOf(null)

    val requestNewIdDialogVisibleState: MutableState<Boolean> = mutableStateOf(false)
    val requestNewIdMobileState: MutableState<String> = mutableStateOf("")
    val requestNewIdUserCodeState: MutableState<String> = mutableStateOf("")
    val requestNewIdOtpState: MutableState<String> = mutableStateOf("")
    val requestNewIdOtpDialogVisibleState: MutableState<Boolean> = mutableStateOf(false)

    val recoverDialogVisibleState: MutableState<Boolean> = mutableStateOf(false)
    val recoverUserIdState: MutableState<String> = mutableStateOf("")
    val recoverMobileState: MutableState<String> = mutableStateOf("")
    val recoverOtpState: MutableState<String> = mutableStateOf("")
    val recoverOtpDialogVisibleState: MutableState<Boolean> = mutableStateOf(false)

    val downloadedImagePathsState: MutableState<List<String>> = mutableStateOf(emptyList())
    val isEnrolledState: MutableState<Boolean> = mutableStateOf(false)

    val isProcessingState: MutableState<Boolean> = mutableStateOf(false)
    val numImagesProcessedState = mutableIntStateOf(0)

    init {
        if (rememberMeState.value) {
            userIdState.value = settingsStore.get(KEY_USER_ID)?.trim().orEmpty()
        }
    }

    fun getLoggedInUserId(): String? = settingsStore.get(KEY_LOGGED_IN_USER_ID)

    fun setLoggedInUser(userId: String) {
        settingsStore.save(KEY_LOGGED_IN_USER_ID, userId)
    }

    fun clearLoggedInUser() {
        settingsStore.save(KEY_LOGGED_IN_USER_ID, "")
    }

    fun loginWithPassword() {
        val userId = userIdState.value.trim()
        val password = passwordState.value.trim()
        if (userId.isEmpty() || password.isEmpty()) {
            return
        }

        settingsStore.saveBoolean(KEY_REMEMBER_ME, rememberMeState.value)
        if (rememberMeState.value) {
            settingsStore.save(KEY_USER_ID, userId)
        } else {
            settingsStore.save(KEY_USER_ID, "")
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Signing in")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val deviceId =
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                        ?.trim()
                        .orEmpty()

                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.login,
                        token = null,
                        body =
                            mapOf(
                                "UserID" to userId,
                                "Password" to password,
                                "MACAddress" to deviceId,
                            ),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "").trim()
                if (statusCode != 1) {
                    if (statusCode == -1 && message == "Invalid UserID/Password") {
                        createAlertDialog(
                            dialogTitle = "Login Failed",
                            dialogText = message,
                            dialogPositiveButtonText = "OK",
                            dialogNegativeButtonText = null,
                            onPositiveButtonClick = {},
                            onNegativeButtonClick = null,
                        )
                        isProcessingState.value = false
                        hideProgressDialog()
                        return@launch
                    }
                    error(if (message.isNotBlank()) message else "Login failed")
                }

                response.optString("token", "").trim().takeIf { it.isNotEmpty() }?.let {
                    settingsStore.save(KEY_AUTH_TOKEN, it)
                    apiTokenState.value = it
                    settingsStore.save(KEY_API_TOKEN, it)
                }

                response.optJSONObject("responseObj")?.let { obj ->
                    obj.optString("userID", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_LOGGED_IN_USER_ID, it)
                    }
                    obj.opt("userAutoID")?.toString()?.takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_AUTO_ID, it)
                    }
                    obj.optString("userCode", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_CODE, it)
                    }
                    obj.optString("userFullName", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_FULL_NAME, it)
                    }
                    obj.optString("mobileNo", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_MOBILE_NO, it)
                    }
                    obj.optString("bpNumber", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_BP_NUMBER, it)
                    }
                    obj.opt("departmentID")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_DEPARTMENT_ID, it)
                    }
                    val unit = obj.optString("unit", obj.optString("Unit", "")).trim()
                    if (unit.isNotEmpty()) {
                        settingsStore.save(KEY_UNIT, unit)
                    }
                }

                val isDeviceVerified = response.optJSONObject("responseObj")?.optBoolean("isDeviceVerified", false) ?: false
                if (!isDeviceVerified) {
                    otpState.value = ""
                    otpDialogVisibleState.value = true
                } else {
                    loginCompletedState.value = true
                }
            }.onFailure {
                setProgressDialogText(it.message ?: "Failed")
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun verifyOtpForLogin() {
        val userId = userIdState.value.trim()
        val otp = otpState.value.trim()
        if (userId.isEmpty() || otp.isEmpty()) {
            return
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Verifying OTP")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val deviceId =
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                        ?.trim()
                        .orEmpty()

                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.verifyOtpForLogin,
                        token = null,
                        body =
                            mapOf(
                                "UserID" to userId,
                                "OTP" to otp,
                                "MACAddress" to deviceId,
                            ),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "")
                if (statusCode != 1) {
                    error(if (message.isNotBlank()) message else "OTP verification failed")
                }

                response.optString("token", "").trim().takeIf { it.isNotEmpty() }?.let {
                    settingsStore.save(KEY_AUTH_TOKEN, it)
                    apiTokenState.value = it
                    settingsStore.save(KEY_API_TOKEN, it)
                }

                response.optJSONObject("responseObj")?.let { obj ->
                    obj.optString("userID", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_LOGGED_IN_USER_ID, it)
                    }
                    obj.opt("userAutoID")?.toString()?.takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_AUTO_ID, it)
                    }
                    obj.optString("userCode", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_CODE, it)
                    }
                    obj.optString("userFullName", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_USER_FULL_NAME, it)
                    }
                    obj.optString("mobileNo", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_MOBILE_NO, it)
                    }
                    obj.optString("bpNumber", "").takeIf { it.isNotEmpty() }?.let {
                        settingsStore.save(KEY_BP_NUMBER, it)
                    }
                    val unit = obj.optString("unit", obj.optString("Unit", "")).trim()
                    if (unit.isNotEmpty()) {
                        settingsStore.save(KEY_UNIT, unit)
                    }
                }

                otpDialogVisibleState.value = false
                loginCompletedState.value = true
            }.onFailure {
                setProgressDialogText(it.message ?: "Failed")
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun requestNewUserId() {
        val mobile = requestNewIdMobileState.value.trim()
        if (mobile.isEmpty()) {
            return
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Requesting new ID")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.requestNewUserId,
                        token = null,
                        body = mapOf("MobileNo" to mobile),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "")
                if (statusCode != 1) {
                    error(if (message.isNotBlank()) message else "Mobile number not found")
                }

                val responseObj = response.optJSONObject("responseObj")
                val userCode = responseObj?.optString("userID", "")?.trim().orEmpty()
                if (userCode.isEmpty()) {
                    error("Unable to get User Code for OTP verification.")
                }

                requestNewIdUserCodeState.value = userCode
                requestNewIdOtpState.value = ""
                requestNewIdDialogVisibleState.value = false
                requestNewIdOtpDialogVisibleState.value = true

                createAlertDialog(
                    dialogTitle = "OTP Sent",
                    dialogText = message.ifBlank { "OTP sent to your mobile number." },
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }.onFailure {
                createAlertDialog(
                    dialogTitle = "Request New ID",
                    dialogText = it.message ?: "Failed",
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun verifyOtpForNewId() {
        val mobile = requestNewIdMobileState.value.trim()
        val otp = requestNewIdOtpState.value.trim()
        if (mobile.isEmpty() || otp.isEmpty()) {
            return
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Verifying OTP")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.verifyOtpForNewId,
                        token = null,
                        body =
                            mapOf(
                                "MobileNumber" to mobile,
                                "OTP" to otp,
                            ),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "")
                if (statusCode != 1) {
                    error(if (message.isNotBlank()) message else "OTP verification failed.")
                }

                requestNewIdOtpDialogVisibleState.value = false
                requestNewIdOtpState.value = ""

                createAlertDialog(
                    dialogTitle = "Successful!",
                    dialogText =
                        message.ifBlank {
                            "Your User Code and Password have been sent to your mobile number via SMS."
                        },
                    dialogPositiveButtonText = "CONTINUE",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }.onFailure {
                createAlertDialog(
                    dialogTitle = "Verify OTP",
                    dialogText = it.message ?: "Failed",
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun recoverPasswordRequest() {
        val userId = recoverUserIdState.value.trim()
        val mobile = recoverMobileState.value.trim()
        if (userId.isEmpty() || mobile.isEmpty()) {
            return
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Recovering password")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.recoverPasswordByMobileNumber,
                        token = null,
                        body =
                            mapOf(
                                "userID" to userId,
                                "mobileNo" to mobile,
                            ),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "")
                if (statusCode != 1 && statusCode != 200 && statusCode != 201 && statusCode != 204) {
                    error(if (message.isNotBlank()) message else "Invalid Mobile no.")
                }

                recoverOtpState.value = ""
                recoverDialogVisibleState.value = false
                recoverOtpDialogVisibleState.value = true
            }.onFailure {
                createAlertDialog(
                    dialogTitle = "Recover Password",
                    dialogText = it.message ?: "Failed",
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun verifyOtpForPasswordRecovery() {
        val userId = recoverUserIdState.value.trim()
        val otp = recoverOtpState.value.trim()
        if (userId.isEmpty() || otp.isEmpty()) {
            return
        }

        isProcessingState.value = true
        showProgressDialog()
        setProgressDialogText("Verifying OTP")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val response =
                    httpPostJson(
                        url = ApiEndpoints.Authenticate.verifyOtpForPasswordRecovery,
                        token = null,
                        body =
                            mapOf(
                                "userID" to userId,
                                "otp" to otp,
                            ),
                    )

                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "")
                if (statusCode != 1 && statusCode != 200 && statusCode != 201 && statusCode != 204) {
                    error(if (message.isNotBlank()) message else "Invalid OTP.")
                }

                recoverOtpDialogVisibleState.value = false
                recoverOtpState.value = ""

                createAlertDialog(
                    dialogTitle = "Successful!",
                    dialogText = message.ifBlank { "Password recovery verified." },
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }.onFailure {
                createAlertDialog(
                    dialogTitle = "Verify OTP",
                    dialogText = it.message ?: "Failed",
                    dialogPositiveButtonText = "OK",
                    dialogNegativeButtonText = null,
                    onPositiveButtonClick = {},
                    onNegativeButtonClick = null,
                )
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    fun downloadAndEnroll() {
        val userId = userIdState.value.trim()
        val token = normalizeToken(apiTokenState.value)
        val apiBaseUrl = apiBaseUrlState.value.trim()

        if (userId.isEmpty()) {
            return
        }

        settingsStore.save(KEY_USER_ID, userId)
        settingsStore.save(KEY_API_TOKEN, token)
        settingsStore.save(KEY_API_BASE_URL, apiBaseUrl)

        isProcessingState.value = true
        numImagesProcessedState.intValue = 0
        isEnrolledState.value = false
        downloadedImagePathsState.value = emptyList()
        showProgressDialog()
        setProgressDialogText("Fetching registration images")

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val apiUrl = apiBaseUrl.ensureEndsWithSlash() + userId
                val imageUrls = fetchImageUrls(apiUrl, token)
                if (imageUrls.isEmpty()) {
                    error("No images returned by API")
                }

                val savedFiles = downloadImages(userId, imageUrls, token)
                downloadedImagePathsState.value = savedFiles.map { it.absolutePath }
                setProgressDialogText("Downloaded ${savedFiles.size} image(s)")

                removeExistingPeopleWithName(userId)

                val personId =
                    personUseCase.addPerson(
                        name = userId,
                        numImages = savedFiles.size.toLong(),
                    )

                var numSuccessful = 0
                savedFiles.forEachIndexed { index, file ->
                    setProgressDialogText("Processing ${index + 1}/${savedFiles.size}")
                    imageVectorUseCase
                        .addImage(personId, userId, Uri.fromFile(file))
                        .onSuccess {
                            numSuccessful += 1
                            numImagesProcessedState.intValue = numSuccessful
                        }
                }

                if (numSuccessful == 0) {
                    error("Could not process any downloaded image(s)")
                }
                isEnrolledState.value = true
            }.onFailure {
                setProgressDialogText(it.message ?: "Failed")
            }

            isProcessingState.value = false
            hideProgressDialog()
        }
    }

    private suspend fun removeExistingPeopleWithName(name: String) {
        val all = personUseCase.getAll().first()
        val matches = all.filter { it.personName == name }
        matches.forEach { person ->
            imageVectorUseCase.removeImages(person.personID)
            personUseCase.removePerson(person.personID)
        }
    }

    private fun fetchImageUrls(
        apiUrl: String,
        token: String,
    ): List<String> {
        val json = httpGetText(apiUrl, token, accept = "application/json")
        val decoded = JSONObject(json)
        val responseObj = decoded.optJSONObject("responseObj") ?: return emptyList()
        val images = responseObj.optJSONArray("agentFaceImages") ?: return emptyList()
        val urls = ArrayList<String>()
        for (i in 0 until images.length()) {
            val imageObj = images.optJSONObject(i) ?: continue
            val raw = imageObj.optString("attachmentLink", "").trim()
            if (raw.isNotEmpty()) {
                urls.add(cleanUrl(raw))
            }
        }
        return urls
    }

    private fun downloadImages(
        userId: String,
        urls: List<String>,
        token: String,
    ): List<File> {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val targetDir = File(picturesDir, "registered_faces/$userId")
        targetDir.mkdirs()

        val saved = ArrayList<File>()
        urls.forEachIndexed { index, url ->
            setProgressDialogText("Downloading ${index + 1}/${urls.size}")
            val bytes = httpGetBytes(url, token, accept = "*/*")
            val file =
                File(
                    targetDir,
                    "reference_${System.currentTimeMillis()}_${index + 1}.jpg",
                )
            file.writeBytes(bytes)
            saved.add(file)
        }
        return saved
    }

    private fun httpGetText(
        url: String,
        token: String,
        accept: String,
    ): String = String(httpGetBytes(url, token, accept))

    private fun httpPostJson(
        url: String,
        token: String?,
        body: Map<String, String>,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        token?.trim()?.takeIf { it.isNotEmpty() }?.let {
            conn.setRequestProperty("Authorization", "Bearer $it")
        }
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val payload = JSONObject(body as Map<*, *>).toString()
        ApiHttpLogger.logRequest(
            method = "POST",
            url = url,
            headers =
                mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json",
                    "Authorization" to conn.getRequestProperty("Authorization"),
                ),
            bodyText = payload,
        )
        conn.outputStream.use { it.write(payload.toByteArray()) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(
            method = "POST",
            url = url,
            statusCode = status,
            bodyText = responseText,
        )
        if (responseText.isBlank()) {
            return JSONObject().put("statusCode", status).put("message", "Empty response")
        }
        return runCatching { JSONObject(responseText) }
            .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
    }

    private fun httpGetBytes(
        url: String,
        token: String,
        accept: String,
    ): ByteArray {
        var currentUrl = url
        repeat(5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection)
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", "FaceNetAndroid")
            buildAuthorizationHeader(token)?.let { conn.setRequestProperty("Authorization", it) }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location")?.trim()
                if (location.isNullOrEmpty()) {
                    error("HTTP $status redirect without Location for $currentUrl")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                return@repeat
            }

            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (accept == "application/json" || accept == "text/plain") {
                ApiHttpLogger.logResponse(
                    method = "GET",
                    url = currentUrl,
                    statusCode = status,
                    bodyText = body.decodeToString(),
                )
            } else {
                ApiHttpLogger.logBinaryResponse(
                    method = "GET",
                    url = currentUrl,
                    statusCode = status,
                    byteCount = body.size,
                )
            }
            if (status !in 200..299) {
                val snippet = body.decodeToString().take(500)
                error("HTTP $status for $currentUrl: $snippet")
            }
            return body
        }
        error("Too many redirects for $url")
    }

    private fun cleanUrl(rawUrl: String): String =
        rawUrl
            .replace("`", "")
            .replace(" ", "")
            .replace("http:////", "http://")
            .replace("https:////", "https://")

    private fun String.ensureEndsWithSlash(): String = if (endsWith("/")) this else "$this/"

    private fun normalizeToken(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) {
            t = t.substring(1, t.length - 1)
        }
        if (t.startsWith("'") && t.endsWith("'") && t.length >= 2) {
            t = t.substring(1, t.length - 1)
        }
        t = t.trim()
        if (t.startsWith("Bearer ", ignoreCase = true)) {
            t = t.substringAfter(" ", "").trim()
        }
        return t
    }

    private fun buildAuthorizationHeader(token: String): String? {
        val t = token.trim()
        if (t.isEmpty()) return null
        return "Bearer $t"
    }
}
