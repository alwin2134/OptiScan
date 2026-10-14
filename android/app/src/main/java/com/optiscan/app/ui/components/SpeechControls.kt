package com.optiscan.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Speech control action bar allowing user to trigger readout or silence speech.
 */
@Composable
fun SpeechControls(
    isSpeaking: Boolean,
    onReadAloud: () -> Unit,
    onStopSpeech: () -> Unit,
    modifier: Modifier = Modifier,
    readAloudText: String = "Read Aloud"
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (!isSpeaking) {
            OptiScanButton(
                text = readAloudText,
                onClick = onReadAloud,
                variant = ButtonVariant.SECONDARY,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDesc = "Read text aloud using text to speech",
                modifier = Modifier.weight(1f)
            )
        } else {
            OptiScanButton(
                text = "Stop Reading",
                onClick = onStopSpeech,
                variant = ButtonVariant.DANGER,
                icon = Icons.Default.Stop,
                contentDesc = "Stop active speech playback immediately",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
