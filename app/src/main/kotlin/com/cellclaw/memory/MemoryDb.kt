package com.cellclaw.memory

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cellclaw.scheduler.ScheduledTaskDao
import com.cellclaw.scheduler.ScheduledTaskEntity
import com.cellclaw.tracker.ApplicationDao
import com.cellclaw.tracker.ApplicationEntity

@Database(
    entities = [MessageEntity::class, MemoryFactEntity::class, ScheduledTaskEntity::class, ApplicationEntity::class],
    version = 4,
    exportSchema = true
)
abstract class MemoryDb : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun applicationDao(): ApplicationDao

    companion object {
        /** Migration from v1 to v2: adds scheduled_tasks table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `interval_minutes` INTEGER NOT NULL,
                        `initial_delay_minutes` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `last_run` INTEGER,
                        `work_id` TEXT,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Migration from v2 to v3: adds applications table for PlacementOS application tracking. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `applications` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `company` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `job_id` TEXT,
                        `source_url` TEXT,
                        `jd_hash` TEXT NOT NULL,
                        `deadline` TEXT,
                        `eligibility_result` TEXT NOT NULL,
                        `resume_version` INTEGER NOT NULL,
                        `resume_hash` TEXT,
                        `state` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `notes` TEXT,
                        `confirmation_reference` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        /** Migration from v3 to v4: adds performance index on jd_hash for fast duplicate lookups. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_applications_jd_hash` ON `applications` (`jd_hash`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "conversation_id") val conversationId: String = "default"
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "category") val category: String = "general",
    @ColumnInfo(name = "confidence") val confidence: Float = 1.0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(conversationId: String = "default", limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    suspend fun getAll(conversationId: String = "default"): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun clearConversation(conversationId: String = "default")

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId")
    suspend fun count(conversationId: String = "default"): Int
}

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: MemoryFactEntity): Long

    @Query("SELECT * FROM memory_facts WHERE category = :category ORDER BY updated_at DESC")
    suspend fun getByCategory(category: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY updated_at DESC")
    suspend fun getAll(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<MemoryFactEntity>

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun delete(id: Long)
}
