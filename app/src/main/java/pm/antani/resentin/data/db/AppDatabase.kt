package pm.antani.resentin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NetworkEntity::class,
        ChannelEntity::class,
        MessageEntity::class,
        IsupportEntity::class,
        MemberEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun isupportDao(): IsupportDao
    abstract fun memberDao(): MemberDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "resentin.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
