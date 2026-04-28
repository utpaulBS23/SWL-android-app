package com.phq.swl.pioms.presentation.screens.dashboard

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class GeofencePolygon(
    val geofenceId: Int?,
    val strokeArgb: Int?,
    val fillArgb: Int?,
    val points: List<Pair<Double, Double>>,
)

data class AssignedTaskItem(
    val assignedTaskId: Int,
    val bpNumber: String,
    val unit: String,
    val assignDate: String,
    val isSeen: Boolean,
)

data class AssignedTaskInfoItem(
    val complainId: Int,
    val complainNo: String,
    val reportType: Int?,
    val reportTypeName: String,
    val specialReportId: Int? = null,
    val workingPlaceReportId: Int? = null,
    val userInfoId: Int? = null,
    val userEducationInfoID: Int? = null,
    val incidentReportId: Int? = null,
)

@KoinViewModel
class DashboardScreenViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_USER_CODE = "user_code"
    private val KEY_USER_FULL_NAME = "user_full_name"
    private val KEY_MOBILE_NO = "mobile_no"
    private val KEY_BP_NUMBER = "bp_number"
    private val KEY_UNIT = "unit"

    val userAutoIdState: MutableState<Int?> =
        mutableStateOf(settingsStore.get(KEY_USER_AUTO_ID)?.trim().orEmpty().toIntOrNull())
    val authTokenState: MutableState<String> = mutableStateOf(settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty())

    val geofenceIdState: MutableState<Int?> = mutableStateOf(null)
    val geofencePointsState: MutableState<List<Pair<Double, Double>>> = mutableStateOf(emptyList())
    val geofenceStrokeArgbState: MutableState<Int?> = mutableStateOf(null)
    val geofenceFillArgbState: MutableState<Int?> = mutableStateOf(null)
    val geofencePolygonsState: MutableState<List<GeofencePolygon>> = mutableStateOf(emptyList())
    private val geofenceLoadedAgentIdState: MutableState<Int?> = mutableStateOf(null)
    val inOutStatusState: MutableState<String?> = mutableStateOf(null)
    val dutyStatusUpdatedState: MutableState<Boolean> = mutableStateOf(false)
    val dutyUpdateResponseState: MutableState<String?> = mutableStateOf(null)
    val geofenceResponseState: MutableState<String?> = mutableStateOf(null)
    val faceRegStatusState: MutableState<Int?> = mutableStateOf(null)
    val faceRegRawResponseState: MutableState<String?> = mutableStateOf(null)
    val faceRegHasResponseObjState: MutableState<Boolean> = mutableStateOf(false)
    val assignedTasksState: MutableState<List<AssignedTaskItem>> = mutableStateOf(emptyList())
    val taskInfoItemsState: MutableState<List<AssignedTaskInfoItem>> = mutableStateOf(emptyList())
    val taskInfoLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val taskInfoErrorState: MutableState<String?> = mutableStateOf(null)

    val passwordChangeLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val passwordChangeSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val passwordChangeMessageState: MutableState<String?> = mutableStateOf(null)

    val unseenTaskCountState: MutableIntState = mutableIntStateOf(0)

    fun reloadSessionFromStore() {
        userAutoIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.trim().orEmpty().toIntOrNull()
        authTokenState.value = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
    }

    fun onDashboardOpened() {
        reloadSessionFromStore()
        userAutoIdState.value?.let { loadGeofenceData(it) }
        startDutyIfNeeded()
        val userId = settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty().ifEmpty { "" }
        loadFaceRegistrationStatus(userId)
        userAutoIdState.value?.let { loadAssignedTasks(it) }
    }

    fun startDutyIfNeeded() {
        if (dutyStatusUpdatedState.value) return
        viewModelScope.launch(Dispatchers.IO) {
            updateDutyStartAndStopStatus(isLive = true)
        }
    }

    suspend fun updateDutyStartAndStopStatus(isLive: Boolean) {
        val userAutoId = settingsStore.get(KEY_USER_AUTO_ID)?.trim()?.toIntOrNull() ?: return
        val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
        if (token.isEmpty()) return

        runCatching {
            val body =
                JSONObject()
                    .put("UserAutoID", userAutoId)
                    .put("IsLive", isLive)
            val res = httpPutJson(ApiEndpoints.Authenticate.updateDutyStartAndStopStatus, token, body)
            dutyUpdateResponseState.value = res.toString()
            val statusCode = res.optInt("statusCode", 1)
            dutyStatusUpdatedState.value = (statusCode == 1)
        }
    }

    fun loadGeofenceData(agentIdOverride: Int? = null) {
        reloadSessionFromStore()
        val agentId = agentIdOverride ?: userAutoIdState.value ?: return
        if (agentId == 0) return
        if (geofenceLoadedAgentIdState.value == agentId && geofencePolygonsState.value.isNotEmpty()) return

        val token = authTokenState.value.trim()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val response = httpGetJson(ApiEndpoints.Users.getAgentGeofenceCoOrdinateById(agentId), token.takeIf { it.isNotEmpty() })
                geofenceResponseState.value = response.toString()
                if (response.optInt("statusCode", -1) != 1) return@runCatching
                val list = response.optJSONArray("responseObj") ?: return@runCatching
                if (list.length() == 0) return@runCatching

                val polygons = ArrayList<GeofencePolygon>(list.length())
                for (i in 0 until list.length()) {
                    val data = list.optJSONObject(i) ?: continue
                    val coords = data.optJSONArray("coordinates") ?: continue
                    val points = ArrayList<Pair<Double, Double>>(coords.length())
                    for (j in 0 until coords.length()) {
                        val p = coords.optJSONObject(j) ?: continue
                        val lat = p.optDouble("latitude", Double.NaN)
                        val lng = p.optDouble("longitude", Double.NaN)
                        if (!lat.isNaN() && !lng.isNaN()) {
                            points.add(lat to lng)
                        }
                    }
                    if (points.isEmpty()) continue

                    polygons.add(
                        GeofencePolygon(
                            geofenceId = data.optInt("geofenceID"),
                            strokeArgb = parseColorNameToArgb(data.optString("borderColor", null)),
                            fillArgb = parseColorNameToArgb(data.optString("fillColor", null), alpha = 0x4D),
                            points = points,
                        ),
                    )
                }

                geofencePolygonsState.value = polygons
                geofenceLoadedAgentIdState.value = agentId

                val first = polygons.firstOrNull()
                geofenceIdState.value = first?.geofenceId
                geofenceStrokeArgbState.value = first?.strokeArgb
                geofenceFillArgbState.value = first?.fillArgb
                geofencePointsState.value = first?.points.orEmpty()
            }
        }
    }

    fun refreshInOutStatusAndNotifyIfNeeded(
        currentLat: Double,
        currentLng: Double,
    ) {
        reloadSessionFromStore()
        val agentId = userAutoIdState.value?.toString()?.trim().orEmpty()
        if (agentId.isEmpty()) return

        val token = authTokenState.value.trim()
        if (token.isEmpty()) return

        val polygons = geofencePolygonsState.value
        if (polygons.isEmpty()) return

        val inside = polygons.any { it.points.isNotEmpty() && isPointInPolygon(currentLat, currentLng, it.points) }
        val type = if (inside) "In" else "Out"

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val statusRes = httpGetJson(ApiEndpoints.NotificationArea.getLastInOutTypeByAgentID(agentId), token)
                val lastStatus =
                    if (statusRes.optInt("statusCode", -1) == 1) {
                        statusRes.opt("responseObj")?.toString()?.trim().orEmpty()
                    } else {
                        ""
                    }

                inOutStatusState.value = type
                if (type == lastStatus) return@runCatching

                val now = LocalDateTime.now()
                val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

                val requestObj =
                    JSONObject()
                        .put("agentID", agentId)
                        .put("notificationType", type)
                        .put("notificationDate", formatted)
                        .put("assignedGeofenceID", geofenceIdState.value)
                        .put("locationLat", currentLat)
                        .put("locationLong", currentLng)

                val body = JSONObject().put("requestObj", requestObj)
                httpPostJson(ApiEndpoints.NotificationArea.saveOutAreaNotification, token, body)
            }
        }
    }

    fun loadFaceRegistrationStatus(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = ApiEndpoints.AgentFaceRegistration.getAgentFaceRegisterStatus(userId)
                val res = httpGetJson(url, null)
                faceRegRawResponseState.value = res.toString()
                val obj = res.optJSONObject("responseObj")
                val hasObj = obj != null && obj.length() > 0
                faceRegHasResponseObjState.value = hasObj
                faceRegStatusState.value = if (hasObj) obj?.optInt("status") else null
            }
        }
    }

    fun loadAssignedTasks(agentCode: Int) {
        val token = authTokenState.value.trim()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val url = ApiEndpoints.AssignedTask.getAssignedTasksByAgent(agentCode)
                val text = httpGetText(url, token.takeIf { it.isNotEmpty() })
                val arr = JSONArray(text)
                val items = ArrayList<AssignedTaskItem>(arr.length())
                var unseenCount = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val isSeen = obj.optBoolean("isSeen", true)
                    if (!isSeen) unseenCount++
                    items.add(
                        AssignedTaskItem(
                            assignedTaskId = obj.optInt("assignedTaskId", 0),
                            bpNumber = obj.optString("bpNumber", "").trim(),
                            unit = obj.optString("unit", "").trim(),
                            assignDate = obj.optString("assignDate", "").trim(),
                            isSeen = isSeen,
                        ),
                    )
                }
                assignedTasksState.value = items
                unseenTaskCountState.intValue = unseenCount
            }
        }
    }

    fun loadTaskInfoForAssignedTask(taskId: Int) {
        val token = authTokenState.value.trim()
        if (token.isEmpty()) return

        taskInfoLoadingState.value = true
        taskInfoErrorState.value = null
        taskInfoItemsState.value = emptyList()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                runCatching {
                    httpPutJson(ApiEndpoints.AssignedTask.updateViewStatus(taskId), token, JSONObject())
                }

                assignedTasksState.value =
                    assignedTasksState.value.map {
                        if (it.assignedTaskId == taskId) it.copy(isSeen = true) else it
                    }
                unseenTaskCountState.intValue = assignedTasksState.value.count { !it.isSeen }

                val url = ApiEndpoints.Complain.getComplainByAssignTask(taskId)
                val text = httpGetText(url, token)

                val items =
                    when {
                        text.trim().startsWith("[") -> parseComplainArray(JSONArray(text))
                        else -> {
                            val obj = JSONObject(text)
                            val statusCode = obj.optInt("statusCode", 0)
                            val responseObj = obj.optJSONArray("responseObj")
                            if (statusCode == 200 && responseObj != null && responseObj.length() > 0) {
                                parseComplainArray(responseObj)
                            } else {
                                emptyList()
                            }
                        }
                    }

                taskInfoItemsState.value = items
            }.onFailure {
                taskInfoErrorState.value = it.message ?: "Failed"
            }
            taskInfoLoadingState.value = false
        }
    }

    fun resolveAssignedTaskRoute(
        item: AssignedTaskInfoItem,
        onResolved: (String?) -> Unit,
    ) {
        reloadSessionFromStore()
        val token = authTokenState.value.trim()
        val agentId = userAutoIdState.value ?: 0
        if (token.isEmpty() || agentId == 0) {
            onResolved(null)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val route =
                runCatching {
                    when (item.reportType) {
                        1 -> {
                            val reportId =
                                item.specialReportId
                                    ?: findReportIdByComplainId(
                                        url = ApiEndpoints.SpecialReport.getSpecialReportListByAgent(agentId),
                                        token = token,
                                        complainId = item.complainId,
                                        idKeys = listOf("specialReportId"),
                                    )
                            val complainNo = item.complainNo.takeIf { it.isNotBlank() } ?: "0"
                            if (reportId != null) {
                                "edit-special-report/$reportId/${item.complainId}/$complainNo"
                            } else {
                                "special-report/${item.complainId}/$complainNo?applicantData="
                            }
                        }
                        2 -> {
                            val reportId =
                                item.userInfoId
                                    ?: findReportIdByComplainId(
                                        url = ApiEndpoints.UserPersonalAndFamilyInfo.getUserPersonalAndFamilyInfoListByAgent(agentId),
                                        token = token,
                                        complainId = item.complainId,
                                        idKeys = listOf("userInfoId"),
                                    )
                            "personal-page/${reportId ?: 0}/${item.complainId}"
                        }
                        3 -> {
                            val reportId =
                                item.workingPlaceReportId
                                    ?: findReportIdByComplainId(
                                        url = ApiEndpoints.WorkingPlaceReport.getWorkingPlaceReportListByAgent(agentId),
                                        token = token,
                                        complainId = item.complainId,
                                        idKeys = listOf("workingPlaceReportId"),
                                    )
                            "workplace-page/${reportId ?: 0}/${item.complainId}"
                        }
                        4 -> {
                            val reportId =
                                item.userEducationInfoID
                                    ?: findReportIdByComplainId(
                                        url = ApiEndpoints.Education.getEducationListByAgent(agentId),
                                        token = token,
                                        complainId = item.complainId,
                                        idKeys = listOf("userEducationInfoID", "userEducationInfoId"),
                                    )
                            "academic-page/${reportId ?: 0}/${item.complainId}"
                        }
                        5 -> {
                            val reportId =
                                item.incidentReportId
                                    ?: findReportIdByComplainId(
                                        url = ApiEndpoints.IncidentReport.getIncidentReportListByAgent(agentId),
                                        token = token,
                                        complainId = item.complainId,
                                        idKeys = listOf("incidentReportId"),
                                    )
                            if (reportId != null) "incident-report/$reportId" else "incident-report"
                        }
                        else -> null
                    }
                }.getOrNull()

            withContext(Dispatchers.Main) {
                onResolved(route)
            }
        }
    }

    fun performLogout(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            updateDutyStartAndStopStatus(isLive = false)
            logout()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun logout() {
        settingsStore.save(KEY_LOGGED_IN_USER_ID, "")
        settingsStore.save(KEY_AUTH_TOKEN, "")
        settingsStore.save(KEY_USER_AUTO_ID, "")
        settingsStore.save(KEY_USER_CODE, "")
        settingsStore.save(KEY_USER_FULL_NAME, "")
        settingsStore.save(KEY_MOBILE_NO, "")
        settingsStore.save(KEY_BP_NUMBER, "")
        settingsStore.save(KEY_UNIT, "")
        geofenceLoadedAgentIdState.value = null
        geofenceIdState.value = null
        geofenceStrokeArgbState.value = null
        geofenceFillArgbState.value = null
        geofencePointsState.value = emptyList()
        geofencePolygonsState.value = emptyList()
        unseenTaskCountState.intValue = 0
    }

    fun changePassword(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String,
    ) {
        reloadSessionFromStore()
        val agentId = userAutoIdState.value?.toString()?.trim().orEmpty()
        val userId = settingsStore.get(KEY_LOGGED_IN_USER_ID)?.trim().orEmpty()
        val token = authTokenState.value.trim()

        if (agentId.isEmpty() || userId.isEmpty() || token.isEmpty()) {
            passwordChangeMessageState.value = "User session is invalid"
            passwordChangeSuccessState.value = false
            return
        }

        if (newPassword != confirmPassword) {
            passwordChangeMessageState.value = "New password and confirm password do not match"
            passwordChangeSuccessState.value = false
            return
        }

        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            passwordChangeMessageState.value = "Please fill in all password fields"
            passwordChangeSuccessState.value = false
            return
        }

        passwordChangeLoadingState.value = true
        passwordChangeSuccessState.value = null
        passwordChangeMessageState.value = null

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val requestObj =
                    JSONObject()
                        .put("userID", userId)
                        .put("oldPassword", oldPassword)
                        .put("newPassword", newPassword)
                        .put("confirmPassword", confirmPassword)
                val body = JSONObject().put("requestObj", requestObj)

                val response = httpPutJson(ApiEndpoints.Users.changeUserPassword, token, body)
                val statusCode = response.optInt("statusCode", -1)
                val message = response.optString("message", "Unknown error").trim()

                if (statusCode == 1) {
                    passwordChangeSuccessState.value = true
                    passwordChangeMessageState.value = message
                    // Logout after successful password change
                    logout()
                } else {
                    passwordChangeSuccessState.value = false
                    passwordChangeMessageState.value = message.ifEmpty { "Failed to change password" }
                }
            }.onFailure {
                passwordChangeSuccessState.value = false
                passwordChangeMessageState.value = it.message ?: "An error occurred"
            }
            passwordChangeLoadingState.value = false
        }
    }

    fun resetPasswordChangeState() {
        passwordChangeLoadingState.value = false
        passwordChangeSuccessState.value = null
        passwordChangeMessageState.value = null
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

    private fun httpGetText(
        url: String,
        token: String?,
    ): String {
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
        if (status !in 200..299) {
            error("HTTP $status: ${responseText.take(500)}")
        }
        return responseText
    }

    private fun parseComplainArray(arr: JSONArray): List<AssignedTaskInfoItem> {
        val items = ArrayList<AssignedTaskInfoItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val complainId = obj.optInt("complainId", 0)
            val complainNo = obj.opt("complainNo")?.toString()?.trim().orEmpty()
            val reportType = obj.optInt("reportType", -1).takeIf { it > 0 }
            val reportTypeName = mapReportType(reportType)
            items.add(
                AssignedTaskInfoItem(
                    complainId = complainId,
                    complainNo = complainNo,
                    reportType = reportType,
                    reportTypeName = reportTypeName,
                    specialReportId = obj.optInt("specialReportId", 0).takeIf { it != 0 },
                    workingPlaceReportId =
                        obj.optInt("workingPlaceReportId", 0)
                            .takeIf { it != 0 }
                            ?: obj.optInt("vrWorkPlaceId", 0).takeIf { it != 0 },
                    userInfoId =
                        obj.optInt("userInfoId", 0)
                            .takeIf { it != 0 }
                            ?: obj.optInt("vrBirthPlaceId", 0).takeIf { it != 0 },
                    userEducationInfoID =
                        obj.optInt("userEducationInfoID", 0)
                            .takeIf { it != 0 }
                            ?: obj.optInt("vrAcademicId", 0).takeIf { it != 0 },
                    incidentReportId = obj.optInt("incidentReportId", 0).takeIf { it != 0 },
                ),
            )
        }
        return items
    }

    private fun findReportIdByComplainId(
        url: String,
        token: String,
        complainId: Int,
        idKeys: List<String>,
    ): Int? {
        val text = httpGetText(url, token)
        val array =
            when {
                text.trim().startsWith("[") -> JSONArray(text)
                else -> {
                    val obj = JSONObject(text)
                    obj.optJSONArray("responseObj") ?: obj.optJSONArray("items") ?: JSONArray()
                }
            }

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optInt("complainId", 0) != complainId) continue
            for (key in idKeys) {
                val id = obj.optInt(key, 0)
                if (id != 0) return id
            }
        }
        return null
    }

    private fun mapReportType(code: Int?): String =
        when (code) {
            1 -> "SpecialReport"
            2 -> "VR_Personal"
            3 -> "VR_Workplace"
            4 -> "VR_Academic"
            5 -> "IncidentReport"
            else -> code?.toString().orEmpty()
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
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
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

    private fun httpPutJson(
        url: String,
        token: String,
        body: JSONObject,
    ): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "PUT"
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val payload = body.toString()
        ApiHttpLogger.logRequest(
            method = "PUT",
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
            method = "PUT",
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

    private fun isPointInPolygon(
        lat: Double,
        lng: Double,
        polygon: List<Pair<Double, Double>>,
    ): Boolean {
        var intersectCount = 0
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (rayCastIntersect(lat, lng, a, b)) {
                intersectCount++
            }
        }
        return (intersectCount % 2) == 1
    }

    private fun rayCastIntersect(
        lat: Double,
        lng: Double,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
    ): Boolean {
        val aY = a.first
        val bY = b.first
        val aX = a.second
        val bX = b.second
        val pY = lat
        val pX = lng

        if ((aY > pY && bY > pY) || (aY < pY && bY < pY) || (aX < pX && bX < pX)) {
            return false
        }

        val m = (aY - bY) / (aX - bX)
        val bee = (-aX) * m + aY
        val x = (pY - bee) / m
        return x > pX
    }

    private fun parseColorNameToArgb(
        name: String?,
        alpha: Int = 0xFF,
    ): Int? {
        val key = name?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        val rgb =
            when (key) {
                "brown" -> 0xA52A2A
                "blue" -> 0x1565C0
                "red" -> 0xD32F2F
                "green" -> 0x2E7D32
                "yellow" -> 0xF9A825
                "orange" -> 0xFB8C00
                "purple" -> 0x6A1B9A
                "black" -> 0x000000
                "white" -> 0xFFFFFF
                "gray", "grey" -> 0x616161
                else -> return null
            }
        return (alpha.coerceIn(0, 255) shl 24) or rgb
    }
}
