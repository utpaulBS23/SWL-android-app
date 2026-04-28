package com.phq.swl.pioms.presentation.screens.vr_report

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phq.swl.pioms.data.SettingsStore
import com.phq.swl.pioms.data.network.ApiEndpoints
import com.phq.swl.pioms.data.network.ApiHttpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WorkplaceFieldType(
    val workingPlaceReceivedFieldTypeId: Int,
    val name: String,
    val orderNo: Int?,
    val isActive: Boolean
)

data class JobHistoryItem(
    val previousWorkingPlaceReportId: Int = 0,
    val workingPlaceReportId: Int = 0,
    val mainUnit: String = "",
    val unit: String = "",
    val subUnit: String = "",
    val rank: String = "",
    val nameAndAddress: String = "",
    val designation: String = "",
    val joiningDate: String = "",
    val leavingDate: String = "",
    val postingReason: String = "",
    val postingRowKey: String? = null,
    val questionResponses: Map<Int, String> = emptyMap()
)

data class WorkplaceAgentSearchResult(
    val userAutoID: Int,
    val userFullName: String,
    val userCode: String,
    val bpNumber: String,
)

data class WorkplaceAgentRelationItem(
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
class WorkplaceViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_DEPARTMENT_ID = "department_id"

    val districtOrUnitNameState = mutableStateOf("")
    val nameState = mutableStateOf("")
    val bpNumberState = mutableStateOf("")
    val designationState = mutableStateOf("")
    val homeDistrictState = mutableStateOf("")
    val mainUnitState = mutableStateOf("")
    val currentWorkingPlaceState = mutableStateOf("")
    val joiningDateTimeState = mutableStateOf("")
    val briberyLevelState = mutableStateOf("")
    val specialCommentState = mutableStateOf("")
    val politicalInvolvementState = mutableStateOf("")
    val firInformationState = mutableStateOf("")
    val othersInfoState = mutableStateOf("")
    
    val applicantPictureState = mutableStateOf<String?>(null)
    val isSearchingState = mutableStateOf(false)
    val isSearchingAgentsState = mutableStateOf(false)
    val showNoAgentFoundDialogState = mutableStateOf(false)
    val selectedWorkplaceTypeState = mutableStateOf("Present") // "Present" or "Past"
    val agentSearchQueryState = mutableStateOf("")
    val agentSearchResultsState: MutableState<List<WorkplaceAgentSearchResult>> = mutableStateOf(emptyList())
    val agentReportRelationsState: MutableState<List<WorkplaceAgentRelationItem>> = mutableStateOf(emptyList())

    // Job History
    val jobHistoryListState: MutableState<List<JobHistoryItem>> = mutableStateOf(listOf(JobHistoryItem()))

    // Dynamic questions
    val fieldTypesState: MutableState<List<WorkplaceFieldType>> = mutableStateOf(emptyList())
    val questionResponsesState: MutableState<Map<Int, String>> = mutableStateOf(emptyMap())
    val receivedInfoIdsState: MutableState<Map<Int, Int>> = mutableStateOf(emptyMap())

    val isPageLoadingState = mutableStateOf(false)
    val isSavingState = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    val isSubmittedState = mutableStateOf(false)
    val currentEditingJobIndexState: MutableState<Int?> = mutableStateOf(null)
    val reportStatusState = mutableStateOf(0)
    val reportFlowLabelState = mutableStateOf(0)
    val currentUserIdState = mutableStateOf(0)

    private var workingPlaceReportId = 0
    private var complainId = 0
    private var complainNo = 0
    private var status = 0
    private var flowLabel = 0
    private var hasLoadedPostingFromPims = false

    private fun formatApiDate(value: String): String {
        if (value.isBlank() || value == "null") return ""
        return value.substringBefore("T")
    }

    fun initData(reportId: Int, cId: Int) {
        workingPlaceReportId = reportId
        complainId = cId
        complainNo = 0
        agentSearchQueryState.value = ""
        agentSearchResultsState.value = emptyList()
        agentReportRelationsState.value = emptyList()
        loadFieldTypes()
        currentUserIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
        if (workingPlaceReportId != 0) {
            loadReportData()
        }
    }

    private fun loadFieldTypes() {
        isPageLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.WorkingPlaceReport.workplaceReceivedFieldTypeList, token)
                val items = response.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<WorkplaceFieldType>()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    list.add(
                        WorkplaceFieldType(
                            workingPlaceReceivedFieldTypeId = obj.getInt("workingPlaceReceivedFieldTypeId"),
                            name = obj.getString("name"),
                            orderNo = if (obj.isNull("orderNo")) null else obj.getInt("orderNo"),
                            isActive = obj.getBoolean("isActive")
                        )
                    )
                }
                // Filter isActive == true and sort by orderNo
                fieldTypesState.value = list.filter { it.isActive }
                    .sortedWith(compareBy<WorkplaceFieldType> { it.orderNo ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase(Locale.ENGLISH) })
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPageLoadingState.value = false
            }
        }
    }

    private fun loadReportData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.WorkingPlaceReport.getWorkingPlaceReport(workingPlaceReportId.toString()), token)
                
                districtOrUnitNameState.value = response.optString("districtOrUnitName", "")
                complainNo = response.optInt("complainNo", 0)
                status = response.optInt("status", 0)
                flowLabel = response.optInt("flowLabel", 0)
                reportStatusState.value = status
                reportFlowLabelState.value = flowLabel
                nameState.value = response.optString("name", "")
                bpNumberState.value = response.optString("bpNumber", "")
                designationState.value = response.optString("designation", "")
                homeDistrictState.value = response.optString("homeDistrict", "")
                mainUnitState.value = response.optString("mainUnit", "")
                currentWorkingPlaceState.value = response.optString("currentWorkingPlace", "")
                joiningDateTimeState.value = formatApiDate(response.optString("joiningDateTime", ""))
                briberyLevelState.value = response.optString("briberyLevel", "")
                specialCommentState.value = response.optString("specialComment", "")
                politicalInvolvementState.value = response.optString("politicalInvolvement", "")
                firInformationState.value = response.optString("firInformation", "")
                othersInfoState.value = response.optString("othersInfo", "")
                isSubmittedState.value = response.optBoolean("isSubmit", false)
                applicantPictureState.value = response.optString("picture", null).takeIf { it != "null" }

                // Load job history
                val history = mutableListOf<JobHistoryItem>()
                val previousReports = response.optJSONArray("vmPreviousWorkingPlaceReports") ?: JSONArray()
                if (previousReports.length() > 0) {
                    for (i in 0 until previousReports.length()) {
                        val item = previousReports.optJSONObject(i) ?: continue
                        
                        val itemResponses = mutableMapOf<Int, String>()
                        val itemReceivedArr = item.optJSONArray("vmInformationReceiveds") ?: JSONArray()
                        for (j in 0 until itemReceivedArr.length()) {
                            val receivedObj = itemReceivedArr.optJSONObject(j) ?: continue
                            val typeId = receivedObj.optInt("workingPlaceReceivedFieldTypeId", 0)
                            if (typeId != 0) {
                                itemResponses[typeId] = receivedObj.optString("fieldDetails", "")
                            }
                        }

                        history.add(
                            JobHistoryItem(
                                previousWorkingPlaceReportId = item.optInt("previousWorkingPlaceReportId", 0),
                                workingPlaceReportId = item.optInt("workingPlaceReportId", 0),
                                mainUnit = item.optString("mainUnit", ""),
                                unit = item.optString("unit", ""),
                                subUnit = item.optString("subUnit", ""),
                                rank = item.optString("rank", ""),
                                nameAndAddress = item.optString("workingPlaceNameAndAddress", ""),
                                designation = item.optString("designation", ""),
                                joiningDate = formatApiDate(item.optString("joiningDateTime", "")),
                                leavingDate = formatApiDate(item.optString("leavingDateTime", "")),
                                postingReason = item.optString("postingReason", ""),
                                postingRowKey = item.optString("postingRowKey", "").takeIf { it.isNotBlank() && it != "null" },
                                questionResponses = itemResponses
                            ),
                        )
                    }
                }
                if (history.isEmpty() && response.has("firstWorkingplaceNameAndAddress") && response.optString("firstWorkingplaceNameAndAddress").isNotBlank()) {
                    history.add(
                        JobHistoryItem(
                            nameAndAddress = response.optString("firstWorkingplaceNameAndAddress", ""),
                            designation = response.optString("firstWorkingPlaceDesignation", ""),
                            joiningDate = formatApiDate(response.optString("firstWorkingplaceJoiningDateTime", "")),
                            leavingDate = formatApiDate(response.optString("firstWorkingplaceLeavingDateTime", "")),
                        ),
                    )
                }
                if (history.isEmpty() && response.has("secondWorkingplaceNameAndAddress") && response.optString("secondWorkingplaceNameAndAddress").isNotBlank()) {
                    history.add(
                        JobHistoryItem(
                            nameAndAddress = response.optString("secondWorkingplaceNameAndAddress", ""),
                            designation = response.optString("secondWorkingPlaceDesignation", ""),
                            joiningDate = formatApiDate(response.optString("secondWorkingplaceJoiningDateTime", "")),
                            leavingDate = formatApiDate(response.optString("secondWorkingplaceLeavingDateTime", "")),
                        ),
                    )
                }
                if (history.isEmpty()) history.add(JobHistoryItem())
                jobHistoryListState.value = history

                val receivedInfos = response.optJSONArray("vmInformationReceiveds") ?: JSONArray()
                val responses = mutableMapOf<Int, String>()
                val ids = mutableMapOf<Int, Int>()
                for (i in 0 until receivedInfos.length()) {
                    val info = receivedInfos.getJSONObject(i)
                    val typeId = info.getInt("workingPlaceReceivedFieldTypeId")
                    responses[typeId] = info.optString("fieldDetails", "")
                    ids[typeId] = info.getInt("informationReceivedId")
                }
                questionResponsesState.value = responses
                receivedInfoIdsState.value = ids

                val relationsArr = response.optJSONArray("agentReportRelations") ?: JSONArray()
                val relations = mutableListOf<WorkplaceAgentRelationItem>()
                for (i in 0 until relationsArr.length()) {
                    val item = relationsArr.optJSONObject(i) ?: continue
                    relations.add(
                        WorkplaceAgentRelationItem(
                            agentId = item.optInt("agentId", 0),
                            reportId = item.optInt("reportId", 0),
                            isLead = item.optBoolean("isLead", false),
                            isAcknowledged = item.optBoolean("isAcknowledged", false),
                            acknowledgeDate = item.optString("acknowledgeDate", "").takeIf { it.isNotBlank() && it != "null" },
                            remarks = item.optString("remarks", "").takeIf { it.isNotBlank() && it != "null" },
                            agentName = item.optString("agentName", ""),
                            agentCode = item.optString("agentCode", ""),
                            bpNumber = item.optString("bpNumber", ""),
                        ),
                    )
                }
                agentReportRelationsState.value = relations
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchAgents() {
        val query = agentSearchQueryState.value.trim()
        if (query.isBlank()) {
            agentSearchResultsState.value = emptyList()
            return
        }
        showNoAgentFoundDialogState.value = false
        isSearchingAgentsState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var results: List<WorkplaceAgentSearchResult> = emptyList()
            var showNoAgent = false
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Users.searchAgents(query), token)
                if (response.optInt("statusCode", -1) == 1) {
                    val arr = response.optJSONArray("responseObj") ?: JSONArray()
                    showNoAgent = arr.length() == 0
                    val existingIds = agentReportRelationsState.value.map { it.agentId }.toSet()
                    results =
                        buildList {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val id = item.optInt("userAutoID", 0)
                                if (id == 0 || id in existingIds) continue
                                val name = item.optString("userFullName", "").trim()
                                if (name.isBlank()) continue
                                add(
                                    WorkplaceAgentSearchResult(
                                        userAutoID = id,
                                        userFullName = name,
                                        userCode = item.optString("userCode", "").trim(),
                                        bpNumber = item.optString("bpNumber", "").trim(),
                                    ),
                                )
                            }
                        }
                }
            } catch (_: Exception) {
                results = emptyList()
            } finally {
                agentSearchResultsState.value = results
                showNoAgentFoundDialogState.value = showNoAgent
                isSearchingAgentsState.value = false
            }
        }
    }

    fun dismissNoAgentFoundDialog() {
        showNoAgentFoundDialogState.value = false
    }

    fun addAgentFromSearch(result: WorkplaceAgentSearchResult) {
        if (agentReportRelationsState.value.any { it.agentId == result.userAutoID }) return
        val item =
            WorkplaceAgentRelationItem(
                agentId = result.userAutoID,
                reportId = workingPlaceReportId,
                isLead = false,
                isAcknowledged = false,
                acknowledgeDate = null,
                remarks = null,
                agentName = result.userFullName,
                agentCode = result.userCode,
                bpNumber = result.bpNumber.ifBlank { agentSearchQueryState.value.trim() },
            )
        agentReportRelationsState.value = agentReportRelationsState.value + item
        agentSearchResultsState.value = emptyList()
    }

    fun removeAgentRelation(index: Int) {
        val list = agentReportRelationsState.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            agentReportRelationsState.value = list
        }
    }

    fun updateAgentRelation(index: Int, remarks: String, isAcknowledged: Boolean) {
        val list = agentReportRelationsState.value.toMutableList()
        if (index !in list.indices) return
        val current = list[index]
        list[index] =
            current.copy(
                remarks = remarks.trim().ifBlank { null },
                isAcknowledged = isAcknowledged,
                acknowledgeDate = if (isAcknowledged) LocalDate.now().format(DateTimeFormatter.ISO_DATE) else null,
            )
        agentReportRelationsState.value = list
    }

    fun fetchApplicantInfoByBP() {
        val bp = bpNumberState.value.trim()
        if (bp.isEmpty()) {
            saveMessageState.value = "Please enter a BP Number"
            return
        }

        isSearchingState.value = true
        applicantPictureState.value = null
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Proxy.getApplicantInfoByBPNumber(bp), token)
                
                if (response.optInt("statusCode") == 1) {
                    val responseObj = response.optJSONArray("responseObj")
                    if (responseObj != null && responseObj.length() > 0) {
                        val info = responseObj.getJSONObject(0)
                        
                        districtOrUnitNameState.value = info.optString("unit", "")
                        nameState.value = info.optString("english_name", "")
                        bpNumberState.value = info.optString("bp", bp)
                        designationState.value = info.optString("present_rank", info.optString("joining_rank", ""))
                        homeDistrictState.value = info.optString("home_district", "")
                        mainUnitState.value = info.optString("main_unit", "")
                        currentWorkingPlaceState.value = info.optString("current_place_of_posting", "")
                        joiningDateTimeState.value = formatApiDate(info.optString("date_of_joining", ""))
                        applicantPictureState.value = info.optString("picture", null).takeIf { it != "null" }
                    } else {
                        saveMessageState.value = "No applicant found"
                    }
                } else {
                    saveMessageState.value = "Search failed"
                }
            } catch (e: Exception) {
                saveMessageState.value = "Error: ${e.message}"
            } finally {
                isSearchingState.value = false
            }
        }
    }

    fun updateQuestionResponse(typeId: Int, response: String) {
        val current = questionResponsesState.value.toMutableMap()
        current[typeId] = response
        questionResponsesState.value = current
    }

    fun addJobHistory() {
        jobHistoryListState.value = jobHistoryListState.value + JobHistoryItem()
    }

    fun updateJobQuestionResponse(jobIndex: Int, typeId: Int, response: String) {
        val list = jobHistoryListState.value.toMutableList()
        if (jobIndex !in list.indices) return
        val item = list[jobIndex]
        val updatedResponses = item.questionResponses.toMutableMap()
        updatedResponses[typeId] = response
        list[jobIndex] = item.copy(questionResponses = updatedResponses)
        jobHistoryListState.value = list
    }

    fun removeJobHistory(index: Int) {
        if (jobHistoryListState.value.size > 1) {
            val list = jobHistoryListState.value.toMutableList()
            list.removeAt(index)
            jobHistoryListState.value = list
        }
    }

    fun updateJobHistory(index: Int, item: JobHistoryItem) {
        val list = jobHistoryListState.value.toMutableList()
        list[index] = item
        jobHistoryListState.value = list
    }

    fun onWorkplaceTypeChanged(type: String) {
        selectedWorkplaceTypeState.value = type
        if (type == "Past") {
            fetchPreviousPostingByBP()
        }
    }

    private fun fetchPreviousPostingByBP() {
        val bp = bpNumberState.value.trim()
        if (bp.isBlank() || hasLoadedPostingFromPims) return
        isSearchingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Proxy.getUserPostingDataByBPNumber(bp), token)
                if (response.optInt("statusCode", -1) == 1) {
                    val arr = response.optJSONArray("responseObj") ?: JSONArray()
                    if (arr.length() > 0) {
                        val loaded = mutableListOf<JobHistoryItem>()
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val mainUnit = item.optString("main_unit", "")
                            val unit = item.optString("unit", "")
                            val subUnit = item.optString("sub_unit", "")
                            val subSubUnit = item.optString("sub_sub_unit", "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                            val workName =
                                listOf(mainUnit, unit, subUnit, subSubUnit)
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() && it != "." }
                                    .joinToString(" - ")
                            loaded.add(
                                JobHistoryItem(
                                    mainUnit = mainUnit,
                                    unit = unit,
                                    subUnit = subUnit,
                                    rank = item.optString("rank", ""),
                                    nameAndAddress = workName,
                                    designation = item.optString("designation", "").ifBlank { item.optString("rank", "") },
                                    joiningDate = formatApiDate(item.optString("join_date", "")),
                                    leavingDate = formatApiDate(item.optString("departure_date", "")),
                                    postingReason = item.optString("posting_reson", ""),
                                ),
                            )
                        }
                        if (loaded.isNotEmpty()) {
                            jobHistoryListState.value = loaded
                            hasLoadedPostingFromPims = true
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                isSearchingState.value = false
            }
        }
    }

    private suspend fun saveComplain(): Boolean {
        try {
            val token = settingsStore.get(KEY_AUTH_TOKEN)
            val agentId = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
            val departmentId = settingsStore.get(KEY_DEPARTMENT_ID)?.toIntOrNull() ?: 0
            
            val now = java.time.LocalDateTime.now()
            val recordDateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val recordDateIso = now.format(java.time.format.DateTimeFormatter.ISO_DATE_TIME)

            val body = JSONObject().apply {
                val requestObj = JSONObject().apply {
                    put("complainID", complainId)
                    put("complainNo", 0)
                    put("bpNumber", bpNumberState.value.trim())
                    put("mainUnitName", mainUnitState.value)
                    put("presentRank", designationState.value)
                    put("recordDateStr", recordDateStr)
                    put("agentID", agentId)
                    put("departmentID", departmentId)
                    put("currentPosition", currentWorkingPlaceState.value)
                    put("recordDate", recordDateIso)
                    put("reportType", 3)
                    
                    // Empty arrays/objects as per demo.md
                    put("pimsProfile", JSONObject())
                    put("lstReward", JSONArray())
                    put("lstPunishment", JSONArray())
                    put("lstAttachment", JSONArray())
                    put("complain_ComplainType_Relations", JSONArray())
                }
                put("RequestObj", requestObj)
            }

            val result = httpPostJson(ApiEndpoints.Complain.saveComplain, body, token)
            if (result.optInt("statusCode") == 1 || result.optInt("statusCode") == 200) {
                val responseObj = result.optJSONObject("responseObj")
                if (complainId == 0 && responseObj != null) {
                    complainId = responseObj.optInt("complainID", 0)
                }
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    fun saveReport(isAcknowledgment: Boolean = false, isForwarding: Boolean = false) {
        isSavingState.value = true
        saveSuccessState.value = null
        saveMessageState.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If acknowledgment, we might want to save complain first? 
                // The user said "pressing on it save with (flowLabel: 0, status: 2)"
                // They didn't mention saving complain, but usually it's needed for flow.
                // However, I'll follow the flowLabel/status instruction strictly.
                
                if (isAcknowledgment) {
                    val ok = saveComplain()
                    if (!ok) {
                        saveSuccessState.value = false
                        saveMessageState.value = "Failed to save complain first"
                        isSavingState.value = false
                        return@launch
                    }
                }

                val token = settingsStore.get(KEY_AUTH_TOKEN) ?: ""
                val body = JSONObject().apply {
                    put("workingPlaceReportId", workingPlaceReportId)
                    put("complainId", complainId)
                    put("complainNo", complainNo)
                    
                    // New logic:
                    // First time save: flowLabel = 0, status = 0
                    // If flowLabel == 0 && status == 0, and user clicks "Send for Acknowledgment": flowLabel = 0, status = 2
                    
                    when {
                        isForwarding -> {
                            put("status", 3)
                            put("flowLabel", 1)
                        }
                        isAcknowledgment -> {
                            put("status", 2)
                            put("flowLabel", 0)
                        }
                        else -> {
                            put("status", 0)
                            put("flowLabel", 0)
                        }
                    }
                    put("districtOrUnitName", districtOrUnitNameState.value)
                    put("name", nameState.value)
                    put("bpNumber", bpNumberState.value)
                    put("designation", designationState.value)
                    put("homeDistrict", homeDistrictState.value)
                    put("mainUnit", mainUnitState.value)
                    put("currentWorkingPlace", currentWorkingPlaceState.value)
                    put("joiningDateTime", joiningDateTimeState.value.takeIf { it.isNotBlank() } ?: java.time.LocalDateTime.now().toString())
                    put("briberyLevel", briberyLevelState.value)
                    put("specialComment", specialCommentState.value)
                    put("politicalInvolvement", politicalInvolvementState.value)
                    put("firInformation", firInformationState.value)
                    put("othersInfo", othersInfoState.value)
                    put("isSubmit", false)

                    // Map Job History
                    val history = jobHistoryListState.value
                    if (history.isNotEmpty()) {
                        put("firstWorkingplaceNameAndAddress", history[0].nameAndAddress)
                        put("firstWorkingPlaceDesignation", history[0].designation)
                        put("firstWorkingplaceJoiningDateTime", history[0].joiningDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                        put("firstWorkingplaceLeavingDateTime", history[0].leavingDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    }
                    if (history.size > 1) {
                        put("secondWorkingplaceNameAndAddress", history[1].nameAndAddress)
                        put("secondWorkingPlaceDesignation", history[1].designation)
                        put("secondWorkingplaceJoiningDateTime", history[1].joiningDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                        put("secondWorkingplaceLeavingDateTime", history[1].leavingDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    }

                    val previousReportsArray = JSONArray()
                    history.forEachIndexed { hIdx, item ->
                        val itemReceivedArray = JSONArray()
                        item.questionResponses.forEach { (typeId, details) ->
                            itemReceivedArray.put(
                                JSONObject().apply {
                                    put("informationReceivedId", 0)
                                    put("workingPlaceReceivedFieldTypeId", typeId)
                                    put("fieldDetails", details)
                                    put("workingPlaceReportId", workingPlaceReportId)
                                }
                            )
                        }
                        
                        previousReportsArray.put(
                            JSONObject().apply {
                                put("previousWorkingPlaceReportId", item.previousWorkingPlaceReportId)
                                put("workingPlaceReportId", workingPlaceReportId)
                                put("mainUnit", item.mainUnit)
                                put("unit", item.unit)
                                put("subUnit", item.subUnit)
                                put("rank", item.rank)
                                put("workingPlaceNameAndAddress", item.nameAndAddress)
                                put("designation", item.designation)
                                put("joiningDateTime", item.joiningDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                put("leavingDateTime", item.leavingDate.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                put("postingReason", item.postingReason)
                                put("postingRowKey", item.postingRowKey ?: JSONObject.NULL)
                                put("vmInformationReceiveds", itemReceivedArray)
                            },
                        )
                    }
                    put("vmPreviousWorkingPlaceReports", previousReportsArray)

                    val receivedArray = JSONArray()
                    questionResponsesState.value.forEach { (typeId, details) ->
                        val infoObj = JSONObject().apply {
                            put("informationReceivedId", receivedInfoIdsState.value[typeId] ?: 0)
                            put("workingPlaceReceivedFieldTypeId", typeId)
                            put("fieldDetails", details)
                            put("workingPlaceReportId", workingPlaceReportId)
                        }
                        receivedArray.put(infoObj)
                    }
                    put("vmInformationReceiveds", receivedArray)

                    val creatorId = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
                    val creatorName = settingsStore.get("user_full_name")?.trim().orEmpty()
                    val creatorCode = settingsStore.get("user_code")?.trim().orEmpty()
                    val creatorBp = settingsStore.get("bp_number")?.trim().orEmpty()

                    val creatorRelation = WorkplaceAgentRelationItem(
                        agentId = creatorId,
                        agentName = creatorName,
                        agentCode = creatorCode,
                        bpNumber = creatorBp,
                        isLead = true,
                        isAcknowledged = true,
                        acknowledgeDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    )

                    val otherAgents = agentReportRelationsState.value.filter { it.agentId != creatorId }
                    val combinedRelations = listOf(creatorRelation) + otherAgents

                    val normalizedRelations = combinedRelations.mapIndexed { i, rel ->
                        rel.copy(
                            isLead = i == 0,
                            isAcknowledged = rel.isAcknowledged,
                            acknowledgeDate = when {
                                rel.isAcknowledged && rel.acknowledgeDate.isNullOrBlank() -> LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                                !rel.isAcknowledged -> null
                                else -> rel.acknowledgeDate
                            }
                        )
                    }

                    val relationsArray = JSONArray()
                    normalizedRelations.forEach { relation ->
                        relationsArray.put(
                            JSONObject().apply {
                                put("agentId", relation.agentId)
                                put("reportId", if (workingPlaceReportId == 0) JSONObject.NULL else workingPlaceReportId)
                                put("isLead", relation.isLead)
                                put("isAcknowledged", relation.isAcknowledged)
                                put("acknowledgeDate", relation.acknowledgeDate ?: JSONObject.NULL)
                                put("remarks", relation.remarks ?: "")
                                put("agentName", relation.agentName)
                                put("agentCode", relation.agentCode)
                                put("bpNumber", relation.bpNumber)
                                put("academicReportID", JSONObject.NULL)
                                put("birthPlaceReportID", JSONObject.NULL)
                                put("workPlaceReportID", if (workingPlaceReportId == 0) JSONObject.NULL else workingPlaceReportId)
                            },
                        )
                    }
                    put("agentReportRelations", relationsArray)
                }

                val isUpdate = workingPlaceReportId != 0
                val result =
                    if (isUpdate) {
                        httpPutJson("${ApiEndpoints.WorkingPlaceReport.saveWorkingPlaceReport}/$workingPlaceReportId", body, token)
                    } else {
                        httpPostJson(ApiEndpoints.WorkingPlaceReport.saveWorkingPlaceReport, body, token)
                    }
                val statusCode = result.optInt("statusCode", 0)
                if (result.optInt("workingPlaceReportId", 0) != 0 || statusCode in 200..299) {
                    if (!isUpdate) {
                        workingPlaceReportId = result.optInt("workingPlaceReportId", workingPlaceReportId)
                    }
                    isSubmittedState.value = isAcknowledgment || isForwarding
                    saveSuccessState.value = true
                    saveMessageState.value = when {
                        isForwarding -> "Forwarded to supervisor successfully"
                        isAcknowledgment -> "Submitted successfully"
                        else -> "Saved successfully"
                    }
                } else {
                    saveSuccessState.value = false
                    saveMessageState.value = result.optString("message", "Failed to save")
                }
            } catch (e: Exception) {
                saveSuccessState.value = false
                saveMessageState.value = e.message ?: "An error occurred"
            } finally {
                isSavingState.value = false
            }
        }
    }

    private fun httpGetJson(url: String, token: String?): JSONObject {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }

            ApiHttpLogger.logRequest(method = "GET", url = url, headers = mapOf("Authorization" to conn.getRequestProperty("Authorization")))

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()

            ApiHttpLogger.logResponse(method = "GET", url = url, statusCode = status, bodyText = responseText)

            JSONObject(responseText)
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(method = "GET", url = url, statusCode = 500, bodyText = e.message ?: "")
            JSONObject().put("statusCode", 500).put("message", e.message)
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

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()

            ApiHttpLogger.logResponse(method = "POST", url = url, statusCode = status, bodyText = responseText)

            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (!json.has("statusCode")) json.put("statusCode", status)
            json
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(method = "POST", url = url, statusCode = 500, bodyText = e.message ?: "")
            JSONObject().put("statusCode", 500).put("message", e.message)
        }
    }

    private fun httpPutJson(url: String, body: JSONObject, token: String?): JSONObject {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", if (it.startsWith("Bearer ")) it else "Bearer $it") }
            conn.doOutput = true

            ApiHttpLogger.logRequest(method = "PUT", url = url, bodyText = body.toString())

            conn.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()

            ApiHttpLogger.logResponse(method = "PUT", url = url, statusCode = status, bodyText = responseText)

            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (!json.has("statusCode")) json.put("statusCode", status)
            json
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(method = "PUT", url = url, statusCode = 500, bodyText = e.message ?: "")
            JSONObject().put("statusCode", 500).put("message", e.message)
        }
    }
}
