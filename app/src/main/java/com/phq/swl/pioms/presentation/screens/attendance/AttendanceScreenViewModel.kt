package com.phq.swl.pioms.presentation.screens.attendance

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import com.phq.swl.pioms.domain.ImageVectorUseCase
import com.phq.swl.pioms.domain.PersonUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class MonthlyAttendanceEntry(
    val attendanceDate: String,
    val time1: String,
    val time2: String,
)

@KoinViewModel
class AttendanceScreenViewModel(
    private val settingsStore: SettingsStore,
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase,
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    private val KEY_FACE_REFERENCE_URL = "face_reference_url"

    val userIdState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty())

    val yearMonthState: MutableState<YearMonth> = mutableStateOf(YearMonth.now())
    val presentDatesState: MutableState<Set<String>> = mutableStateOf(emptySet())
    val absentDatesState: MutableState<Set<String>> = mutableStateOf(emptySet())
    val entriesState: MutableState<List<MonthlyAttendanceEntry>> = mutableStateOf(emptyList())
    val presentCountState: MutableState<Int> = mutableStateOf(0)
    val absentCountState: MutableState<Int> = mutableStateOf(0)
    val lastCheckInTextState: MutableState<String> = mutableStateOf("-")
    val lastCheckOutTextState: MutableState<String> = mutableStateOf("-")
    val loadingState: MutableState<Boolean> = mutableStateOf(false)
    val messageState: MutableState<String?> = mutableStateOf(null)
    val faceReferenceUrlState: MutableState<String?> = mutableStateOf(settingsStore.get(KEY_FACE_REFERENCE_URL))
    val faceReferenceReadyState: MutableState<Boolean> = mutableStateOf(false)
    val attendanceSaveLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val attendanceSaveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val attendanceSaveMessageState: MutableState<String?> = mutableStateOf(null)

    val capturedImageState: MutableState<Bitmap?> = mutableStateOf(null)
    val faceVerificationLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val similarityScoreState: MutableState<Double?> = mutableStateOf(null)
    val storedFaceBase64State: MutableState<String?> = mutableStateOf(null)

    fun loadMonthStats(yearMonth: YearMonth = yearMonthState.value) {
        val agentId = settingsStore.get(KEY_USER_AUTO_ID)?.trim().orEmpty().toIntOrNull() ?: 0
        if (agentId <= 0) {
            messageState.value = "Agent ID not found"
            presentDatesState.value = emptySet()
            absentDatesState.value = emptySet()
            entriesState.value = emptyList()
            presentCountState.value = 0
            absentCountState.value = 0
            lastCheckInTextState.value = "-"
            lastCheckOutTextState.value = "-"
            return
        }

        val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
        loadingState.value = true
        messageState.value = null

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body =
                    JSONObject()
                        .put(
                            "requestObj",
                            JSONObject()
                                .put("AgentID", agentId)
                                .put("Year", yearMonth.year)
                                .put("Month", yearMonth.monthValue),
                        )

                val response = httpPostJson(ApiEndpoints.AgentAttendance.getMonthlyFirstAttendanceReport, token, body)
                val statusCode = response.optInt("statusCode", -1)
                val responseObj = response.optJSONArray("responseObj")
                val msg = response.optString("message", "").trim()
                if (statusCode != 1 || responseObj == null) {
                    messageState.value = if (msg.isNotEmpty()) msg else "Failed to load attendance"
                    presentDatesState.value = emptySet()
                    absentDatesState.value = emptySet()
                    entriesState.value = emptyList()
                    presentCountState.value = 0
                    absentCountState.value = 0
                    lastCheckInTextState.value = "-"
                    lastCheckOutTextState.value = "-"
                    return@runCatching
                }

                val presentDates = LinkedHashSet<String>()
                val entries = ArrayList<MonthlyAttendanceEntry>(responseObj.length())
                for (i in 0 until responseObj.length()) {
                    val item = responseObj.optJSONObject(i) ?: continue
                    val dateStr = item.optString("attendanceDate", "").trim()
                    if (dateStr.isEmpty()) continue
                    presentDates.add(dateStr)
                    entries.add(
                        MonthlyAttendanceEntry(
                            attendanceDate = dateStr,
                            time1 = item.optString("attendanceTime1", "").trim(),
                            time2 = item.optString("attendanceTime2", "").trim(),
                        ),
                    )
                }
                entries.sortBy { it.attendanceDate }

                val daysInMonth = yearMonth.lengthOfMonth()
                val now = LocalDate.now()
                val isCurrentMonth = now.year == yearMonth.year && now.monthValue == yearMonth.monthValue
                val upToDay = if (isCurrentMonth) now.dayOfMonth else daysInMonth
                var present = 0
                val absentDates = LinkedHashSet<String>()
                for (d in 1..upToDay) {
                    val key = LocalDate.of(yearMonth.year, yearMonth.monthValue, d).toString()
                    if (presentDates.contains(key)) {
                        present++
                    } else {
                        absentDates.add(key)
                    }
                }
                val absent = (upToDay - present).coerceIn(0, upToDay)

                presentDatesState.value = presentDates
                absentDatesState.value = absentDates
                entriesState.value = entries
                presentCountState.value = present
                absentCountState.value = absent
                lastCheckInTextState.value = entries.lastOrNull()?.time1?.ifBlank { "-" } ?: "-"
                lastCheckOutTextState.value = entries.lastOrNull()?.time2?.ifBlank { "-" } ?: "-"
                messageState.value = null
            }.onFailure {
                messageState.value = it.message ?: "Failed"
                presentDatesState.value = emptySet()
                absentDatesState.value = emptySet()
                entriesState.value = emptyList()
                presentCountState.value = 0
                absentCountState.value = 0
                lastCheckInTextState.value = "-"
                lastCheckOutTextState.value = "-"
            }
            loadingState.value = false
        }
    }

    fun prepareFaceReference(context: Context) {
        val userId =
            settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty().ifEmpty { "" }
                .also { userIdState.value = it }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = ApiEndpoints.AgentFaceRegistration.getRegistrationByUserIDWithImages(userId)
                val json = httpGetJson(url, settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty().takeIf { it.isNotEmpty() })
                val statusCode = json.optInt("statusCode", -1)
                if (statusCode != 1) return@runCatching

                val responseObj = json.optJSONObject("responseObj") ?: return@runCatching
                val images = responseObj.optJSONArray("agentFaceImages") ?: return@runCatching
                if (images.length() == 0) return@runCatching

                val first = images.optJSONObject(0) ?: return@runCatching
                val rawLink = first.optString("attachmentLink", "").trim()
                val cleanLink = normalizeUrl(rawLink)
                if (cleanLink.isEmpty()) return@runCatching

                val personName = userId
                val person =
                    personUseCase.findByName(personName)
                        ?: run {
                            val id = personUseCase.addPerson(personName, 0)
                            personUseCase.findByName(personName) ?: return@runCatching
                        }

                val cachedUrl = settingsStore.get(KEY_FACE_REFERENCE_URL)
                if (cleanLink == cachedUrl && imageVectorUseCase.hasImages(person.personID)) {
                    // Already have this image processed
                    faceReferenceReadyState.value = true
                    // Still need to load base64 for similarity checks if missing
                    if (storedFaceBase64State.value == null) {
                        prefetchStoredImage(context)
                    }
                    return@runCatching
                }

                settingsStore.save(KEY_FACE_REFERENCE_URL, cleanLink)
                faceReferenceUrlState.value = cleanLink

                val bytes = httpGetBytes(cleanLink)
                if (bytes.isEmpty()) return@runCatching
                val file = File(context.cacheDir, "face_ref_${userId}_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { it.write(bytes) }
                val uri = Uri.fromFile(file)

                imageVectorUseCase.removeImages(person.personID)
                val added = imageVectorUseCase.addImage(person.personID, personName, uri)
                
                // Also cache the base64 for similarity checks
                storedFaceBase64State.value = Base64.encodeToString(bytes, Base64.NO_WRAP)
                
                faceReferenceReadyState.value = added.isSuccess
            }.onFailure {
                it.printStackTrace()
                faceReferenceReadyState.value = false
            }
        }
    }

    fun prefetchStoredImage(context: Context) {
        if (storedFaceBase64State.value != null) return
        CoroutineScope(Dispatchers.IO).launch {
            storedFaceBase64State.value = getStoredImageBase64(context)
        }
    }

    fun saveAttendanceCheckIn(
        dutyPlace: String,
        latitude: String,
        longitude: String,
        remarks: String = "CHECK IN",
    ) {
        val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
        val agentId = settingsStore.get(KEY_USER_AUTO_ID)?.trim().orEmpty()
        if (token.isEmpty() || agentId.isEmpty()) {
            attendanceSaveSuccessState.value = false
            attendanceSaveMessageState.value = "Session not found"
            return
        }
        if (latitude.isBlank() || longitude.isBlank()) {
            attendanceSaveSuccessState.value = false
            attendanceSaveMessageState.value = "Location not found"
            return
        }
        if (dutyPlace.isBlank()) {
            attendanceSaveSuccessState.value = false
            attendanceSaveMessageState.value = "Duty place not found"
            return
        }

        attendanceSaveLoadingState.value = true
        attendanceSaveSuccessState.value = null
        attendanceSaveMessageState.value = null

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val time = java.time.LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val body =
                    JSONObject()
                        .put(
                            "RequestObj",
                            JSONObject()
                                .put("complainID", 0)
                                .put("AgentID", agentId)
                                .put("DutyPlace", dutyPlace)
                                .put("AttendanceTime", time)
                                .put("Latitude", latitude)
                                .put("Longitude", longitude)
                                .put("Remarks", remarks),
                        )
                val res = httpPostJson(ApiEndpoints.AgentAttendance.save, token, body)
                val statusCode = res.optInt("statusCode", -1)
                val msg = res.optString("message", "").trim()
                val ok = statusCode == 1
                attendanceSaveSuccessState.value = ok
                attendanceSaveMessageState.value =
                    when {
                        ok -> "Attendance Successful"
                        msg.isNotEmpty() -> msg
                        else -> "Attendance failed"
                    }

                if (ok) {
                    val today = java.time.LocalDate.now().toString()
                    val updatedPresent = LinkedHashSet(presentDatesState.value)
                    updatedPresent.add(today)
                    presentDatesState.value = updatedPresent

                    val updatedAbsent = LinkedHashSet(absentDatesState.value)
                    updatedAbsent.remove(today)
                    absentDatesState.value = updatedAbsent

                    val yearMonth = yearMonthState.value
                    val now = java.time.LocalDate.now()
                    val isCurrentMonth = now.year == yearMonth.year && now.monthValue == yearMonth.monthValue
                    if (isCurrentMonth) {
                        val upToDay = now.dayOfMonth
                        val present = (1..upToDay).count { d ->
                            val key = java.time.LocalDate.of(yearMonth.year, yearMonth.monthValue, d).toString()
                            presentDatesState.value.contains(key)
                        }
                        presentCountState.value = present
                        absentCountState.value = (upToDay - present).coerceIn(0, upToDay)
                    }
                }
            }.onFailure {
                attendanceSaveSuccessState.value = false
                attendanceSaveMessageState.value = it.message ?: "Attendance failed"
            }
            attendanceSaveLoadingState.value = false
        }
    }

    fun verifyFaceSimilarity(storedBase64: String, capturedBase64: String) {
        faceVerificationLoadingState.value = true
        similarityScoreState.value = null
        
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body = JSONObject()
                    .put("data", JSONArray().apply {
                        put("data:image/jpeg;base64,$storedBase64")
                        put("data:image/jpeg;base64,$capturedBase64")
                    })
                    .put("event_data", JSONObject.NULL)
                    .put("fn_index", 2)
                    .put("session_hash", "c18f4ji6gj6")

                val res = httpPostJson("https://justin2341-facerecognition.hf.space/run/predict", "", body)
                val data = res.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val resultString = data.optString(0)
                    // Extract similarity score from HTML: "Similarity: 0.88" or "Similarity Score: 0.88"
                    val pattern = Pattern.compile("Similarity(?:\\s+Score)?:\\s*([0-9.]+)")
                    val matcher = pattern.matcher(resultString)
                    if (matcher.find()) {
                        val score = matcher.group(1)?.toDoubleOrNull()
                        similarityScoreState.value = score
                    } else {
                        similarityScoreState.value = 0.0
                    }
                } else {
                    similarityScoreState.value = 0.0
                }
            }.onFailure {
                similarityScoreState.value = 0.0
            }
            faceVerificationLoadingState.value = false
        }
    }

    fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun getStoredImageBase64(context: Context): String? {
        var url = faceReferenceUrlState.value
        if (url.isNullOrBlank()) {
            val userId = userIdState.value.ifEmpty { settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty() }
            if (userId.isNotEmpty()) {
                try {
                    val apiUrl = ApiEndpoints.AgentFaceRegistration.getRegistrationByUserIDWithImages(userId)
                    val json = httpGetJson(apiUrl, settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty())
                    if (json.optInt("statusCode") == 1) {
                        val responseObj = json.optJSONObject("responseObj")
                        val images = responseObj?.optJSONArray("agentFaceImages")
                        if (images != null && images.length() > 0) {
                            val rawLink = images.optJSONObject(0).optString("attachmentLink", "")
                            url = normalizeUrl(rawLink)
                            faceReferenceUrlState.value = url
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (url.isNullOrBlank()) return null

        return runCatching {
            val bytes = httpGetBytes(url!!)
            if (bytes.isEmpty()) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun normalizeUrl(raw: String): String {
        var s = raw.trim().trim('`').trim()
        if (s.isEmpty()) return ""
        
        // Fix potential common formatting issues
        if (s.startsWith("http:////", ignoreCase = true)) {
            s = "http://" + s.removePrefix("http:////")
        }
        
        // Handle relative paths
        if (!s.startsWith("http", ignoreCase = true)) {
            val baseUrl = ApiEndpoints.API_BASE_ROOT.substringBeforeLast("/api")
            // Ensure s starts with / if it doesn't and baseUrl doesn't end with it
            val prefix = if (s.startsWith("\\") || s.startsWith("/")) "" else "/"
            s = "$baseUrl$prefix$s"
        }
        
        // Normalize slashes and remove spaces
        s = s.replace("\\", "/").replace(" ", "")
        
        return s
    }

    private fun httpGetJson(
        url: String,
        token: String?,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        token?.trim()?.takeIf { it.isNotEmpty() }?.let {
            conn.setRequestProperty("Authorization", "Bearer $it")
        }
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        ApiHttpLogger.logRequest(
            method = "GET",
            url = url,
            headers =
                mapOf(
                    "Accept" to "application/json",
                    "Authorization" to conn.getRequestProperty("Authorization"),
                ),
        )

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(
            method = "GET",
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

    private fun httpGetBytes(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        ApiHttpLogger.logBinaryResponse(
            method = "GET",
            url = url,
            statusCode = status,
            byteCount = body.size,
        )
        return if (status in 200..299) body else ByteArray(0)
    }

    private fun httpPostJson(
        url: String,
        token: String,
        body: JSONObject,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        token.trim().takeIf { it.isNotEmpty() }?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val payload = body.toString()
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
}
