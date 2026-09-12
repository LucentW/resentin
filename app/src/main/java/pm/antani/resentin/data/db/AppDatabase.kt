package pm.antani.resentin.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NetworkEntity::class,
        ChannelEntity::class,
        MessageEntity::class,
        IsupportEntity::class,
        MemberEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun isupportDao(): IsupportDao
    abstract fun memberDao(): MemberDao

    companion object {
        const val DB_NAME = "resentin.db"

        // 11 -> 12: nullable servicesFlavor on networks (NickServ wizard gating).
        // Explicit (not destructive): a version bump must never wipe scrollback
        // + DM rows — queries have no REST endpoint to come back from, only the
        // WS query_windows_list snapshot.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE networks ADD COLUMN servicesFlavor TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
    }
}
