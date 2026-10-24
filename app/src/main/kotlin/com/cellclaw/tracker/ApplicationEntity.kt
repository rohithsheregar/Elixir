package com.cellclaw.tracker

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "applications")
data class ApplicationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "company") val company: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "job_id") val jobId: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "jd_hash") val jdHash: String,
    @ColumnInfo(name = "deadline") val deadline: String?,
    @ColumnInfo(name = "eligibility_result") val eligibilityResult: String,
    @ColumnInfo(name = "resume_version") val resumeVersion: Int,
    @ColumnInfo(name = "resume_hash") val resumeHash: String?,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "confirmation_reference") val confirmationReference: String?
) {
    fun toRecord(): ApplicationRecord = ApplicationRecord(
        id = id,
        company = company,
        role = role,
        jobId = jobId,
        sourceUrl = sourceUrl,
        jdHash = jdHash,
        deadline = deadline,
        eligibilityResult = eligibilityResult,
        resumeVersion = resumeVersion,
        resumeHash = resumeHash,
        state = ApplicationState.valueOf(state),
        createdAt = createdAt,
        updatedAt = updatedAt,
        notes = notes,
        confirmationReference = confirmationReference
    )

    companion object {
        fun fromRecord(record: ApplicationRecord): ApplicationEntity = ApplicationEntity(
            id = record.id,
            company = record.company,
            role = record.role,
            jobId = record.jobId,
            sourceUrl = record.sourceUrl,
            jdHash = record.jdHash,
            deadline = record.deadline,
            eligibilityResult = record.eligibilityResult,
            resumeVersion = record.resumeVersion,
            resumeHash = record.resumeHash,
            state = record.state.name,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            notes = record.notes,
            confirmationReference = record.confirmationReference
        )
    }
}

@Dao
interface ApplicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(application: ApplicationEntity): Long

    @Query("SELECT * FROM applications WHERE id = :id")
    suspend fun getById(id: String): ApplicationEntity?

    @Query("SELECT * FROM applications ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApplicationEntity>

    @Query("SELECT * FROM applications ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<ApplicationEntity>>

    @Query("DELETE FROM applications WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM applications")
    suspend fun deleteAll(): Int
}
