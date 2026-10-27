package vn.ptit.fwp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id != 'me' AND blocked = 0 ORDER BY id")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = 'me' LIMIT 1")
    fun observeMe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("UPDATE profiles SET blocked = :blocked WHERE id = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("DELETE FROM profiles WHERE id = 'me'")
    suspend fun deleteMe()

    @Query("DELETE FROM profiles WHERE id != 'me'")
    suspend fun deleteDemoProfiles()
}

@Dao
interface SwipeDao {
    @Query("SELECT * FROM swipes ORDER BY createdAt DESC")
    fun observeSwipes(): Flow<List<SwipeEntity>>

    @Query("SELECT * FROM swipes WHERE targetId = :targetId LIMIT 1")
    suspend fun getByTarget(targetId: String): SwipeEntity?

    @Query("SELECT * FROM swipes ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLast(): SwipeEntity?

    @Query("SELECT * FROM swipes WHERE targetId = :targetId LIMIT 1")
    suspend fun find(targetId: String): SwipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(swipe: SwipeEntity)

    @Query("DELETE FROM swipes WHERE targetId = :targetId")
    suspend fun delete(targetId: String)

    @Query("DELETE FROM swipes")
    suspend fun clear()
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY COALESCE(lastMessageAt, matchedAt) DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY COALESCE(lastMessageAt, matchedAt) DESC")
    suspend fun getAll(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE profileId = :profileId LIMIT 1")
    suspend fun getByProfile(profileId: String): MatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(match: MatchEntity)

    @Update
    suspend fun update(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM matches WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: String)

    @Query("UPDATE matches SET unreadCount = 0 WHERE id = :id")
    suspend fun clearUnread(id: String)

    @Query("SELECT COUNT(*) FROM matches")
    suspend fun count(): Int

    @Query("DELETE FROM matches")
    suspend fun clear()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE matchId = :matchId ORDER BY createdAt ASC")
    fun observeByMatch(matchId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE matchId = :matchId ORDER BY createdAt ASC")
    suspend fun getByMatch(matchId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLast(): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE matchId = :matchId AND senderId != 'me'")
    suspend fun markIncomingRead(matchId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE matchId = :matchId")
    suspend fun countForMatch(matchId: String): Int

    @Query("DELETE FROM messages WHERE matchId = :matchId")
    suspend fun deleteByMatch(matchId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 60")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun clear()
}
