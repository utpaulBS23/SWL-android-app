package com.phq.swl.pioms.presentation.screens.submitted_reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmittedReportTypesScreen(
    onNavigateBack: () -> Unit,
    onSelectReportType: (ReportType) -> Unit
) {
    val reportTypes = listOf(
        ReportTypeItem("Special Report", Icons.Default.Star, ReportType.SPECIAL),
        ReportTypeItem("VR Workplace", Icons.Default.Work, ReportType.WORKPLACE),
        ReportTypeItem("VR Personal", Icons.Outlined.Person, ReportType.PERSONAL),
        ReportTypeItem("VR Academic", Icons.Default.School, ReportType.ACADEMIC),
        ReportTypeItem("Incident Report", Icons.Default.Warning, ReportType.INCIDENT),
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Submitted Reports", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0B4AA2)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            reportTypes.forEach { item ->
                SubmittedReportTypeCard(
                    item = item,
                    onTap = { onSelectReportType(item.reportType) }
                )
            }
        }
    }
}

data class ReportTypeItem(
    val title: String,
    val icon: ImageVector,
    val reportType: ReportType
)

@Composable
fun SubmittedReportTypeCard(
    item: ReportTypeItem,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .background(
                color = Color.White,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color(0xFFE8F1FF),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF1E3A8A)
                )
            }

            Text(
                text = item.title,
                maxLines = 1,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W800,
                color = Color(0xFF1E3A8A),
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFF1E3A8A)
            )
        }
    }
}
