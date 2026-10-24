package com.cellclaw.ui.navigation

sealed class ElixirRoute(val route: String, val title: String) {
    object Tracker : ElixirRoute("tracker", "Applications")
    object Profile : ElixirRoute("profile", "Placement Profile")
    object NewApplication : ElixirRoute("new_application", "New Application")
    object EligibilityReport : ElixirRoute("eligibility_report/{appId}", "Eligibility Report") {
        fun createRoute(appId: String): String = "eligibility_report/$appId"
    }
    object ResumePreview : ElixirRoute("resume_preview/{appId}", "Resume Preview") {
        fun createRoute(appId: String): String = "resume_preview/$appId"
    }
    object ReviewScreen : ElixirRoute("review/{appId}", "Review & Submit") {
        fun createRoute(appId: String): String = "review/$appId"
    }
}
