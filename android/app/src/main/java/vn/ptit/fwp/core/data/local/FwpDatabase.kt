package vn.ptit.fwp.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        SwipeEntity::class,
        MatchEntity::class,
        MessageEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FwpDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun swipeDao(): SwipeDao
    abstract fun matchDao(): MatchDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var instance: FwpDatabase? = null

        fun get(context: Context): FwpDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FwpDatabase::class.java,
                "friendwithptit.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
