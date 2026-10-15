package com.optiscan.app.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.optiscan.app.ui.components.rememberAccessibleTapHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.CameraPreviewView
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen C: Camera
 * Full-screen viewfinder with live CameraX or fallback simulated state, flash toggle, and capture action.
 */
@Composable
fun CameraScreen(
    task: ScanTask,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit,
    onCaptureClicked: () -> Unit,
    onBackClick: () -> Unit,
    onInstructionSpoken: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onInstructionSpoken()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Back & Flash
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Back Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics {
                        this.role = Role.Button
                        this.contentDescription = "Back to Home screen"
                    }
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                }

                // Flash Toggle Button
                OptiScanButton(
                    text = if (flashEnabled) "Flash ON" else "Flash OFF",
                    onClick = { flashEnabled = !flashEnabled },
                    variant = if (flashEnabled) ButtonVariant.PRIMARY else ButtonVariant.SURFACE,
                    icon = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDesc = if (flashEnabled) "Camera Flash is turned on. Tap to turn off." else "Camera Flash is turned off. Tap to turn on.",
                    minHeight = 48,
                    modifier = Modifier.width(150.dp)
                )
            }

            // Task banner & instruction
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = task.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = ActionPrimaryBlue
                )
                Text(
                    text = "Point camera at object. Press volume button or tap Capture.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextWhite
                )
            }

            val viewfinderTapHandler = rememberAccessibleTapHandler(
                description = "Camera Viewfinder. Double tap for guidance. Triple tap anywhere or press volume key to capture photo",
                onAction = onCaptureClicked
            )

            // Viewfinder Area (Full-screen accessible tap target)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDarkCharcoal)
                    .border(2.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Capture photo from viewfinder",
                        onClick = viewfinderTapHandler
                    )
                    .semantics {
                        this.role = Role.Button
                        this.contentDescription = "Camera viewfinder. Press physical volume button or triple tap anywhere to capture photo."
                    }
            ) {
                if (hasCameraPermission) {
                    CameraPreviewView(
                        onPreviewViewCreated = onPreviewReady,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Simulated / Permission Needed Viewfinder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        StatusMessage(
                            message = "Camera access is not yet granted. You can grant permission or test with simulated capture.",
                            level = StatusLevel.WARNING,
                            title = "Camera Permission"
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OptiScanButton(
                            text = "Grant Camera Permission",
                            onClick = onRequestCameraPermission,
                            variant = ButtonVariant.PRIMARY,
                            contentDesc = "Request camera permission button"
                        )
                    }
                }
            }

            // Bottom Capture Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                OptiScanButton(
                    text = "Capture Photo",
                    onClick = onCaptureClicked,
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.Default.Camera,
                    contentDesc = "Capture photo button. Triple-tap or press physical volume key to take picture.",
                    minHeight = 72
                )
            }
        }
    }
}
