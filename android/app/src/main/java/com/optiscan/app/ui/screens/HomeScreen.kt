package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.ui.components.AccessibleActionCard
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.ActionSecondaryGreen
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen B: Home
 * Displays three primary, full-width actions for TalkBack and low-vision accessibility.
 */
@Composable
fun HomeScreen(
    onNavigateToCamera: (ScanTask) -> Unit,
    onNavigateToAskQuestion: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onAnnounceHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onAnnounceHome()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundNearBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar with Title and Settings Icon
            ScreenTitle(
                title = "OptiScan",
                subtitle = "Select an action below",
                trailingContent = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(52.dp)
                            .semantics {
                                this.contentDescription = "Open Settings and Accessibility options"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action 1: Read Text
            AccessibleActionCard(
                title = "Read Text",
                subtitle = "Read labels, documents, medicine packaging, and signs.",
                icon = Icons.Default.Description,
                accentColor = ActionPrimaryBlue,
                onClick = { onNavigateToCamera(ScanTask.READ_TEXT) }
            )

            // Action 2: Describe Surroundings
            AccessibleActionCard(
                title = "Describe Surroundings",
                subtitle = "Describe objects, rooms, and walking paths.",
                icon = Icons.Default.Visibility,
                accentColor = ActionSecondaryGreen,
                onClick = { onNavigateToCamera(ScanTask.DESCRIBE_SURROUNDINGS) }
            )

            // Action 3: Ask a Question
            AccessibleActionCard(
                title = "Ask a Question",
                subtitle = "Ask spoken or typed questions about what you see.",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                accentColor = ActionPrimaryBlue,
                onClick = onNavigateToAskQuestion
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Secondary explicit Settings button at the bottom for easy reach
            OptiScanButton(
                text = "Settings & Preferences",
                onClick = onNavigateToSettings,
                variant = ButtonVariant.SURFACE,
                icon = Icons.Default.Settings,
                contentDesc = "Settings and accessibility preferences button"
            )
        }
    }
}
