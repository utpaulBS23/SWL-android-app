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

data class EducationItem(
    val educationId: Int = 0,
    val degreeTypeId: Int? = null,
    val institutionName: String = "",
    val subject: String = "",
    val session: String = "",
    val hallNameAndRoomNumber: String = "",
    val friendsInfo: String = "",
    val politicalInvolvement: Boolean = false,
    val politicalDesignation: String = "",
    val politicalDetails: String = ""
)

data class AcademicAgentSearchResult(
    val userAutoID: Int,
    val userFullName: String,
    val userCode: String,
    val bpNumber: String,
)

data class AcademicAgentRelationItem(
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
class AcademicViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_DEPARTMENT_ID = "department_id"

    // General Info
    val bpNumberState = mutableStateOf("")
    val nameState = mutableStateOf("")
    val designationState = mutableStateOf("")
    val mainUnitState = mutableStateOf("")
    val currentWorkingPlaceState = mutableStateOf("")
    
    val applicantPictureState = mutableStateOf<String?>(null)
    val isSearchingState = mutableStateOf(false)
    val isSearchingAgentsState = mutableStateOf(false)
    val showNoAgentFoundDialogState = mutableStateOf(false)
    val agentSearchQueryState = mutableStateOf("")
    val agentSearchResultsState: MutableState<List<AcademicAgentSearchResult>> = mutableStateOf(emptyList())
    val agentReportRelationsState: MutableState<List<AcademicAgentRelationItem>> = mutableStateOf(emptyList())

    // Dynamic List
    val educationListState: MutableState<List<EducationItem>> = mutableStateOf(listOf(EducationItem()))

    // Dropdown Data
    val degreeTypeListState: MutableState<List<DegreeType>> = mutableStateOf(emptyList())

    val isPageLoadingState = mutableStateOf(false)
    val isSavingState = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    val reportStatusState = mutableStateOf(0)
    val reportFlowLabelState = mutableStateOf(0)
    val currentUserIdState = mutableStateOf(0)

    private var userEducationInfoID = 0
    private var complainId = 0
    private var complainNo = 0
    private var status = 0
    private var flowLabel = 0

    fun initData(eduId: Int, cId: Int) {
        userEducationInfoID = eduId
        complainId = cId
        complainNo = 0
        agentSearchQueryState.value = ""
        agentSearchResultsState.value = emptyList()
        agentReportRelationsState.value = emptyList()
        currentUserIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
        loadDegreeTypes()
        if (userEducationInfoID != 0) {
            loadEducationData()
        }
    }

    private fun loadDegreeTypes() {
        isPageLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.UserPersonalAndFamilyInfo.degreeTypeList, token)
                val items = response.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<DegreeType>()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    list.add(DegreeType(
                        degreeTypeId = obj.getInt("degreeTypeId"),
                        name = obj.getString("name"),
                        orderNo = if (obj.isNull("orderNo")) null else obj.getInt("orderNo"),
                        isActive = obj.getBoolean("isActive")
                    ))
                }
                degreeTypeListState.value = list.filter { it.isActive }
                    .sortedWith(compareBy<DegreeType> { it.orderNo ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase(Locale.ENGLISH) })
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPageLoadingState.value = false
            }
        }
    }

    private fun loadEducationData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Education.getEducationById(userEducationInfoID), token)
                
                bpNumberState.value = response.optString("bpNumber", "")
                complainNo = response.optInt("complainNo", 0)
                status = response.optInt("status", 0)
                flowLabel = response.optInt("flowLabel", 0)
                reportStatusState.value = status
                reportFlowLabelState.value = flowLabel

                // If bp is present, fetch applicant info to fill name, rank etc.
                if (bpNumberState.value.isNotBlank()) {
                    fetchApplicantInfoByBP(bpNumberState.value)
                }

                val eduArray = response.optJSONArray("educations") ?: JSONArray()
                val eduList = mutableListOf<EducationItem>()
                for (i in 0 until eduArray.length()) {
                    val obj = eduArray.getJSONObject(i)
                    eduList.add(EducationItem(
                        educationId = obj.optInt("educationId"),
                        degreeTypeId = obj.optInt("degreeTypeId").takeIf { it != 0 },
                        institutionName = obj.optString("institutionName", ""),
                        subject = obj.optString("subject", ""),
                        session = obj.optString("session", ""),
                        hallNameAndRoomNumber = obj.optString("hallNameAndRoomNumber", ""),
                        friendsInfo = obj.optString("friendsInfo", ""),
                        politicalInvolvement = obj.optBoolean("politicalInvolvement", false),
                        politicalDesignation = obj.optString("politicalDesignation", ""),
                        politicalDetails = obj.optString("politicalDetails", "")
                    ))
                }
                if (eduList.isEmpty()) eduList.add(EducationItem())
                educationListState.value = eduList

                val relationsArr = response.optJSONArray("agentReportRelations") ?: JSONArray()
                val relations = mutableListOf<AcademicAgentRelationItem>()
                for (i in 0 until relationsArr.length()) {
                    val item = relationsArr.optJSONObject(i) ?: continue
                    relations.add(
                        AcademicAgentRelationItem(
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

    fun fetchApplicantInfoByBP(bp: String = bpNumberState.value.trim()) {
        if (bp.isEmpty()) return

        isSearchingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Proxy.getApplicantInfoByBPNumber(bp), token)
                
                if (response.optInt("statusCode") == 1) {
                    val responseObj = response.optJSONArray("responseObj")
                    if (responseObj != null && responseObj.length() > 0) {
                        val info = responseObj.getJSONObject(0)
                        nameState.value = info.optString("english_name", "")
                        designationState.value = info.optString("present_rank", info.optString("joining_rank", ""))
                        mainUnitState.value = info.optString("main_unit", "")
                        currentWorkingPlaceState.value = info.optString("current_place_of_posting", "")
                        applicantPictureState.value = info.optString("picture", null).takeIf { it != "null" }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearchingState.value = false
            }
        }
    }

    fun addEducation() {
        educationListState.value = educationListState.value + EducationItem()
    }

    fun removeEducation(index: Int) {
        if (educationListState.value.size > 1) {
            val list = educationListState.value.toMutableList()
            list.removeAt(index)
            educationListState.value = list
        }
    }

    fun updateEducation(index: Int, item: EducationItem) {
        val list = educationListState.value.toMutableList()
        list[index] = item
        educationListState.value = list
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
            var results: List<AcademicAgentSearchResult> = emptyList()
            var showNoAgent = false
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.Users.searchAgents(query), token)
                if (response.optInt("statusCode", -1) == 1) {
                    val arr = response.optJSONArray("responseObj") ?: JSONArray()
                    showNoAgent = arr.length() == 0
                    val existingIds = agentReportRelationsState.value.map { it.agentId }.toSet()
                    results = buildList {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val id = item.optInt("userAutoID", 0)
                            if (id == 0 || id in existingIds) continue
                            val name = item.optString("userFullName", "").trim()
                            if (name.isBlank()) continue
                            add(
                                AcademicAgentSearchResult(
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

    fun addAgentFromSearch(result: AcademicAgentSearchResult) {
        if (agentReportRelationsState.value.any { it.agentId == result.userAutoID }) return
        agentReportRelationsState.value =
            agentReportRelationsState.value +
                AcademicAgentRelationItem(
                    agentId = result.userAutoID,
                    reportId = userEducationInfoID,
                    isLead = false,
                    isAcknowledged = false,
                    acknowledgeDate = null,
                    remarks = null,
                    agentName = result.userFullName,
                    agentCode = result.userCode,
                    bpNumber = result.bpNumber.ifBlank { agentSearchQueryState.value.trim() },
                )
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
                    put("complainID", 0) // Reset to 0 as per demo logic
                    put("complainNo", 0)
                    put("bpNumber", bpNumberState.value.trim())
                    put("mainUnitName", mainUnitState.value)
                    put("presentRank", designationState.value)
                    put("recordDateStr", recordDateStr)
                    put("agentID", agentId)
                    put("departmentID", departmentId)
                    put("currentPosition", currentWorkingPlaceState.value)
                    put("recordDate", recordDateIso)
                    put("reportType", 4) // Academic Report Type
                    
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
                if (isAcknowledgment) {
                    val ok = saveComplain()
                    if (!ok) {
                        saveSuccessState.value = false
                        saveMessageState.value = "Failed to save complain"
                        isSavingState.value = false
                        return@launch
                    }
                }

                val token = settingsStore.get(KEY_AUTH_TOKEN) ?: ""
                val body = JSONObject().apply {
                    put("userEducationInfoID", userEducationInfoID)
                    put("bpNumber", bpNumberState.value.trim())
                    put("isSubmit", false)
                    put("complainId", if (complainId == 0) JSONObject.NULL else complainId)
                    put("complainNo", if (complainNo == 0) JSONObject.NULL else complainNo)
                    
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
                    
                    put("isDeleted", false)
                    put("isSubmitted", JSONObject.NULL)
                    put("createdAt", java.time.LocalDateTime.now().toString())
                    put("updatedAt", "0001-01-01T00:00:00")
                    put("submittedStatus", JSONObject.NULL)

                    val eduArray = JSONArray()
                    educationListState.value.forEach { item ->
                        val obj = JSONObject().apply {
                            put("educationId", item.educationId)
                            put("userEducationInfoID", userEducationInfoID)
                            put("degreeTypeId", item.degreeTypeId ?: 0)
                            put("institutionName", item.institutionName)
                            put("subject", item.subject)
                            put("session", item.session)
                            put("hallNameAndRoomNumber", item.hallNameAndRoomNumber)
                            put("friendsInfo", item.friendsInfo)
                            put("politicalInvolvement", item.politicalInvolvement.toString())
                        }
                        eduArray.put(obj)
                    }
                    put("educations", eduArray)

                    val creatorId = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
                    val creatorName = settingsStore.get("user_full_name")?.trim().orEmpty()
                    val creatorCode = settingsStore.get("user_code")?.trim().orEmpty()
                    val creatorBp = settingsStore.get("bp_number")?.trim().orEmpty()

                    val creatorRelation = AcademicAgentRelationItem(
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
                                put("reportId", if (userEducationInfoID == 0) JSONObject.NULL else userEducationInfoID)
                                put("isLead", relation.isLead)
                                put("isAcknowledged", relation.isAcknowledged)
                                put("acknowledgeDate", relation.acknowledgeDate ?: JSONObject.NULL)
                                put("remarks", relation.remarks ?: "")
                                put("agentName", relation.agentName)
                                put("agentCode", relation.agentCode)
                                put("bpNumber", relation.bpNumber)
                                put("academicReportID", if (userEducationInfoID == 0) JSONObject.NULL else userEducationInfoID)
                                put("birthPlaceReportID", JSONObject.NULL)
                                put("workPlaceReportID", JSONObject.NULL)
                            },
                        )
                    }
                    put("agentReportRelations", relationsArray)
                }

                android.util.Log.d("AcademicSave", "Request body: $body")

                val result = httpPostJson(ApiEndpoints.Education.saveEducation, body, token)
                android.util.Log.d("AcademicSave", "Server response: $result")
                
                val statusCode = result.optInt("statusCode", 0)
                if (result.optInt("userEducationInfoID", 0) != 0 || statusCode in 200..299) {
                    val newEduId = result.optInt("userEducationInfoID", 0)
                    if (userEducationInfoID == 0 && newEduId != 0) {
                        userEducationInfoID = newEduId
                    }
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
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            JSONObject(responseText)
        } catch (e: Exception) {
            JSONObject()
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
            conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (!json.has("statusCode")) json.put("statusCode", status)
            json
        } catch (e: Exception) {
            JSONObject()
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
            conn.outputStream.use { os -> os.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (!json.has("statusCode")) json.put("statusCode", status)
            json
        } catch (e: Exception) {
            JSONObject()
        }
    }
}
