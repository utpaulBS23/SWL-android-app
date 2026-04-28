package com.phq.swl.pioms.presentation.screens.special_report

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun normalizeServerText(value: String): String = value.replace("+", " ").trim()

private fun ensureBpCivPrefix(value: String): String {
    val compact = value.trim().replace(" ", "")
    if (compact.isBlank()) return ""
    return when {
        compact.startsWith("BP", ignoreCase = true) -> "BP" + compact.substring(2)
        compact.startsWith("CIV", ignoreCase = true) -> "CIV" + compact.substring(3)
        else -> "BP$compact"
    }
}

data class SpecialReportComplainType(
    val id: Int,
    val title: String,
    val orderNo: Int?,
    val isActive: Boolean,
)

data class AllegationItem(
    val id: Int = 0,
    val complainTypeId: Int? = null,
    val complainComplainTypeRelationID: Int = 0,
    val title: String = "",
    val details: String = "",
    val witness: String = "",
    val attachments: List<AttachmentItem> = emptyList()
)

data class AttachmentItem(
    val id: Int = 0,
    val title: String = "",
    val uri: Uri? = null,
    val file: File? = null,
    val filePath: String? = null
)

data class AgentSearchResult(
    val userAutoID: Int,
    val userFullName: String,
    val userCode: String,
    val bpNumber: String,
)

data class AgentReportRelationItem(
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

data class AccusedItem(
    val specialReportAccusedId: Int = 0,
    val englishName: String = "",
    val banglaName: String = "",
    val bpNumber: String = "",
    val designation: String = "",
    val currentWorkingPlace: String? = null,
    val joiningDateTime: String? = null,
    val previousWorkPlace: String? = null,
    val homeDistrict: String? = null,
    val phoneNumber: String? = null,
    val punishment: String? = null,
    val reward: String? = null,
    val previousIrregularity: String? = null,
    val specialReportId: Int = 0,
    val is_main: Boolean = false
)

@KoinViewModel
class SpecialReportViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"

    val reportTitleState: MutableState<String> = mutableStateOf("")
    val englishNameState: MutableState<String> = mutableStateOf("")
    val banglaNameState: MutableState<String> = mutableStateOf("")
    val bpNumberState: MutableState<String> = mutableStateOf("")
    val designationState: MutableState<String> = mutableStateOf("")
    val currentWorkplaceState: MutableState<String> = mutableStateOf("")
    val joiningDateState: MutableState<String> = mutableStateOf("")
    val previousWorkplaceState: MutableState<String> = mutableStateOf("")
    val homeDistrictState: MutableState<String> = mutableStateOf("")
    val mobileState: MutableState<String> = mutableStateOf("")
    val punishmentState: MutableState<String> = mutableStateOf("")
    val rewardState: MutableState<String> = mutableStateOf("")
    val irregularitiesState: MutableState<String> = mutableStateOf("")
    val othersInfoState: MutableState<String> = mutableStateOf("")
    val reportSummaryState: MutableState<String> = mutableStateOf("")
    val agentRecommendationState: MutableState<String> = mutableStateOf("")

    val agentSearchQueryState: MutableState<String> = mutableStateOf("")
    val agentSearchResultsState: MutableState<List<AgentSearchResult>> = mutableStateOf(emptyList())
    val isSearchingAgentsState: MutableState<Boolean> = mutableStateOf(false)
    val showNoAgentFoundDialogState: MutableState<Boolean> = mutableStateOf(false)
    val agentReportRelationsState: MutableState<List<AgentReportRelationItem>> = mutableStateOf(emptyList())

    val allegationsState: MutableState<List<AllegationItem>> = mutableStateOf(listOf(AllegationItem()))
    val accusedListState: MutableState<List<AccusedItem>> = mutableStateOf(emptyList())
    val complainTypesState: MutableState<List<SpecialReportComplainType>> = mutableStateOf(emptyList())
    val isLoadingComplainTypesState: MutableState<Boolean> = mutableStateOf(false)

    val isSavingState: MutableState<Boolean> = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    
    val isLoadingReportState: MutableState<Boolean> = mutableStateOf(false)
    val reportStatusState = mutableStateOf(0)
    val reportFlowLabelState = mutableStateOf(0)
    val currentUserIdState = mutableStateOf(0)
    val isSubmittedState = mutableStateOf(false)

    private var complainId: Int = 0
    private var complainNo: String = ""
    private var specialReportId: Int = 0
    private var status: Int = 0
    private var flowLabel: Int = 0
    private var createAt: String? = null
    private var createdByUserId: Int? = null
    private var updatedAt: String? = null
    private var isDeleted: Boolean = false

    fun initData(id: Int, no: String, applicantJson: String? = null) {
        complainId = id
        complainNo = no
        agentReportRelationsState.value = emptyList()
        agentSearchQueryState.value = ""
        agentSearchResultsState.value = emptyList()
        showNoAgentFoundDialogState.value = false
        currentUserIdState.value = settingsStore.get(KEY_USER_AUTO_ID)?.toIntOrNull() ?: 0

        if (applicantJson != null && applicantJson.isNotBlank()) {
            runCatching {
                val data = JSONObject(applicantJson)
                val name = normalizeServerText(data.optString("name", ""))
                val bName = normalizeServerText(data.optString("banglaName", ""))
                val rank = normalizeServerText(data.optString("rank", ""))
                englishNameState.value = name
                banglaNameState.value = bName
                designationState.value = rank
                
                bpNumberState.value = normalizeServerText(data.optString("bpNumber", "")).takeIf { it.isNotBlank() }
                    ?: settingsStore.get("bp_number") ?: ""
                
                currentWorkplaceState.value = normalizeServerText(data.optString("mainUnit", ""))
                joiningDateState.value = normalizeServerText(data.optString("date_of_joining_at_present_rank", ""))
                mobileState.value = normalizeServerText(data.optString("mobile", ""))
                othersInfoState.value = normalizeServerText(data.optString("notes", ""))
                val isMain = data.optBoolean("isMain", true)

                // Build accused list
                val accusedList = mutableListOf<AccusedItem>()
                val incomingAccused = data.optJSONArray("accusedList")
                if (incomingAccused != null && incomingAccused.length() > 0) {
                    for (i in 0 until incomingAccused.length()) {
                        val p = incomingAccused.optJSONObject(i) ?: continue
                        accusedList.add(
                            AccusedItem(
                                specialReportAccusedId = p.optInt("specialReportAccusedId", 0),
                                englishName = normalizeServerText(p.optString("englishName", p.optString("name", ""))),
                                banglaName = normalizeServerText(p.optString("banglaName", p.optString("bangla_name", ""))),
                                bpNumber = normalizeServerText(p.optString("bpNumber", p.optString("bp_number", ""))),
                                designation = normalizeServerText(p.optString("designation", p.optString("rank", ""))),
                                currentWorkingPlace = normalizeServerText(p.optString("currentWorkingPlace", "")).ifBlank { null },
                                joiningDateTime = normalizeServerText(p.optString("joiningDateTime", "")).ifBlank { null },
                                previousWorkPlace = normalizeServerText(p.optString("previousWorkPlace", "")).ifBlank { null },
                                homeDistrict = normalizeServerText(p.optString("homeDistrict", "")).ifBlank { null },
                                phoneNumber = normalizeServerText(p.optString("phoneNumber", p.optString("mobile", ""))).ifBlank { null },
                                punishment = normalizeServerText(p.optString("punishment", "")).ifBlank { null },
                                reward = normalizeServerText(p.optString("reward", "")).ifBlank { null },
                                previousIrregularity = normalizeServerText(p.optString("previousIrregularity", "")).ifBlank { null },
                                specialReportId = p.optInt("specialReportId", 0),
                                is_main = p.optBoolean("isMain", p.optBoolean("is_main", false)),
                            ),
                        )
                    }
                } else {
                    accusedList.add(
                        AccusedItem(
                            englishName = name,
                            banglaName = bName,
                            bpNumber = bpNumberState.value,
                            designation = rank,
                            currentWorkingPlace = currentWorkplaceState.value,
                            joiningDateTime = joiningDateState.value,
                            phoneNumber = mobileState.value,
                            homeDistrict = normalizeServerText(data.optString("homeDistrict", data.optString("home_district", ""))),
                            is_main = isMain,
                        ),
                    )

                    val extraPeople = data.optJSONArray("extraPeople")
                    if (extraPeople != null) {
                        for (i in 0 until extraPeople.length()) {
                            val p = extraPeople.optJSONObject(i) ?: continue
                            accusedList.add(
                                AccusedItem(
                                    specialReportAccusedId = p.optInt("specialReportAccusedId", 0),
                                    englishName = normalizeServerText(p.optString("englishName", p.optString("name", ""))),
                                    banglaName = normalizeServerText(p.optString("banglaName", p.optString("bangla_name", ""))),
                                    bpNumber = normalizeServerText(p.optString("bpNumber", p.optString("bp_number", ""))),
                                    designation = normalizeServerText(p.optString("designation", p.optString("rank", ""))),
                                    currentWorkingPlace = normalizeServerText(p.optString("currentWorkingPlace", "")).ifBlank { null },
                                    joiningDateTime = normalizeServerText(p.optString("joiningDateTime", "")).ifBlank { null },
                                    previousWorkPlace = normalizeServerText(p.optString("previousWorkPlace", "")).ifBlank { null },
                                    homeDistrict = normalizeServerText(p.optString("homeDistrict", "")).ifBlank { null },
                                    phoneNumber = normalizeServerText(p.optString("phoneNumber", p.optString("mobile", ""))).ifBlank { null },
                                    punishment = normalizeServerText(p.optString("punishment", "")).ifBlank { null },
                                    reward = normalizeServerText(p.optString("reward", "")).ifBlank { null },
                                    previousIrregularity = normalizeServerText(p.optString("previousIrregularity", "")).ifBlank { null },
                                    specialReportId = p.optInt("specialReportId", 0),
                                    is_main = p.optBoolean("isMain", p.optBoolean("is_main", false)),
                                ),
                            )
                        }
                    }
                }
                accusedListState.value = accusedList.distinctBy {
                    listOf(
                        it.bpNumber.trim().lowercase(Locale.ENGLISH),
                        it.englishName.trim().lowercase(Locale.ENGLISH),
                    ).joinToString("|")
                }

                val mainAccused = accusedListState.value.firstOrNull { it.is_main } ?: accusedListState.value.firstOrNull()
                if (mainAccused != null) {
                    englishNameState.value = mainAccused.englishName
                    banglaNameState.value = mainAccused.banglaName
                    designationState.value = mainAccused.designation
                    bpNumberState.value = mainAccused.bpNumber
                    currentWorkplaceState.value = mainAccused.currentWorkingPlace.orEmpty()
                    joiningDateState.value = mainAccused.joiningDateTime.orEmpty()
                    previousWorkplaceState.value = mainAccused.previousWorkPlace.orEmpty()
                    homeDistrictState.value = mainAccused.homeDistrict.orEmpty()
                    mobileState.value = mainAccused.phoneNumber.orEmpty()
                    punishmentState.value = mainAccused.punishment.orEmpty()
                    rewardState.value = mainAccused.reward.orEmpty()
                    irregularitiesState.value = mainAccused.previousIrregularity.orEmpty()
                }
                
                // Set default allegation
                val complains = data.optJSONArray("complainList")
                val relations = data.optJSONArray("complain_ComplainType_Relations") ?: data.optJSONArray("complainTypeRelations")
                
                if (complains != null && complains.length() > 0) {
                    val newList = mutableListOf<AllegationItem>()
                    for (i in 0 until complains.length()) {
                        val title = normalizeServerText(complains.optString(i, ""))
                        if (title.isNotBlank()) {
                            var relationId = 0
                            if (relations != null) {
                                for (j in 0 until relations.length()) {
                                    val rel = relations.optJSONObject(j)
                                    if (normalizeServerText(rel?.optString("complain_Type_Details", "") ?: "") == title) {
                                        relationId = rel.optInt("complainComplainTypeRelationID", 0).takeIf { it != 0 }
                                            ?: rel.optInt("id", 0)
                                        break
                                    }
                                }
                            }
                            newList.add(AllegationItem(title = title, complainComplainTypeRelationID = relationId))
                        }
                    }
                    if (newList.isNotEmpty()) {
                        allegationsState.value = newList
                    }
                }
            }
        } else {
            bpNumberState.value = normalizeServerText(settingsStore.get("bp_number") ?: "")
        }
        
        loadComplainTypes()
    }

    fun loadSpecialReportData(reportId: Int) {
        specialReportId = reportId
        isLoadingReportState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val url = ApiEndpoints.SpecialReport.getSpecialReport(specialReportId)
                val response = httpGetJson(url, token.takeIf { it.isNotBlank() })
                
                val getString = { key: String ->
                    val value = response.opt(key)
                    if (value != null && value != JSONObject.NULL) {
                        normalizeServerText(value.toString())
                    } else {
                        ""
                    }
                }
                
                complainId = response.optInt("complainId", complainId)
                val cNo = getString("complainNo")
                if (cNo.isNotBlank() && cNo != "null") complainNo = cNo
                status = response.optInt("status", 0)
                flowLabel = response.optInt("flowLabel", 0)
                reportStatusState.value = status
                reportFlowLabelState.value = flowLabel
                createAt = getString("createAt").takeIf { it.isNotBlank() && it != "null" }
                createdByUserId = if (response.has("createdByUserId") && !response.isNull("createdByUserId")) response.optInt("createdByUserId") else null
                updatedAt = getString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
                isDeleted = response.optBoolean("isDeleted", false)

                reportTitleState.value = getString("title")
                
                englishNameState.value = getString("englishName")
                banglaNameState.value = getString("banglaName")
                designationState.value = getString("designation")
                bpNumberState.value = getString("bpNumber")
                currentWorkplaceState.value = getString("currentWorkingPlace")
                
                val joiningDate = getString("joiningDateTime")
                if (joiningDate.isNotEmpty()) {
                    joiningDateState.value = joiningDate.substringBefore("T")
                }
                
                previousWorkplaceState.value = getString("previousWorkPlace")
                homeDistrictState.value = getString("homeDistrict")
                mobileState.value = getString("phoneNumber")
                punishmentState.value = getString("punishment")
                rewardState.value = getString("reward")
                irregularitiesState.value = getString("previousIrregularity")
                othersInfoState.value = getString("othersInfo")
                reportSummaryState.value = getString("reportSummary")
                agentRecommendationState.value = getString("agentRecommendation")

                val relationsArr = response.optJSONArray("agentReportRelations")
                if (relationsArr != null) {
                    val relList = mutableListOf<AgentReportRelationItem>()
                    for (ri in 0 until relationsArr.length()) {
                        val o = relationsArr.optJSONObject(ri) ?: continue
                        val ackRaw = o.opt("acknowledgeDate")
                        val ackStr = if (ackRaw == null || ackRaw == JSONObject.NULL) null else normalizeServerText(ackRaw.toString()).takeIf { it.isNotBlank() && it != "null" }
                        relList.add(
                            AgentReportRelationItem(
                                agentId = o.optInt("agentId", 0),
                                reportId = o.optInt("reportId", 0),
                                isLead = o.optBoolean("isLead", false),
                                isAcknowledged = o.optBoolean("isAcknowledged", false),
                                acknowledgeDate = ackStr,
                                remarks = o.optString("remarks", "").takeIf { it.isNotBlank() && it != "null" },
                                agentName = normalizeServerText(o.optString("agentName", "")),
                                agentCode = normalizeServerText(o.optString("agentCode", "")),
                                bpNumber = normalizeServerText(o.optString("bpNumber", "")),
                            )
                        )
                    }
                    agentReportRelationsState.value = relList
                }
                
                val complaintsList = mutableListOf<AllegationItem>()
                val vmComplains = response.optJSONArray("vmSpecialReportComplains")
                if (vmComplains != null) {
                    for (i in 0 until vmComplains.length()) {
                        val complaint = vmComplains.optJSONObject(i) ?: continue
                        val attachments = mutableListOf<AttachmentItem>()
                        val vmAttachments = complaint.optJSONArray("vmComplainAttachements")
                        if (vmAttachments != null) {
                            for (j in 0 until vmAttachments.length()) {
                                val attachment = vmAttachments.optJSONObject(j) ?: continue
                                attachments.add(
                                    AttachmentItem(
                                        id = attachment.optInt("complainAttachementId", 0),
                                        title = normalizeServerText(attachment.optString("title", "")),
                                        filePath = normalizeServerText(attachment.optString("filePath", "")).takeIf { it.isNotBlank() && it != "null" }
                                    )
                                )
                            }
                        }
                        complaintsList.add(
                            AllegationItem(
                                id = complaint.optInt("specialReportComplainId", 0),
                                complainTypeId = complaint.optInt("complainTypeId", 0).takeIf { it != 0 },
                                complainComplainTypeRelationID = complaint.optInt("complainComplainTypeRelationID", 0),
                                title = normalizeServerText(complaint.optString("title", "")),
                                details = normalizeServerText(complaint.optString("details", "")),
                                witness = normalizeServerText(complaint.optString("attestor", "")),
                                attachments = attachments
                            )
                        )
                    }
                }
                if (complaintsList.isNotEmpty()) allegationsState.value = complaintsList
            }.onFailure {
                it.printStackTrace()
            }
            isLoadingReportState.value = false
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

    fun dismissNoAgentFoundDialog() {
        showNoAgentFoundDialogState.value = false
    }

    fun addAgentFromSearch(result: AgentSearchResult) {
        if (agentReportRelationsState.value.any { it.agentId == result.userAutoID }) return
        val bp = result.bpNumber.trim().ifBlank { ensureBpCivPrefix(agentSearchQueryState.value) }
        val newItem = AgentReportRelationItem(
            agentId = result.userAutoID,
            reportId = specialReportId,
            agentName = result.userFullName,
            agentCode = result.userCode,
            bpNumber = bp,
        )
        agentReportRelationsState.value = agentReportRelationsState.value + newItem
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
        val existing = list[index]
        val acknowledgeDate = if (isAcknowledged) LocalDate.now().format(DateTimeFormatter.ISO_DATE) else null
        list[index] = existing.copy(
            isAcknowledged = isAcknowledged,
            acknowledgeDate = acknowledgeDate,
            remarks = remarks.trim().ifBlank { null },
        )
        agentReportRelationsState.value = list
    }

    fun loadComplainTypes() {
        isLoadingComplainTypesState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var parsedTypes: List<SpecialReportComplainType> = emptyList()
            var matchedAllegations = allegationsState.value
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val res = httpGetJson(ApiEndpoints.SpecialReport.complainTypeList, token.takeIf { it.isNotBlank() })
                val items = res.optJSONArray("items") ?: JSONArray()
                parsedTypes = buildList {
                    for (i in 0 until items.length()) {
                        val obj = items.optJSONObject(i) ?: continue
                        if (obj.optBoolean("isActive", false)) {
                            add(SpecialReportComplainType(
                                id = obj.optInt("complainTypeId", obj.optInt("id", 0)),
                                title = normalizeServerText(obj.optString("title", "")),
                                orderNo = if (obj.isNull("orderNo")) null else obj.optInt("orderNo"),
                                isActive = true
                            ))
                        }
                    }
                }.sortedBy { it.orderNo ?: Int.MAX_VALUE }

                val current = allegationsState.value
                matchedAllegations = current.map { allegation ->
                    if (allegation.complainTypeId == null && allegation.title.isNotBlank()) {
                        val match = parsedTypes.find { it.title.equals(allegation.title, ignoreCase = true) }
                        if (match != null) allegation.copy(complainTypeId = match.id) else allegation
                    } else allegation
                }
            }
            withContext(Dispatchers.Main) {
                complainTypesState.value = parsedTypes
                allegationsState.value = matchedAllegations
                isLoadingComplainTypesState.value = false
            }
        }
    }

    fun addAllegation() {
        allegationsState.value = allegationsState.value + AllegationItem()
    }

    fun addAllegation(type: SpecialReportComplainType) {
        allegationsState.value = allegationsState.value + AllegationItem(complainTypeId = type.id, title = type.title)
    }

    fun removeAllegation(index: Int) {
        if (allegationsState.value.size > 1) {
            val newList = allegationsState.value.toMutableList()
            newList.removeAt(index)
            allegationsState.value = newList
        }
    }

    fun updateAllegation(index: Int, newItem: AllegationItem) {
        val newList = allegationsState.value.toMutableList()
        newList[index] = newItem
        allegationsState.value = newList
    }

    fun addAttachmentToAllegation(index: Int, title: String, uri: Uri, file: File?) {
        val list = allegationsState.value.toMutableList()
        val item = list[index]
        val newAttachments = item.attachments + AttachmentItem(title = title, uri = uri, file = file)
        list[index] = item.copy(attachments = newAttachments)
        allegationsState.value = list
    }

    fun removeAttachmentFromAllegation(allegationIndex: Int, attachmentIndex: Int) {
        val list = allegationsState.value.toMutableList()
        val item = list[allegationIndex]
        val newAttachments = item.attachments.toMutableList()
        newAttachments.removeAt(attachmentIndex)
        list[allegationIndex] = item.copy(attachments = newAttachments)
        allegationsState.value = list
    }

    fun saveReport(isAcknowledgment: Boolean = false, isForwarding: Boolean = false) {
        if (bpNumberState.value.trim().isBlank()) {
            saveSuccessState.value = false
            saveMessageState.value = "BP Number is required"
            return
        }
        
        isSavingState.value = true
        saveSuccessState.value = null
        saveMessageState.value = null

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val creatorId = settingsStore.get(KEY_USER_AUTO_ID)?.trim()?.toIntOrNull() ?: 0
                val now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

                val fields = LinkedHashMap<String, String>()
                fun addField(key: String, value: Any?) {
                    val s = value?.toString()?.trim()
                    if (!s.isNullOrBlank() && s != "null") fields[key] = s
                }
                
                addField("specialReportId", specialReportId)
                addField("complainId", complainId)
                addField("complainNo", complainNo)
                
                when {
                    isForwarding -> {
                        addField("status", 3)
                        addField("flowLabel", 1)
                    }
                    isAcknowledgment -> {
                        addField("status", 2)
                        addField("flowLabel", 0)
                    }
                    else -> {
                        addField("status", 0)
                        addField("flowLabel", 0)
                    }
                }
                addField("isSubmit", false)
                fun formatDate(d: String?): String? {
                    val s = d?.trim()
                    if (s.isNullOrBlank() || s == "null") return null
                    return if (s.contains("T")) s else "${s}T00:00:00"
                }

                val validAccusedList = accusedListState.value.filter { it.englishName.isNotBlank() || it.banglaName.isNotBlank() || it.bpNumber.isNotBlank() }
                val mainAccused = validAccusedList.firstOrNull { it.is_main } ?: validAccusedList.firstOrNull()


                addField("title", reportTitleState.value)
                addField("englishName", mainAccused?.englishName?.trim() ?: englishNameState.value.trim())
                addField("banglaName", mainAccused?.banglaName?.trim() ?: banglaNameState.value.trim())
                addField("bpNumber", ensureBpCivPrefix(mainAccused?.bpNumber?.trim() ?: bpNumberState.value.trim()))
                addField("designation", mainAccused?.designation?.trim() ?: designationState.value.trim())
                addField("currentWorkingPlace", mainAccused?.currentWorkingPlace?.trim().orEmpty().ifBlank { currentWorkplaceState.value.trim() })
                addField("joiningDateTime", formatDate(mainAccused?.joiningDateTime ?: joiningDateState.value))
                addField("previousWorkPlace", mainAccused?.previousWorkPlace?.trim().orEmpty().ifBlank { previousWorkplaceState.value.trim() })
                addField("homeDistrict", mainAccused?.homeDistrict?.trim().orEmpty().ifBlank { homeDistrictState.value.trim() })
                addField("phoneNumber", mainAccused?.phoneNumber?.trim().orEmpty().ifBlank { mobileState.value.trim() })
                addField("punishment", punishmentState.value)
                addField("reward", rewardState.value)
                addField("previousIrregularity", irregularitiesState.value)
                addField("reportSummary", reportSummaryState.value)
                addField("agentRecommendation", agentRecommendationState.value)
                addField("authorityRecommendation", "")
                addField("othersInfo", othersInfoState.value)

                if (createAt != null) addField("createAt", createAt)
                addField("updatedAt", now)
                addField("isDeleted", isDeleted)
                if (createdByUserId != null) addField("createdByUserId", createdByUserId)

                val files = LinkedHashMap<String, File>()
                allegationsState.value.filter { it.title.isNotBlank() || it.details.isNotBlank() }.forEachIndexed { i, allegation ->
                    fields["vmSpecialReportComplains[$i].specialReportComplainId"] = allegation.id.toString()
                    fields["vmSpecialReportComplains[$i].specialReportId"] = specialReportId.toString()
                    fields["vmSpecialReportComplains[$i].complainTypeId"] = (allegation.complainTypeId ?: 0).toString()
                    if (allegation.complainComplainTypeRelationID != 0) fields["vmSpecialReportComplains[$i].complainComplainTypeRelationID"] = allegation.complainComplainTypeRelationID.toString()
                    addField("vmSpecialReportComplains[$i].title", allegation.title)
                    addField("vmSpecialReportComplains[$i].details", allegation.details)
                    addField("vmSpecialReportComplains[$i].attestor", allegation.witness)

                    allegation.attachments.forEachIndexed { j, attachment ->
                        fields["vmSpecialReportComplains[$i].vmComplainAttachements[$j].complainAttachementId"] = attachment.id.toString()
                        fields["vmSpecialReportComplains[$i].vmComplainAttachements[$j].specialReportComplainId"] = allegation.id.toString()
                        addField("vmSpecialReportComplains[$i].vmComplainAttachements[$j].title", attachment.title)
                        addField("vmSpecialReportComplains[$i].vmComplainAttachements[$j].filePath", attachment.filePath)
                        attachment.file?.let { files["vmSpecialReportComplains[$i].vmComplainAttachements[$j].file"] = it }
                    }
                }

                validAccusedList.forEachIndexed { i, accused ->
                    fields["vmSpecialReportAccuseds[$i].specialReportAccusedId"] = accused.specialReportAccusedId.toString()
                    fields["vmSpecialReportAccuseds[$i].specialReportId"] = specialReportId.toString()
                    addField("vmSpecialReportAccuseds[$i].englishName", accused.englishName)
                    addField("vmSpecialReportAccuseds[$i].banglaName", accused.banglaName)
                    addField("vmSpecialReportAccuseds[$i].bpNumber", ensureBpCivPrefix(accused.bpNumber.trim()))
                    addField("vmSpecialReportAccuseds[$i].designation", accused.designation)
                    addField("vmSpecialReportAccuseds[$i].currentWorkingPlace", accused.currentWorkingPlace)
                    addField("vmSpecialReportAccuseds[$i].joiningDateTime", formatDate(accused.joiningDateTime))
                    addField("vmSpecialReportAccuseds[$i].previousWorkPlace", accused.previousWorkPlace)
                    addField("vmSpecialReportAccuseds[$i].homeDistrict", accused.homeDistrict)
                    addField("vmSpecialReportAccuseds[$i].phoneNumber", accused.phoneNumber)
                    addField("vmSpecialReportAccuseds[$i].punishment", accused.punishment)
                }

                // Add Creator as Lead Agent
                val creatorName = settingsStore.get("user_full_name")?.trim().orEmpty()
                val creatorCode = settingsStore.get("user_code")?.trim().orEmpty()
                val creatorBp = settingsStore.get("bp_number")?.trim().orEmpty()

                val creatorRelation = AgentReportRelationItem(
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

                normalizedRelations.forEachIndexed { i, rel ->
                    fields["agentReportRelations[$i].agentId"] = rel.agentId.toString()
                    fields["agentReportRelations[$i].reportId"] = specialReportId.toString()
                    fields["agentReportRelations[$i].isLead"] = rel.isLead.toString()
                    fields["agentReportRelations[$i].isAcknowledged"] = rel.isAcknowledged.toString()
                    addField("agentReportRelations[$i].acknowledgeDate", formatDate(rel.acknowledgeDate))
                    addField("agentReportRelations[$i].remarks", rel.remarks)
                    addField("agentReportRelations[$i].agentName", rel.agentName)
                    addField("agentReportRelations[$i].agentCode", rel.agentCode)
                    fields["agentReportRelations[$i].bpNumber"] = ensureBpCivPrefix(rel.bpNumber.trim())
                }

                val httpMethod = if (specialReportId > 0) "PUT" else "POST"
                val apiUrl = if (specialReportId > 0) "${ApiEndpoints.SpecialReport.saveSpecialReport}/$specialReportId" else ApiEndpoints.SpecialReport.saveSpecialReport
                val res = uploadMultipart(apiUrl, token, fields, files, httpMethod)
                val statusCode = res.optInt("statusCode", -1)
                val returnedId = res.optInt("specialReportId", 0)
                
                if (returnedId > 0 || statusCode == 1 || statusCode in 200..299) {
                    if (specialReportId == 0 && returnedId > 0) specialReportId = returnedId
                    isSubmittedState.value = isAcknowledgment || isForwarding
                    saveSuccessState.value = true
                    saveMessageState.value = when {
                        isForwarding -> "Special report forwarded to supervisor successfully"
                        isAcknowledgment -> "Special report submitted successfully"
                        else -> "Special report saved successfully"
                    }
                } else {
                    val errorTitle = res.optString("title", "Failed to save")
                    val errors = res.optJSONObject("errors")
                    val errorDetail = if (errors != null) {
                        val details = mutableListOf<String>()
                        errors.keys().forEach { key ->
                            val msg = errors.opt(key)?.toString() ?: ""
                            details.add("$key: $msg")
                        }
                        details.joinToString("\n")
                    } else res.optString("message", res.toString().take(200))
                    error("$errorTitle\n$errorDetail")
                }
            }.onFailure {
                saveSuccessState.value = false
                saveMessageState.value = it.message ?: "Failed to save"
            }
            isSavingState.value = false
        }
    }

    private fun uploadMultipart(url: String, token: String, fields: LinkedHashMap<String, String>, imageFiles: LinkedHashMap<String, File>, method: String = "POST"): JSONObject {
        val boundary = "Boundary-" + System.currentTimeMillis()
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = method
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "PIOMS")
        conn.setRequestProperty("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        try {
            conn.outputStream.use { out ->
                for ((k, v) in fields) {
                    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Disposition: form-data; name=\"$k\"\r\n\r\n".toByteArray(Charsets.UTF_8))
                    out.write(v.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray(Charsets.UTF_8))
                }
                for ((k, file) in imageFiles) {
                    val filename = file.name
                    val contentType = when (file.extension.lowercase()) {
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Disposition: form-data; name=\"$k\"; filename=\"$filename\"\r\n".toByteArray(Charsets.UTF_8))
                    out.write("Content-Type: $contentType\r\n\r\n".toByteArray(Charsets.UTF_8))
                    file.inputStream().use { it.copyTo(out) }
                    out.write("\r\n".toByteArray(Charsets.UTF_8))
                }
                out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(method = method, url = url, statusCode = -2, bodyText = "Error writing to stream: ${e.message}")
            return JSONObject().put("statusCode", -2).put("message", "Error writing to stream: ${e.message}")
        }

        val status = try { conn.responseCode } catch (e: Exception) { -1 }
        val stream = try { if (status in 200..299) conn.inputStream else conn.errorStream } catch (e: Exception) { conn.errorStream }
        val responseText = stream?.use { String(it.readBytes()) }.orEmpty()
        ApiHttpLogger.logResponse(method = method, url = url, statusCode = status, bodyText = responseText)

        return runCatching {
            val json = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
            if (!json.has("statusCode")) json.put("statusCode", status)
            json
        }.getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
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
            JSONObject().put("statusCode", 500).put("message", e.message)
        }
    }

    fun copyUriToFile(context: Context, uri: Uri): File? {
        val f = File(context.cacheDir, "special_report_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { out -> input.copyTo(out) }
            }
            f
        } catch (e: Exception) {
            null
        }
    }
}
