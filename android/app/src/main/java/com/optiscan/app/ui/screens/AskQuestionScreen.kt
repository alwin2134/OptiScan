package com.optiscan.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.speech.SpeechInputManager
import com.optiscan.app.speech.SpeechRecognitionState
import com.optiscan.app.ui.components.ButtonVariant
import com.optiscan.app.ui.components.OptiScanButton
import com.optiscan.app.ui.components.ResultCard
import com.optiscan.app.ui.components.ScreenTitle
import com.optiscan.app.ui.components.SpeechControls
import com.optiscan.app.ui.components.StatusLevel
import com.optiscan.app.ui.components.StatusMessage
import com.optiscan.app.ui.localization.AppStrings
import com.optiscan.app.ui.theme.ActionPrimaryBlue
import com.optiscan.app.ui.theme.AlertError
import com.optiscan.app.ui.theme.BackgroundNearBlack
import com.optiscan.app.ui.theme.BorderHighlight
import com.optiscan.app.ui.theme.BorderSubtle
import com.optiscan.app.ui.theme.SurfaceDarkCharcoal
import com.optiscan.app.ui.theme.TextLightGrey
import com.optiscan.app.ui.theme.TextWhite

/**
 * Screen H: Ask a Question
 * Conversational voice assistant screen supporting Hindi & English speech input,
 * microphone permission handling, accessible error recovery, and typed question fallback.
 */
@Composable
fun AskQuestionScreen(
    contextContent: String?,
    language: AppLanguage = AppLanguage.ENGLISH,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeech: () -> Unit,
    onSubmitQuestion: ((query: String, onAnswerReceived: (String) -> Unit) -> Unit)? = null,
    onBackClick: () -> Unit,
    onAnnounceScreen: () -> Unit,
    speechInputManagerProvider: ((Context) -> SpeechInputManager)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = AppStrings.get(language)
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechInputManager = remember {
        speechInputManagerProvider?.invoke(context) ?: SpeechInputManager(context)
    }

    var typedQuestion by remember { mutableStateOf("") }
    var recognizedQuestion by remember { mutableStateOf<String?>(null) }
    var answerText by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onAnnounceScreen()
    }

    fun submitQuestion(query: String) {
        if (query.isBlank()) return
        recognizedQuestion = query
        speechError = null
        if (onSubmitQuestion != null) {
            onSubmitQuestion(query) { answer ->
                answerText = answer
            }
        } else {
            val answer = MockData.answerQuestion(query, contextContent, language.code)
            answerText = answer
            onSpeak(answer)
        }
    }

    fun startListening() {
        onStopSpeech()
        speechError = null
        isListening = true
        onSpeak(strings.listeningNowSpoken)
        speechInputManager.startListening(language)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            val permMsg = strings.micPermissionRequiredWarning
            speechError = permMsg
            onSpeak(permMsg)
        }
    }

    DisposableEffect(speechInputManager, language) {
        speechInputManager.onStateChanged = { state ->
            when (state) {
                is SpeechRecognitionState.Ready -> {
                    isListening = true
                }
                is SpeechRecognitionState.Listening -> {
                    isListening = true
                }
                is SpeechRecognitionState.Success -> {
                    isListening = false
                    speechError = null
                    submitQuestion(state.recognizedText)
                }
                is SpeechRecognitionState.Error -> {
                    isListening = false
                    speechError = state.message
                    onSpeak(state.message)
                }
                is SpeechRecognitionState.Idle -> {
                    isListening = false
                }
            }
        }
        onDispose {
            speechInputManager.destroy()
        }
    }

    fun triggerVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                title = strings.askQuestionTitle,
                subtitle = strings.askQuestionScreenSubtitle,
                onBackClick = onBackClick,
                backContentDescription = strings.returnToPreviousScreen
            )

            if (!contextContent.isNullOrBlank()) {
                StatusMessage(
                    message = strings.activeContextMessage,
                    level = StatusLevel.INFO,
                    title = strings.activeContextTitle
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
                    text = if (isListening) strings.listeningTitle else strings.tapToSpeak,
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
                                strings.micListeningContentDescription
                            } else {
                                strings.micButtonContentDescription
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
                    IconButton(
                        onClick = {
                            if (isListening) {
                                speechInputManager.cancel()
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
                    text = if (isListening) strings.listeningMicrophonePrompt else strings.touchToActivateVoice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLightGrey
                )
            }

            // Speech Error Banner
            if (speechError != null) {
                StatusMessage(
                    title = strings.errorPrefix.trimEnd(':', ' '),
                    message = speechError ?: "",
                    level = StatusLevel.ERROR
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
                    text = strings.orTypeQuestion,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = typedQuestion,
                    onValueChange = { typedQuestion = it },
                    label = { Text(strings.enterQuestionHint, color = TextLightGrey) },
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
                    text = strings.sendQuestion,
                    onClick = {
                        keyboardController?.hide()
                        submitQuestion(typedQuestion)
                    },
                    variant = ButtonVariant.PRIMARY,
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = typedQuestion.isNotBlank(),
                    contentDesc = "${strings.sendQuestion} button",
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
                            title = strings.questionAskedTitle
                        )
                    }

                    ResultCard(
                        title = strings.spokenAnswerTitle,
                        content = answerText ?: ""
                    )

                    SpeechControls(
                        isSpeaking = isSpeaking,
                        onReadAloud = { onSpeak(answerText ?: "") },
                        onStopSpeech = onStopSpeech,
                        readAloudText = strings.readAnswerAloud
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
