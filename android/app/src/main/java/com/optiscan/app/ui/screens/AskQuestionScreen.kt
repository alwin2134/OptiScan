package com.optiscan.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.AlertError
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceCard
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen H: Ask a Question
 * Conversational voice assistant screen supporting voice input and accessible text fallback.
 */
@Composable
fun AskQuestionScreen(
    contextContent: String?,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onBackClick: () -> Unit,
    onAnnounceScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var typedQuestion by remember { mutableStateOf("") }
    var recognizedQuestion by remember { mutableStateOf<String?>(null) }
    var answerText by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onAnnounceScreen()
    }

    fun submitQuestion(query: String) {
        if (query.isBlank()) return
        recognizedQuestion = query
        val answer = MockData.answerQuestion(query, contextContent)
        answerText = answer
        onSpeak(answer)
    }

    fun triggerVoiceInput() {
        if (isListening) return
        coroutineScope.launch {
            isListening = true
            onSpeak("Listening for your question. Speak now.")
            delay(2200) // Simulated voice recognition delay
            isListening = false
            val simulatedQuery = if (contextContent?.contains("Acetaminophen", ignoreCase = true) == true) {
                "What is the recommended dosage?"
            } else {
                "Is the walking path ahead clear?"
            }
            submitQuestion(simulatedQuery)
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
            // Header
            ScreenTitle(
                title = "Ask a Question",
                subtitle = "Ask using voice or type below",
                onBackClick = onBackClick,
                backContentDescription = "Return to previous screen"
            )

            if (!contextContent.isNullOrBlank()) {
                StatusMessage(
                    message = "Context attached from previous analysis.",
                    level = StatusLevel.INFO,
                    title = "Active Context"
                )
            }

            // Central Voice Button Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = if (isListening) "Listening..." else "Tap to Speak Question",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Microphone Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(if (isListening) AlertError else ActionPrimaryBlue)
                        .border(3.dp, BorderHighlight, CircleShape)
                        .semantics {
                            this.role = Role.Button
                            this.contentDescription = if (isListening) {
                                "Currently listening for speech. Tap to cancel."
                            } else {
                                "Microphone button. Tap to ask your question aloud."
                            }
                        }
                ) {
                    if (isListening) {
                        CircularProgressIndicator(
                            color = TextWhite,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (isListening) {
                                isListening = false
                                onStopSpeech()
                            } else {
                                triggerVoiceInput()
                            }
                        },
                        modifier = Modifier.size(92.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isListening) "Listening to microphone... Speak clearly." else "Touch to activate voice input",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )
            }

            // Text Input Fallback
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDarkCharcoal, RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Or Type Your Question",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = typedQuestion,
                    onValueChange = { typedQuestion = it },
                    label = { Text("Enter question here", color = TextLightGrey) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        keyboardController?.hide()
                        submitQuestion(typedQuestion)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = ActionPrimaryBlue,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = ActionPrimaryBlue,
                        unfocusedLabelColor = TextLightGrey,
                        cursorColor = ActionPrimaryBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OptiScanButton(
                    text = "Send Question",
                    onClick = {
                        keyboardController?.hide()
                        submitQuestion(typedQuestion)
                    },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = typedQuestion.isNotBlank(),
                    contentDesc = "Send typed question button",
                    minHeight = 52
                )
            }

            // Results Section
            if (answerText != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (recognizedQuestion != null) {
                        StatusMessage(
                            message = "\"$recognizedQuestion\"",
                            level = StatusLevel.INFO,
                            title = "Question Asked"
                        )
                    }

                    ResultCard(
                        title = "Spoken Answer",
                        content = answerText ?: ""
                    )

                    SpeechControls(
                        isSpeaking = isSpeaking,
                        onReadAloud = { onSpeak(answerText ?: "") },
                        onStopSpeech = onStopSpeech,
                        readAloudText = "Read Answer Aloud"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
