package com.cellclaw.profile

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Non-sensitive metadata only; candidate facts remain exclusively in encrypted preferences. */
@Entity(tableName = "profile_metadata")
data class ProfileMetadataEntity(
    @PrimaryKey val id: Int = SINGLE_PROFILE_ID,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long
) {
    companion object {
        const val SINGLE_PROFILE_ID = 1
    }
}

@Dao
interface ProfileMetadataDao {
    @Query("SELECT * FROM profile_metadata WHERE id = :id")
    suspend fun get(id: Int = ProfileMetadataEntity.SINGLE_PROFILE_ID): ProfileMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: ProfileMetadataEntity)

    @Query("DELETE FROM profile_metadata WHERE id = :id")
    suspend fun delete(id: Int = ProfileMetadataEntity.SINGLE_PROFILE_ID)
}

@Database(entities = [ProfileMetadataEntity::class], version = 1, exportSchema = false)
abstract class ProfileMetadataDb : RoomDatabase() {
    abstract fun profileMetadataDao(): ProfileMetadataDao
}
