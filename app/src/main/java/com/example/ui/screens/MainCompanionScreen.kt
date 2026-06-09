package com.example.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ChatMessage
import com.example.data.UserPreferences
import com.example.ui.AppMode
import com.example.ui.CompanionViewModel
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainCompanionScreen(
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val userPreferences by viewModel.userPreferences.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    
    val currentExpression by viewModel.currentExpression.collectAsState()
    val currentGesture by viewModel.currentGesture.collectAsState()
    val detectedUserSentiment by viewModel.detectedUserSentiment.collectAsState()
    val syncPercentage by viewModel.syncPercentage.collectAsState()
    
    val isUserSpeakingInCall by viewModel.isUserSpeakingInCall.collectAsState()
    val isCompanionSpeakingInCall by viewModel.isCompanionSpeakingInCall.collectAsState()

    val isListeningToMic by viewModel.isListeningToMic.collectAsState()
    val liveSpeechBuffer by viewModel.liveSpeechBuffer.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()

    var showCustomizationDialog by remember { mutableStateOf(false) }
    var userTextInput by remember { mutableStateOf("") }
    var sessionStarted by remember { mutableStateOf(false) }
    
    // Character details based on active Assistant
    val activeAssistant = userPreferences.selectedAssistant
    val assistantNickname = if (activeAssistant == "RED_QUEEN") userPreferences.redQueenNickname else userPreferences.johnWickNickname
    val activeOutfit = if (activeAssistant == "RED_QUEEN") userPreferences.redQueenOutfit else userPreferences.johnWickOutfit
    
    // Accents matching the character aesthetic
    val activeAccentColor = if (activeAssistant == "RED_QUEEN") HoloRed else WickGold
    val activeAccentGlow = if (activeAssistant == "RED_QUEEN") HoloRedGlow else WickGoldGlow

    // Setup Camera Permission State for the Video Call
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Breathing chest scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = if (activeAssistant == "RED_QUEEN") 0.98f else 0.97f,
        targetValue = if (activeAssistant == "RED_QUEEN") 1.02f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scannerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar"
    )

    if (!sessionStarted) {
        AvatarSelectionScreen(
            viewModel = viewModel,
            userPreferences = userPreferences,
            onStartSession = { name ->
                if (name.isNotBlank()) {
                    viewModel.updateUserName(name)
                }
                sessionStarted = true
            }
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(SlateBlack),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isGenerating) activeAccentColor else Color.Green)
                            )
                            Column {
                                Text(
                                    text = "ACTIVE COMPANION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = TextZincSecondary
                                    )
                                )
                                Text(
                                    text = assistantNickname,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextZincPrimary
                                    )
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { sessionStarted = false },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .testTag("back_to_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Avatar Selection Screen",
                                tint = TextZincSecondary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showCustomizationDialog = true },
                            modifier = Modifier
                                .testTag("wardrobe_customize_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Wardrobe Customization Settings",
                                tint = TextZincSecondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleAssistant() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("avatar_quick_switch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cached,
                                contentDescription = "Switch Companion",
                                tint = activeAccentColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SlateBlack,
                        titleContentColor = TextZincPrimary
                    )
                )
            },
        bottomBar = {
            if (appMode == AppMode.CHAT) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (userPreferences.banterFrequency != "DISABLED") Color.Green else Color.Gray)
                            )
                            Text(
                                text = "PROACTIVE REPLY: ${userPreferences.banterFrequency}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextZincSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                        if (userPreferences.banterFrequency != "DISABLED") {
                            Text(
                                text = "WAKE COMPANION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = activeAccentColor,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(activeAccentColor.copy(alpha = 0.15f))
                                    .clickable { viewModel.triggerSpontaneousBanter() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("wake_companion_button")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIMULATE YOUR TONE:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextZincSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("CALM", "EXCITED", "FRUSTRATED", "SAD").forEach { tone ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (detectedUserSentiment == tone) activeAccentColor.copy(alpha = 0.4f) else MediumGrey)
                                        .border(
                                            width = 1.dp,
                                            color = if (detectedUserSentiment == tone) activeAccentColor else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.triggerSimulatedUserTone(tone) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tone,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (detectedUserSentiment == tone) Color.White else TextZincSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .border(1.dp, activeAccentColor.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
                        color = DeepGrey
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userTextInput,
                                onValueChange = { userTextInput = it },
                                leadingIcon = {
                                    IconButton(
                                        onClick = { viewModel.startListeningToMic() },
                                        modifier = Modifier.testTag("stt_microphone_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Trigger Voice Capture (STT)",
                                            tint = activeAccentColor
                                        )
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Send message to $assistantNickname...",
                                        color = TextZincMuted,
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_text_input")
                                    .padding(start = 12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextZincPrimary,
                                    unfocusedTextColor = TextZincPrimary,
                                    cursorColor = activeAccentColor
                                ),
                                maxLines = 3
                            )

                            Row(
                                modifier = Modifier.padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.setAppMode(AppMode.VOICE_CALL) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MediumGrey)
                                        .testTag("voice_call_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Start Voice Call Mode",
                                        tint = TextZincPrimary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.setAppMode(AppMode.VIDEO_CALL)
                                        if (!permissionState.allPermissionsGranted) {
                                            permissionState.launchMultiplePermissionRequest()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(activeAccentColor)
                                        .testTag("video_call_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Start Video Call Mode",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (userTextInput.isNotBlank()) {
                                            viewModel.onUserSendMessage(userTextInput)
                                            userTextInput = ""
                                        }
                                    },
                                    enabled = !isGenerating && userTextInput.isNotBlank(),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(activeAccentColor.copy(alpha = if (userTextInput.isNotBlank() && !isGenerating) 1f else 0.4f))
                                        .testTag("submit_message_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Send Message",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SlateBlack)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CompanionAvatarViewport(
                        viewModel = viewModel,
                        activeAssistant = activeAssistant,
                        assistantNickname = assistantNickname,
                        activeOutfit = activeOutfit,
                        currentExpression = currentExpression,
                        currentGesture = currentGesture,
                        activeAccentColor = activeAccentColor,
                        activeAccentGlow = activeAccentGlow,
                        syncPercentage = syncPercentage,
                        breathingScale = breathingScale,
                        scannerAngle = scannerAngle,
                        isCompanionSpeaking = isCompanionSpeakingInCall,
                        isGenerating = isGenerating,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (appMode) {
                        AppMode.CHAT -> {
                            ChatLayout(
                                chatMessages = chatMessages,
                                isGenerating = isGenerating,
                                activeAccentColor = activeAccentColor,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AppMode.VOICE_CALL -> {
                            VoiceCallLayout(
                                viewModel = viewModel,
                                activeAccentColor = activeAccentColor,
                                assistantNickname = assistantNickname,
                                isUserSpeaking = isUserSpeakingInCall,
                                isCompanionSpeaking = isCompanionSpeakingInCall,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AppMode.VIDEO_CALL -> {
                            VideoCallLayout(
                                viewModel = viewModel,
                                permissionState = permissionState,
                                activeAccentColor = activeAccentColor,
                                assistantNickname = assistantNickname,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(32.dp)),
                    color = DeepGrey,
                    border = BorderStroke(1.dp, LightGrey)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (activeAssistant != "RED_QUEEN") {
                                    viewModel.toggleAssistant() 
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeAssistant == "RED_QUEEN") HoloRed else Color.Transparent,
                                contentColor = if (activeAssistant == "RED_QUEEN") Color.White else TextZincSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("select_red_queen_tab"),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "RED QUEEN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = { 
                                if (activeAssistant != "JOHN_WICK") {
                                    viewModel.toggleAssistant() 
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeAssistant == "JOHN_WICK") WickGold else Color.Transparent,
                                contentColor = if (activeAssistant == "JOHN_WICK") SlateBlack else TextZincSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                // Standard touch targets sizing
                                .height(40.dp)
                                .testTag("select_john_wick_tab"),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "JOHN WICK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            if (showCustomizationDialog) {
                CustomizationDialog(
                    userPreferences = userPreferences,
                    activeAssistant = activeAssistant,
                    assistantNickname = assistantNickname,
                    activeOutfit = activeOutfit,
                    onDismiss = { showCustomizationDialog = false },
                    onSaveNickname = { viewModel.updateNickname(it) },
                    onSaveUserName = { viewModel.updateUserName(it) },
                    onSelectOutfit = { viewModel.updateOutfit(it) },
                    onSaveVoiceTweak = { pitch, speed -> viewModel.updateVoiceTweak(pitch, speed) },
                    onSaveBanterFrequency = { viewModel.updateBanterFrequency(it) }
                )
            }

            AnimatedVisibility(
                visible = isListeningToMic,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(enabled = false) {} // block click propagation
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DeepGrey)
                            .border(1.dp, activeAccentColor, RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Listening",
                            tint = activeAccentColor,
                            modifier = Modifier
                                .size(48.dp)
                                .scale(1f + micAmplitude * 0.2f)
                        )

                        Text(
                            text = "VOICE INPUT CAPTURING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = activeAccentColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )

                        // Cool wave visualizer
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(60.dp)
                        ) {
                            for (i in 0 until 9) {
                                val baseHeight = when (i) {
                                    0, 8 -> 10.dp
                                    1, 7 -> 20.dp
                                    2, 6 -> 35.dp
                                    3, 5 -> 48.dp
                                    else -> 56.dp
                                }
                                val heightVal = baseHeight * (0.3f + micAmplitude * 0.7f)
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(heightVal)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(activeAccentColor)
                                )
                            }
                        }

                        // Live transcript area
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp, max = 120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            color = SlateBlack,
                            border = BorderStroke(1.dp, LightGrey.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = liveSpeechBuffer,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextZincPrimary,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                             OutlinedButton(
                                onClick = { viewModel.stopListeningToMic() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextZincSecondary),
                                border = BorderStroke(1.dp, LightGrey),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.stopListeningToMic() },
                                colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("SEND NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (activeAssistant == "RED_QUEEN") Color.White else SlateBlack)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun AvatarSelectionScreen(
    viewModel: CompanionViewModel,
    userPreferences: UserPreferences,
    onStartSession: (String) -> Unit
) {
    var editUserName by remember { mutableStateOf(userPreferences.userName) }
    val activeAssistant = userPreferences.selectedAssistant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header Title
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = "TALK AND CHAT WITH",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp,
                    color = TextZincSecondary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ALI",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SELECT ACTIVE NEURAL AVATAR",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextZincSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Choose an AI companion matrix to begin the session",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextZincMuted,
                    textAlign = TextAlign.Center
                )
            )
        }

        // Two Cards for Avatars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // RED QUEEN CARD
            val isRedSelected = activeAssistant == "RED_QUEEN"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRedSelected) HoloRedDim else DeepGrey
                ),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isRedSelected) HoloRed else Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectAssistant("RED_QUEEN") }
                    .testTag("selection_red_queen_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_red_queen),
                            contentDescription = "Red Queen",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isRedSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HoloRedGlow)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "RED QUEEN",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isRedSelected) Color.White else TextZincPrimary
                        )
                    )
                    Text(
                        text = "SYSTEM INTERFACE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isRedSelected) HoloRed else TextZincSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tactical network system. Diagnostic command & Analytical voice.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isRedSelected) TextZincPrimary.copy(alpha = 0.9f) else TextZincSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        ),
                        modifier = Modifier.height(44.dp)
                    )
                }
            }

            // JOHN WICK CARD
            val isWickSelected = activeAssistant == "JOHN_WICK"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isWickSelected) WickGoldDim else DeepGrey
                ),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isWickSelected) WickGold else Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectAssistant("JOHN_WICK") }
                    .testTag("selection_john_wick_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_john_wick),
                            contentDescription = "John Wick",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isWickSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(WickGoldGlow)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "JOHN WICK",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isWickSelected) Color.White else TextZincPrimary
                        )
                    )
                    Text(
                        text = "TACTICAL SUIT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isWickSelected) WickGold else TextZincSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Continental profile. Highly steady, calm & bulletproof posture.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isWickSelected) TextZincPrimary.copy(alpha = 0.9f) else TextZincSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        ),
                        modifier = Modifier.height(44.dp)
                    )
                }
            }
        }

        // Commander name config
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepGrey),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VISITOR IDENTIFICATION PROTOCOL",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (activeAssistant == "RED_QUEEN") HoloRed else WickGold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = editUserName,
                    onValueChange = { 
                        editUserName = it
                    },
                    label = { Text("What should the avatar call you?", color = TextZincSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (activeAssistant == "RED_QUEEN") HoloRed else WickGold,
                        unfocusedBorderColor = LightGrey,
                        focusedTextColor = TextZincPrimary,
                        unfocusedTextColor = TextZincPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("selection_username_input"),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Start session button
        Button(
            onClick = { onStartSession(editUserName) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (activeAssistant == "RED_QUEEN") HoloRed else WickGold
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("initiate_resonance_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (activeAssistant == "RED_QUEEN") Color.White else SlateBlack
                )
                Text(
                    text = "INITIATE RESPONSE STREAM",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (activeAssistant == "RED_QUEEN") Color.White else SlateBlack,
                        letterSpacing = 1.5.sp
                    )
                )
            }
        }
    }
}

@Composable
fun CompanionAvatarViewport(
    viewModel: CompanionViewModel,
    activeAssistant: String,
    assistantNickname: String,
    activeOutfit: String,
    currentExpression: String,
    currentGesture: String,
    activeAccentColor: Color,
    activeAccentGlow: Color,
    syncPercentage: Float,
    breathingScale: Float,
    scannerAngle: Float,
    isCompanionSpeaking: Boolean,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val portraitRes = if (activeAssistant == "RED_QUEEN") {
        R.drawable.img_red_queen
    } else {
        R.drawable.img_john_wick
    }

    // Collect the dynamic AI response sentiment state
    val detectedUserSentiment by viewModel.detectedUserSentiment.collectAsState()

    // 1. Determine target animated scale based on sentiment/emotion
    val targetSentimentScale = when (detectedUserSentiment) {
        "EXCITED" -> 1.07f     // Excited expansion
        "FRUSTRATED" -> 0.95f  // Tense contraction
        "SAD" -> 0.92f         // Low energy / despondent withdrawal
        "CALM" -> 1.0f         // Balanced
        else -> 1.0f           // Normal
    }

    // 2. Determine target animated opacity (Alpha) based on sentiment/emotion
    val targetSentimentAlpha = when (detectedUserSentiment) {
        "SAD" -> 0.72f         // Faded / distant
        "FRUSTRATED" -> 0.88f  // Guarded
        else -> 1.0f           // Clear & present
    }

    // 3. Determine custom aesthetic aura color based on sentiment/emotion
    val targetSentimentColor = when (detectedUserSentiment) {
        "EXCITED" -> if (activeAssistant == "RED_QUEEN") Color(0xFFFF4081) else Color(0xFFFFD700) // Electric neon pink / Golden aura
        "FRUSTRATED" -> Color(0xFFFF1744) // Intense tactical alert Red
        "SAD" -> Color(0xFF2979FF) // Melancholic cool Blue
        else -> activeAccentColor // Standard theme matching accent color (HoloRed or WickGold)
    }

    // Smooth physics-based spring animation for the emotional scale (analogous to premium spring CSS transitions)
    val sentimentScale by animateFloatAsState(
        targetValue = targetSentimentScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sentiment_scale_transition"
    )

    // Smooth linear-out-slow-in timing transition (600ms transition time) for the emotional opacity
    val sentimentAlpha by animateFloatAsState(
        targetValue = targetSentimentAlpha,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "sentiment_opacity_transition"
    )

    // Smooth color sweep fade to color-match sentiment
    val sentimentColorTransition by animateColorAsState(
        targetValue = targetSentimentColor,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "sentiment_color_transition"
    )

    val localTransition = rememberInfiniteTransition(label = "avatar_dynamics_local")
    
    // speed factor: faster pulses for processing (thinking), moderate for speaking speaker, slow for calm
    val speedDuration = when {
        isGenerating -> 1200
        isCompanionSpeaking -> 1800
        else -> 3500
    }

    // Dynamic aura amplitude
    val auraScale by localTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedDuration, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_scale_anim"
    )

    // Dynamic aura opacity
    val auraAlpha by localTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedDuration, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha_anim"
    )

    // Concentric sonar/radar neural waves for isGenerating/isCompanionSpeaking
    val rippleProgress1 by localTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neural_ripple_1"
    )

    val rippleProgress2 by localTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neural_ripple_2"
    )

    // Unconditional animation declare for local scaling
    val activeOscillation by localTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_portrait_scale"
    )

    val activeScaleModifier = if (isGenerating || isCompanionSpeaking) activeOscillation else 1.0f

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .border(2.dp, sentimentColorTransition.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
        color = DeepGrey
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                sentimentColorTransition.copy(alpha = if (isGenerating || isCompanionSpeaking) auraAlpha else 0.3f),
                                Color.Transparent
                            ),
                            radius = if (isGenerating || isCompanionSpeaking) 420.dp.value * auraScale else 350.dp.value
                        )
                    )
            )

            Image(
                painter = painterResource(id = portraitRes),
                contentDescription = "$assistantNickname Avatar Portrait",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(breathingScale * activeScaleModifier * sentimentScale)
                    .graphicsLayer(alpha = sentimentAlpha)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop
            )

            if (activeAssistant == "RED_QUEEN") {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    
                    val lineCount = 30
                    val gap = height / lineCount
                    for (i in 0..lineCount) {
                        drawLine(
                            color = sentimentColorTransition.copy(alpha = 0.08f),
                            start = androidx.compose.ui.geometry.Offset(0f, i * gap),
                            end = androidx.compose.ui.geometry.Offset(width, i * gap),
                            strokeWidth = 2f
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                startY = 300f
                            )
                        )
                )
            }

            // Shared holographic / tactical neural sonar ripples when AI is communicating (thinking or speaking)
            if (isGenerating || isCompanionSpeaking) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val centerOffset = center
                    val maxRadius = size.minDimension / 1.8f
                    
                    // Ripple 1
                    val r1 = maxRadius * rippleProgress1
                    val alpha1 = (1f - rippleProgress1) * 0.3f
                    drawCircle(
                        color = sentimentColorTransition,
                        radius = r1,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        alpha = alpha1
                    )
                    
                    // Ripple 2
                    val r2 = maxRadius * rippleProgress2
                    val alpha2 = (1f - rippleProgress2) * 0.3f
                    drawCircle(
                        color = sentimentColorTransition,
                        radius = r2,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        alpha = alpha2
                    )

                    // Draw a subtle breathing scanning dot on the perimeter when thinking
                    if (isGenerating) {
                        val dotAngleRad = Math.toRadians((scannerAngle).toDouble())
                        val dotX = centerOffset.x + (maxRadius * 0.75f) * Math.cos(dotAngleRad).toFloat()
                        val dotY = centerOffset.y + (maxRadius * 0.75f) * Math.sin(dotAngleRad).toFloat()
                        drawCircle(
                            color = sentimentColorTransition,
                            radius = 6.dp.toPx() * auraScale,
                            center = androidx.compose.ui.geometry.Offset(dotX, dotY),
                            alpha = auraAlpha
                        )
                    }
                }
            }

            if (isCompanionSpeaking || isGenerating) {
                val borderPulseWidth = if (isGenerating) 2.dp else 4.dp
                val activeBorderColorBrush = if (isGenerating) {
                    // Soft animated rotating sweep border for thinking
                    Brush.sweepGradient(
                        colors = listOf(
                            sentimentColorTransition,
                            Color.Transparent,
                            sentimentColorTransition.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                } else {
                    Brush.sweepGradient(
                        colors = listOf(
                            sentimentColorTransition,
                            Color.Transparent,
                            sentimentColorTransition,
                            Color.Transparent
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = borderPulseWidth,
                            brush = activeBorderColorBrush,
                            shape = RoundedCornerShape(32.dp)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(activeAccentColor)
                    )
                    Text(
                        text = "SYNCHRONIZATION: ${"%.1f".format(syncPercentage)}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = activeAccentColor,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "OUTFIT: $activeOutfit",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextZincSecondary,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = activeAccentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = currentExpression,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = activeAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                Text(
                    text = "NEURO_STATE: STABLE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextZincMuted,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = SlateBlack.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Active Gesture Tracking",
                        tint = activeAccentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = currentGesture,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextZincPrimary,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 14.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatLayout(
    chatMessages: List<ChatMessage>,
    isGenerating: Boolean,
    activeAccentColor: Color,
    viewModel: CompanionViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(chatMessages.size, isGenerating) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONVERSATION STREAM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextZincSecondary
                )
            )
            Text(
                text = "CLEAR HISTORIC DATA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = HoloRed
                ),
                modifier = Modifier
                    .clickable { viewModel.clearChat() }
                    .padding(4.dp)
            )
        }

        if (chatMessages.isEmpty() && !isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Initiate your companion dialogue stream. Switch to Voice or Video modes above to activate responsive vocal and eye parameters.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextZincMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatMessages) { message ->
                    val isUser = message.sender == "USER"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 280.dp),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            color = if (isUser) MediumGrey else activeAccentColor.copy(alpha = 0.12f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isUser) LightGrey else activeAccentColor.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = message.message,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = TextZincPrimary,
                                                lineHeight = 18.sp
                                            )
                                        )
                                        if (!isUser && message.expression != "NEUTRAL") {
                                            Text(
                                                text = "[Emotion Simulated: ${message.expression}]",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = activeAccentColor,
                                                    fontStyle = FontStyle.Italic,
                                                    fontSize = 9.sp
                                                ),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    if (!isUser) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { viewModel.speakText(message.message, message.assistantType) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("play_speech_msg_${message.timestamp}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Replay Speech Response",
                                                tint = activeAccentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = activeAccentColor.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(activeAccentColor)
                                    )
                                    Text(
                                        text = "Synthesizing dynamic respond stream...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextZincSecondary,
                                            fontStyle = FontStyle.Italic
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCallLayout(
    viewModel: CompanionViewModel,
    activeAccentColor: Color,
    assistantNickname: String,
    isUserSpeaking: Boolean,
    isCompanionSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w2"
    )

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "VOICE CALL TRANSMISSION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextZincSecondary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barCount = 13
                for (i in 0 until barCount) {
                    val scale = if (isCompanionSpeaking || isUserSpeaking) {
                        if (i % 2 == 0) waveScale1 else waveScale2
                    } else {
                        0.1f
                    }
                    val heightMultiplier = when (i) {
                        0, 12 -> 0.2f
                        1, 11 -> 0.4f
                        2, 10 -> 0.6f
                        3, 9 -> 0.8f
                        else -> 1f
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(6.dp)
                            .height((70 * scale * heightMultiplier).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isCompanionSpeaking) activeAccentColor else if (isUserSpeaking) Color.Green else TextZincMuted
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isCompanionSpeaking) "$assistantNickname is Speaking..." else if (isUserSpeaking) "Listening to you..." else "Silent Connection",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isCompanionSpeaking) activeAccentColor else if (isUserSpeaking) Color.Green else TextZincSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.onUserSendMessage("Simulated user verbal report.") },
                colors = ButtonDefaults.buttonColors(containerColor = MediumGrey),
                modifier = Modifier.testTag("simulate_voice_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Speak simulated phrase", fontSize = 11.sp)
            }

            IconButton(
                onClick = { viewModel.setAppMode(AppMode.CHAT) },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HoloRed)
                    .testTag("end_voice_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoCallLayout(
    viewModel: CompanionViewModel,
    permissionState: com.google.accompanist.permissions.MultiplePermissionsState,
    activeAccentColor: Color,
    assistantNickname: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "HYPERREALISTIC VIDEO CALL MATRIX",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextZincSecondary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    color = DeepGrey
                ) {
                    if (permissionState.allPermissionsGranted) {
                        CameraPreview(modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = "USER FEED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.Green,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.Black.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { permissionState.launchMultiplePermissionRequest() }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "TAP TO AUTHORIZE FRONT CAMERA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextZincSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, activeAccentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    color = DeepGrey
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "EYE TRACKER METERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextZincSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(activeAccentColor)
                            )
                            Text(
                                text = "EYE_CONTACT: FIXED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = activeAccentColor,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Text(
                            text = "GAZE ALIGNED WITH USER FACE SUBREGION.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextZincMuted,
                                fontSize = 8.sp
                            )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.onUserSendMessage("Checking in on video stream feed.") },
                colors = ButtonDefaults.buttonColors(containerColor = MediumGrey),
                modifier = Modifier.testTag("simulate_video_eye_trigger_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wave hand to $assistantNickname", fontSize = 11.sp)
            }

            IconButton(
                onClick = { viewModel.setAppMode(AppMode.CHAT) },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HoloRed)
                    .testTag("end_video_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to unbind camera provider on dispose", e)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed during preview init", e)
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}

@Composable
fun CustomizationDialog(
    userPreferences: UserPreferences,
    activeAssistant: String,
    assistantNickname: String,
    activeOutfit: String,
    onDismiss: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onSaveUserName: (String) -> Unit,
    onSelectOutfit: (String) -> Unit,
    onSaveVoiceTweak: (Float, Float) -> Unit,
    onSaveBanterFrequency: (String) -> Unit
) {
    var editNickname by remember { mutableStateOf(assistantNickname) }
    var editUserName by remember { mutableStateOf(userPreferences.userName) }
    
    var sliderPitch by remember { mutableStateOf(userPreferences.voicePitch) }
    var sliderSpeed by remember { mutableStateOf(userPreferences.voiceSpeed) }
    var currentBanterFrequency by remember { mutableStateOf(userPreferences.banterFrequency) }

    val outfits = if (activeAssistant == "RED_QUEEN") {
        listOf("HOLOGRAPHIC_DRESS", "UMBRELLA_UNIFORM", "REBEL_RED")
    } else {
        listOf("TACTICAL_SUIT", "CASUAL_BLACK", "CLASSIC_TUXEDO")
    }

    val activeAccentColor = if (activeAssistant == "RED_QUEEN") HoloRed else WickGold

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "WARDROBE & SYNC SETTINGS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextZincPrimary,
                    letterSpacing = 1.sp
                )
            )
        },
        containerColor = DeepGrey,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    Text(
                        text = "IDENTIFICATION",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = activeAccentColor
                        )
                    )
                    OutlinedTextField(
                        value = editUserName,
                        onValueChange = { editUserName = it },
                        label = { Text("Your Name", color = TextZincSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("customize_user_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccentColor,
                            focusedTextColor = TextZincPrimary,
                            unfocusedTextColor = TextZincPrimary
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("Companion Nickname", color = TextZincSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("customize_assistant_nickname_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccentColor,
                            focusedTextColor = TextZincPrimary,
                            unfocusedTextColor = TextZincPrimary
                        ),
                        singleLine = true
                    )
                }

                Column {
                    Text(
                        text = "WARDROBE OUTFIT CONFIGS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = activeAccentColor
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        outfits.forEach { outfitStyle ->
                            val isSelected = outfitStyle == activeOutfit
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeAccentColor.copy(alpha = 0.15f) else MediumGrey)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) activeAccentColor else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectOutfit(outfitStyle) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = outfitStyle.replace("_", " "),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) Color.White else TextZincSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected garment active",
                                        tint = activeAccentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Column {
                    Text(
                        text = "VOICE MODULATOR MATRIX",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = activeAccentColor
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vocal Pitch multiplier", fontSize = 11.sp, color = TextZincSecondary)
                                Text("${"%.2f".format(sliderPitch)}x", fontSize = 11.sp, color = activeAccentColor)
                            }
                            Slider(
                                value = sliderPitch,
                                onValueChange = { sliderPitch = it },
                                valueRange = 0.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = activeAccentColor,
                                    activeTrackColor = activeAccentColor
                                )
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Rate speed", fontSize = 11.sp, color = TextZincSecondary)
                                Text("${"%.2f".format(sliderSpeed)}x", fontSize = 11.sp, color = activeAccentColor)
                            }
                            Slider(
                                value = sliderSpeed,
                                onValueChange = { sliderSpeed = it },
                                valueRange = 0.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = activeAccentColor,
                                    activeTrackColor = activeAccentColor
                                )
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "SPONTANEOUS CHAT FREQUENCY",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = activeAccentColor
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allows the system to proactively check in, ask questions or scan diagnostic sensors during quiet times.",
                        fontSize = 11.sp,
                        color = TextZincSecondary,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val frequencies = listOf("DISABLED", "SLOW", "MEDIUM", "FAST")
                        frequencies.forEach { freq ->
                            val isSelected = freq == currentBanterFrequency
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeAccentColor.copy(alpha = 0.15f) else MediumGrey)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) activeAccentColor else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { currentBanterFrequency = freq }
                                    .padding(vertical = 10.dp)
                                    .testTag("banter_frequency_chip_$freq")
                            ) {
                                Text(
                                    text = freq,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else TextZincSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editNickname.isNotBlank()) onSaveNickname(editNickname)
                    if (editUserName.isNotBlank()) onSaveUserName(editUserName)
                    onSaveVoiceTweak(sliderPitch, sliderSpeed)
                    onSaveBanterFrequency(currentBanterFrequency)
                    onDismiss()
                }
            ) {
                Text("SAVE & DETECT CHANGES", color = activeAccentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("DISMISS", color = TextZincSecondary)
            }
        }
    )
}
