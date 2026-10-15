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
import androidx.compose.material.icons.filled.Refresh
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
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen E: Processing
 * Displays large accessible progress indicator during active network analysis,
 * or an accessible error card with retry action if analysis fails.
 */
@Composable
fun ProcessingScreen(
    task: ScanTask,
    isProcessing: Boolean = true,
    errorMessage: String? = null,
    onStartAnalysis: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onAnnounceProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (task) {
        ScanTask.READ_TEXT -> "Reading printed text..."
        ScanTask.DESCRIBE_SURROUNDINGS -> "Analyzing surroundings..."
    }

    LaunchedEffect(task) {
        if (errorMessage == null) {
            onAnnounceProcessing()
            onStartAnalysis()
        }
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
                if (errorMessage != null) {
                    Text(
                        text = "Analysis Issue",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We could not complete the image analysis.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextLightGrey
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    StatusMessage(
                        title = "Service Notification",
                        message = errorMessage,
                        level = StatusLevel.ERROR
                    )
                } else {
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
                        statusText = if (isProcessing) statusText else "Preparing analysis...",
                        detailText = "Please hold steady. Results will be read aloud momentarily."
                    )
                }
            }

            // Bottom Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    OptiScanButton(
                        text = "Retry Analysis",
                        onClick = onRetry,
                        variant = ButtonVariant.PRIMARY,
                        icon = Icons.Default.Refresh,
                        contentDesc = "Retry image analysis button",
                        minHeight = 56
                    )

                    OptiScanButton(
                        text = "Return to Camera",
                        onClick = onCancel,
                        variant = ButtonVariant.SURFACE,
                        icon = Icons.Default.Close,
                        contentDesc = "Return to camera screen to retake photo",
                        minHeight = 56
                    )
                } else {
                    OptiScanButton(
                        text = "Cancel Analysis",
                        onClick = onCancel,
                        variant = ButtonVariant.SURFACE,
                        icon = Icons.Default.Close,
                        contentDesc = "Cancel image analysis and return to camera",
                        minHeight = 56
                    )
                }
            }
        }
    }
}
