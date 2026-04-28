package com.phq.swl.pioms.presentation.screens.incident_report

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ThanaItem(
    val thanaID: Int,
    val thanaName: String,
    val districtID: Int,
    val divisionID: Int
)

data class AgentSearchResult(
    val userAutoID: Int,
    val userFullName: String,
    val userCode: String,
    val bpNumber: String,
)

data class AgentRelationItem(
    val agentId: Int,
    val reportId: Int = 0,
    val isLead: Boolean = false,
    val isAcknowledged: Boolean = false,
    val acknowledgeDate: String? = null,
    val remarks: String? = null,
    val agentName: String = "",
    val agentCode: String = "",
    val bpNumber: String = "",
)

@KoinViewModel
class IncidentViewModel(
    private val settingsStore: SettingsStore,
    private val context: Context
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"

    val selectedThanaIdState = mutableStateOf<Int?>(null)
    val thanaListState: MutableState<List<ThanaItem>> = mutableStateOf(emptyList())
    
    val dateState = mutableStateOf("")
    val timeState = mutableStateOf("")
    val subjectState = mutableStateOf("")
    val descriptionState = mutableStateOf("")
    
    val attachmentUrisState: MutableState<List<Uri>> = mutableStateOf(emptyList())

    val isSearchingAgentsState = mutableStateOf(false)
    val showNoAgentFoundDialogState = mutableStateOf(false)
    val agentSearchQueryState = mutableStateOf("")
    val agentSearchResultsState: MutableState<List<AgentSearchResult>> = mutableStateOf(emptyList())
    val agentReportRelationsState: MutableState<List<AgentRelationItem>> = mutableStateOf(emptyList())

    val isPageLoadingState = mutableStateOf(false)
    val isSavingState = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    val reportStatusState = mutableStateOf(0)
    val reportFlowLabelState = mutableStateOf(0)
    val currentUserIdState = mutableStateOf(0)
    private var incidentReportId: Int = 0
    private var status = 0
    private var flowLabel = 0

    init {
        val now = LocalDateTime.now()
        dateState.value = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        timeState.value = now.format(DateTimeFormatter.ofPattern("hh:mm a"))
        currentUserIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
        loadThanaList()
    }

    fun loadIncidentReport(id: Int) {
        incidentReportId = id
        isPageLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.IncidentReport.getIncidentReportById(id), token)
                val data = response.optJSONObject("responseObj") ?: response

                withContext(Dispatchers.Main) {
                    selectedThanaIdState.value =
                        data.optInt("thanaID", 0)
                            .takeIf { it != 0 }
                            ?: data.optInt("ThanaID", 0).takeIf { it != 0 }

                    subjectState.value =
                        data.optString("incidentType", "")
                            .ifBlank { data.optString("IncidentType", "") }
                    descriptionState.value =
                        data.optString("incidentDetails", "")
                            .ifBlank { data.optString("IncidentDetails", "") }
                    
                    status = data.optInt("status", data.optInt("Status", 0))
                    flowLabel = data.optInt("flowLabel", data.optInt("FlowLabel", 0))
                    reportStatusState.value = status
                    reportFlowLabelState.value = flowLabel

                    val reportDateRaw =
                        data.optString("reportDate", "")
                            .ifBlank { data.optString("ReportDate", "") }
                    if (reportDateRaw.isNotBlank()) {
                        runCatching {
                            val normalized = reportDateRaw.replace('T', ' ').trim()
                            val dateTime = LocalDateTime.parse(
                                normalized.substringBeforeLast("."),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                            )
                            dateState.value = dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                            timeState.value = dateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
                        }
                    }

                    // Load agent relations
                    val relationsArr = data.optJSONArray("agentReportRelations") ?: data.optJSONArray("AgentReportRelations")
                    if (relationsArr != null) {
                        val relList = mutableListOf<AgentRelationItem>()
                        for (i in 0 until relationsArr.length()) {
                            val o = relationsArr.optJSONObject(i) ?: continue
                            relList.add(
                                AgentRelationItem(
                                    agentId = o.optInt("agentId", o.optInt("AgentId", 0)),
                                    reportId = o.optInt("incidentReportID", o.optInt("IncidentReportID", 0)),
                                    isLead = o.optBoolean("isLead", o.optBoolean("IsLead", false)),
                                    isAcknowledged = o.optBoolean("isAcknowledged", o.optBoolean("IsAcknowledged", false)),
                                    acknowledgeDate = o.optString("acknowledgeDate", "").takeIf { it.isNotBlank() && it != "null" },
                                    remarks = o.optString("remarks", "").takeIf { it.isNotBlank() && it != "null" },
                                    agentName = normalizeServerText(o.optString("agentName", o.optString("AgentName", ""))),
                                    agentCode = normalizeServerText(o.optString("agentCode", o.optString("AgentCode", ""))),
                                    bpNumber = normalizeServerText(o.optString("bpNumber", o.optString("BpNumber", ""))),
                                )
                            )
                        }
                        agentReportRelationsState.value = relList
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    saveSuccessState.value = false
                    saveMessageState.value = e.message ?: "Failed to load incident report"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isPageLoadingState.value = false
                }
            }
        }
    }

    private fun loadThanaList() {
        isPageLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val agentId = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
                
                val body = JSONObject().apply {
                    put("requestObj", JSONObject().apply {
                        put("AgentID", agentId)
                    })
                }
                
                val response = httpPostJson(ApiEndpoints.IncidentReport.assignedThanaByAgentID, body, token)
                if (response.optInt("statusCode") == 1) {
                    val responseObj = response.optJSONArray("responseObj") ?: JSONArray()
                    val list = mutableListOf<ThanaItem>()
                    for (i in 0 until responseObj.length()) {
                        val obj = responseObj.getJSONObject(i)
                        list.add(ThanaItem(
                            thanaID = obj.getInt("thanaID"),
                            thanaName = obj.getString("thanaName"),
                            districtID = obj.getInt("districtID"),
                            divisionID = obj.getInt("divisionID")
                        ))
                    }
                    withContext(Dispatchers.Main) {
                        thanaListState.value = list
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isPageLoadingState.value = false
                }
            }
        }
    }

    fun searchAgents() {
        val q = agentSearchQueryState.value.trim()
        if (q.isBlank()) {
            agentSearchResultsState.value = emptyList()
            return
        }
        showNoAgentFoundDialogState.value = false
        isSearchingAgentsState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var results: List<AgentSearchResult> = emptyList()
            var showNoAgentFound = false
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val url = ApiEndpoints.Users.searchAgents(q)
                val res = httpGetJson(url, token.takeIf { it.isNotBlank() })
                if (res.optInt("statusCode", -1) == 1) {
                    val arr = res.optJSONArray("responseObj") ?: JSONArray()
                    showNoAgentFound = arr.length() == 0
                    val existingIds = agentReportRelationsState.value.map { it.agentId }.toSet()
                    results = buildList {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val id = o.optInt("userAutoID", 0)
                            if (id == 0 || id in existingIds) continue
                            val name = normalizeServerText(o.optString("userFullName", ""))
                            if (name.isNotBlank()) {
                                add(AgentSearchResult(
                                    userAutoID = id,
                                    userFullName = name,
                                    userCode = normalizeServerText(o.optString("userCode", "")),
                                    bpNumber = normalizeServerText(o.optString("bpNumber", "")),
                                ))
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                agentSearchResultsState.value = results
                showNoAgentFoundDialogState.value = showNoAgentFound
                isSearchingAgentsState.value = false
            }
        }
    }

    fun addAgentFromSearch(result: AgentSearchResult) {
        if (agentReportRelationsState.value.any { it.agentId == result.userAutoID }) return
        val newItem = AgentRelationItem(
            agentId = result.userAutoID,
            reportId = incidentReportId,
            agentName = result.userFullName,
            agentCode = result.userCode,
            bpNumber = result.bpNumber,
        )
        agentReportRelationsState.value = agentReportRelationsState.value + newItem
        agentSearchResultsState.value = emptyList()
        agentSearchQueryState.value = ""
    }

    fun removeAgentRelation(index: Int) {
        val list = agentReportRelationsState.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            agentReportRelationsState.value = list
        }
    }

    private fun normalizeServerText(text: String?): String {
        if (text == null || text == "null") return ""
        return text.trim()
    }

    fun submitReport(isAcknowledgment: Boolean = false, isForwarding: Boolean = false) {
        val thanaId = selectedThanaIdState.value
        if (thanaId == null) {
            saveMessageState.value = "Please select a Thana"
            return
        }

        isSavingState.value = true
        saveSuccessState.value = null
        saveMessageState.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val agentId = settingsStore.get(KEY_USER_AUTO_ID) ?: ""
                val selectedThana = thanaListState.value.first { it.thanaID == thanaId }
                
                // Format ReportDate as per demo: 2026-04-11 13:49:00.000
                val datePart = java.time.LocalDate.parse(dateState.value, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                val timePart = java.time.LocalTime.parse(timeState.value, DateTimeFormatter.ofPattern("hh:mm a"))
                val reportDate = LocalDateTime.of(datePart, timePart).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000"))

                val statusVal = when {
                    isForwarding -> 3
                    isAcknowledgment -> 2
                    else -> 0
                }
                val flowLabelVal = if (isForwarding) 1 else 0

                val fieldMap = mutableMapOf(
                    "IncidentReportId" to incidentReportId.toString(),
                    "DivisionID" to selectedThana.divisionID.toString(),
                    "DistrictID" to selectedThana.districtID.toString(),
                    "ThanaID" to selectedThana.thanaID.toString(),
                    "AgentID" to agentId,
                    "IncidentType" to subjectState.value,
                    "IncidentDetails" to descriptionState.value,
                    "Status" to statusVal.toString(),
                    "FlowLabel" to flowLabelVal.toString(),
                    "ReportDate" to reportDate,
                    "isSubmit" to "false"
                )

                // Add agent relations
                val currentRelations = agentReportRelationsState.value
                val creatorId = settingsStore.get("user_auto_id")?.toIntOrNull() ?: 0
                
                // If creator is not in relations, add them as Lead
                val finalRelations = if (currentRelations.none { it.agentId == creatorId }) {
                    val creatorName = settingsStore.get("user_full_name") ?: ""
                    val creatorCode = settingsStore.get("user_code") ?: ""
                    val creatorBp = settingsStore.get("bp_number") ?: ""
                    
                    val lead = AgentRelationItem(
                        agentId = creatorId,
                        reportId = incidentReportId,
                        isLead = true,
                        isAcknowledged = true,
                        acknowledgeDate = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                        agentName = creatorName,
                        agentCode = creatorCode,
                        bpNumber = creatorBp
                    )
                    listOf(lead) + currentRelations
                } else {
                    currentRelations
                }

                finalRelations.forEachIndexed { i, rel ->
                    fieldMap["agentReportRelations[$i].agentId"] = rel.agentId.toString()
                    fieldMap["agentReportRelations[$i].reportId"] = incidentReportId.toString()
                    fieldMap["agentReportRelations[$i].isLead"] = (i == 0 || rel.isLead).toString()
                    fieldMap["agentReportRelations[$i].isAcknowledged"] = rel.isAcknowledged.toString()
                    fieldMap["agentReportRelations[$i].acknowledgeDate"] = rel.acknowledgeDate ?: ""
                    fieldMap["agentReportRelations[$i].remarks"] = rel.remarks ?: ""
                    fieldMap["agentReportRelations[$i].agentName"] = rel.agentName
                    fieldMap["agentReportRelations[$i].agentCode"] = rel.agentCode
                    fieldMap["agentReportRelations[$i].bpNumber"] = rel.bpNumber
                }

                val isUpdate = incidentReportId != 0
                val result =
                    httpPostMultipart(
                        url =
                            if (isUpdate) {
                                ApiEndpoints.IncidentReport.getIncidentReportById(incidentReportId)
                            } else {
                                ApiEndpoints.IncidentReport.saveIncidentWithAttachments
                            },
                        fields = fieldMap,
                        fileUris = attachmentUrisState.value,
                        token = token,
                        method = if (isUpdate) "PUT" else "POST",
                    )
                if (result.optInt("statusCode") == 1 || result.optInt("statusCode") == 200) {
                    withContext(Dispatchers.Main) {
                        saveSuccessState.value = true
                        saveMessageState.value = "Report submitted successfully"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        saveSuccessState.value = false
                        saveMessageState.value = result.optString("message", "Failed to submit")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    saveSuccessState.value = false
                    saveMessageState.value = e.message ?: "An error occurred"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSavingState.value = false
                }
            }
        }
    }

    private fun httpPostJson(url: String, body: JSONObject, token: String?): JSONObject {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            conn.doOutput = true
            ApiHttpLogger.logRequest(method = "POST", url = url, bodyText = body.toString())
            conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            ApiHttpLogger.logResponse(method = "POST", url = url, statusCode = status, bodyText = responseText)
            JSONObject(responseText)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun httpGetJson(url: String, token: String?): JSONObject {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            ApiHttpLogger.logResponse(method = "GET", url = url, statusCode = status, bodyText = responseText)
            
            if (responseText.isNotBlank()) {
                val trimmed = responseText.trim()
                if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
                } else {
                    JSONObject(trimmed)
                }
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }

    private fun httpPostMultipart(
        url: String,
        fields: Map<String, String>,
        fileUris: List<Uri>,
        token: String?,
        method: String = "POST",
    ): JSONObject {
        return try {
            val boundary = "----${UUID.randomUUID()}"
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            conn.doOutput = true

            conn.outputStream.use { os ->
                // Add form fields
                for ((key, value) in fields) {
                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$key\"\r\n\r\n".toByteArray())
                    os.write("$value\r\n".toByteArray())
                }

                // Add files
                for (fileUri in fileUris) {
                    val inputStream = context.contentResolver.openInputStream(fileUri)
                    val fileName = fileUri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
                    val fileData = inputStream?.readBytes() ?: byteArrayOf()
                    inputStream?.close()

                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"Files\"; filename=\"$fileName\"\r\n".toByteArray())
                    os.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                    os.write(fileData)
                    os.write("\r\n".toByteArray())
                }

                // Write final boundary
                os.write("--$boundary--\r\n".toByteArray())
                os.flush()
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            ApiHttpLogger.logResponse(method = method, url = url, statusCode = status, bodyText = responseText)
            if (responseText.isNotEmpty()) JSONObject(responseText) else JSONObject()
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }
}
