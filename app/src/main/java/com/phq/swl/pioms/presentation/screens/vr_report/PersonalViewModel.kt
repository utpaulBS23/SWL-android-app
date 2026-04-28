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

data class DegreeType(
    val degreeTypeId: Int,
    val name: String,
    val orderNo: Int?,
    val isActive: Boolean
)

data class RelationType(
    val relationTypeId: Int,
    val name: String,
    val orderNo: Int?,
    val isActive: Boolean
)

data class FamilyInfoItem(
    val userFamilyInformationId: Int = 0,
    val relationTypeId: Int? = null,
    val relationshipDetails: String = "",
    val occupationDetails: String = "",
    val isPoliticalInvolvement: Boolean = false,
    val politicalDesignation: String = "",
    val politicalDetailsInfo: String = ""
)

data class ResidentialAddressItem(
    val residentialAddressPastFiveYearId: Int = 0,
    val address: String = "",
    val startDateTime: String = "",
    val endDateTime: String = ""
)

data class EducationalQualificationItem(
    val userEducationalQualificationId: Int = 0,
    val degreeTypeId: Int? = null,
    val groupOrSubject: String = "",
    val result: String = "",
    val boardOrUniversity: String = ""
)

data class PersonalJobHistoryItem(
    val nameAndAddress: String = "",
    val designation: String = "",
    val joiningDate: String = "",
    val leavingDate: String = ""
)

data class PersonalAgentSearchResult(
    val userAutoID: Int,
    val userFullName: String,
    val userCode: String,
    val bpNumber: String,
)

data class PersonalAgentRelationItem(
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
class PersonalViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_DEPARTMENT_ID = "department_id"

    // General Personal Info
    val nameState = mutableStateOf("")
    val pscRankState = mutableStateOf("")
    val bpNumberState = mutableStateOf("")
    val batchState = mutableStateOf("")
    val designationState = mutableStateOf("")
    val joiningDateTimeState = mutableStateOf("")
    val currentWorkingPlaceState = mutableStateOf("")
    val permanentAddressState = mutableStateOf("")
    val homeDistrictState = mutableStateOf("")
    val dateOfBirthState = mutableStateOf("")
    val nidState = mutableStateOf("")
    val phoneNumberState = mutableStateOf("")
    val emailState = mutableStateOf("")
    val politicalInvolvementState = mutableStateOf("")

    val applicantPictureState = mutableStateOf<String?>(null)
    val isSearchingState = mutableStateOf(false)
    val isSearchingAgentsState = mutableStateOf(false)
    val showNoAgentFoundDialogState = mutableStateOf(false)
    val agentSearchQueryState = mutableStateOf("")
    val agentSearchResultsState: MutableState<List<PersonalAgentSearchResult>> = mutableStateOf(emptyList())
    val agentReportRelationsState: MutableState<List<PersonalAgentRelationItem>> = mutableStateOf(emptyList())

    // Lists for dynamic sections
    val familyInfoListState: MutableState<List<FamilyInfoItem>> = mutableStateOf(listOf(FamilyInfoItem()))
    val residentialAddressListState: MutableState<List<ResidentialAddressItem>> = mutableStateOf(listOf(ResidentialAddressItem()))
    val educationalQualificationListState: MutableState<List<EducationalQualificationItem>> = mutableStateOf(listOf(EducationalQualificationItem()))
    val jobHistoryListState: MutableState<List<PersonalJobHistoryItem>> = mutableStateOf(listOf(PersonalJobHistoryItem()))

    // Dropdown Data
    val degreeTypeListState: MutableState<List<DegreeType>> = mutableStateOf(emptyList())
    val relationTypeListState: MutableState<List<RelationType>> = mutableStateOf(emptyList())

    val isPageLoadingState = mutableStateOf(false)
    val isSavingState = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    val reportStatusState = mutableStateOf(0)
    val reportFlowLabelState = mutableStateOf(0)
    val isSubmittedState = mutableStateOf(false)
    val currentUserIdState = mutableStateOf(0)

    private var userInfoId = 0
    private var complainId = 0
    private var complainNo = 0
    private var status = 0
    private var flowLabel = 0

    private fun formatApiDate(value: String): String {
        if (value.isBlank() || value == "null") return ""
        return value.substringBefore("T")
    }

    private fun normalizeDateForBackend(input: String?): Any {
        if (input.isNullOrBlank()) return JSONObject.NULL
        val trimmed = input.trim().substringBefore("T")
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
            return "$trimmed-01-01"
        }
        if (trimmed.length == 7 && trimmed.matches(Regex("\\d{4}-\\d{2}"))) {
            return "$trimmed-01"
        }
        if (trimmed.length == 10 && trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return trimmed
        }
        return trimmed
    }

    fun initData(uId: Int, cId: Int) {
        userInfoId = uId
        complainId = cId
        complainNo = 0
        agentSearchQueryState.value = ""
        agentSearchResultsState.value = emptyList()
        agentReportRelationsState.value = emptyList()
        currentUserIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
        loadDropdownData()
        if (userInfoId != 0) {
            loadPersonalData()
        }
    }

    private fun loadDropdownData() {
        isPageLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                
                // Load Degree Types
                val degreeResponse = httpGetJson(ApiEndpoints.UserPersonalAndFamilyInfo.degreeTypeList, token)
                val degreeItems = degreeResponse.optJSONArray("items") ?: JSONArray()
                val degreeList = mutableListOf<DegreeType>()
                for (i in 0 until degreeItems.length()) {
                    val obj = degreeItems.getJSONObject(i)
                    degreeList.add(DegreeType(
                        degreeTypeId = obj.getInt("degreeTypeId"),
                        name = obj.getString("name"),
                        orderNo = if (obj.isNull("orderNo")) null else obj.getInt("orderNo"),
                        isActive = obj.getBoolean("isActive")
                    ))
                }
                degreeTypeListState.value = degreeList.filter { it.isActive }
                    .sortedWith(compareBy<DegreeType> { it.orderNo ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase(Locale.ENGLISH) })

                // Load Relation Types
                val relationResponse = httpGetJson(ApiEndpoints.UserPersonalAndFamilyInfo.relationTypeList, token)
                val relationItems = relationResponse.optJSONArray("items") ?: JSONArray()
                val relationList = mutableListOf<RelationType>()
                for (i in 0 until relationItems.length()) {
                    val obj = relationItems.getJSONObject(i)
                    relationList.add(RelationType(
                        relationTypeId = obj.getInt("relationTypeId"),
                        name = obj.getString("name"),
                        orderNo = if (obj.isNull("orderNo")) null else obj.getInt("orderNo"),
                        isActive = obj.getBoolean("isActive")
                    ))
                }
                relationTypeListState.value = relationList.filter { it.isActive }
                    .sortedWith(compareBy<RelationType> { it.orderNo ?: Int.MAX_VALUE }
                        .thenBy { it.name.lowercase(Locale.ENGLISH) })

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPageLoadingState.value = false
            }
        }
    }

    private fun loadPersonalData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = settingsStore.get(KEY_AUTH_TOKEN)
                val response = httpGetJson(ApiEndpoints.UserPersonalAndFamilyInfo.getUserPersonalAndFamilyInfo(userInfoId.toString()), token)
                
                nameState.value = response.optString("name", "")
                complainNo = response.optInt("complainNo", 0)
                status = response.optInt("status", 0)
                flowLabel = response.optInt("flowLabel", 0)
                reportStatusState.value = status
                reportFlowLabelState.value = flowLabel
                pscRankState.value = response.optString("pscRank", "")
                bpNumberState.value = response.optString("bpNumber", "")
                batchState.value = response.optString("batch", "")
                designationState.value = response.optString("designation", "")
                joiningDateTimeState.value = formatApiDate(response.optString("joiningDateTime", ""))
                currentWorkingPlaceState.value = response.optString("currentWorkingPlace", "")
                permanentAddressState.value = response.optString("permanentAddress", "")
                homeDistrictState.value = response.optString("homeDistrict", "")
                dateOfBirthState.value = formatApiDate(response.optString("dateOfBirth", ""))
                nidState.value = response.optString("nid", "")
                phoneNumberState.value = response.optString("phone", "").takeIf { it.isNotBlank() && it != "null" } ?: response.optString("phoneNumber", "")
                emailState.value = response.optString("email", "")
                politicalInvolvementState.value = response.optString("politicalInvolvement", "")
                isSubmittedState.value = response.optBoolean("isSubmit", false)
                applicantPictureState.value = response.optString("picture", null).takeIf { it != "null" }

                // Load Family Info
                val familyArray = response.optJSONArray("vmUserFamilyInformations") ?: JSONArray()
                val familyList = mutableListOf<FamilyInfoItem>()
                for (i in 0 until familyArray.length()) {
                    val obj = familyArray.getJSONObject(i)
                    familyList.add(FamilyInfoItem(
                        userFamilyInformationId = obj.optInt("userFamilyInformationId"),
                        relationTypeId = obj.optInt("relationTypeId").takeIf { it != 0 },
                        relationshipDetails = obj.optString("relationshipDetails", ""),
                        occupationDetails = obj.optString("occupationDetails", ""),
                        isPoliticalInvolvement = obj.optBoolean("isPoliticalInvolvement", false),
                        politicalDesignation = obj.optString("politicalDesignation", ""),
                        politicalDetailsInfo = obj.optString("politicalDetailsInfo", "")
                    ))
                }
                if (familyList.isEmpty()) familyList.add(FamilyInfoItem())
                familyInfoListState.value = familyList

                // Load Residential Addresses
                val addressArray = response.optJSONArray("vmResidentialAddressPastFiveYears") ?: JSONArray()
                val addressList = mutableListOf<ResidentialAddressItem>()
                for (i in 0 until addressArray.length()) {
                    val obj = addressArray.getJSONObject(i)
                    addressList.add(ResidentialAddressItem(
                        residentialAddressPastFiveYearId = obj.optInt("residentialAddressPastFiveYearId"),
                        address = obj.optString("address", ""),
                        startDateTime = obj.optString("startDateTime", ""),
                        endDateTime = obj.optString("endDateTime", "")
                    ))
                }
                if (addressList.isEmpty()) addressList.add(ResidentialAddressItem())
                residentialAddressListState.value = addressList

                // Load Educational Qualifications
                val educationArray = response.optJSONArray("vmUserEducationalQualifications") ?: JSONArray()
                val educationList = mutableListOf<EducationalQualificationItem>()
                for (i in 0 until educationArray.length()) {
                    val obj = educationArray.getJSONObject(i)
                    educationList.add(EducationalQualificationItem(
                        userEducationalQualificationId = obj.optInt("userEducationalQualificationId"),
                        degreeTypeId = obj.optInt("degreeTypeId").takeIf { it != 0 },
                        groupOrSubject = obj.optString("groupOrSubject", ""),
                        result = obj.optString("result", ""),
                        boardOrUniversity = obj.optString("boardOrUniversity", "")
                    ))
                }
                if (educationList.isEmpty()) educationList.add(EducationalQualificationItem())
                educationalQualificationListState.value = educationList

                // Load Job History
                val jobHistoryList = mutableListOf<PersonalJobHistoryItem>()
                if (response.optString("firstWorkingplaceNameAndAddress").isNotBlank()) {
                    jobHistoryList.add(
                        PersonalJobHistoryItem(
                            nameAndAddress = response.optString("firstWorkingplaceNameAndAddress", ""),
                            designation = response.optString("firstWorkingPlaceDesignation", ""),
                            joiningDate = response.optString("firstWorkingplaceJoiningDateTime", ""),
                            leavingDate = response.optString("firstWorkingplaceLeavingDateTime", "")
                        )
                    )
                }
                if (response.optString("secondWorkingplaceNameAndAddress").isNotBlank()) {
                    jobHistoryList.add(
                        PersonalJobHistoryItem(
                            nameAndAddress = response.optString("secondWorkingplaceNameAndAddress", ""),
                            designation = response.optString("secondWorkingPlaceDesignation", ""),
                            joiningDate = response.optString("secondWorkingplaceJoiningDateTime", ""),
                            leavingDate = response.optString("secondWorkingplaceLeavingDateTime", "")
                        )
                    )
                }
                if (jobHistoryList.isEmpty()) jobHistoryList.add(PersonalJobHistoryItem())
                jobHistoryListState.value = jobHistoryList

                val relationsArr = response.optJSONArray("agentReportRelations") ?: JSONArray()
                val relations = mutableListOf<PersonalAgentRelationItem>()
                for (i in 0 until relationsArr.length()) {
                    val item = relationsArr.optJSONObject(i) ?: continue
                    relations.add(
                        PersonalAgentRelationItem(
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
                        
                        nameState.value = info.optString("english_name", "")
                        bpNumberState.value = info.optString("bp", bp)
                        designationState.value = info.optString("present_rank", info.optString("joining_rank", ""))
                        homeDistrictState.value = info.optString("home_district", "")
                        currentWorkingPlaceState.value = info.optString("current_place_of_posting", "")
                        joiningDateTimeState.value = formatApiDate(info.optString("date_of_joining", ""))
                        dateOfBirthState.value = formatApiDate(info.optString("date_of_birth", ""))
                        phoneNumberState.value = info.optString("phone", "").takeIf { it.isNotBlank() && it != "null" } ?: info.optString("mobile", "")
                        permanentAddressState.value = info.optString("permanent_address", "")
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

    fun addFamilyInfo() {
        familyInfoListState.value = familyInfoListState.value + FamilyInfoItem()
    }

    fun removeFamilyInfo(index: Int) {
        if (familyInfoListState.value.size > 1) {
            val list = familyInfoListState.value.toMutableList()
            list.removeAt(index)
            familyInfoListState.value = list
        }
    }

    fun updateFamilyInfo(index: Int, item: FamilyInfoItem) {
        val list = familyInfoListState.value.toMutableList()
        list[index] = item
        familyInfoListState.value = list
    }

    fun addResidentialAddress() {
        residentialAddressListState.value = residentialAddressListState.value + ResidentialAddressItem()
    }

    fun removeResidentialAddress(index: Int) {
        if (residentialAddressListState.value.size > 1) {
            val list = residentialAddressListState.value.toMutableList()
            list.removeAt(index)
            residentialAddressListState.value = list
        }
    }

    fun updateResidentialAddress(index: Int, item: ResidentialAddressItem) {
        val list = residentialAddressListState.value.toMutableList()
        list[index] = item
        residentialAddressListState.value = list
    }

    fun addEducationalQualification() {
        educationalQualificationListState.value = educationalQualificationListState.value + EducationalQualificationItem()
    }

    fun removeEducationalQualification(index: Int) {
        if (educationalQualificationListState.value.size > 1) {
            val list = educationalQualificationListState.value.toMutableList()
            list.removeAt(index)
            educationalQualificationListState.value = list
        }
    }

    fun updateEducationalQualification(index: Int, item: EducationalQualificationItem) {
        val list = educationalQualificationListState.value.toMutableList()
        list[index] = item
        educationalQualificationListState.value = list
    }

    fun addJobHistory() {
        jobHistoryListState.value = jobHistoryListState.value + PersonalJobHistoryItem()
    }

    fun removeJobHistory(index: Int) {
        if (jobHistoryListState.value.size > 1) {
            val list = jobHistoryListState.value.toMutableList()
            list.removeAt(index)
            jobHistoryListState.value = list
        }
    }

    fun updateJobHistory(index: Int, item: PersonalJobHistoryItem) {
        val list = jobHistoryListState.value.toMutableList()
        list[index] = item
        jobHistoryListState.value = list
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
            var results: List<PersonalAgentSearchResult> = emptyList()
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
                                PersonalAgentSearchResult(
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

    fun addAgentFromSearch(result: PersonalAgentSearchResult) {
        if (agentReportRelationsState.value.any { it.agentId == result.userAutoID }) return
        agentReportRelationsState.value =
            agentReportRelationsState.value +
                PersonalAgentRelationItem(
                    agentId = result.userAutoID,
                    reportId = userInfoId,
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
                    put("complainID", complainId)
                    put("complainNo", 0)
                    put("bpNumber", bpNumberState.value.trim())
                    put("mainUnitName", "")
                    put("presentRank", designationState.value)
                    put("recordDateStr", recordDateStr)
                    put("agentID", agentId)
                    put("departmentID", departmentId)
                    put("currentPosition", currentWorkingPlaceState.value)
                    put("recordDate", recordDateIso)
                    put("reportType", 2) // Personal Report Type
                    
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
                        saveMessageState.value = "Failed to save complain first"
                        isSavingState.value = false
                        return@launch
                    }
                }

                val token = settingsStore.get(KEY_AUTH_TOKEN) ?: ""
                val body = JSONObject().apply {
                    put("userInfoId", userInfoId)
                    put("complainId", complainId)
                    put("complainNo", complainNo)
                    
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
                    
                    put("name", nameState.value)
                    put("pscRank", pscRankState.value.toIntOrNull() ?: 0)
                    put("bpNumber", bpNumberState.value)
                    put("batch", batchState.value)
                    put("designation", designationState.value)
                    put("joiningDateTime", normalizeDateForBackend(joiningDateTimeState.value))
                    put("currentWorkingPlace", currentWorkingPlaceState.value)
                    put("permanentAddress", permanentAddressState.value)
                    put("homeDistrict", homeDistrictState.value)
                    put("dateOfBirth", normalizeDateForBackend(dateOfBirthState.value))
                    put("nid", nidState.value)
                    put("phoneNumber", phoneNumberState.value)
                    put("email", emailState.value)
                    put("politicalInvolvement", politicalInvolvementState.value)
                    put("isSubmit", false)

                    // Map Family Info
                    val familyArray = JSONArray()
                    familyInfoListState.value.forEach { item ->
                        val obj = JSONObject().apply {
                            put("userFamilyInformationId", item.userFamilyInformationId)
                            put("userInfoId", userInfoId)
                            put("relationTypeId", item.relationTypeId ?: 0)
                            put("relationshipDetails", item.relationshipDetails)
                            put("occupationDetails", item.occupationDetails)
                            put("isPoliticalInvolvement", item.isPoliticalInvolvement)
                            put("politicalDesignation", item.politicalDesignation)
                            put("politicalDetailsInfo", item.politicalDetailsInfo)
                        }
                        familyArray.put(obj)
                    }
                    put("vmUserFamilyInformations", familyArray)

                    // Map Residential Address
                    val addressArray = JSONArray()
                    residentialAddressListState.value.forEach { item ->
                        val obj = JSONObject().apply {
                            put("residentialAddressPastFiveYearId", item.residentialAddressPastFiveYearId)
                            put("userInfoId", userInfoId)
                            put("address", item.address)
                            put("startDateTime", normalizeDateForBackend(item.startDateTime))
                            put("endDateTime", normalizeDateForBackend(item.endDateTime))
                        }
                        addressArray.put(obj)
                    }
                    put("vmResidentialAddressPastFiveYears", addressArray)

                    // Map Educational Qualifications
                    val educationArray = JSONArray()
                    educationalQualificationListState.value.forEach { item ->
                        val obj = JSONObject().apply {
                            put("userEducationalQualificationId", item.userEducationalQualificationId)
                            put("userInfoId", userInfoId)
                            put("degreeTypeId", item.degreeTypeId ?: 0)
                            put("groupOrSubject", item.groupOrSubject)
                            put("result", item.result)
                            put("boardOrUniversity", item.boardOrUniversity)
                        }
                        educationArray.put(obj)
                    }
                    put("vmUserEducationalQualifications", educationArray)

                    val history = jobHistoryListState.value
                    if (history.isNotEmpty()) {
                        put("firstWorkingplaceNameAndAddress", history[0].nameAndAddress)
                        put("firstWorkingPlaceDesignation", history[0].designation)
                        put("firstWorkingplaceJoiningDateTime", normalizeDateForBackend(history[0].joiningDate))
                        put("firstWorkingplaceLeavingDateTime", normalizeDateForBackend(history[0].leavingDate))
                    }
                    if (history.size > 1) {
                        put("secondWorkingplaceNameAndAddress", history[1].nameAndAddress)
                        put("secondWorkingPlaceDesignation", history[1].designation)
                        put("secondWorkingplaceJoiningDateTime", normalizeDateForBackend(history[1].joiningDate))
                        put("secondWorkingplaceLeavingDateTime", normalizeDateForBackend(history[1].leavingDate))
                    }

                    val creatorId = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0
                    val creatorName = settingsStore.get("user_full_name")?.trim().orEmpty()
                    val creatorCode = settingsStore.get("user_code")?.trim().orEmpty()
                    val creatorBp = settingsStore.get("bp_number")?.trim().orEmpty()

                    val creatorRelation = PersonalAgentRelationItem(
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
                                put("reportId", if (userInfoId == 0) JSONObject.NULL else userInfoId)
                                put("isLead", relation.isLead)
                                put("isAcknowledged", relation.isAcknowledged)
                                put("acknowledgeDate", relation.acknowledgeDate ?: JSONObject.NULL)
                                put("remarks", relation.remarks ?: "")
                                put("agentName", relation.agentName)
                                put("agentCode", relation.agentCode)
                                put("bpNumber", relation.bpNumber)
                                put("academicReportID", JSONObject.NULL)
                                put("birthPlaceReportID", if (userInfoId == 0) JSONObject.NULL else userInfoId)
                                put("workPlaceReportID", JSONObject.NULL)
                            },
                        )
                    }
                    put("agentReportRelations", relationsArray)
                }

                val isUpdate = userInfoId != 0
                val result =
                    if (isUpdate) {
                        httpPutJson("${ApiEndpoints.UserPersonalAndFamilyInfo.saveUserPersonalAndFamilyInfo}/$userInfoId", body, token)
                    } else {
                        httpPostJson(ApiEndpoints.UserPersonalAndFamilyInfo.saveUserPersonalAndFamilyInfo, body, token)
                    }
                val statusCode = result.optInt("statusCode", 0)
                if (result.optInt("userInfoId", 0) != 0 || statusCode in 200..299) {
                    if (!isUpdate) {
                        userInfoId = result.optInt("userInfoId", userInfoId)
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
