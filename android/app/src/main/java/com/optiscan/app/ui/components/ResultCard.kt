package com.optiscan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.ui.theme.DarkSurface
import com.optiscan.app.ui.theme.LightText
import com.optiscan.app.ui.theme.RecaptureText
import com.optiscan.app.ui.theme.RecaptureWarning

/**
 * Result display card showing AI description, OCR output, or recapture guidance.
 */
@Composable
fun ResultCard(
    title: String,
    content: String,
    shouldRecapture: Boolean,
    recaptureReason: String?,
    onRepeatSpeech: () -> Unit,
    onRecapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(2.dp, Color.White),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics {
                this.contentDescription = "$title. $content ${if (shouldRecapture) "Recapture recommended: $recaptureReason" else ""}"
            }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Recapture Prompt Banner (if image is blurry or poor quality)
            if (shouldRecapture) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RecaptureWarning, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = RecaptureText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTION REQUIRED: RETAKE PHOTO",
                            style = MaterialTheme.typography.titleLarge,
                            color = RecaptureText
                        )
                    }
                    if (!recaptureReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recaptureReason,
                            style = MaterialTheme.typography.bodyLarge,
                            color = RecaptureText
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AccessibleButton(
                        text = "Retake Photo Now",
                        onClick = onRecapture,
                        icon = Icons.Default.Refresh,
                        backgroundColor = Color.Black,
                        contentColor = Color.White,
                        contentDesc = "Retake photo button"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Main Text Output
            if (content.isNotBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LightText
                )

                Spacer(modifier = Modifier.height(16.dp))

                AccessibleButton(
                    text = "Repeat Spoken Output",
                    onClick = onRepeatSpeech,
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    backgroundColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    contentDesc = "Repeat spoken output aloud button"
                )
            }
        }
    }
}
