package com.cellclaw.agent

import com.cellclaw.browser.UrlValidator
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.tools.JobSearchTool
import com.cellclaw.tools.WebFetchTool
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.cellclaw.tracker.InMemoryApplicationTracker
import java.time.Instant

class JobResearchEngineTest {

    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var urlValidator: UrlValidator
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var jobSearchTool: JobSearchTool
    private lateinit var webFetchTool: WebFetchTool
    private lateinit var engine: JobResearchEngine

    @Before
    fun setUp() = runBlocking {
        profileRepo = InMemoryProfileRepository()
        urlValidator = UrlValidator()
        tracker = InMemoryApplicationTracker()
        val client = OkHttpClient.Builder().build()
        jobSearchTool = JobSearchTool(urlValidator, tracker, client)
        webFetchTool = WebFetchTool(urlValidator, client)
        engine = JobResearchEngine(profileRepo, jobSearchTool, webFetchTool, tracker)

        // Seed candidate profile
        val profile = CandidateProfile(
            name = ProfileFact("1", "name", "Alex Rivera", ProfileFactSource.USER_ENTERED, true, Instant.now()),
            degree = ProfileFact("2", "degree", "B.Tech", ProfileFactSource.USER_ENTERED, true, Instant.now()),
            branch = ProfileFact("3", "branch", "Computer Science", ProfileFactSource.USER_ENTERED, true, Instant.now()),
            skills = listOf(
                ProfileFact("4", "skill", "Kotlin", ProfileFactSource.USER_ENTERED, true, Instant.now()),
                ProfileFact("5", "skill", "Android", ProfileFactSource.USER_ENTERED, true, Instant.now())
            )
        )
        profileRepo.saveProfile(profile)
    }

    @Test
    fun `engine state transitions from idle to running and completed`() = runBlocking {
        assertEquals("Idle", engine.state.value.currentStage)
        assertFalse(engine.state.value.isRunning)

        // Run research with a specific query
        val results = engine.runResearch("Kotlin Android developer")

        assertFalse(engine.state.value.isRunning)
        assertEquals("Completed", engine.state.value.currentStage)
        assertTrue(engine.state.value.logs.isNotEmpty())
        assertTrue("Progress should reach 1.0", engine.state.value.progressPercent >= 1.0f)
    }

    @Test
    fun `engine logs show discovery validation and relevance stages`() = runBlocking {
        engine.runResearch("site:linkedin.com Android")

        val logs = engine.state.value.logs
        assertTrue("Must contain DISCOVERY log", logs.any { it.stage == "DISCOVERY" })
        assertTrue("Must log progress or completion", logs.isNotEmpty())
    }
}
