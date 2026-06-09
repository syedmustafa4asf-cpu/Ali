package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ROOM ENTITIES

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,
    val userName: String = "Commander",
    val selectedAssistant: String = "RED_QUEEN", // RED_QUEEN or JOHN_WICK
    
    val redQueenNickname: String = "Red Queen",
    val redQueenOutfit: String = "HOLOGRAPHIC_DRESS", // HOLOGRAPHIC_DRESS, UMBRELLA_UNIFORM, REBEL_RED
    
    val johnWickNickname: String = "John Wick",
    val johnWickOutfit: String = "TACTICAL_SUIT", // TACTICAL_SUIT, CASUAL_BLACK, CLASSIC_TUXEDO
    
    val voicePitch: Float = 1.0f,
    val voiceSpeed: Float = 1.0f,
    val banterFrequency: String = "MEDIUM" // DISABLED, SLOW, MEDIUM, FAST
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assistantType: String, // RED_QUEEN or JOHN_WICK
    val sender: String, // USER or ASSISTANT
    val message: String,
    val timestamp: Long,
    val expression: String = "NEUTRAL"
)

// DAO INTERFACE

@Dao
interface AssistantDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun getUserPreferencesFlow(): Flow<UserPreferences?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getUserPreferences(): UserPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPreferences(preferences: UserPreferences)

    @Query("SELECT * FROM chat_messages WHERE assistantType = :assistantType ORDER BY timestamp ASC")
    fun getChatMessagesFlow(assistantType: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE assistantType = :assistantType")
    suspend fun deleteChatMessagesForAssistant(assistantType: String)
}

// ROOM DATABASE

@Database(entities = [UserPreferences::class, ChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val assistantDao: AssistantDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "companion_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// REPOSITORY LOGIC

class AssistantRepository(private val dao: AssistantDao) {

    val userPreferencesFlow: Flow<UserPreferences?> = dao.getUserPreferencesFlow()

    suspend fun getPreferences(): UserPreferences {
        return dao.getUserPreferences() ?: UserPreferences().also {
            dao.insertUserPreferences(it)
        }
    }

    suspend fun savePreferences(preferences: UserPreferences) {
        dao.insertUserPreferences(preferences)
    }

    fun getChatMessages(assistantType: String): Flow<List<ChatMessage>> {
        return dao.getChatMessagesFlow(assistantType)
    }

    suspend fun addChatMessage(message: ChatMessage) {
        dao.insertChatMessage(message)
    }

    suspend fun clearChat(assistantType: String) {
        dao.deleteChatMessagesForAssistant(assistantType)
    }
}
