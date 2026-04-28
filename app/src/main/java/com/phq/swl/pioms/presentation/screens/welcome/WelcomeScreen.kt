package com.phq.swl.pioms.presentation.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phq.swl.pioms.presentation.theme.FaceNetAndroidTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onOpenFaceList: () -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: WelcomeScreenViewModel = koinViewModel()
    val userId by remember { viewModel.userIdState }

    FaceNetAndroidTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = "Welcome", style = MaterialTheme.typography.headlineSmall) },
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (userId.isBlank()) "Logged in" else "Logged in as: $userId",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenFaceList,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Open Face List")
                }
                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Logout")
                }
            }
        }
    }
}
