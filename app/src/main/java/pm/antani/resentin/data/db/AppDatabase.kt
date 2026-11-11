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
        E2eMessageEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun channelDao(): ChannelDao
    abstract fun messageDao(): MessageDao
    abstract fun isupportDao(): IsupportDao
    abstract fun memberDao(): MemberDao
    abstract fun e2eMessageDao(): E2eMessageDao

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

        // 12 -> 13: our decrypted copies of encrypted messages (grappa keeps none).
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `e2e_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`networkSlug` TEXT NOT NULL, `peer` TEXT NOT NULL, `peerNick` TEXT NOT NULL, " +
                        "`outgoing` INTEGER NOT NULL, `body` TEXT NOT NULL, `at` INTEGER NOT NULL, `status` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_e2e_messages_networkSlug_peer` ON `e2e_messages` (`networkSlug`, `peer`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
    }
}
