package com.phq.swl.pioms.presentation.screens.submitted_reports

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

// Maps UI tab label -> API tab query param key
val SPECIAL_REPORT_TAB_MAP = linkedMapOf(
    "Draft"                  to "draft",
    "Acknowledge Pending"    to "acknowledgePending",
    "Forwarded"              to "forwarding",
    "Ready to submit"        to "readyToSubmit",
    "Submitted"              to "submitted",
    "Completed"              to "completed",
    "Acknowledge Completed"  to "acknowledgeCompleted",
    "Resend"                 to "resend",
    "Resend to Agent"        to "resendToAgent"
)

data class ReportItem(
    val id: String,
    val bpNo: String,
    val name: String,
    val reportType: String,
    val status: String,
    val updatedAt: String,
    val rawData: Map<String, Any?> = emptyMap()
)

enum class ReportType(val displayName: String, val apiKey: String) {
    SPECIAL("Special Report", "SpecialReport"),
    WORKPLACE("VR Workplace", "WorkingPlaceReport"),
    PERSONAL("VR Personal", "UserPersonalAndFamilyInfo"),
    ACADEMIC("VR Academic", "Education"),
    INCIDENT("Incident Report", "IncidentReport")
}

@KoinViewModel
class SubmittedReportsViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val BASE_URL = ApiEndpoints.API_BASE_ROOT

    val isLoadingState = mutableStateOf(false)
    val reportsState: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    val filteredReportsState: MutableState<List<ReportItem>> = mutableStateOf(emptyList())
    val statusCountsState: MutableState<Map<String, Int>> = mutableStateOf(emptyMap())

    val selectedReportType: MutableState<ReportType?> = mutableStateOf(null)
    // For Special Reports: the currently active UI tab label
    val selectedStatusState = mutableStateOf("Draft")
    val bpFilterState = mutableStateOf("")
    val startDateState: MutableState<Long?> = mutableStateOf(null)
    val endDateState: MutableState<Long?> = mutableStateOf(null)

    val statusTabsState = mutableStateOf(listOf("Draft", "Acknowledge Pending", "Forwarded", "Submitted", "Completed", "Cancel"))

    // Pagination state for Special Reports
    private var specialPageOffset = 0
    private val specialPageSize = 10

    fun fetchReports(reportType: ReportType) {
        selectedReportType.value = reportType
        isLoadingState.value = true

        if (reportType == ReportType.SPECIAL) {
            // Use tab-based paginated API for Special Reports
            fetchSpecialReportTab(selectedStatusState.value)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val userAutoId = settingsStore.get(KEY_USER_AUTO_ID) ?: "0"

                val url = when (reportType) {
                    ReportType.SPECIAL -> ApiEndpoints.SpecialReport.getSpecialReportListByAgent(userAutoId.toInt())
                    ReportType.WORKPLACE -> ApiEndpoints.WorkingPlaceReport.getWorkingPlaceReportListByAgent(userAutoId.toInt())
                    ReportType.PERSONAL -> ApiEndpoints.UserPersonalAndFamilyInfo.getUserPersonalAndFamilyInfoListByAgent(userAutoId.toInt())
                    ReportType.ACADEMIC -> ApiEndpoints.Education.getEducationListByAgent(userAutoId.toInt())
                    ReportType.INCIDENT -> ApiEndpoints.IncidentReport.getIncidentReportListByAgent(userAutoId.toInt())
                }
                val reports = httpGet(url, token)

                statusTabsState.value = listOf("Draft", "Acknowledge Pending", "Forwarded", "Submitted", "Completed", "Cancel")
                reportsState.value = reports
                applyFilters()
            } catch (e: Exception) {
                e.printStackTrace()
                reportsState.value = emptyList()
            } finally {
                isLoadingState.value = false
            }
        }
    }

    /**
     * Fetches Special Reports from the tab-based paginated API.
     * [tabLabel] is the UI display label (e.g. "Draft", "Submitted").
     */
    fun fetchSpecialReportTab(tabLabel: String) {
        val tabKey = SPECIAL_REPORT_TAB_MAP[tabLabel] ?: "draft"
        selectedStatusState.value = tabLabel
        selectedReportType.value = ReportType.SPECIAL
        statusTabsState.value = SPECIAL_REPORT_TAB_MAP.keys.toList()
        isLoadingState.value = true
        specialPageOffset = 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val url = "$BASE_URL/SpecialReport/specialReport/list?tab=$tabKey&pageOffset=$specialPageOffset&pageSize=$specialPageSize"
                httpGetSpecial(url, token)
            } catch (e: Exception) {
                e.printStackTrace()
                reportsState.value = emptyList()
                filteredReportsState.value = emptyList()
            } finally {
                isLoadingState.value = false
            }
        }
    }

    /** Performs the HTTP GET for the Special Report tab API, parses items + tabCounts. */
    private fun httpGetSpecial(url: String, token: String?) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val httpStatus = conn.responseCode
            val stream = if (httpStatus in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()

            if (responseText.isEmpty()) {
                reportsState.value = emptyList()
                filteredReportsState.value = emptyList()
                return
            }

            val root = JSONObject(responseText)
            val responseObj = root.optJSONObject("responseObj") ?: run {
                reportsState.value = emptyList()
                filteredReportsState.value = emptyList()
                return
            }

            // Parse items
            val itemsArr = responseObj.optJSONArray("items") ?: JSONArray()
            val reports = mutableListOf<ReportItem>()
            for (i in 0 until itemsArr.length()) {
                try {
                    val obj = itemsArr.getJSONObject(i)
                    val item = extractSpecialReportItem(obj)
                    if (item != null) reports.add(item)
                } catch (e: Exception) { continue }
            }
            reportsState.value = reports
            filteredReportsState.value = reports

            // Parse tabCounts and populate statusCountsState
            val tabCounts = responseObj.optJSONObject("tabCounts")
            if (tabCounts != null) {
                val counts = mutableMapOf<String, Int>()
                for ((uiLabel, apiKey) in SPECIAL_REPORT_TAB_MAP) {
                    counts[uiLabel] = tabCounts.optInt(apiKey, 0)
                }
                statusCountsState.value = counts
            }
        } catch (e: Exception) {
            e.printStackTrace()
            reportsState.value = emptyList()
            filteredReportsState.value = emptyList()
        }
    }

    /** Extracts a ReportItem specifically from a Special Report API item object. */
    private fun extractSpecialReportItem(json: JSONObject): ReportItem? {
        val rawData = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try { rawData[key] = json.get(key) } catch (e: Exception) { rawData[key] = json.optString(key) }
        }

        val id = json.optString("specialReportId", "")
        if (id.isEmpty() || id == "0") return null

        val bpNo = json.optString("bpNumber", "").takeIf { it.isNotEmpty() } ?: "N/A"
        val banglaName = json.optString("banglaName", "").takeIf { it.isNotEmpty() }
        val englishName = json.optString("englishName", "").takeIf { it.isNotEmpty() }
        val name = banglaName ?: englishName ?: "N/A"

        var updatedAt = json.optString("updatedAt", "")
        if (updatedAt.isEmpty() || updatedAt.startsWith("0001-01-01")) {
            updatedAt = json.optString("createAt", "")
        }
        if (updatedAt.isEmpty()) updatedAt = "N/A"

        return ReportItem(
            id = id,
            bpNo = bpNo,
            name = name,
            reportType = ReportType.SPECIAL.displayName,
            status = "",
            updatedAt = updatedAt,
            rawData = rawData
        )
    }

    fun updateStatusFilter(status: String) {
        if (selectedReportType.value == ReportType.SPECIAL) {
            // For Special Reports, re-fetch from API with the new tab
            fetchSpecialReportTab(status)
        } else {
            selectedStatusState.value = status
            applyFilters()
        }
    }

    fun updateBpFilter(bp: String) {
        bpFilterState.value = bp
        applyFilters()
    }

    fun updateDateRange(startDate: Long?, endDate: Long?) {
        startDateState.value = startDate
        endDateState.value = endDate
        applyFilters()
    }

    fun deleteReport(report: ReportItem, type: ReportType, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val id = report.id
                val endpoint = when (type) {
                    ReportType.SPECIAL -> "SpecialReport/$id"
                    ReportType.WORKPLACE -> "WorkingPlaceReport/$id"
                    ReportType.PERSONAL -> "UserPersonalAndFamilyInfo/$id"
                    ReportType.ACADEMIC -> "Education/$id"
                    ReportType.INCIDENT -> "IncidentReport/$id"
                }
                
                val url = URL("$BASE_URL/$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                
                val requestMethod = "DELETE"
                conn.requestMethod = requestMethod
                token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
                
                val status = conn.responseCode
                onResult(status in 200..299)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun submitDraftReport(report: ReportItem, type: ReportType, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val body = JSONObject(report.rawData).apply {
                    put("isSubmit", true)
                }
                
                val endpoint = when (type) {
                    ReportType.SPECIAL -> "SpecialReport"
                    ReportType.WORKPLACE -> "WorkingPlaceReport"
                    ReportType.PERSONAL -> "UserPersonalAndFamilyInfo"
                    ReportType.ACADEMIC -> "Education"
                    ReportType.INCIDENT -> "IncidentReport/saveIncidentWithAttachments"
                }

                val url = URL("$BASE_URL/$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
                conn.doOutput = true
                
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                
                val status = conn.responseCode
                onResult(status in 200..299)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun getStatusForReport(report: ReportItem): String {
        return try {
            getStatusForReportInternal(report)
        } catch (e: Exception) {
            e.printStackTrace()
            "Draft" // Default to Draft if calculation fails
        }
    }

    private fun applyFilters() {
        try {
            var baseFiltered = reportsState.value

            // Filter by BP number
            val bpQuery = bpFilterState.value.trim()
            if (bpQuery.isNotEmpty()) {
                baseFiltered = baseFiltered.filter { it.bpNo.contains(bpQuery, ignoreCase = true) }
            }

            // Filter by date range
            val startDate = startDateState.value
            val endDate = endDateState.value
            if (startDate != null || endDate != null) {
                baseFiltered = baseFiltered.filter { report ->
                    val reportDate = parseDate(report.updatedAt)?.time ?: return@filter false
                    val isAfterStart = startDate == null || reportDate >= startDate
                    val isBeforeEnd = endDate == null || reportDate <= endDate
                    isAfterStart && isBeforeEnd
                }
            }

            // Calculate status counts on baseFiltered
            val statuses = listOf("Draft", "Acknowledge Pending", "Forwarded", "Ready to submit", "Submitted", "Completed", "Acknowledge Completed", "Resend", "Resend to Agent", "Cancel")
            val counts = mutableMapOf<String, Int>()
            for (status in statuses) {
                counts[status] = baseFiltered.count { 
                    try { getStatusForReportInternal(it) == status } catch(e: Exception) { false } 
                }
            }
            statusCountsState.value = counts

            // Apply selected status filter
            var filtered = baseFiltered.filter { 
                try {
                    getStatusForReportInternal(it) == selectedStatusState.value
                } catch (e: Exception) {
                    false
                }
            }

            // Sort by date descending
            filtered = filtered.sortedByDescending { parseDate(it.updatedAt)?.time ?: 0L }

            filteredReportsState.value = filtered
        } catch (e: Exception) {
            e.printStackTrace()
            filteredReportsState.value = emptyList()
        }
    }

    private fun getStatusForReportInternal(report: ReportItem): String {
        return try {
            val rawData = report.rawData

            val isDeleted = isTrue(rawData["isDeleted"])
            if (isDeleted) return "Cancel"

            // For Special Reports: the API tab drives the status label directly
            if (report.reportType == ReportType.SPECIAL.displayName) {
                return selectedStatusState.value
            }

            val flowLabel = tryParseInt(rawData["flowLabel"]) ?: 0
            val status = tryParseInt(rawData["status"]) ?: 0

            if (report.reportType == ReportType.INCIDENT.displayName) {
                if (status == 2 && flowLabel == 0) return "Acknowledge Pending"
                return if (status == 2 || status == 3 || isTrue(rawData["isSubmit"])) "Submitted" else "Draft"
            }

            if (flowLabel == 0 && status == 0) return "Draft"
            if (flowLabel == 0 && status == 2) return "Acknowledge Pending"
            if (flowLabel == 1 && status == 3) return "Forwarded"
            if (flowLabel == 0 && status == 3) return "Resend to Agent"
            if (flowLabel == 3 && status == 6) return "Completed"


            val isSubmitted = isTrue(rawData["isSubmit"])
            if (isSubmitted) "Submitted" else "Draft"
        } catch (e: Exception) {
            e.printStackTrace()
            "Draft" // Default to Draft on error
        }
    }

    private fun tryParseInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun isTrue(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.lowercase() in listOf("true", "1", "yes", "y")
            else -> false
        }
    }

    private fun parseDate(dateStr: String): java.util.Date? {
        return try {
            if (dateStr.isEmpty() || dateStr.startsWith("0001-01-01")) return null
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(dateStr)
                ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).parse(dateStr)
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String, token: String?): List<ReportItem> {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()

            if (responseText.isEmpty()) return emptyList()

            // Try to parse direct array or wrapped response
            val responseArr = try {
                if (responseText.trim().startsWith("[")) {
                    JSONArray(responseText)
                } else {
                    val json = JSONObject(responseText)
                    json.optJSONArray("responseObj") ?: json.optJSONArray("items") ?: JSONArray()
                }
            } catch (e: Exception) {
                JSONArray()
            }

            val reports = mutableListOf<ReportItem>()
            for (i in 0 until responseArr.length()) {
                try {
                    val obj = responseArr.getJSONObject(i)
                    val item = extractReportItem(obj)
                    if (item != null) {
                        reports.add(item)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            reports
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractReportItem(json: JSONObject): ReportItem? {
        val reportType = selectedReportType.value ?: return null
        val rawData = mutableMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                rawData[key] = json.get(key)
            } catch (e: Exception) {
                rawData[key] = json.optString(key)
            }
        }

        val bpNoStr = json.optString("bpNumber", "") + json.optString("bpNo", "")
        val bpNo = bpNoStr.takeIf { it.isNotEmpty() } 
            ?: if (reportType == ReportType.INCIDENT) json.optString("agentID", "N/A") else "N/A"
            
        val banglaName = json.optString("banglaName", "").takeIf { it.isNotEmpty() }
        val englishName = json.optString("englishName", "").takeIf { it.isNotEmpty() }
        val directName = json.optString("name", "").takeIf { it.isNotEmpty() }
        val name = (directName ?: banglaName ?: englishName ?: if (reportType == ReportType.INCIDENT) json.optString("incidentType", "N/A") else "N/A")
        
        // Use createdAt as primary date, fallback to updatedAt, then createAt
        var updatedAt = json.optString("updatedAt", "")
        if (updatedAt.isEmpty() || updatedAt.startsWith("0001-01-01")) {
            updatedAt = json.optString("createAt", "")
        }
        if (updatedAt.isEmpty() || updatedAt.startsWith("0001-01-01")) {
            updatedAt = json.optString("createdAt", "")
        }
        if (updatedAt.isEmpty()) {
            updatedAt = when (reportType) {
                ReportType.INCIDENT -> json.optString("reportDate", "N/A")
                else -> "N/A"
            }
        }

        val id = when (reportType) {
            ReportType.SPECIAL -> json.optString("specialReportId", "")
            ReportType.WORKPLACE -> json.optString("workingPlaceReportId", "")
            ReportType.PERSONAL -> json.optString("userInfoId", "")
            ReportType.ACADEMIC -> json.optString("userEducationInfoID", "")
            ReportType.INCIDENT -> json.optString("incidentReportID", json.optString("incidentReportId", ""))
        }

        // Return null if critical fields are empty
        if (id.isEmpty() || bpNo == "N/A") {
            return null
        }

        return ReportItem(
            id = id,
            bpNo = bpNo,
            name = name,
            reportType = reportType.displayName,
            status = "", // Status calculated dynamically by getStatusForReportInternal()
            updatedAt = updatedAt,
            rawData = rawData
        )
    }
}
