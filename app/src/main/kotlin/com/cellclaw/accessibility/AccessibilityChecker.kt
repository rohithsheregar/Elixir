package com.cellclaw.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AccessibilityInspector {
    fun isServiceEnabled(context: Context): Boolean
    fun createSettingsIntent(context: Context): Intent
}

@Singleton
open class AccessibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : AccessibilityInspector {

    companion object {
        const val ACCESSIBILITY_SERVICE_CLASS = "com.cellclaw.service.CellClawAccessibility"
        const val ACTION_ACCESSIBILITY_DETAIL_SETTINGS = "android.settings.ACCESSIBILITY_DETAIL_SETTINGS"
    }

    /**
     * Checks if the Elixir (CellClaw) AccessibilityService is currently enabled in Android system settings.
     */
    override fun isServiceEnabled(context: Context): Boolean {
        return checkEnabled(context)
    }

    open fun isEnabled(): Boolean = isServiceEnabled(context)

    /**
     * Creates an Intent directing the user to enable the Elixir AccessibilityService in Settings.
     * Uses detail settings on API 36+ where available, falling back to standard ACTION_ACCESSIBILITY_SETTINGS.
     */
    override fun createSettingsIntent(context: Context): Intent {
        val componentName = ComponentName(context, ACCESSIBILITY_SERVICE_CLASS)

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val detailIntent = Intent(ACTION_ACCESSIBILITY_DETAIL_SETTINGS)
                detailIntent.putExtra(Intent.EXTRA_COMPONENT_NAME, componentName.flattenToString())
                detailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return detailIntent
            } catch (_: Throwable) {
                // Fall back to generic accessibility settings
            }
        }

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Throwable) {
            // Unmocked on JVM test runtime
        }
        return intent
    }

    open fun getSettingsIntent(): Intent = createSettingsIntent(context)

    open fun checkEnabled(ctx: Context): Boolean {
        val expectedComponent = ComponentName(ctx, ACCESSIBILITY_SERVICE_CLASS)
        val enabledServices = try {
            Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (_: Exception) {
            null
        } ?: return false

        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == expectedComponent
        }
    }
}
