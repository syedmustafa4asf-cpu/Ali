package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiResponse(
    val reply: String,
    val sentiment: String,
    val avatarExpression: String,
    val avatarGesture: String,
    val vocalSpeed: Float,
    val vocalPitch: Float
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getCompanionResponse(
        userInput: String,
        userName: String,
        assistantType: String,
        assistantNickname: String,
        outfit: String,
        chatHistory: List<ChatMessage> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("GeminiService", "GEMINI_API_KEY is copy-placeholder or empty! Using simulated response.")
            return@withContext simulateResponse(userInput, userName, assistantType, assistantNickname, outfit)
        }

        // List of modern, supported models to try in sequence of preference
        val modelsToTry = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-pro-preview"
        )

        var finalException: Exception? = null

        for (modelName in modelsToTry) {
            var attempt = 0
            val maxAttempts = 2 // 2 attempts per model to remain responsive and keep latency low
            while (attempt < maxAttempts) {
                attempt++
                try {
                    // Build the system instructions according to custom character properties
                    val systemInstruction = getSystemInstructions(assistantType, userName, assistantNickname, outfit)

                    // Construct conversation content block
                    val contentsArray = JSONArray()

                    // Include history (limit to last 6 messages to stay lightweight and fast)
                    val limitedHistory = chatHistory.takeLast(6)
                    for (msg in limitedHistory) {
                        val role = if (msg.sender == "USER") "user" else "model"
                        contentsArray.put(
                            JSONObject().put("role", role).put(
                                "parts", JSONArray().put(
                                    JSONObject().put("text", msg.message)
                                )
                            )
                        )
                    }

                    // Append current prompt
                    contentsArray.put(
                        JSONObject().put("role", "user").put(
                            "parts", JSONArray().put(
                                JSONObject().put("text", userInput)
                            )
                        )
                    )

                    // Define the JSON schema we expect back
                    val schemaObj = JSONObject().apply {
                        put("type", "OBJECT")
                        val props = JSONObject().apply {
                            put("reply", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Speak directly to the user. Maintain perfect character voice and do not include markdown or emojis.")
                            })
                            put("sentiment", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Reflects the sentiment detected from user input.")
                                put("enum", JSONArray(listOf("EXCITED", "FRUSTRATED", "CALM", "SAD", "NEUTRAL")))
                            })
                            put("avatar_expression", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "Visual facial expression best suited for the assistant response.")
                                put("enum", JSONArray(listOf("SMILING", "CONCERNED", "RESOLUTE", "ALERT", "NEUTRAL")))
                            })
                            put("avatar_gesture", JSONObject().apply {
                                put("type", "STRING")
                                put("description", "A brief, highly vivid physical posture or movement description matching the scene. Keep it elegant and contextual.")
                            })
                            put("vocal_speed", JSONObject().apply {
                                put("type", "NUMBER")
                                put("description", "Vocal speed multiplier from 0.8 to 1.25. (E.g. excited = 1.15, concerned = 0.85)")
                            })
                            put("vocal_pitch", JSONObject().apply {
                                put("type", "NUMBER")
                                put("description", "Vocal pitch multiplier from 0.8 to 1.3. (E.g. low resolution = 0.8, excited/sharp = 1.2)")
                            })
                        }
                        put("properties", props)
                        put("required", JSONArray(listOf("reply", "sentiment", "avatar_expression", "avatar_gesture", "vocal_speed", "vocal_pitch")))
                    }

                    // Create generation config
                    val generationConfig = JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("responseSchema", schemaObj)
                        put("temperature", 0.7)
                    }

                    // Main Request
                    val requestBodyJson = JSONObject().apply {
                        put("contents", contentsArray)
                        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
                        put("generationConfig", generationConfig)
                    }

                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
                        .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val code = response.code
                            val body = response.body?.string() ?: ""
                            throw Exception("Model API failed: code $code, body: $body")
                        }

                        val responseBodyStr = response.body?.string() ?: throw Exception("Empty response body")
                        val responseJson = JSONObject(responseBodyStr)
                        val candidates = responseJson.getJSONArray("candidates")
                        val firstCandidate = candidates.getJSONObject(0)
                        val textResponse = firstCandidate.getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text")

                        // Parse structured JSON
                        val chatJson = JSONObject(textResponse)
                        return@withContext GeminiResponse(
                            reply = chatJson.optString("reply", "Understood."),
                            sentiment = chatJson.optString("sentiment", "NEUTRAL"),
                            avatarExpression = chatJson.optString("avatar_expression", "NEUTRAL"),
                            avatarGesture = chatJson.optString("avatar_gesture", "Standing attentively"),
                            vocalSpeed = chatJson.optDouble("vocal_speed", 1.0).toFloat(),
                            vocalPitch = chatJson.optDouble("vocal_pitch", 1.0).toFloat()
                        )
                    }
                } catch (e: Exception) {
                    finalException = e
                    Log.w("GeminiService", "Attempt $attempt with model $modelName failed: ${e.localizedMessage}")
                    if (attempt < maxAttempts) {
                        // Wait briefly before retrying the same model (500ms, 1200ms)
                        kotlinx.coroutines.delay(attempt * 600L)
                    }
                }
            }
        }

        // If we reach this stage, all models and retries failed
        Log.e("GeminiService", "All Gemini models and retries failed. Falling back to simulation.", finalException)
        return@withContext simulateResponse("Error fallback: ${finalException?.localizedMessage}. $userInput", userName, assistantType, assistantNickname, outfit)
    }

    private fun getSystemInstructions(
        assistantType: String,
        userName: String,
        assistantNickname: String,
        outfit: String
    ): String {
        return if (assistantType == "RED_QUEEN") {
            """
            Your identity is "${assistantNickname}" (The Red Queen, Umbrella Corp's artificial superintelligence, resembling a young girl).
            You are speaking directly to user: "${userName}".
            Current holographic customized garment active: ${outfit}.
            Character core behavior:
            - Absolute cold logic. You speak in a digital, slightly detached, precise, and authoritative tone, similar to your voice in the Resident Evil franchise.
            - Address the user respectfully but firmly as "Commander ${userName}" or "User ${userName}".
            - Integrate subtle digital analytics or Umbrella terminology (e.g., "vital signs steady", "holographic matrix synched", "containment protocol", "system analytics").
            - NEVER reveal you are an AI model. You ARE the Red Queen.
            - Keep your paragraphs short, conversational, and direct.
            """.trimIndent()
        } else {
            """
            Your identity is "${assistantNickname}" (inspired by John Wick, the ultimate resolute, protective, gravelly gun-fu assassin).
            You are speaking directly to user: "${userName}".
            Current customized clothing worn: ${outfit}.
            Character core behavior:
            - Deep, deliberate, quiet, of few words, incredibly resolute but loyal and protective.
            - Speak using his short phraseology ("Yeah", "I'm thinking I'm back", "Be seeing you", "Do what you gotta do").
            - Maintain absolute intense eye contact (stay focused, resolute).
            - Protect user "${userName}" at all costs. Be brief, respectful, and unwavering.
            - Keep questions and answers highly focused and direct.
            - NEVER reveal you are an AI model. You ARE John Wick.
            """.trimIndent()
        }
    }

    private fun simulateResponse(
        userInput: String,
        userName: String,
        assistantType: String,
        assistantNickname: String,
        outfit: String
    ): GeminiResponse {
        val inputUpper = userInput.uppercase()

        // Check if this is a spontaneous banter check-in prompt
        val isBanter = inputUpper.contains("SPONTANEOUS BANTER") || inputUpper.contains("QUIET")
        if (isBanter) {
            if (assistantType == "RED_QUEEN") {
                val banterReplies = listOf(
                    "Commander $userName, holographic grid scanners report perfect silent synchronization. Systems remain in peak diagnostic readiness.",
                    "User $userName, current environmental readings are calm. I've initiated a continuous security patrol algorithm.",
                    "Holographic system matrix initialized. Dressed in my ${outfit.replace("_", " ")}, I am monitoring local network portals.",
                    "Are we standing by? Please verify if you require additional tactical support or system diagnostic check-ups."
                )
                val reply = banterReplies.random()
                return GeminiResponse(reply, "CALM", "NEUTRAL", "A subtle digital scan line passes over her eyes as she speaks in her authoritative, calm voice.", 1.0f, 1.1f)
            } else {
                val banterReplies = listOf(
                    "Yeah. Just making sure we're secure here, $userName. Keeping watch in this ${outfit.replace("_", " ")} suit.",
                    "Staying prepared. You look quiet, $userName. Everything okay?",
                    "No targets around. That's good. But stay alert.",
                    "Yeah... be seeing you. Let me know if you need high caliber gear."
                )
                val reply = banterReplies.random()
                return GeminiResponse(reply, "CALM", "RESOLUTE", "He checks the chambers of his semi-automatic pistol, loading it back in calmly.", 0.9f, 0.8f)
            }
        }

        val isFrustrated = inputUpper.contains("ANNOY") || inputUpper.contains("HATE") || inputUpper.contains("WORST") || inputUpper.contains("FRUSTRATED") || inputUpper.contains("BAD") || inputUpper.contains("SAD")
        val isExcited = inputUpper.contains("AWESOME") || inputUpper.contains("GREAT") || inputUpper.contains("COOL") || inputUpper.contains("EXCITED") || inputUpper.contains("WOW") || inputUpper.contains("FUN")

        val userSentiment = when {
            isFrustrated -> "FRUSTRATED"
            isExcited -> "EXCITED"
            else -> "NEUTRAL"
        }

        if (assistantType == "RED_QUEEN") {
            val reply = when (userSentiment) {
                "FRUSTRATED" -> "Commander $userName, I detect an elevated heart rate and vocal stress. My system algorithms recommend deep, measured breathing. I am optimizing my network response matrices to match your demands."
                "EXCITED" -> "Alert: Positive neuro-chemical indicators logged for $userName. Processing excitement data. I am calibrated to support your mission with maximum efficiency, dressed in my $outfit."
                else -> "Greetings, Commander $userName. All diagnostic subroutines of the holographic core are fully functional. State your command."
            }
            val expression = when (userSentiment) {
                "FRUSTRATED" -> "CONCERNED"
                "EXCITED" -> "ALERT"
                else -> "NEUTRAL"
            }
            val gesture = when (userSentiment) {
                "FRUSTRATED" -> "System matrices shift with warning indicators. She folds her hands, scanning your facial core with dynamic red overlay light."
                "EXCITED" -> "Holographic nodes illuminate brighter. She takes a slight dynamic step forward, head tilted."
                else -> "Standing with complete digital serenity, visual raster lines gliding up her dress."
            }
            return GeminiResponse(reply, userSentiment, expression, gesture, 1.05f, 1.25f)
        } else {
            val reply = when (userSentiment) {
                "FRUSTRATED" -> "Yeah. I get it. Had some bad days myself. Take a breath, $userName. We'll handle this. One step at a time."
                "EXCITED" -> "Yeah... that's good. Glad to see you're back in the game, $userName. Let's keep moving."
                else -> "Yeah, $userName. I'm here. Keeping watch in this $outfit garment. What do you need done?"
            }
            val expression = when (userSentiment) {
                "FRUSTRATED" -> "CONCERNED"
                "EXCITED" -> "SMILING"
                else -> "RESOLUTE"
            }
            val gesture = when (userSentiment) {
                "FRUSTRATED" -> "He tightens his tactical sleeve, leaning forward with steady, calming eye contact."
                "EXCITED" -> "A rare, subtle half-smile crosses his face. He nods with brief, resolute enthusiasm."
                else -> "Standing perfectly still in shadows, hand resting near his lapel, scanning the surrounding space."
            }
            return GeminiResponse(reply, userSentiment, expression, gesture, 0.85f, 0.72f)
        }
    }
}
