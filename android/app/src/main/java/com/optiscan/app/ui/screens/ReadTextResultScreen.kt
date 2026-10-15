package com.optiscan.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.optiscan.app.ui.theme.TextLightGrey

/**
 * Screen F: Read Text Results
 * Displays extracted text from product labels or documents with Read Aloud, Copy, Q&A,
 * and high-visibility recapture guidance if the image is too blurry.
 */
@Composable
fun ReadTextResultScreen(
    text: String = MockData.SAMPLE_PRODUCT_LABEL,
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
    val context = LocalContext.current
    var showCopyBanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (shouldRecapture) {
            onAnnounceResult("Recapture recommended. ${recaptureReason ?: "Text is too blurry to read. Tap Retake Photo to try again."}")
        } else {
            onAnnounceResult("Text extracted from image. Tap Read Aloud to listen.")
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
                title = if (shouldRecapture) "Recapture Document" else "Extracted Text",
                subtitle = if (shouldRecapture) "Image quality check notification" else "Optical Character Recognition Results",
                onBackClick = onBackToHome,
                backContentDescription = "Return to Home screen"
            )

            if (shouldRecapture) {
                // High-visibility Recapture Warning
                StatusMessage(
                    title = "Recapture Recommended",
                    message = recaptureReason ?: "The text is too blurry or low contrast to read reliably.",
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
                // Copy feedback announcement banner
                if (showCopyBanner) {
                    StatusMessage(
                        message = "All extracted text copied to your clipboard.",
                        level = StatusLevel.SUCCESS,
                        title = "Copied"
                    )
                }

                // Text Result Card
                ResultCard(
                    title = "Document Text",
                    content = text,
                    noticeText = if (isLiveResult) "AI Text Extraction by Google Gemini" else "Simulated demonstration output",
                    onCopyClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OptiScan Extracted Text", text)
                        clipboard.setPrimaryClip(clip)
                        showCopyBanner = true
                    }
                )

                // Primary Read Aloud & Stop Speech Controls
                SpeechControls(
                    isSpeaking = isSpeaking,
                    onReadAloud = { onReadAloud(text) },
                    onStopSpeech = onStopSpeech,
                    readAloudText = "Read Aloud All Text"
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action: Ask a Question about this text
                OptiScanButton(
                    text = "Ask a Question About This Text",
                    onClick = { onAskQuestion(text) },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDesc = "Ask a question about the medicine label text"
                )

                // Action: Capture Another Image
                OptiScanButton(
                    text = "Capture Another Document",
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
