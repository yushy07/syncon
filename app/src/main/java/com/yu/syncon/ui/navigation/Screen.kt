package com.yu.syncon.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object AppList : Screen("app_list")
    data object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    data object Trends : Screen("trends")
    data object Settings : Screen("settings")
    data object TrackingStatus : Screen("tracking_status")
}
