package com.familyconnect.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.familyconnect.app.ui.child.ChildIdleScreen
import com.familyconnect.app.ui.onboarding.OnboardingScreen
import com.familyconnect.app.ui.pairing.ChildPairingScreen
import com.familyconnect.app.ui.pairing.PairingViewModel
import com.familyconnect.app.ui.pairing.ParentPairingScreen
import com.familyconnect.app.ui.pairing.QrScannerScreen
import com.familyconnect.app.ui.parent.dashboard.ParentDashboardScreen
import com.familyconnect.app.ui.parent.history.HistoryScreen
import com.familyconnect.app.ui.parent.liveview.LiveViewScreen
import com.familyconnect.app.ui.roleselection.RoleSelectionScreen
import com.familyconnect.app.ui.roleselection.RoleSelectionViewModel
import com.familyconnect.app.ui.settings.SettingsScreen
import com.familyconnect.app.MainActivity
import com.familyconnect.app.ui.splash.SplashScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route
    ) {
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onNavigation = { destination ->
                    val pendingCode = MainActivity.pendingPairingCode
                    val finalDestination = if (pendingCode != null) {
                        NavRoutes.ChildPairing.route
                    } else {
                        destination
                    }
                    navController.navigate(finalDestination) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(NavRoutes.RoleSelection.route) {
                        popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.RoleSelection.route) {
            val vm: RoleSelectionViewModel = viewModel()
            RoleSelectionScreen(
                viewModel = vm,
                onRoleSelected = { role ->
                    when (role) {
                        com.familyconnect.app.data.model.UserRole.PARENT -> {
                            navController.navigate(NavRoutes.ParentPairing.route) {
                                popUpTo(NavRoutes.RoleSelection.route) { inclusive = true }
                            }
                        }
                        com.familyconnect.app.data.model.UserRole.CHILD -> {
                            navController.navigate(NavRoutes.ChildPairing.route) {
                                popUpTo(NavRoutes.RoleSelection.route) { inclusive = true }
                            }
                        }
                    }
                },
                onSkipToDashboard = {
                    navController.navigate(NavRoutes.ParentDashboard.route) {
                        popUpTo(NavRoutes.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.ParentPairing.route) {
            val vm: PairingViewModel = viewModel()
            ParentPairingScreen(
                viewModel = vm,
                onPaired = {
                    navController.navigate(NavRoutes.ParentDashboard.route) {
                        popUpTo(NavRoutes.ParentPairing.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ChildPairing.route) { backStackEntry ->
            val vm: PairingViewModel = viewModel()
            var scannedCode = backStackEntry.savedStateHandle.get<String>("scanned_code") ?: ""
            if (scannedCode.isEmpty()) {
                scannedCode = MainActivity.pendingPairingCode ?: ""
                MainActivity.pendingPairingCode = null
            }
            if (scannedCode.isNotEmpty()) {
                backStackEntry.savedStateHandle.remove<String>("scanned_code")
            }
            ChildPairingScreen(
                viewModel = vm,
                scannedCode = scannedCode,
                onPaired = {
                    navController.navigate(NavRoutes.ChildIdle.route) {
                        popUpTo(NavRoutes.ChildPairing.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate(NavRoutes.QrScanner.route) }
            )
        }

        composable(NavRoutes.QrScanner.route) {
            QrScannerScreen(
                onCodeScanned = { code ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_code", code)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ParentDashboard.route) {
            ParentDashboardScreen(
                onLiveView = { childId ->
                    navController.navigate(NavRoutes.LiveView.createRoute(childId))
                },
                onHistory = { navController.navigate(NavRoutes.History.route) },
                onSettings = { navController.navigate(NavRoutes.Settings.route) },
                onAddChild = {
                    navController.navigate(NavRoutes.ParentPairing.route)
                }
            )
        }

        composable(
            route = NavRoutes.LiveView.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getString("childId") ?: ""
            LiveViewScreen(
                childId = childId,
                onStop = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(NavRoutes.RoleSelection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.ChildIdle.route) {
            ChildIdleScreen(
                onNavigate = { route ->
                    when (route) {
                        "settings" -> navController.navigate(NavRoutes.Settings.route)
                        else -> navController.navigate(route)
                    }
                }
            )
        }
    }
}
