package com.yu.syncon.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.service.tracking.ForegroundTrackingService
import com.yu.syncon.ui.appdetail.AppDetailScreen
import com.yu.syncon.ui.applist.AppListScreen
import com.yu.syncon.ui.dashboard.DashboardScreen
import com.yu.syncon.ui.navigation.Screen
import com.yu.syncon.ui.onboarding.OnboardingScreen
import com.yu.syncon.ui.settings.SettingsScreen
import com.yu.syncon.ui.theme.SyncOnTheme
import com.yu.syncon.ui.trends.TrendsScreen
import com.yu.syncon.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: UsageRepository

    private var hasUsageAccess by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isBatteryOptimizationIgnored by mutableStateOf(false)
    private var isOnboardingCompleted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = UsageRepository(applicationContext)

        val prefs = getSharedPreferences("syncon_prefs", Context.MODE_PRIVATE)
        isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        setContent {
            SyncOnTheme {
                MainAppContent(
                    repository = repository,
                    hasUsageAccess = hasUsageAccess,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    isOnboardingCompleted = isOnboardingCompleted,
                    onCompleteOnboarding = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                        isOnboardingCompleted = true
                        startTrackingServiceIfNeeded()
                    },
                    onStartTrackingService = { startTrackingServiceIfNeeded() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()

        if (PermissionUtils.hasAllCorePermissions(this)) {
            startTrackingServiceIfNeeded()
            lifecycleScope.launch {
                // Gap reconciliation on resume (PRD §6.7 & §7.6)
                repository.reconcileGaps()
                repository.syncInstalledApps()
            }
        }
    }

    private fun refreshPermissionStates() {
        hasUsageAccess = PermissionUtils.hasUsageAccess(this)
        isAccessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(this)
        isBatteryOptimizationIgnored = PermissionUtils.isIgnoringBatteryOptimizations(this)
    }

    private fun startTrackingServiceIfNeeded() {
        try {
            val serviceIntent = Intent(this, ForegroundTrackingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: Exception) {
            // Guard against background startup limitations
        }
    }
}

@Composable
fun MainAppContent(
    repository: UsageRepository,
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isOnboardingCompleted: Boolean,
    onCompleteOnboarding: () -> Unit,
    onStartTrackingService: () -> Unit
) {
    val navController = rememberNavController()

    if (!isOnboardingCompleted) {
        OnboardingScreen(
            hasUsageAccess = hasUsageAccess,
            isAccessibilityEnabled = isAccessibilityEnabled,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            onAllGranted = {
                onCompleteOnboarding()
            }
        )
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.AppList.route,
                    Screen.Trends.route,
                    Screen.Settings.route
                )
            ) {
                NavigationBar(
                    containerColor = com.yu.syncon.ui.theme.WarmBackground,
                    contentColor = com.yu.syncon.ui.theme.TextPrimary
                ) {
                    val navItemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = com.yu.syncon.ui.theme.PrimaryIndigo,
                        selectedTextColor = com.yu.syncon.ui.theme.PrimaryIndigo,
                        indicatorColor = com.yu.syncon.ui.theme.PrimaryIndigoLight,
                        unselectedIconColor = com.yu.syncon.ui.theme.TextSecondary,
                        unselectedTextColor = com.yu.syncon.ui.theme.TextSecondary
                    )

                    NavigationBarItem(
                        selected = currentRoute == Screen.Dashboard.route,
                        onClick = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Today") },
                        label = { Text("Today") },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.AppList.route,
                        onClick = {
                            navController.navigate(Screen.AppList.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Apps") },
                        label = { Text("Apps") },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Trends.route,
                        onClick = {
                            navController.navigate(Screen.Trends.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Trends") },
                        label = { Text("Trends") },
                        colors = navItemColors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = navItemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    repository = repository,
                    onAppClick = { pkg ->
                        navController.navigate(Screen.AppDetail.createRoute(pkg))
                    }
                )
            }
            composable(Screen.AppList.route) {
                AppListScreen(
                    repository = repository,
                    onAppClick = { pkg ->
                        navController.navigate(Screen.AppDetail.createRoute(pkg))
                    }
                )
            }
            composable(
                route = Screen.AppDetail.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val pkg = backStackEntry.arguments?.getString("packageName") ?: ""
                AppDetailScreen(
                    packageName = pkg,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Trends.route) {
                TrendsScreen(repository = repository)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    hasUsageAccess = hasUsageAccess,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored
                )
            }
        }
    }
}
