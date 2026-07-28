package com.familyconnect.app.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Splash : NavRoutes("splash")
    data object Onboarding : NavRoutes("onboarding")
    data object RoleSelection : NavRoutes("role_selection")
    data object ParentPairing : NavRoutes("parent_pairing")
    data object ChildPairing : NavRoutes("child_pairing")
    data object ParentDashboard : NavRoutes("parent_dashboard")
    data object LiveView : NavRoutes("live_view/{childId}") {
        fun createRoute(childId: String): String = "live_view/$childId"
    }
    data object History : NavRoutes("history")
    data object Settings : NavRoutes("settings")
    data object ChildIdle : NavRoutes("child_idle")
    data object QrScanner : NavRoutes("qr_scanner")
}
