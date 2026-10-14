package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.LoadingIndicator
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite
import kotlinx.coroutines.delay

/**
 * Screen E: Processing
 * Displays large accessible progress indicator and simulates analysis completion after a short delay.
 */
@Composable
fun ProcessingScreen(
    task: ScanTask,
    onProcessingComplete: () -> Unit,
    onCancel: () -> Unit,
    onAnnounceProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (task) {
        ScanTask.READ_TEXT -> "Reading printed text..."
        ScanTask.DESCRIBE_SURROUNDINGS -> "Analyzing surroundings..."
    }

    // Speak announcement once on mount
    LaunchedEffect(Unit) {
        onAnnounceProcessing()
        // Simulate processing time for demo (1.8 seconds)
        delay(1800)
        onProcessingComplete()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "OptiScan Processing",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Analyzing your image",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextLightGrey
                )

                Spacer(modifier = Modifier.height(36.dp))

                LoadingIndicator(
                    statusText = statusText,
                    detailText = "Please hold steady. Results will be read aloud momentarily."
                )
            }

            // Cancel button
            OptiScanButton(
                text = "Cancel Analysis",
                onClick = onCancel,
                variant = ButtonVariant.SURFACE,
                icon = Icons.Default.Close,
                contentDesc = "Cancel image analysis and return to camera",
                minHeight = 56,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
