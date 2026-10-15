package com.optiscan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.theme.BackgroundNearBlack

/**
 * Screen G: Image Description Results
 * Presents scene understanding descriptions with spoken playback,
 * prominent recapture guidance when image is blurry or dark, and conversational follow-up triggers.
 */
@Composable
fun DescriptionResultScreen(
    description: String = MockData.SAMPLE_SCENE_DESCRIPTION,
    shouldRecapture: Boolean = false,
    recaptureReason: String? = null,
    isLiveResult: Boolean = true,
    isSpeaking: Boolean,
    onReadAloud: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onAskQuestion: (contextContent: String) -> Unit,
    onCaptureAnother: () -> Unit,
    onBackToHome: () -> Unit,
    onAnnounceResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        if (shouldRecapture) {
            onAnnounceResult("Recapture recommended. ${recaptureReason ?: "Image is too blurry or dark. Tap Retake Photo to try again."}")
        } else {
            onAnnounceResult("Scene description ready. Tap Read Aloud to listen.")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen Header
            ScreenTitle(
                title = if (shouldRecapture) "Recapture Photo" else "Scene Description",
                subtitle = if (shouldRecapture) "Image quality check notification" else "Surroundings and walking path analysis",
                onBackClick = onBackToHome,
                backContentDescription = "Return to Home screen"
            )

            if (shouldRecapture) {
                // High-visibility Recapture Warning
                StatusMessage(
                    title = "Recapture Recommended",
                    message = recaptureReason ?: "The captured image is too blurry or low quality for a reliable description.",
                    level = StatusLevel.WARNING
                )

                Spacer(modifier = Modifier.height(8.dp))

                OptiScanButton(
                    text = "Retake Photo",
                    onClick = onCaptureAnother,
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.Default.CameraAlt,
                    contentDesc = "Retake photo with camera"
                )

                OptiScanButton(
                    text = "Back to Home",
                    onClick = onBackToHome,
                    variant = ButtonVariant.OUTLINE,
                    icon = Icons.Default.Home,
                    contentDesc = "Return to Home screen button"
                )
            } else {
                // Result Card
                ResultCard(
                    title = "Your Surroundings",
                    content = description,
                    noticeText = if (isLiveResult) "AI Scene Analysis by Google Gemini" else "Simulated demonstration output"
                )

                // Speech Controls
                SpeechControls(
                    isSpeaking = isSpeaking,
                    onReadAloud = { onReadAloud(description) },
                    onStopSpeech = onStopSpeech,
                    readAloudText = "Read Aloud Description"
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Ask a Question
                OptiScanButton(
                    text = "Ask a Question About This Scene",
                    onClick = { onAskQuestion(description) },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDesc = "Ask a follow up question about this room description"
                )

                // Action: Capture Another Image
                OptiScanButton(
                    text = "Capture Another Scene",
                    onClick = onCaptureAnother,
                    variant = ButtonVariant.SURFACE,
                    icon = Icons.Default.CameraAlt,
                    contentDesc = "Capture another image button"
                )

                // Navigation: Back to Home
                OptiScanButton(
                    text = "Back to Home",
                    onClick = onBackToHome,
                    variant = ButtonVariant.OUTLINE,
                    icon = Icons.Default.Home,
                    contentDesc = "Return to Home screen button"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
