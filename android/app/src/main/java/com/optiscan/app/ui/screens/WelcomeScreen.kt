package com.optiscan.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.R
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen A: Welcome
 * Welcomes the user with a high-contrast hero layout, spoken introduction, and Get Started trigger.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSpeakIntro: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onSpeakIntro()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp)
        ) {
            // High-Contrast Logo Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(118.dp)
                    .semantics { contentDescription = "OptiScan Vision Assistant Logo" }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(118.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Name
            Text(
                text = "OptiScan",
                style = MaterialTheme.typography.headlineLarge,
                color = TextWhite,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle
            Text(
                text = "See the world with your voice.",
                style = MaterialTheme.typography.titleMedium,
                color = TextLightGrey
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Accessible AI assistant for reading printed text and exploring your surroundings.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLightGrey,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Large Get Started Action pinned to bottom
        OptiScanButton(
            text = "Get Started",
            onClick = onGetStarted,
            variant = ButtonVariant.PRIMARY,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            contentDesc = "Get Started button. Double-tap to enter the main menu.",
            minHeight = 64,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
