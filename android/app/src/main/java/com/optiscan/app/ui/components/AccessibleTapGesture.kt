package com.optiscan.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Timeout window in milliseconds to group sequential taps.
 * 450ms provides comfortable motor timing for visually impaired users.
 */
const val ACCESSIBLE_TAP_TIMEOUT_MS = 450L

/**
 * Haptic feedback strengths for accessible multi-tap communication.
 */
enum class HapticType {
    SUBTLE_TICK,       // 1 tap: safety ignore acknowledgement
    DOUBLE_TICK,       // 2 taps: audio description readout
    CONFIRMED_ACTION   // 3 taps: action executed
}

/**
 * Composition locals for voice readouts, haptic feedback, and tap mode.
 */
val LocalAccessibleAnnouncer = staticCompositionLocalOf<(String) -> Unit> { {} }
val LocalHapticFeedbackHandler = staticCompositionLocalOf<(HapticType) -> Unit> { {} }
val LocalTripleTapModeEnabled = staticCompositionLocalOf<Boolean> { true }

/**
 * Reusable accessible tap gesture handler:
 * - 1 Tap: Ignored (subtle haptic tick, prevents accidental execution on flat glass).
 * - 2 Taps: Reads aloud what the button does via TTS.
 * - 3 Taps: Opens the page / executes the action.
 *
 * If tripleTapMode is false, single tap triggers onAction immediately.
 */
@Composable
fun rememberAccessibleTapHandler(
    description: String,
    enabled: Boolean = true,
    tripleTapMode: Boolean = LocalTripleTapModeEnabled.current,
    onAnnounce: (String) -> Unit = LocalAccessibleAnnouncer.current,
    onHaptic: (HapticType) -> Unit = LocalHapticFeedbackHandler.current,
    onAction: () -> Unit
): () -> Unit {
    val coroutineScope = rememberCoroutineScope()
    var tapCount by remember { mutableIntStateOf(0) }
    var resetJob by remember { mutableStateOf<Job?>(null) }

    return remember(enabled, tripleTapMode, description, onAction, onAnnounce, onHaptic) {
        {
            if (enabled) {
                if (!tripleTapMode) {
                    onHaptic(HapticType.CONFIRMED_ACTION)
                    onAction()
                } else {
                    resetJob?.cancel()
                    tapCount++

                    when (tapCount) {
                        1 -> {
                            // 1 Tap: Ignored for safety against accidental touch.
                            onHaptic(HapticType.SUBTLE_TICK)
                            resetJob = coroutineScope.launch {
                                delay(ACCESSIBLE_TAP_TIMEOUT_MS)
                                tapCount = 0
                            }
                        }
                        2 -> {
                            // 2 Taps: Spoken readout explaining what the button does.
                            onHaptic(HapticType.DOUBLE_TICK)
                            val announcement = if (description.contains("triple-tap", ignoreCase = true) ||
                                description.contains("triple tap", ignoreCase = true)
                            ) {
                                description
                            } else {
                                "$description. Triple tap to open."
                            }
                            onAnnounce(announcement)
                            resetJob = coroutineScope.launch {
                                delay(ACCESSIBLE_TAP_TIMEOUT_MS)
                                tapCount = 0
                            }
                        }
                        else -> {
                            // 3 Taps: Action confirmed and executed!
                            tapCount = 0
                            onHaptic(HapticType.CONFIRMED_ACTION)
                            onAction()
                        }
                    }
                }
            }
        }
    }
}
