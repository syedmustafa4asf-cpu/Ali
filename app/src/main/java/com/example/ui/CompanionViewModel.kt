package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppMode {
    CHAT, VOICE_CALL, VIDEO_CALL
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AssistantRepository(database.assistantDao)
    private val voiceManager = VoiceManager(application)
    private val geminiService = GeminiService()

    // Preferences and settings flows
    val userPreferences: StateFlow<UserPreferences> = repository.userPreferencesFlow
        .map { it ?: UserPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    // UI screen modes
    private val _appMode = MutableStateFlow(AppMode.CHAT)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    // Speech-to-Text UI States
    private val _isListeningToMic = MutableStateFlow(false)
    val isListeningToMic: StateFlow<Boolean> = _isListeningToMic.asStateFlow()

    private val _liveSpeechBuffer = MutableStateFlow("")
    val liveSpeechBuffer: StateFlow<String> = _liveSpeechBuffer.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private var simulationJob: Job? = null

    // Loading indicator for API calls
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Current expression, gestures and visual details updated dynamically by AI
    private val _currentExpression = MutableStateFlow("NEUTRAL") // NEUTRAL, SMILING, CONCERNED, RESOLUTE, ALERT
    val currentExpression: StateFlow<String> = _currentExpression.asStateFlow()

    private val _currentGesture = MutableStateFlow("Waiting elegantly")
    val currentGesture: StateFlow<String> = _currentGesture.asStateFlow()

    private val _detectedUserSentiment = MutableStateFlow("CALM") // CALM, EXCITED, FRUSTRATED, SAD, NEUTRAL
    val detectedUserSentiment: StateFlow<String> = _detectedUserSentiment.asStateFlow()

    private val _syncPercentage = MutableStateFlow(98.4f)
    val syncPercentage: StateFlow<Float> = _syncPercentage.asStateFlow()

    // Voice call simulation states
    private val _isUserSpeakingInCall = MutableStateFlow(false)
    val isUserSpeakingInCall: StateFlow<Boolean> = _isUserSpeakingInCall.asStateFlow()

    private val _isCompanionSpeakingInCall = MutableStateFlow(false)
    val isCompanionSpeakingInCall: StateFlow<Boolean> = _isCompanionSpeakingInCall.asStateFlow()

    private val _lastMessageTime = MutableStateFlow(System.currentTimeMillis())
    private var banterJob: Job? = null

    // Combined chat history
    val chatMessages: StateFlow<List<ChatMessage>> = userPreferences
        .flatMapLatest { prefs ->
            repository.getChatMessages(prefs.selectedAssistant)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Register listener for TTS speaking state to update UI dynamically
        voiceManager.onSpeakingStateListener = { speaking ->
            _isCompanionSpeakingInCall.value = speaking
        }

        // Log basic initial preferences
        viewModelScope.launch {
            val initial = repository.getPreferences()
            _currentGesture.value = if (initial.selectedAssistant == "RED_QUEEN") {
                "Holographic diagnostic routine completed"
            } else {
                "Checking weapons and securing surroundings"
            }
        }

        // Initialize SpeechRecognizer on the main thread safely
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(application)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
                        setRecognitionListener(SpeechListener())
                    }
                }
            } catch (e: Exception) {
                Log.e("CompanionViewModel", "SpeechRecognizer initialization failed or not supported", e)
            }
        }

        startBanterTimer()
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        if (mode == AppMode.CHAT) {
            voiceManager.stop()
        }
    }

    fun toggleAssistant() {
        viewModelScope.launch {
            val current = userPreferences.value
            val target = if (current.selectedAssistant == "RED_QUEEN") "JOHN_WICK" else "RED_QUEEN"
            
            // Save state
            repository.savePreferences(current.copy(selectedAssistant = target))
            
            // Speak switch transition
            val switchAnnouncement = if (target == "RED_QUEEN") {
                "Red Queen system matrix online. Tactical grid synchronized."
            } else {
                "Yeah. John Wick. I'm listening."
            }
            
            _currentGesture.value = if (target == "RED_QUEEN") {
                "Holographic grid initializing"
            } else {
                "Standing by calmly, buttoning jacket"
            }
            _currentExpression.value = "NEUTRAL"
            
            speakText(switchAnnouncement, target)
        }
    }

    fun selectAssistant(target: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            if (current.selectedAssistant == target) return@launch
            
            // Save state
            repository.savePreferences(current.copy(selectedAssistant = target))
            
            // Speak switch transition
            val switchAnnouncement = if (target == "RED_QUEEN") {
                "Red Queen system matrix online. Tactical grid synchronized."
            } else {
                "Yeah. John Wick. I'm listening."
            }
            
            _currentGesture.value = if (target == "RED_QUEEN") {
                "Holographic grid initializing"
            } else {
                "Standing by calmly, buttoning jacket"
            }
            _currentExpression.value = "NEUTRAL"
            
            speakText(switchAnnouncement, target)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.savePreferences(current.copy(userName = name))
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            val updated = if (current.selectedAssistant == "RED_QUEEN") {
                current.copy(redQueenNickname = nickname)
            } else {
                current.copy(johnWickNickname = nickname)
            }
            repository.savePreferences(updated)
        }
    }

    fun updateOutfit(outfitStyle: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            val updated = if (current.selectedAssistant == "RED_QUEEN") {
                current.copy(redQueenOutfit = outfitStyle)
            } else {
                current.copy(johnWickOutfit = outfitStyle)
            }
            repository.savePreferences(updated)
            
            val feedback = if (current.selectedAssistant == "RED_QUEEN") {
                "Holographic garment matrix configured to $outfitStyle."
            } else {
                "Yeah. Dress configured. Ready."
            }
            speakText(feedback, current.selectedAssistant)
        }
    }

    fun updateVoiceTweak(pitch: Float, speed: Float) {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.savePreferences(current.copy(voicePitch = pitch, voiceSpeed = speed))
        }
    }

    fun triggerSimulatedUserTone(tone: String) {
        _detectedUserSentiment.value = tone
        viewModelScope.launch {
            // Log appropriate simulated AI response matching tone selection
            val prefs = userPreferences.value
            val promptMsg = when (tone) {
                "FRUSTRATED" -> "I am feeling extremely frustrated with this day."
                "EXCITED" -> "I am so excited! Something amazing just happened!"
                "SAD" -> "I feel pretty sad and tired."
                else -> "I am doing alright."
            }
            onUserSendMessage(promptMsg)
        }
    }

    fun onUserSendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return
        _lastMessageTime.value = System.currentTimeMillis()

        viewModelScope.launch {
            val prefs = userPreferences.value
            val currentAssistant = prefs.selectedAssistant
            
            // 1. Log User message in Room Database
            val userMsg = ChatMessage(
                assistantType = currentAssistant,
                sender = "USER",
                message = text,
                timestamp = System.currentTimeMillis()
            )
            repository.addChatMessage(userMsg)
            
            _isGenerating.value = true
            _isUserSpeakingInCall.value = false
            
            // 2. Fetch API dynamic emotion Response
            val currentNickname = if (currentAssistant == "RED_QUEEN") prefs.redQueenNickname else prefs.johnWickNickname
            val currentOutfit = if (currentAssistant == "RED_QUEEN") prefs.redQueenOutfit else prefs.johnWickOutfit
            val history = chatMessages.value

            try {
                val response = geminiService.getCompanionResponse(
                    userInput = text,
                    userName = prefs.userName,
                    assistantType = currentAssistant,
                    assistantNickname = currentNickname,
                    outfit = currentOutfit,
                    chatHistory = history
                )

                _detectedUserSentiment.value = response.sentiment
                _currentExpression.value = response.avatarExpression
                _currentGesture.value = response.avatarGesture
                // Add fluctuating sync indicator
                _syncPercentage.value = 95f + (Math.random() * 5).toFloat()

                // Speak matching vocal intonations
                speakText(response.reply, currentAssistant, response.vocalPitch, response.vocalSpeed)

                // 3. Log Assistant response
                val aiMsg = ChatMessage(
                    assistantType = currentAssistant,
                    sender = "ASSISTANT",
                    message = response.reply,
                    timestamp = System.currentTimeMillis(),
                    expression = response.avatarExpression
                )
                repository.addChatMessage(aiMsg)
                _lastMessageTime.value = System.currentTimeMillis()

            } catch (e: Exception) {
                Log.e("CompanionViewModel", "API dispatch failed", e)
            } finally {
                _isGenerating.value = false
                _lastMessageTime.value = System.currentTimeMillis()
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat(userPreferences.value.selectedAssistant)
        }
    }

    fun updateBanterFrequency(frequency: String) {
        viewModelScope.launch {
            val current = userPreferences.value
            repository.savePreferences(current.copy(banterFrequency = frequency))
            _lastMessageTime.value = System.currentTimeMillis()
        }
    }

    fun triggerSpontaneousBanter() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            val prefs = userPreferences.value
            val currentAssistant = prefs.selectedAssistant
            val currentNickname = if (currentAssistant == "RED_QUEEN") prefs.redQueenNickname else prefs.johnWickNickname
            val currentOutfit = if (currentAssistant == "RED_QUEEN") prefs.redQueenOutfit else prefs.johnWickOutfit
            val history = chatMessages.value
            
            val promptMsg = "[Spontaneous Banter Check-In. The user is currently quiet. Based on your character traits, your active holographic gown/outfit ($currentOutfit), and previous context, say a brief, 1 or 2 sentence spontaneous comment. It could be warning, or checking on them, or tactical diagnostic scanning. Do not use markdown.]"
            
            try {
                val response = geminiService.getCompanionResponse(
                    userInput = promptMsg,
                    userName = prefs.userName,
                    assistantType = currentAssistant,
                    assistantNickname = currentNickname,
                    outfit = currentOutfit,
                    chatHistory = history
                )
                
                _detectedUserSentiment.value = "CALM"
                _currentExpression.value = response.avatarExpression
                _currentGesture.value = response.avatarGesture
                _syncPercentage.value = 95f + (Math.random() * 5).toFloat()
                
                speakText(response.reply, currentAssistant, response.vocalPitch, response.vocalSpeed)
                
                val aiMsg = ChatMessage(
                    assistantType = currentAssistant,
                    sender = "ASSISTANT",
                    message = response.reply,
                    timestamp = System.currentTimeMillis(),
                    expression = response.avatarExpression
                )
                repository.addChatMessage(aiMsg)
                _lastMessageTime.value = System.currentTimeMillis()
                
            } catch (e: Exception) {
                Log.e("CompanionViewModel", "Banter dispatch failed", e)
            } finally {
                _isGenerating.value = false
                _lastMessageTime.value = System.currentTimeMillis()
            }
        }
    }

    private fun startBanterTimer() {
        banterJob?.cancel()
        banterJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(4000) // check every 4 seconds
                val prefs = userPreferences.value
                val freq = prefs.banterFrequency
                if (freq == "DISABLED") continue

                val intervalMs = when (freq) {
                    "FAST" -> 14000L      // 14 seconds
                    "SLOW" -> 60000L      // 1 minute
                    else -> 28000L        // 28 seconds (MEDIUM)
                }

                val now = System.currentTimeMillis()
                val idleTime = now - _lastMessageTime.value

                if (idleTime >= intervalMs && !_isGenerating.value && !_isListeningToMic.value && _appMode.value != AppMode.VIDEO_CALL) {
                    _lastMessageTime.value = now
                    triggerSpontaneousBanter()
                }
            }
        }
    }

    fun speakText(text: String, assistantType: String, pitchFactor: Float = 1.0f, speedFactor: Float = 1.0f) {
        _isCompanionSpeakingInCall.value = true
        val prefs = userPreferences.value
        val effectivePitch = prefs.voicePitch * pitchFactor
        val effectiveSpeed = prefs.voiceSpeed * speedFactor
        voiceManager.speak(text, assistantType, effectivePitch, effectiveSpeed)
    }

    fun stopSpeaking() {
        voiceManager.stop()
        _isCompanionSpeakingInCall.value = false
    }

    fun startListeningToMic() {
        // Cancel active TTS output so they do not conflict
        stopSpeaking()

        _liveSpeechBuffer.value = "System initializing..."
        _isListeningToMic.value = true
        _micAmplitude.value = 0.1f

        viewModelScope.launch(Dispatchers.Main) {
            val hasRecognizer = speechRecognizer != null
            if (hasRecognizer) {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.e("CompanionViewModel", "Failed starting SpeechRecognizer", e)
                    launchSimulationFallback()
                }
            } else {
                launchSimulationFallback()
            }
        }
    }

    fun stopListeningToMic() {
        _isListeningToMic.value = false
        _micAmplitude.value = 0f
        simulationJob?.cancel()

        viewModelScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("CompanionViewModel", "Error stopping SpeechRecognizer", e)
            }
        }
    }

    private fun launchSimulationFallback() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            _liveSpeechBuffer.value = "Listening..."
            // Simulate changing mic amplitude and then generating a cool query based on assistant type!
            for (i in 1..40) {
                if (!_isListeningToMic.value) return@launch
                // Pulsate amplitude
                _micAmplitude.value = (0.2f + 0.8f * kotlin.math.sin(System.currentTimeMillis() / 80.0).toFloat().coerceIn(-1f, 1f) * (0.8f + 0.2f * java.lang.Math.random().toFloat())).coerceIn(0.1f, 1.0f)
                // Add a dynamic simulation typing
                if (i == 10) _liveSpeechBuffer.value = "Listening... [How's the"
                if (i == 20) _liveSpeechBuffer.value = "Listening... [How's the sector scan looking"
                if (i == 30) _liveSpeechBuffer.value = "Listening... [How's the sector scan looking there?"
                delay(100)
            }

            if (_isListeningToMic.value) {
                val assistant = userPreferences.value.selectedAssistant
                val mockPhrases = if (assistant == "RED_QUEEN") {
                    listOf(
                        "Are we fully synchronized? Run sector diagnostics.",
                        "Is there any breach in secure sector seven?",
                        "Confirm status of defensive array and energy shields."
                    )
                } else {
                    listOf(
                        "Yeah. Continental report. Is our track secure?",
                        "We need high caliber gear. Any updates?",
                        "I need coordinates for the targets on sector five."
                    )
                }
                val chosenText = mockPhrases.random()
                _liveSpeechBuffer.value = chosenText
                delay(400)
                _isListeningToMic.value = false
                _micAmplitude.value = 0f
                onUserSendMessage(chosenText)
            }
        }
    }

    inner class SpeechListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _liveSpeechBuffer.value = "Listening..."
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            _micAmplitude.value = ((rmsdB + 2f) / 10f).coerceIn(0.1f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _isListeningToMic.value = false
        }
        override fun onError(error: Int) {
            Log.e("CompanionViewModel", "Speech Recognizer error: $error")
            _isListeningToMic.value = false
            _micAmplitude.value = 0f
            if (_liveSpeechBuffer.value == "Listening..." || _liveSpeechBuffer.value.isBlank()) {
                _liveSpeechBuffer.value = "Voice system error. Try typing or tap again."
            }
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                _liveSpeechBuffer.value = text
                onUserSendMessage(text)
            } else {
                _liveSpeechBuffer.value = "No audio detected."
            }
            _isListeningToMic.value = false
            _micAmplitude.value = 0f
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                _liveSpeechBuffer.value = matches[0]
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCleared() {
        voiceManager.shutdown()
        simulationJob?.cancel()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("CompanionViewModel", "Error destroying SpeechRecognizer", e)
        }
        super.onCleared()
    }
}
