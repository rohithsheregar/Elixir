package com.cellclaw.tracker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

data class DuplicateCheckResult(
    val hasLikelyDuplicate: Boolean,
    val existingApplication: ApplicationRecord? = null,
    val reason: String? = null
)

interface ApplicationTracker {
    suspend fun registerApplication(record: ApplicationRecord): DuplicateCheckResult
    suspend fun getApplication(id: String): ApplicationRecord?
    suspend fun getAllApplications(): List<ApplicationRecord>
    fun observeApplications(): Flow<List<ApplicationRecord>>
    suspend fun updateApplication(record: ApplicationRecord)
    suspend fun transitionState(
        id: String,
        targetState: ApplicationState,
        notes: String? = null,
        confirmationReference: String? = null
    ): ApplicationRecord
    suspend fun checkDuplicate(candidate: ApplicationRecord): DuplicateCheckResult
    suspend fun deleteApplication(id: String)
    suspend fun clearAll()
}

/**
 * In-memory implementation of [ApplicationTracker] for pure JVM unit tests.
 */
class InMemoryApplicationTracker : ApplicationTracker {
    private val records = mutableMapOf<String, ApplicationRecord>()
    private val stateFlow = MutableStateFlow<List<ApplicationRecord>>(emptyList())

    private fun notifyFlow() {
        stateFlow.value = records.values.sortedByDescending { it.updatedAt }
    }

    override suspend fun registerApplication(record: ApplicationRecord): DuplicateCheckResult {
        val dupResult = checkDuplicate(record)
        records[record.id] = record
        notifyFlow()
        return dupResult
    }

    override suspend fun getApplication(id: String): ApplicationRecord? = records[id]

    override suspend fun getAllApplications(): List<ApplicationRecord> =
        records.values.sortedByDescending { it.updatedAt }

    override fun observeApplications(): Flow<List<ApplicationRecord>> = stateFlow.asStateFlow()

    override suspend fun updateApplication(record: ApplicationRecord) {
        records[record.id] = record
        notifyFlow()
    }

    override suspend fun transitionState(
        id: String,
        targetState: ApplicationState,
        notes: String?,
        confirmationReference: String?
    ): ApplicationRecord {
        val existing = records[id] ?: throw NoSuchElementException("Application record not found: $id")

        if (targetState == ApplicationState.SUBMITTED_CONFIRMED) {
            require(!confirmationReference.isNullOrBlank()) {
                "Cannot mark SUBMITTED_CONFIRMED without explicit confirmation reference."
            }
        }

        val updated = existing.transitionTo(targetState, notes, confirmationReference)
        records[id] = updated
        notifyFlow()
        return updated
    }

    override suspend fun checkDuplicate(candidate: ApplicationRecord): DuplicateCheckResult {
        for (existing in records.values) {
            if (candidate.isLikelyDuplicateOf(existing)) {
                val reason = when {
                    candidate.company.trim().equals(existing.company.trim(), ignoreCase = true) &&
                    candidate.role.trim().equals(existing.role.trim(), ignoreCase = true) ->
                        "An application already exists for ${existing.company} - ${existing.role} (status: ${existing.state})."
                    else ->
                        "An application with matching source URL already exists (${existing.company} - ${existing.role})."
                }
                return DuplicateCheckResult(
                    hasLikelyDuplicate = true,
                    existingApplication = existing,
                    reason = reason
                )
            }
        }
        return DuplicateCheckResult(hasLikelyDuplicate = false)
    }

    override suspend fun deleteApplication(id: String) {
        records.remove(id)
        notifyFlow()
    }

    override suspend fun clearAll() {
        records.clear()
        notifyFlow()
    }
}

/**
 * Room-backed implementation of [ApplicationTracker] for Android runtime.
 */
class RoomApplicationTracker(
    private val dao: ApplicationDao
) : ApplicationTracker {

    override suspend fun registerApplication(record: ApplicationRecord): DuplicateCheckResult {
        val dupResult = checkDuplicate(record)
        dao.upsert(ApplicationEntity.fromRecord(record))
        return dupResult
    }

    override suspend fun getApplication(id: String): ApplicationRecord? {
        return dao.getById(id)?.toRecord()
    }

    override suspend fun getAllApplications(): List<ApplicationRecord> {
        return dao.getAll().map { it.toRecord() }
    }

    override fun observeApplications(): Flow<List<ApplicationRecord>> {
        return dao.getAllFlow().map { entities -> entities.map { it.toRecord() } }
    }

    override suspend fun updateApplication(record: ApplicationRecord) {
        dao.upsert(ApplicationEntity.fromRecord(record))
    }

    override suspend fun transitionState(
        id: String,
        targetState: ApplicationState,
        notes: String?,
        confirmationReference: String?
    ): ApplicationRecord {
        val existing = dao.getById(id)?.toRecord()
            ?: throw NoSuchElementException("Application record not found: $id")

        if (targetState == ApplicationState.SUBMITTED_CONFIRMED) {
            require(!confirmationReference.isNullOrBlank()) {
                "Cannot mark SUBMITTED_CONFIRMED without explicit confirmation reference."
            }
        }

        val updated = existing.transitionTo(targetState, notes, confirmationReference)
        dao.upsert(ApplicationEntity.fromRecord(updated))
        return updated
    }

    override suspend fun checkDuplicate(candidate: ApplicationRecord): DuplicateCheckResult {
        val all = dao.getAll()
        for (entity in all) {
            val existing = entity.toRecord()
            if (candidate.isLikelyDuplicateOf(existing)) {
                val reason = when {
                    candidate.company.trim().equals(existing.company.trim(), ignoreCase = true) &&
                    candidate.role.trim().equals(existing.role.trim(), ignoreCase = true) ->
                        "An application already exists for ${existing.company} - ${existing.role} (status: ${existing.state})."
                    else ->
                        "An application with matching source URL already exists (${existing.company} - ${existing.role})."
                }
                return DuplicateCheckResult(
                    hasLikelyDuplicate = true,
                    existingApplication = existing,
                    reason = reason
                )
            }
        }
        return DuplicateCheckResult(hasLikelyDuplicate = false)
    }

    override suspend fun deleteApplication(id: String) {
        dao.deleteById(id)
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
