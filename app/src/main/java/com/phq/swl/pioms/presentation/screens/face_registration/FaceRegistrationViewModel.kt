package com.phq.swl.pioms.presentation.screens.face_registration

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

@KoinViewModel
class FaceRegistrationViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_USER_FULL_NAME = "user_full_name"
    private val KEY_MOBILE_NO = "mobile_no"
    private val KEY_BP_NUMBER = "bp_number"
    private val KEY_UNIT = "unit"
    private val KEY_DEPARTMENT_ID = "department_id"
    private val KEY_FACE_REFERENCE_URL = "face_reference_url"

    val userIdState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty())
    val agentCodeState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_USER_AUTO_ID)?.trim().orEmpty())
    val agentNameState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_USER_FULL_NAME)?.trim().orEmpty())
    val departmentIdState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_DEPARTMENT_ID)?.trim().orEmpty())
    val mobileState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_MOBILE_NO)?.trim().orEmpty())
    val statusState: MutableState<String> = mutableStateOf("0")

    val selectedImagesState: MutableState<List<Uri>> = mutableStateOf(emptyList())
    val uploadingState: MutableState<Boolean> = mutableStateOf(false)
    val uploadMessageState: MutableState<String?> = mutableStateOf(null)
    val uploadSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val logoutRequestedState: MutableState<Boolean> = mutableStateOf(false)

    fun addCapturedImage(uri: Uri) {
        selectedImagesState.value = (selectedImagesState.value + uri).take(5)
    }

    fun removeImage(uri: Uri) {
        selectedImagesState.value = selectedImagesState.value.filterNot { it == uri }
    }

    fun submit(context: Context) {
        val userId = userIdState.value.trim()
        val agentCode = agentCodeState.value.trim()
        val agentName = agentNameState.value.trim()
        val deptId = departmentIdState.value.trim()
        val mobile = mobileState.value.trim()
        val status = statusState.value.trim().ifBlank { "0" }
        val images = selectedImagesState.value

        if (images.isEmpty()) {
            uploadSuccessState.value = false
            uploadMessageState.value = "Please capture at least 1 face image"
            return
        }

        uploadingState.value = true
        uploadSuccessState.value = null
        uploadMessageState.value = null

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                if (token.isEmpty()) {
                    error("Authorization token not found")
                }
                val files = images.mapNotNull { uri -> copyToCacheFile(context, uri) }
                if (files.isEmpty()) error("No images to upload")

                val response =
                    uploadMultipart(
                        url = ApiEndpoints.AgentFaceRegistration.saveAgentFaceRegistration,
                        token = normalizeBearerToken(token),
                        fields =
                            linkedMapOf(
                                "AgentFaceRegistration.UserID" to userId,
                                "AgentFaceRegistration.AgentCode" to agentCode,
                                "AgentFaceRegistration.AgentName" to agentName,
                                "AgentFaceRegistration.departmentID" to deptId,
                                "AgentFaceRegistration.Mobile" to mobile,
                                "AgentFaceRegistration.Status" to status,
                            ),
                        imageFiles = files,
                    )

                val statusCode = response.optInt("statusCode", -1)
                val ok = statusCode == 1 || statusCode == 0
                uploadSuccessState.value = ok
                uploadMessageState.value =
                    when {
                        ok -> "Face registration submitted"
                        else -> response.optString("message", "Failed")
                    }
                if (ok) {
                    logoutRequestedState.value = true
                }
            }.onFailure {
                uploadSuccessState.value = false
                uploadMessageState.value = it.message ?: "Failed"
            }
            uploadingState.value = false
        }
    }

    private fun copyToCacheFile(
        context: Context,
        uri: Uri,
    ): File? {
        val ext = guessExtension(context, uri)
        val f = File(context.cacheDir, "face_reg_${System.currentTimeMillis()}.$ext")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            f
        }.getOrNull()
    }

    private fun guessExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            type.contains("png") -> "png"
            type.contains("webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun uploadMultipart(
        url: String,
        token: String,
        fields: LinkedHashMap<String, String>,
        imageFiles: List<File>,
    ): JSONObject {
        val boundary = "----PIOMS${System.currentTimeMillis()}"
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.setRequestProperty("Authorization", token)
        conn.connectTimeout = 20000
        conn.readTimeout = 20000

        ApiHttpLogger.logRequest(
            method = "POST",
            url = url,
            headers =
                mapOf(
                    "Content-Type" to conn.getRequestProperty("Content-Type"),
                    "Authorization" to conn.getRequestProperty("Authorization"),
                ),
            bodyText =
                buildString {
                    append("multipart fields=").append(fields.keys.joinToString())
                    append(" files=").append(imageFiles.size)
                },
        )

        conn.outputStream.use { out ->
            writeFields(out, boundary, fields)
            writeFiles(out, boundary, "FaceImages", imageFiles)
            out.write("--$boundary--\r\n".toByteArray())
        }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(method = "POST", url = url, statusCode = status, bodyText = responseText)
        if (responseText.isBlank()) {
            return JSONObject().put("statusCode", status).put("message", "Empty response")
        }
        return runCatching { JSONObject(responseText) }
            .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
    }

    private fun writeFields(
        out: OutputStream,
        boundary: String,
        fields: LinkedHashMap<String, String>,
    ) {
        for ((k, v) in fields) {
            out.write("--$boundary\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$k\"\r\n\r\n".toByteArray())
            out.write(v.toByteArray())
            out.write("\r\n".toByteArray())
        }
    }

    private fun writeFiles(
        out: OutputStream,
        boundary: String,
        fieldName: String,
        files: List<File>,
    ) {
        for (file in files) {
            val filename = file.name
            val contentType =
                when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
            out.write("--$boundary\r\n".toByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$filename\"\r\n".toByteArray(),
            )
            out.write("Content-Type: $contentType\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n".toByteArray())
        }
    }

    private fun normalizeBearerToken(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    fun performLogout() {
        settingsStore.save(KEY_LOGGED_IN_USER_ID, "")
        settingsStore.save(KEY_AUTH_TOKEN, "")
        settingsStore.save(KEY_USER_AUTO_ID, "")
        settingsStore.save(KEY_USER_FULL_NAME, "")
        settingsStore.save(KEY_MOBILE_NO, "")
        settingsStore.save(KEY_BP_NUMBER, "")
        settingsStore.save(KEY_DEPARTMENT_ID, "")
        settingsStore.save(KEY_FACE_REFERENCE_URL, "")
    }
}
