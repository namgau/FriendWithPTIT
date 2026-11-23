package vn.ptit.fwp.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.chatNudges by preferencesDataStore(name = "chat_nudges")

data class ChatNudgePreference(val overrides: Int = 0) {
    val enabled: Boolean get() = overrides < LIMIT
    companion object { const val LIMIT = 3 }
}

interface ChatNudgePreferences {
    val settings: Flow<ChatNudgePreference>
    suspend fun recordOverride(): ChatNudgePreference
    suspend fun setEnabled(enabled: Boolean)
}

/** Only a capped counter is stored, never drafts or message history. */
class ChatNudgeStore internal constructor(private val data: DataStore<Preferences>) {
    constructor(context: Context) : this(context.chatNudges)

    fun forConversation(owner: String, matchId: String): ChatNudgePreferences {
        // Length framing prevents ambiguous pairs; hashing keeps account/IDs out of key names.
        val bytes = "${owner.length}:$owner$matchId".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val key = intPreferencesKey("overrides_$hash")
        return object : ChatNudgePreferences {
            override val settings = data.data.map { ChatNudgePreference((it[key] ?: 0).coerceIn(0, ChatNudgePreference.LIMIT)) }
            override suspend fun recordOverride(): ChatNudgePreference {
                val saved = data.edit { prefs ->
                    prefs[key] = ((prefs[key] ?: 0).coerceIn(0, ChatNudgePreference.LIMIT) + 1).coerceAtMost(ChatNudgePreference.LIMIT)
                }
                return ChatNudgePreference(saved[key] ?: 0)
            }
            override suspend fun setEnabled(enabled: Boolean) {
                data.edit { prefs ->
                    if (enabled) prefs.remove(key) else prefs[key] = ChatNudgePreference.LIMIT
                }
            }
        }
    }
}
