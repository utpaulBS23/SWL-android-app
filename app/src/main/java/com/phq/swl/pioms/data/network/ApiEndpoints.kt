package com.phq.swl.pioms.data.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ApiEndpoints {
    //const val API_BASE_ROOT: String = "http://182.160.105.228:7088/api"
    const val API_BASE_ROOT: String = "http://182.160.105.228:6021/api"

    object Authenticate {
        const val login: String = "$API_BASE_ROOT/Authenticate/login"
        const val verifyOtpForLogin: String = "$API_BASE_ROOT/Authenticate/verifyOTPForLogin"
        const val requestNewUserId: String = "$API_BASE_ROOT/Authenticate/requestNewUserID"
        const val verifyOtpForNewId: String = "$API_BASE_ROOT/Authenticate/verifyOTPForNewID"
        const val recoverPasswordByMobileNumber: String = "$API_BASE_ROOT/Authenticate/recoverPasswordByMobileNumber"
        const val verifyOtpForPasswordRecovery: String = "$API_BASE_ROOT/Authenticate/verifyOtpForPasswordRecovery"
        const val updateDutyStartAndStopStatus: String = "$API_BASE_ROOT/Authenticate/updateDutyStartAndStopStatus"
        const val insertAgentLocationHistory: String = "$API_BASE_ROOT/Authenticate/insertAgentLocationHistory"
    }

    object Users {
        const val changeUserPassword: String = "$API_BASE_ROOT/Users/changeUserPassword"
        fun getAgentGeofenceCoOrdinateById(agentId: Int): String =
            "$API_BASE_ROOT/Users/GetAgentGeofenceCoOrdinateById?id=$agentId"

        fun searchAgents(query: String): String {
            val q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
            return "$API_BASE_ROOT/users/searchAgents?query=$q"
        }
    }

    object NotificationArea {
        const val saveOutAreaNotification: String = "$API_BASE_ROOT/NotificationArea/SaveOutAreaNotification"
        fun getLastInOutTypeByAgentID(agentId: String): String =
            "$API_BASE_ROOT/NotificationArea/getLastInOutTypeByAgentID/$agentId"
    }

    object AgentFaceRegistration {
        fun getAgentFaceRegisterStatus(userId: String): String =
            "$API_BASE_ROOT/AgentFaceRegistration/getAgentFaceRegisterStatus?userId=$userId"

        fun getRegistrationByUserIDWithImages(userId: String): String =
            "$API_BASE_ROOT/AgentFaceRegistration/getRegistrationByUserIDWithImages/$userId"

        const val saveAgentFaceRegistration: String =
            "$API_BASE_ROOT/AgentFaceRegistration/saveAgentFaceRegistration"
    }

    object AssignedTask {
        fun getAssignedTasksByAgent(agentCode: Int): String =
            "$API_BASE_ROOT/AssignedTask/getAssignedTasksByAgent/$agentCode"

        fun updateViewStatus(taskId: Int): String =
            "$API_BASE_ROOT/AssignedTask/updateViewStatus?taskId=$taskId"
    }

    object Complain {
        fun getComplainByAssignTask(taskId: Int): String =
            "$API_BASE_ROOT/Complain/getComplainByAssignTask/$taskId"

        const val getComplainReferenceNo: String = "$API_BASE_ROOT/Complain/GetComplainReferenceNo"
        const val saveComplain: String = "$API_BASE_ROOT/Complain/saveComplain"
    }

    object SpecialReport {
        const val complainTypeList: String = "$API_BASE_ROOT/SpecialReport/complainType/list"
        const val saveSpecialReport: String = "$API_BASE_ROOT/SpecialReport"
        fun getSpecialReportListByAgent(agentId: Int): String = "$API_BASE_ROOT/SpecialReport/specialReport/list/$agentId"
        fun getSpecialReport(reportId: Int): String = "$API_BASE_ROOT/SpecialReport/$reportId"
    }

    object WorkingPlaceReport {
        const val workplaceReceivedFieldTypeList: String =
            "$API_BASE_ROOT/WorkingPlaceReport/workingPlaceReceivedFieldType/list?pageOffset=0&pageSize=100"
        const val saveWorkingPlaceReport: String = "$API_BASE_ROOT/WorkingPlaceReport"
        fun getWorkingPlaceReportListByAgent(agentId: Int): String = "$API_BASE_ROOT/WorkingPlaceReport/list?pageOffset=0&pageSize=100"
        fun getWorkingPlaceReport(reportId: String): String =
            "$API_BASE_ROOT/WorkingPlaceReport/$reportId"
    }

    object UserPersonalAndFamilyInfo {
        const val degreeTypeList: String = "$API_BASE_ROOT/UserPersonalAndFamilyInfo/degreeType/list?pageOffset=0&pageSize=100"
        const val relationTypeList: String = "$API_BASE_ROOT/UserPersonalAndFamilyInfo/relationType/list"
        const val saveUserPersonalAndFamilyInfo: String = "$API_BASE_ROOT/UserPersonalAndFamilyInfo"
        fun getUserPersonalAndFamilyInfoListByAgent(agentId: Int): String =
            "$API_BASE_ROOT/UserPersonalAndFamilyInfo/list?pageOffset=0&pageSize=100"
        fun getUserPersonalAndFamilyInfo(userId: String): String =
            "$API_BASE_ROOT/UserPersonalAndFamilyInfo/$userId"
    }

    object Education {
        const val saveEducation: String = "$API_BASE_ROOT/Education"
        fun getEducationListByAgent(agentId: Int): String = "$API_BASE_ROOT/Education/list?pageOffset=0&pageSize=100"
        fun getEducationById(educationInfoId: Int): String =
            "$API_BASE_ROOT/Education/$educationInfoId"
    }

    object IncidentReport {
        const val assignedThanaByAgentID: String = "$API_BASE_ROOT/AgentUnit/GetAssignedThanaByAgentID"
        const val saveIncidentWithAttachments: String = "$API_BASE_ROOT/IncidentReport/saveIncidentWithAttachments"
        fun getIncidentReportListByAgent(agentId: Int): String = "$API_BASE_ROOT/IncidentReport/list/$agentId"
        fun getIncidentReportById(incidentReportId: Int): String = "$API_BASE_ROOT/IncidentReport/details/$incidentReportId"
    }

    object Proxy {
        private fun formatBpNumber(bp: String): String {
            val trimmed = bp.trim().uppercase()
            return if (trimmed.startsWith("BP") || trimmed.startsWith("CIV")) {
                trimmed
            } else {
                "BP$trimmed"
            }
        }

        fun getApplicantInfoByBPNumber(bpNumber: String): String =
            "$API_BASE_ROOT/Proxy/getApplicantInfoByBPNumber/${formatBpNumber(bpNumber)}"

        fun getApplicantRewardByBPNumber(bpNumber: String): String =
            "$API_BASE_ROOT/Proxy/getApplicantRewardByBPNumber/${formatBpNumber(bpNumber)}"

        fun getApplicantPunishmentByBPNumber(bpNumber: String): String =
            "$API_BASE_ROOT/Proxy/getApplicantPunishmemntByBPNumber/${formatBpNumber(bpNumber)}"

        fun getUserPostingDataByBPNumber(bpNumber: String): String =
            "$API_BASE_ROOT/Proxy/getUserPostingDataByBPNumber/${formatBpNumber(bpNumber)}"
    }

    object AgentAttendance {
        const val getMonthlyFirstAttendanceReport: String =
            "$API_BASE_ROOT/AgentAttendance/GetMonthlyFirstAttendanceReport"
        const val save: String = "$API_BASE_ROOT/AgentAttendance/save"
    }
}
