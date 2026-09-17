package com.yu.syncon.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * Checks if Usage Access permission (PACKAGE_USAGE_STATS) has been granted.
     */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Checks if the app's BlockAccessibilityService is enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (_: Settings.SettingNotFoundException) {
            0
        }
        if (accessibilityEnabled != 1) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedComponent = "${context.packageName}/com.yu.syncon.service.accessibility.BlockAccessibilityService"
        val expectedShortComponent = "${context.packageName}/.service.accessibility.BlockAccessibilityService"

        return enabledServices.split(":").any { service ->
            service.equals(expectedComponent, ignoreCase = true) ||
            service.equals(expectedShortComponent, ignoreCase = true) ||
            (service.contains(context.packageName) && service.contains("BlockAccessibilityService"))
        }
    }

    /**
     * Checks if battery optimizations are ignored for this app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Checks if POST_NOTIFICATIONS is granted (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if all 3 core permissions (Usage Access, Accessibility, Battery Optimizations) are granted.
     */
    fun hasAllCorePermissions(context: Context): Boolean {
        return hasUsageAccess(context) &&
               isAccessibilityServiceEnabled(context) &&
               isIgnoringBatteryOptimizations(context)
    }

    /**
     * Intent to open Usage Access settings screen.
     */
    fun getUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to open Accessibility settings screen.
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to request ignoring battery optimizations.
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Intent to open App Details / App Info screen.
     * Essential on Android 13+ / Xiaomi for tapping 3 dots -> 'Allow restricted settings'
     * so Accessibility can be enabled on sideloaded apps.
     */
    fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
