package com.cellclaw.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cellclaw.config.AppConfig
import com.cellclaw.provider.ProviderManager
import com.cellclaw.service.CellClawService
import com.cellclaw.service.overlay.OverlayService
import com.cellclaw.ui.screens.*
import com.cellclaw.ui.theme.CellClawTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var providerManager: ProviderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle debug setup via adb: am start -n com.cellclaw/.ui.MainActivity
        //   --es provider "openrouter" --es api_key "sk-or-..." --es model "google/gemini-2.5-flash"
        intent?.let { handleSetupIntent(it) }

        // Start foreground service to maintain network access when backgrounded
        if (appConfig.isSetupComplete) {
            startService()
            // Start overlay if enabled and permission granted
            if (appConfig.overlayEnabled && Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }

        val pendingMessage = intent?.getStringExtra("message")
        val isShareIntent = intent?.action == Intent.ACTION_SEND
        val sharedText = if (isShareIntent) intent?.getStringExtra(Intent.EXTRA_TEXT) else null
        val sharedStreamUri = if (isShareIntent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else null

        setContent {
            CellClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDest = NavigationRoutes.START_DESTINATION

                    LaunchedEffect(isShareIntent) {
                        if (isShareIntent) {
                            navController.navigate(NavigationRoutes.NEW_APPLICATION)
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        enterTransition = {
                            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        composable(NavigationRoutes.SETUP) {
                            SetupScreen(
                                onComplete = {
                                    appConfig.isSetupComplete = true
                                    startService()
                                    navController.navigate(NavigationRoutes.TRACKER) {
                                        popUpTo(NavigationRoutes.SETUP) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(NavigationRoutes.CHAT) {
                            ChatScreen(
                                onNavigateToSettings = { navController.navigate(NavigationRoutes.SETTINGS) },
                                onNavigateToSkills = { navController.navigate(NavigationRoutes.SKILLS) },
                                onNavigateToApprovals = { navController.navigate(NavigationRoutes.APPROVALS) },
                                onNavigateToTracker = { navController.navigate(NavigationRoutes.TRACKER) },
                                initialMessage = pendingMessage
                            )
                        }
                        composable(NavigationRoutes.SETTINGS) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(NavigationRoutes.SKILLS) {
                            SkillsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(NavigationRoutes.APPROVALS) {
                            ApprovalScreen(onBack = { navController.popBackStack() })
                        }
                        composable(NavigationRoutes.TRACKER) {
                            TrackerScreen(
                                onNavigateToNewApp = { navController.navigate(NavigationRoutes.NEW_APPLICATION) },
                                onNavigateToProfile = { navController.navigate(NavigationRoutes.PROFILE) },
                                onNavigateToSettings = { navController.navigate(NavigationRoutes.SETTINGS) },
                                onNavigateToChat = { navController.navigate(NavigationRoutes.CHAT) },
                                onSelectApplication = { appId -> navController.navigate(NavigationRoutes.reviewRoute(appId)) }
                            )
                        }
                        composable(NavigationRoutes.PROFILE) {
                            ProfileScreen(onBack = { navController.popBackStack() })
                        }
                        composable(NavigationRoutes.NEW_APPLICATION) {
                            NewApplicationScreen(
                                onBack = { navController.popBackStack() },
                                onApplicationCreated = { appId -> navController.navigate(NavigationRoutes.eligibilityReportRoute(appId)) },
                                initialJdText = sharedText,
                                initialStreamUri = sharedStreamUri
                            )
                        }
                        composable(NavigationRoutes.ELIGIBILITY_REPORT) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                            EligibilityReportScreen(
                                appId = appId,
                                onBack = { navController.popBackStack() },
                                onProceedToResume = { id -> navController.navigate(NavigationRoutes.resumePreviewRoute(id)) }
                            )
                        }
                        composable(NavigationRoutes.RESUME_PREVIEW) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                            ResumePreviewScreen(
                                appId = appId,
                                onBack = { navController.popBackStack() },
                                onApproveResume = { id -> navController.navigate(NavigationRoutes.reviewRoute(id)) }
                            )
                        }
                        composable(NavigationRoutes.REVIEW) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                            ReviewScreen(
                                appId = appId,
                                onBack = { navController.popBackStack() },
                                onApproveSubmission = { id -> navController.navigate(NavigationRoutes.runRoute(id)) }
                            )
                        }
                        composable(NavigationRoutes.RUN) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                            RunScreen(
                                appId = appId,
                                onBack = { navController.popBackStack() },
                                onComplete = { id -> navController.navigate(NavigationRoutes.resultRoute(id)) }
                            )
                        }
                        composable(NavigationRoutes.RESULT) { backStackEntry ->
                            val appId = backStackEntry.arguments?.getString("appId").orEmpty()
                            ResultScreen(
                                appId = appId,
                                onBack = { navController.popBackStack() },
                                onReturnToTracker = {
                                    navController.navigate(NavigationRoutes.TRACKER) {
                                        popUpTo(NavigationRoutes.TRACKER) { inclusive = true }
                                    }
                                },
                                onRetry = { id ->
                                    navController.navigate(NavigationRoutes.reviewRoute(id))
                                }
                            )
                        }
                    }

                }
            }
        }
    }

    private fun handleSetupIntent(intent: Intent) {
        val provider = intent.getStringExtra("provider") ?: return
        val apiKey = intent.getStringExtra("api_key") ?: return
        val model = intent.getStringExtra("model")

        providerManager.switchProvider(provider)
        providerManager.setApiKey(provider, apiKey)
        if (model != null) {
            appConfig.model = model
        }
        appConfig.isSetupComplete = true
    }

    private fun startService() {
        val intent = Intent(this, CellClawService::class.java).apply {
            action = CellClawService.ACTION_START
        }
        startForegroundService(intent)
    }
}
