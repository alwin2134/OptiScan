package com.optiscan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Large, accessible loading indicator with clear textual status and screen reader announcement.
 */
@Composable
fun LoadingIndicator(
    statusText: String,
    modifier: Modifier = Modifier,
    detailText: String? = "Please hold steady while your request is processed."
) {
    val accessibleLabel = "In progress: $statusText. $detailText"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
            .padding(vertical = 36.dp, horizontal = 24.dp)
            .semantics {
                this.liveRegion = LiveRegionMode.Polite
                this.contentDescription = accessibleLabel
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = ActionPrimaryBlue,
                strokeWidth = 6.dp,
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite
            )

            if (!detailText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )
            }
        }
    }
}
