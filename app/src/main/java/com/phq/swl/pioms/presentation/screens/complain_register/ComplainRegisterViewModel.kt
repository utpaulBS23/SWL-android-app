package com.phq.swl.pioms.presentation.screens.complain_register

import android.util.Base64
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
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.annotation.KoinViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun normalizeServerText(value: String): String = value.replace("+", " ").trim()

data class ComplainType(
    val id: Int,
    val title: String,
    val orderNo: Int?,
    val isActive: Boolean,
)

data class ComplainExtraPersonItem(
    val id: Long,
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
    val isMain: Boolean = false,
)

@KoinViewModel
class ComplainRegisterViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val KEY_AUTH_TOKEN = "auth_token"
    private val KEY_USER_AUTO_ID = "user_auto_id"
    private val KEY_DEPARTMENT_ID = "department_id"

    val bpNumberState: MutableState<String> = mutableStateOf("")
    val nameState: MutableState<String> = mutableStateOf("")
    val banglaNameState: MutableState<String> = mutableStateOf("")
    val rankState: MutableState<String> = mutableStateOf("")
    val mainUnitState: MutableState<String> = mutableStateOf("")
    val currentPositionState: MutableState<String> = mutableStateOf("")
    val mobileState: MutableState<String> = mutableStateOf("")
    val homeDistrictState: MutableState<String> = mutableStateOf("")
    val isMainState: MutableState<Boolean> = mutableStateOf(true)
    
    val extraPeopleState: MutableState<List<ComplainExtraPersonItem>> = mutableStateOf(emptyList())
    private var nextExtraPersonId = 1L

    val recordDateUiState: MutableState<String> =
        mutableStateOf(
            LocalDate
                .now()
                .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)),
        )

    val complainDetailsState: MutableState<String> = mutableStateOf("")
    val remarksState: MutableState<String> = mutableStateOf("")

    val complainRefNoState: MutableState<Int> = mutableStateOf(0)
    val complainTypesLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val complainTypesState: MutableState<List<ComplainType>> = mutableStateOf(emptyList())

    val applicantLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val applicantInfoState: MutableState<JSONObject?> = mutableStateOf(null)
    val applicantPhotoBytesState: MutableState<ByteArray?> = mutableStateOf(null)

    val rewardsLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val rewardsState: MutableState<List<JSONObject>> = mutableStateOf(emptyList())

    val punishmentsLoadingState: MutableState<Boolean> = mutableStateOf(false)
    val punishmentsState: MutableState<List<JSONObject>> = mutableStateOf(emptyList())

    val complainListState: MutableState<List<String>> = mutableStateOf(emptyList())

    val savingState: MutableState<Boolean> = mutableStateOf(false)
    val saveSuccessState: MutableState<Boolean?> = mutableStateOf(null)
    val saveMessageState: MutableState<String?> = mutableStateOf(null)
    val savedComplainIdState: MutableState<Int?> = mutableStateOf(null)
    val savedComplainNoState: MutableState<String?> = mutableStateOf(null)

    val showExtraSectionsState: MutableState<Boolean> =
        mutableStateOf(settingsStore.get(KEY_DEPARTMENT_ID)?.trim().orEmpty() != "")

    init {
        loadComplainTypes()
        fetchComplainReferenceNo()
    }

    fun addComplain(text: String) {
        val v = text.trim()
        if (v.isBlank()) return
        if (complainListState.value.any { it.equals(v, ignoreCase = true) }) return
        complainListState.value = complainListState.value + v
    }

    fun removeComplain(text: String) {
        complainListState.value = complainListState.value.filterNot { it == text }
    }

    fun addExtraPerson() {
        val id = nextExtraPersonId++
        extraPeopleState.value = extraPeopleState.value + ComplainExtraPersonItem(
            id = id,
            englishName = "",
            banglaName = "",
            designation = "",
            isMain = isMainState.value == false && extraPeopleState.value.none { it.isMain }
        )
    }

    fun addCurrentApplicantToAccused() {
        val englishName = nameState.value.trim()
        val banglaName = banglaNameState.value.trim()
        val bp = bpNumberState.value.trim()
        val rank = rankState.value.trim()
        val unit = mainUnitState.value.trim()
        val mobile = mobileState.value.trim()
        val joiningDate = applicantInfoState.value?.optString("date_of_joining_at_present_rank")?.substringBefore("T")?.takeIf { it.isNotBlank() }

        if (englishName.isBlank() && banglaName.isBlank() && bp.isBlank()) {
            saveSuccessState.value = false
            saveMessageState.value = "Search applicant by BP first"
            return
        }

        val exists = extraPeopleState.value.any {
            (bp.isNotBlank() && it.bpNumber.equals(bp, ignoreCase = true)) ||
                (bp.isBlank() && englishName.isNotBlank() && it.englishName.equals(englishName, ignoreCase = true))
        }
        if (exists) {
            saveSuccessState.value = false
            saveMessageState.value = "Applicant already added in accused list"
            return
        }

        val id = nextExtraPersonId++
        extraPeopleState.value += ComplainExtraPersonItem(
            id = id,
            englishName = englishName,
            banglaName = banglaName,
            bpNumber = bp,
            designation = rank,
            currentWorkingPlace = unit.takeIf { it.isNotBlank() },
            joiningDateTime = joiningDate.takeIf { it?.isNotBlank() ?: false},
            phoneNumber = mobile.takeIf { it.isNotBlank() },
            homeDistrict = homeDistrictState.value.takeIf { it.isNotBlank() },
            isMain = false,
        )
    }

    fun removeExtraPerson(id: Long) {
        extraPeopleState.value = extraPeopleState.value.filterNot { it.id == id }
    }

    fun updateExtraPerson(id: Long, updated: ComplainExtraPersonItem) {
        extraPeopleState.value = extraPeopleState.value.map { 
            if (it.id == id) {
                if (updated.isMain) {
                    isMainState.value = false
                    updated.copy(isMain = true)
                } else {
                    updated.copy(isMain = false)
                }
            } else {
                if (updated.isMain) it.copy(isMain = false) else it
            }
        }
    }

    fun setPrimaryMain(checked: Boolean) {
        isMainState.value = checked
        if (checked) {
            extraPeopleState.value = extraPeopleState.value.map { it.copy(isMain = false) }
        }
    }

    fun clearAll() {
        bpNumberState.value = ""
        nameState.value = ""
        rankState.value = ""
        mainUnitState.value = ""
        currentPositionState.value = ""
        mobileState.value = ""
        homeDistrictState.value = ""
        isMainState.value = true
        extraPeopleState.value = emptyList()
        nextExtraPersonId = 1L
        complainDetailsState.value = ""
        remarksState.value = ""
        complainListState.value = emptyList()
        applicantInfoState.value = null
        applicantPhotoBytesState.value = null
        rewardsState.value = emptyList()
        punishmentsState.value = emptyList()
        saveSuccessState.value = null
        saveMessageState.value = null
    }

    fun loadComplainTypes() {
        complainTypesLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var parsedTypes: List<ComplainType> = emptyList()
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val res = httpGetJson(ApiEndpoints.SpecialReport.complainTypeList, token.takeIf { it.isNotBlank() })
                val items = res.optJSONArray("items") ?: JSONArray()
                parsedTypes =
                    buildList {
                    for (i in 0 until items.length()) {
                        val obj = items.optJSONObject(i) ?: continue
                        val isActive = obj.optBoolean("isActive", false)
                        val title = normalizeServerText(obj.optString("title", ""))
                        val orderNo = if (obj.isNull("orderNo")) null else obj.optInt("orderNo", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                        val id =
                            when {
                                obj.has("complainTypeId") -> obj.optInt("complainTypeId", 0)
                                obj.has("id") -> obj.optInt("id", 0)
                                else -> 0
                            }
                        if (title.isNotBlank()) {
                            add(
                                ComplainType(
                                    id = id,
                                    title = title,
                                    orderNo = orderNo,
                                    isActive = isActive,
                                ),
                            )
                        }
                    }
                }.filter { it.isActive }
                        .sortedWith(
                            compareBy<ComplainType> { it.orderNo ?: Int.MAX_VALUE }
                                .thenBy { it.title.lowercase(Locale.ENGLISH) },
                        )
            }.onFailure {
                parsedTypes = emptyList()
            }
            withContext(Dispatchers.Main) {
                complainTypesState.value = parsedTypes
                complainTypesLoadingState.value = false
            }
        }
    }

    fun fetchComplainReferenceNo() {
        viewModelScope.launch(Dispatchers.IO) {
            var complainRefNo = 0
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val res = httpGetJson(ApiEndpoints.Complain.getComplainReferenceNo, token.takeIf { it.isNotBlank() })
                val statusCode = res.optInt("statusCode", -1)
                val obj = res.opt("responseObj")
                if (statusCode == 1 && obj != null && obj != JSONObject.NULL) {
                    val v =
                        when (obj) {
                            is Number -> obj.toInt()
                            else -> obj.toString().trim().toIntOrNull() ?: 0
                        }
                    complainRefNo = v
                }
            }
            withContext(Dispatchers.Main) {
                if (complainRefNo != 0) {
                    complainRefNoState.value = complainRefNo
                }
            }
        }
    }

    fun fetchApplicantByBp() {
        val bp = bpNumberState.value.trim()
        if (bp.isBlank()) {
            saveSuccessState.value = false
            saveMessageState.value = "Please enter a BP Number"
            return
        }

        applicantLoadingState.value = true
        // Do not clear extraPeopleState here as per user request
        
        viewModelScope.launch(Dispatchers.IO) {
            var applicantInfo: JSONObject? = null
            var applicantPhoto: ByteArray? = null
            var applicantName = ""
            var applicantBanglaName = ""
            var applicantRank = ""
            var applicantMainUnit = ""
            var applicantCurrentPosition = ""
            var applicantMobile = ""
            var applicantHomeDistrict = ""
            var loadError: String? = null
            
            runCatching {
                val url = ApiEndpoints.Proxy.getApplicantInfoByBPNumber(bp)
                val res = httpGetJson(url, null)
                val statusCode = res.optInt("statusCode", -1)
                val arr = res.optJSONArray("responseObj") ?: JSONArray()
                if (statusCode != 1 || arr.length() == 0) {
                    error("No applicant found with this BP Number")
                }

                val info = arr.optJSONObject(0) ?: error("No applicant found with this BP Number")
                val eName = normalizeServerText(info.optString("englishName", info.optString("english_name", "")))
                val bName = normalizeServerText(info.optString("banglaName", info.optString("bangla_name", "")))
                val rnk = normalizeServerText(info.optString("designation", info.optString("present_rank", "").ifBlank { info.optString("joining_rank", "") }))
                val unit = normalizeServerText(info.optString("currentWorkingPlace", info.optString("main_unit", "")))
                val pos = normalizeServerText(info.optString("current_place_of_posting", ""))
                val ph = normalizeServerText(info.optString("phoneNumber", info.optString("phone", "")))

                applicantInfo = info
                applicantHomeDistrict = normalizeServerText(info.optString("homeDistrict", info.optString("home_district", "")))
                applicantPhoto = decodePictureBytes(info.optString("picture", "").trim())
                applicantName = eName
                applicantBanglaName = bName
                applicantRank = rnk
                applicantMainUnit = unit
                applicantCurrentPosition = pos.ifBlank { unit }
                applicantMobile = ph

                //fetchRewards(bp)
                //fetchPunishments(bp)
            }.onFailure {
                loadError = it.message ?: "Failed to load applicant"
            }
            withContext(Dispatchers.Main) {
                if (applicantName.isNotBlank()) {
                    applicantInfoState.value = applicantInfo
                    applicantPhotoBytesState.value = applicantPhoto
                    nameState.value = applicantName
                    banglaNameState.value = applicantBanglaName
                    rankState.value = applicantRank
                    mainUnitState.value = applicantMainUnit
                    currentPositionState.value = applicantCurrentPosition
                    mobileState.value = applicantMobile
                    homeDistrictState.value = applicantHomeDistrict
                }
                loadError?.let {
                    saveSuccessState.value = false
                    saveMessageState.value = it
                }
                applicantLoadingState.value = false
            }
        }
    }

    private fun fetchRewards(bp: String) {
        rewardsLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var rewards: List<JSONObject> = emptyList()
            runCatching {
                val res = httpGetJson(ApiEndpoints.Proxy.getApplicantRewardByBPNumber(bp), null)
                val statusCode = res.optInt("statusCode", -1)
                val arr = res.optJSONArray("responseObj") ?: JSONArray()
                if (statusCode != 1) {
                    return@runCatching
                }
                rewards = toObjectList(arr)
            }.onFailure {
                rewards = emptyList()
            }
            withContext(Dispatchers.Main) {
                rewardsState.value = rewards
                rewardsLoadingState.value = false
            }
        }
    }

    private fun fetchPunishments(bp: String) {
        punishmentsLoadingState.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var punishments: List<JSONObject> = emptyList()
            runCatching {
                val res = httpGetJson(ApiEndpoints.Proxy.getApplicantPunishmentByBPNumber(bp), null)
                val statusCode = res.optInt("statusCode", -1)
                val arr = res.optJSONArray("responseObj") ?: JSONArray()
                if (statusCode != 1) {
                    return@runCatching
                }
                punishments = toObjectList(arr)
            }.onFailure {
                punishments = emptyList()
            }
            withContext(Dispatchers.Main) {
                punishmentsState.value = punishments
                punishmentsLoadingState.value = false
            }
        }
    }

    fun saveComplain(
        complainId: Int = 0,
        complainNoOverride: Int? = null,
    ) {
        val bp = bpNumberState.value.trim()
        val name = nameState.value.trim()
        val rank = rankState.value.trim()
        val mainUnit = mainUnitState.value.trim()
        val currentPosition = currentPositionState.value.trim()
        val complainDetails = complainDetailsState.value.trim()
        val positiveRemarks = remarksState.value.trim()

        if (bp.isBlank()) {
            saveSuccessState.value = false
            saveMessageState.value = "BP Number is required"
            return
        }
        if (name.isBlank() || rank.isBlank() || mainUnit.isBlank()) {
            saveSuccessState.value = false
            saveMessageState.value = "Please load applicant info first"
            return
        }
        if (complainListState.value.isEmpty()) {
            saveSuccessState.value = false
            saveMessageState.value = "Please add at least one complain"
            return
        }

        savingState.value = true
        saveSuccessState.value = null
        saveMessageState.value = null

        viewModelScope.launch(Dispatchers.IO) {
            var savedComplainId: Int? = null
            var savedComplainNo: String? = null
            var saveSuccess = false
            var saveMessage = "Failed to save"
            runCatching {
                val token = settingsStore.get(KEY_AUTH_TOKEN)?.trim().orEmpty()
                val agentId = settingsStore.get(KEY_USER_AUTO_ID)?.trim()?.toIntOrNull() ?: 0
                val departmentId = settingsStore.get(KEY_DEPARTMENT_ID)?.trim()?.toIntOrNull() ?: 0

                val complainNo = complainNoOverride ?: complainRefNoState.value
                if (complainNo == 0) error("Complain reference no not ready")

                val recordDateUi = recordDateUiState.value.trim()
                val recordLocalDate =
                    runCatching {
                        LocalDate.parse(
                            recordDateUi,
                            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
                        )
                    }.getOrElse { LocalDate.now() }
                val recordDateStr = recordLocalDate.format(DateTimeFormatter.ISO_DATE)
                val recordDateIso =
                    recordLocalDate
                        .atStartOfDay()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH))

                val complainTypeRelations = JSONArray()
                for (c in complainListState.value) {
                    val t = complainTypesState.value.firstOrNull { it.title == c }
                    complainTypeRelations.put(
                        JSONObject()
                            .put("complainTypeId", t?.id ?: 0)
                            .put("complain_Type_Details", c),
                    )
                }

                val pimsProfile = applicantInfoState.value ?: JSONObject()
                val lstReward = JSONArray().apply { rewardsState.value.forEach { put(it) } }
                val lstPunishment = JSONArray().apply { punishmentsState.value.forEach { put(it) } }

                val requestObj =
                    JSONObject()
                        .put("complainID", complainId)
                        .put("complainNo", complainNo)
                        .put("bpNumber", bp)
                        .put("mainUnitName", mainUnit)
                        .put("presentRank", rank)
                        .put("recordDateStr", recordDateStr)
                        .put("agentID", agentId)
                        .put("departmentID", departmentId)
                        .put("selectedComplainTypeEdited", "")
                        .put("complainDetails", complainDetails)
                        .put("complainComment", "")
                        .put("currentPosition", currentPosition)
                        .put("positive_Remarks", positiveRemarks)
                        .put("pimsProfile", pimsProfile)
                        .put("lstReward", lstReward)
                        .put("lstPunishment", lstPunishment)
                        .put("lstAttachment", JSONArray())
                        .put("complain_ComplainType_Relations", complainTypeRelations)
                        .put("agentName", "")
                        .put("bangla_name", "")
                        .put("complainResultStatus", 0)
                        .put("actionTypeID", 1)
                        .put("actionRemarks", "")
                        .put("complainFlowHistory", JSONObject())
                        .put("complainCategoryID", 0)
                        .put("complainTypeId", 0)
                        .put("recordDate", recordDateIso)
                        .put("reportType", 1)

                val body = JSONObject().put("RequestObj", requestObj)
                val res = httpPostJson(ApiEndpoints.Complain.saveComplain, token, body)
                val statusCode = res.optInt("statusCode", -1)
                if (statusCode != 1 && statusCode != 200) {
                    error(res.optString("message", "Failed to save"))
                }
                val responseObj = res.optJSONObject("responseObj")
                savedComplainId = responseObj?.optInt("complainID", 0)
                savedComplainNo = responseObj?.optString("complainNo", "")
                saveSuccess = true
                saveMessage = "Complain saved successfully"
            }.onFailure {
                saveSuccess = false
                saveMessage = it.message ?: "Failed to save"
            }
            withContext(Dispatchers.Main) {
                savedComplainIdState.value = savedComplainId
                savedComplainNoState.value = savedComplainNo
                saveSuccessState.value = saveSuccess
                saveMessageState.value = saveMessage
                savingState.value = false
            }
        }
    }

    private fun decodePictureBytes(rawPicture: String): ByteArray? {
        val t = rawPicture.trim()
        if (t.isBlank()) return null
        val cleaned = if (t.contains(",")) t.split(",").last().trim() else t
        return runCatching { Base64.decode(cleaned, Base64.DEFAULT) }.getOrNull()
    }

    private fun toObjectList(arr: JSONArray): List<JSONObject> =
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(obj)
            }
        }

    private fun httpGetJson(
        url: String,
        token: String?,
    ): JSONObject {
        return try {
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
                JSONObject().put("statusCode", status).put("message", "Empty response")
            } else {
                runCatching { JSONObject(responseText) }
                    .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
            }
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(
                method = "GET",
                url = url,
                statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR,
                bodyText = e.message ?: "Unknown network error",
            )
            JSONObject().put("statusCode", HttpURLConnection.HTTP_INTERNAL_ERROR).put("message", e.message)
        }
    }

    private fun httpPostJson(
        url: String,
        token: String,
        body: JSONObject,
    ): JSONObject {
        return try {
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
                JSONObject().put("statusCode", status).put("message", "Empty response")
            } else {
                runCatching { JSONObject(responseText) }
                    .getOrElse { JSONObject().put("statusCode", status).put("message", responseText) }
            }
        } catch (e: Exception) {
            ApiHttpLogger.logResponse(
                method = "POST",
                url = url,
                statusCode = HttpURLConnection.HTTP_INTERNAL_ERROR,
                bodyText = e.message ?: "Unknown network error",
            )
            JSONObject().put("statusCode", HttpURLConnection.HTTP_INTERNAL_ERROR).put("message", e.message)
        }
    }
}
