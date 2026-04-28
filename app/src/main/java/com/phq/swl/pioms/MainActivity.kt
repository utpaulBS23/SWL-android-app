package com.phq.swl.pioms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phq.swl.pioms.presentation.screens.add_face.AddFaceScreen
import com.phq.swl.pioms.presentation.screens.assigned_task.AssignedTaskScreen
import com.phq.swl.pioms.presentation.screens.attendance.AttendanceScreen
import com.phq.swl.pioms.presentation.screens.complain_register.ComplainRegisterScreen
import com.phq.swl.pioms.presentation.screens.dashboard.DashboardScreen
import com.phq.swl.pioms.presentation.screens.dashboard.PlaceholderScreen
import com.phq.swl.pioms.presentation.screens.detect_screen.DetectScreen
import com.phq.swl.pioms.presentation.screens.face_registration.FaceRegistrationScreen
import com.phq.swl.pioms.presentation.screens.face_list.FaceListScreen
import com.phq.swl.pioms.presentation.screens.login.LoginScreen
import com.phq.swl.pioms.presentation.screens.special_report.SpecialReportScreen
import com.phq.swl.pioms.presentation.screens.incident_report.IncidentScreen
import com.phq.swl.pioms.presentation.screens.vr_report.AcademicScreen
import com.phq.swl.pioms.presentation.screens.vr_report.PersonalScreen
import com.phq.swl.pioms.presentation.screens.vr_report.VRReportScreen
import com.phq.swl.pioms.presentation.screens.vr_report.WorkplaceScreen
import com.phq.swl.pioms.presentation.screens.welcome.WelcomeScreen
import com.phq.swl.pioms.presentation.screens.submitted_reports.SubmittedReportsNavigationHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navHostController = rememberNavController()
            NavHost(
                navController = navHostController,
                startDestination = "login",
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable("add-face") { AddFaceScreen { navHostController.navigateUp() } }
                composable("detect") { DetectScreen { navHostController.navigate("face-list") } }
                composable("face-list") {
                    FaceListScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onAddFaceClick = { navHostController.navigate("add-face") },
                    )
                }
                composable("login") {
                    LoginScreen(
                        onLoginSuccess = {
                            navHostController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                    )
                }
                composable("dashboard") {
                    DashboardScreen(
                        onOpenAttendanceEntry = { navHostController.navigate("attendance") },
                        onOpenFaceRegistration = { navHostController.navigate("face-registration") },
                        onOpenSpecialReport = { navHostController.navigate("complain-register") },
                        onOpenVrReport = { navHostController.navigate("vr-report") },
                        onOpenIncidentReport = { navHostController.navigate("incident-report") },
                        onOpenComplainRegister = { navHostController.navigate("complain-register") },
                        onOpenAssignedTask = { navHostController.navigate("assigned-task") },
                        onOpenDraftReports = { navHostController.navigate("draft-report") },
                        onOpenSubmittedReports = { navHostController.navigate("submitted-reports") },
                        onLogout = {
                            navHostController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        },
                    )
                }
                composable("attendance") {
                    AttendanceScreen(onNavigateBack = { navHostController.navigateUp() })
                }
                composable("face-registration") {
                    FaceRegistrationScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onLogout = {
                            navHostController.navigate("login") {
                                popUpTo(navHostController.graph.startDestinationId) { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    "special-report/{complainId}/{complainNo}?applicantData={applicantData}",
                    arguments = listOf(
                        navArgument("complainId") { type = NavType.IntType },
                        navArgument("complainNo") { type = NavType.StringType },
                        navArgument("applicantData") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("complainId") ?: 0
                    val no = backStackEntry.arguments?.getString("complainNo") ?: ""
                    val applicantData = backStackEntry.arguments?.getString("applicantData")
                    SpecialReportScreen(
                        complainId = id,
                        complainNo = no,
                        applicantData = applicantData,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            navHostController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    )
                }
                composable(
                    "edit-special-report/{specialReportId}/{complainId}/{complainNo}",
                    arguments = listOf(
                        navArgument("specialReportId") { type = NavType.IntType },
                        navArgument("complainId") { type = NavType.IntType },
                        navArgument("complainNo") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val specialReportId = backStackEntry.arguments?.getInt("specialReportId") ?: 0
                    val complainId = backStackEntry.arguments?.getInt("complainId") ?: 0
                    val complainNo = backStackEntry.arguments?.getString("complainNo") ?: ""
                    SpecialReportScreen(
                        complainId = complainId,
                        complainNo = complainNo,
                        specialReportId = specialReportId,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            navHostController.navigate("submitted-reports") {
                                popUpTo("dashboard") { inclusive = false }
                            }
                        }
                    )
                }
                composable("vr-report") {
                    VRReportScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onOpenWorkplace = { navHostController.navigate("workplace-page/0/0") },
                        onOpenPersonal = { navHostController.navigate("personal-page/0/0") },
                        onOpenAcademic = { navHostController.navigate("academic-page/0/0") }
                    )
                }
                composable(
                    "workplace-page/{reportId}/{complainId}",
                    arguments = listOf(
                        navArgument("reportId") { type = NavType.IntType },
                        navArgument("complainId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val reportId = backStackEntry.arguments?.getInt("reportId") ?: 0
                    val complainId = backStackEntry.arguments?.getInt("complainId") ?: 0
                    WorkplaceScreen(
                        reportId = reportId,
                        complainId = complainId,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            val target = if (reportId == 0) "dashboard" else "submitted-reports"
                            navHostController.navigate(target) {
                                popUpTo("dashboard") { inclusive = target == "dashboard" }
                            }
                        }
                    )
                }
                composable(
                    "personal-page/{userInfoId}/{complainId}",
                    arguments = listOf(
                        navArgument("userInfoId") { type = NavType.IntType },
                        navArgument("complainId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val userInfoId = backStackEntry.arguments?.getInt("userInfoId") ?: 0
                    val complainId = backStackEntry.arguments?.getInt("complainId") ?: 0
                    PersonalScreen(
                        userInfoId = userInfoId,
                        complainId = complainId,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            val target = if (userInfoId == 0) "dashboard" else "submitted-reports"
                            navHostController.navigate(target) {
                                popUpTo("dashboard") { inclusive = target == "dashboard" }
                            }
                        }
                    )
                }
                composable(
                    "academic-page/{eduId}/{complainId}",
                    arguments = listOf(
                        navArgument("eduId") { type = NavType.IntType },
                        navArgument("complainId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val eduId = backStackEntry.arguments?.getInt("eduId") ?: 0
                    val complainId = backStackEntry.arguments?.getInt("complainId") ?: 0
                    AcademicScreen(
                        eduId = eduId,
                        complainId = complainId,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            val target = if (eduId == 0) "dashboard" else "submitted-reports"
                            navHostController.navigate(target) {
                                popUpTo("dashboard") { inclusive = target == "dashboard" }
                            }
                        }
                    )
                }
                composable("incident-report") {
                    IncidentScreen(
                        incidentReportId = null,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            navHostController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    )
                }
                composable(
                    "incident-report/{incidentReportId}",
                    arguments = listOf(
                        navArgument("incidentReportId") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val incidentReportId = backStackEntry.arguments?.getInt("incidentReportId")
                    IncidentScreen(
                        incidentReportId = incidentReportId,
                        onNavigateBack = { navHostController.navigateUp() },
                        onSaveSuccess = { 
                            navHostController.navigate("submitted-reports") {
                                popUpTo("dashboard") { inclusive = false }
                            }
                        }
                    )
                }
                composable("assigned-task") {
                    AssignedTaskScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onOpenTaskReportRoute = { route -> navHostController.navigate(route) },
                    )
                }
                composable("complain-register") {
                    ComplainRegisterScreen(
                        onNavigateBack = { navHostController.navigateUp() },
                        onOpenSpecialReport = { complainId, complainNo, applicantData ->
                            val encoded = java.net.URLEncoder.encode(applicantData, "UTF-8")
                            navHostController.navigate("special-report/$complainId/$complainNo?applicantData=$encoded")
                        }
                    )
                }
                composable("draft-report") {
                    PlaceholderScreen(title = "Draft Report", onNavigateBack = { navHostController.navigateUp() })
                }
                composable("submitted-reports") {
                    SubmittedReportsNavigationHost(
                        onNavigateBack = { navHostController.navigateUp() },
                        onNavigateToEdit = { report, type ->
                            val route = when (type) {
                                com.phq.swl.pioms.presentation.screens.submitted_reports.ReportType.SPECIAL -> {
                                    val specialReportId = report.rawData["specialReportId"]?.toString()?.toIntOrNull() ?: 0
                                    val complainId = report.rawData["complainId"]?.toString()?.toIntOrNull() ?: 0
                                    val complainNo = report.rawData["complainNo"]?.toString()?.takeIf { it.isNotBlank() } ?: "0"
                                    "edit-special-report/$specialReportId/$complainId/$complainNo"
                                }
                                com.phq.swl.pioms.presentation.screens.submitted_reports.ReportType.WORKPLACE -> {
                                    val workplaceReportId = report.rawData["workingPlaceReportId"]?.toString()?.toIntOrNull() ?: 0
                                    val complainId = report.rawData["complainId"]?.toString()?.toIntOrNull() ?: 0
                                    "workplace-page/$workplaceReportId/$complainId"
                                }
                                com.phq.swl.pioms.presentation.screens.submitted_reports.ReportType.PERSONAL -> {
                                    val userInfoId = report.rawData["userInfoId"]?.toString()?.toIntOrNull() ?: 0
                                    val complainId = report.rawData["complainId"]?.toString()?.toIntOrNull() ?: 0
                                    "personal-page/$userInfoId/$complainId"
                                }
                                com.phq.swl.pioms.presentation.screens.submitted_reports.ReportType.ACADEMIC -> {
                                    val eduId = report.rawData["userEducationInfoID"]?.toString()?.toIntOrNull() ?: 0
                                    val complainId = report.rawData["complainId"]?.toString()?.toIntOrNull() ?: 0
                                    "academic-page/$eduId/$complainId"
                                }
                                com.phq.swl.pioms.presentation.screens.submitted_reports.ReportType.INCIDENT -> {
                                    val id = report.rawData["incidentReportID"]?.toString()?.toIntOrNull() ?: 0
                                    "incident-report/$id"
                                }
                                else -> "dashboard"
                            }
                            navHostController.navigate(route)
                        }
                    )
                }
                composable("welcome") {
                    WelcomeScreen(
                        onOpenFaceList = { navHostController.navigate("face-list") },
                        onLogout = {
                            navHostController.navigate("login") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}
