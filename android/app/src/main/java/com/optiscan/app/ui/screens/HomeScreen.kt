package com.optiscan.app.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScreenState
import com.optiscan.app.ui.components.AccessibleButton
import com.optiscan.app.ui.components.CameraPreviewView
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.theme.DarkBackground
import com.optiscan.app.ui.theme.ErrorRed
import com.optiscan.app.ui.theme.LightText

/**
 * Main accessible screen for OptiScan assistant.
 */
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel,
    onRequestCameraPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Bar / Title
            Text(
                text = "OptiScan Assistant",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "OptiScan Visual Assistant Header"
                }
            )

            // Dynamic Content based on State
            when (val state = uiState) {
                is ScreenState.PermissionDenied -> {
                    PermissionDeniedView(onRequestPermission = onRequestCameraPermission)
                }

                is ScreenState.Processing -> {
                    ProcessingView(taskName = state.taskName)
                }

                is ScreenState.Capturing -> {
                    ProcessingView(taskName = "Capturing photo...")
                }

                is ScreenState.Error -> {
                    ErrorView(
                        errorMessage = state.errorMessage,
                        onRetry = { viewModel.resetToIdle() }
                    )
                }

                is ScreenState.Result -> {
                    ResultCard(
                        title = state.taskName,
                        content = state.text,
                        shouldRecapture = state.shouldRecapture,
                        recaptureReason = state.recaptureReason,
                        onRepeatSpeech = { viewModel.repeatSpeech() },
                        onRecapture = { viewModel.resetToIdle() }
                    )
                }

                is ScreenState.Idle -> {
                    // Camera viewfinder
                    CameraPreviewView(
                        onPreviewViewCreated = onPreviewReady,
                        modifier = Modifier.semantics {
                            contentDescription = "Live camera viewfinder preview"
                        }
                    )

                    Text(
                        text = "Aim camera at an object or text and select an action below.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LightText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Primary Accessible Action Buttons (Always visible and large)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AccessibleButton(
                    text = "Read Text",
                    onClick = { viewModel.onReadTextClicked(context) },
                    icon = Icons.Default.Description,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    contentDesc = "Action button: Read printed text from camera photo"
                )

                AccessibleButton(
                    text = "Describe Scene",
                    onClick = { viewModel.onDescribeImageClicked(context) },
                    icon = Icons.Default.Visibility,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    contentDesc = "Action button: Describe visual surroundings using AI"
                )

                if (uiState !is ScreenState.Idle && uiState !is ScreenState.PermissionDenied) {
                    AccessibleButton(
                        text = "Back to Camera",
                        onClick = { viewModel.resetToIdle() },
                        icon = Icons.Default.CameraAlt,
                        backgroundColor = Color.DarkGray,
                        contentColor = Color.White,
                        contentDesc = "Return to live camera view"
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingView(taskName: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black, MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Processing: $taskName. Please hold steady." }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = taskName,
                style = MaterialTheme.typography.titleLarge,
                color = LightText
            )
        }
    }
}

@Composable
private fun ErrorView(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, MaterialTheme.shapes.medium)
            .padding(16.dp)
            .semantics { contentDescription = "Error: $errorMessage" },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Notice",
                style = MaterialTheme.typography.titleLarge,
                color = ErrorRed,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = LightText
        )
        AccessibleButton(
            text = "Try Again",
            onClick = onRetry,
            icon = Icons.Default.Refresh,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PermissionDeniedView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "OptiScan requires camera access to read printed text and describe your surroundings.",
            style = MaterialTheme.typography.bodyLarge,
            color = LightText
        )
        AccessibleButton(
            text = "Grant Permission",
            onClick = onRequestPermission,
            backgroundColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            contentDesc = "Grant Camera Permission button"
        )
    }
}
