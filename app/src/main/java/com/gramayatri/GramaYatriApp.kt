package com.gramayatri

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.gramayatri.data.model.NetworkResult
import com.gramayatri.data.model.Route
import com.gramayatri.data.model.UserPreferences
import com.gramayatri.data.repository.FirebaseRepository
import com.gramayatri.data.repository.LocalCacheRepository
import com.gramayatri.ui.screens.alerts.AlertsScreen
import com.gramayatri.ui.screens.auth.AuthScreen
import com.gramayatri.ui.screens.home.HomeScreen
import com.gramayatri.ui.screens.onboarding.OnboardingScreen
import com.gramayatri.ui.screens.ping.PingScreen
import com.gramayatri.ui.screens.settings.SettingsScreen
import com.gramayatri.ui.theme.GramaYatriTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

object NavRoutes {
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PING = "ping/{routeId}"
    const val ALERTS = "alerts/{routeId}/{routeName}"
    const val SETTINGS = "settings"

    fun ping(routeId: String) = "ping/$routeId"
    fun alerts(routeId: String, routeName: String) =
        "alerts/$routeId/${routeName.replace("/", "|")}"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val localCacheRepository: LocalCacheRepository,
    private val firebaseRepository: FirebaseRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> =
        localCacheRepository.userPreferencesFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferences()
            )

    val routes: StateFlow<List<Route>> = firebaseRepository.observeRoutes()
        .mapNotNull { result ->
            when (result) {
                is NetworkResult.Success -> result.data
                else -> null
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val isAuthenticated: Boolean
        get() = firebaseAuth.currentUser != null

    val isGuest: Boolean
        get() = firebaseAuth.currentUser?.isAnonymous == true

    val userName: String
        get() = firebaseAuth.currentUser?.displayName ?: ""

    fun completeOnboarding(name: String, stopId: String, routeId: String) {
        viewModelScope.launch {
            val finalName = name.ifEmpty { userName }
            localCacheRepository.completeOnboarding(finalName, stopId, routeId)
        }
    }
}

@Composable
fun GramaYatriApp(viewModel: AppViewModel = hiltViewModel()) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()

    GramaYatriTheme {
        val navController = rememberNavController()

        val startDestination = when {
            !viewModel.isAuthenticated -> NavRoutes.AUTH
            !prefs.hasCompletedOnboarding -> NavRoutes.ONBOARDING
            else -> NavRoutes.HOME
        }

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {

            // ── Auth ──────────────────────────────────────────────
            composable(NavRoutes.AUTH) {
                AuthScreen(
                    onAuthSuccess = {
                        val dest = if (prefs.hasCompletedOnboarding)
                            NavRoutes.HOME else NavRoutes.ONBOARDING
                        navController.navigate(dest) {
                            popUpTo(NavRoutes.AUTH) { inclusive = true }
                        }
                    }
                )
            }

            // ── Onboarding ────────────────────────────────────────
            composable(NavRoutes.ONBOARDING) {
                OnboardingScreen(
                    routes = routes,
                    onComplete = { name, stopId, routeId ->
                        viewModel.completeOnboarding(name, stopId, routeId)
                        navController.navigate(NavRoutes.HOME) {
                            popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // ── Home ──────────────────────────────────────────────
            composable(NavRoutes.HOME) {
                HomeScreen(
                    onNavigateToPing = { routeId ->
                        navController.navigate(NavRoutes.ping(routeId))
                    },
                    onNavigateToAlerts = { routeId, routeName ->
                        navController.navigate(NavRoutes.alerts(routeId, routeName))
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoutes.SETTINGS)
                    }
                )
            }

            // ── Ping ──────────────────────────────────────────────
            composable(
                route = NavRoutes.PING,
                arguments = listOf(
                    navArgument("routeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments
                    ?.getString("routeId") ?: ""
                val route = routes.find { it.id == routeId }
                    ?: routes.firstOrNull()
                if (route != null) {
                    PingScreen(
                        route = route,
                        userName = prefs.userName.ifEmpty { viewModel.userName },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // ── Alerts ────────────────────────────────────────────
            composable(
                route = NavRoutes.ALERTS,
                arguments = listOf(
                    navArgument("routeId") { type = NavType.StringType },
                    navArgument("routeName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments
                    ?.getString("routeId") ?: ""
                val routeName = backStackEntry.arguments
                    ?.getString("routeName")
                    ?.replace("|", "/") ?: ""
                AlertsScreen(
                    routeId = routeId,
                    routeName = routeName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Settings ──────────────────────────────────────────
            composable(NavRoutes.SETTINGS) {
                SettingsScreen(
                    routes = routes,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}