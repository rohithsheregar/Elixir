package com.cellclaw.ui

/**
 * Central navigation route definitions for the Elixir application.
 * Elixir places the Applications Tracker as the primary start destination.
 */
object NavigationRoutes {
    const val START_DESTINATION = "tracker"
    const val TRACKER = "tracker"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CHAT = "chat"
    const val SKILLS = "skills"
    const val APPROVALS = "approvals"
    const val RESEARCH = "research"
    const val SETUP = "setup"
    const val NEW_APPLICATION = "new_application"
    const val ELIGIBILITY_REPORT = "eligibility_report/{appId}"
    const val RESUME_PREVIEW = "resume_preview/{appId}"
    const val REVIEW = "review/{appId}"
    const val RUN = "run/{appId}"
    const val RESULT = "result/{appId}"

    fun eligibilityReportRoute(appId: String) = "eligibility_report/$appId"
    fun resumePreviewRoute(appId: String) = "resume_preview/$appId"
    fun reviewRoute(appId: String) = "review/$appId"
    fun runRoute(appId: String) = "run/$appId"
    fun resultRoute(appId: String) = "result/$appId"
}
