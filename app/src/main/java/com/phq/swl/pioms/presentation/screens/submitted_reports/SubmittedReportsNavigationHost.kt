package com.phq.swl.pioms.presentation.screens.submitted_reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun SubmittedReportsNavigationHost(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (ReportItem, ReportType) -> Unit
) {
    val selectedReportType = remember { mutableStateOf<ReportType?>(null) }

    when (selectedReportType.value) {
        null -> {
            SubmittedReportTypesScreen(
                onNavigateBack = onNavigateBack,
                onSelectReportType = { reportType ->
                    selectedReportType.value = reportType
                }
            )
        }
        else -> {
            SubmittedReportsScreen(
                reportType = selectedReportType.value!!,
                onNavigateBack = {
                    selectedReportType.value = null
                },
                onNavigateToEdit = { report, type ->
                    onNavigateToEdit(report, type)
                }
            )
        }
    }
}
