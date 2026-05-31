package com.gramayatri

import android.content.Context
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
import com.gramayatri.data.worker.ProximityWorker
import com.gramayatri.ui.screens.alerts.AlertsScreen
import com.gramayatri.ui.screens.auth.AuthScreen
import com.gramayatri.ui.screens.home.HomeScreen
import com.gramayatri.ui.screens.intro.IntroScreen
import com.gramayatri.ui.screens.language.LanguageScreen
import com.gramayatri.ui.screens.onboarding.OnboardingScreen
import com.gramayatri.ui.screens.ping.PingScreen
import com.gramayatri.ui.screens.settings.SettingsScreen
import com.gramayatri.ui.theme.GramaYatriTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.gramayatri.ui.screens.search.RouteSearchScreen
import com.gramayatri.ui.screens.splash.SplashScreen

object NavRoutes {
    const val SPLASH = "splash"
    const val LANGUAGE = "language"
    const val INTRO = "intro"
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PING = "ping/{routeId}"
    const val ALERTS = "alerts/{routeId}/{routeName}"
    const val SETTINGS = "settings"
    const val ROUTE_SEARCH = "route_search"

    fun ping(routeId: String) = "ping/$routeId"
    fun alerts(routeId: String, routeName: String) =
        "alerts/$routeId/${routeName.replace("/", "|")}"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val localCacheRepository: LocalCacheRepository,
    private val firebaseRepository: dagger.Lazy<FirebaseRepository>,
    private val firebaseAuth: dagger.Lazy<FirebaseAuth>,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            localCacheRepository.userPreferencesFlow.first()
            _isReady.value = true
        }

        viewModelScope.launch {
            delay(2_500)
            ProximityWorker.schedule(appContext)
        }
    }

    val userPreferences: StateFlow<UserPreferences> =
        localCacheRepository.userPreferencesFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferences()
            )

    val routes: StateFlow<List<Route>> = flow {
        emitAll(firebaseRepository.get().observeRoutes())
    }
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
        get() = firebaseAuth.get().currentUser != null

    val isGuest: Boolean
        get() = firebaseAuth.get().currentUser?.isAnonymous == true

    val userName: String
        get() = firebaseAuth.get().currentUser?.displayName ?: ""

    fun completeOnboarding(name: String, stopId: String, routeId: String) {
        viewModelScope.launch {
            val finalName = name.ifEmpty { userName }
            localCacheRepository.completeOnboarding(finalName, stopId, routeId)
        }
    }

    fun selectLanguage(language: com.gramayatri.data.model.AppLanguage) {
        viewModelScope.launch {
            localCacheRepository.updateLanguage(language)
        }
    }

    fun markIntroSeen() {
        viewModelScope.launch {
            localCacheRepository.markIntroSeen()
        }
    }

    fun dashboardRouteFor(prefs: UserPreferences): String {
        return if (prefs.hasCompletedOnboarding) NavRoutes.HOME else NavRoutes.ONBOARDING
    }
}

@Composable
fun GramaYatriApp(viewModel: AppViewModel = hiltViewModel()) {
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    GramaYatriTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = NavRoutes.SPLASH
        ) {
            // ── Splash ──────────────────────────────────────────
            composable(NavRoutes.SPLASH) {
                SplashScreen(
                    isReady = isReady,
                    onReady = {
                        if (isReady) {
                            val dest = when {
                                !prefs.hasSelectedLanguage -> NavRoutes.LANGUAGE
                                !prefs.hasSeenIntro -> NavRoutes.INTRO
                                else -> viewModel.dashboardRouteFor(prefs)
                            }
                            navController.navigate(dest) {
                                popUpTo(NavRoutes.SPLASH) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // ── Auth ──────────────────────────────────────────────
            composable(NavRoutes.LANGUAGE) {
                LanguageScreen(
                    onLanguageSelected = { language ->
                        viewModel.selectLanguage(language)
                        navController.navigate(NavRoutes.INTRO) {
                            popUpTo(NavRoutes.LANGUAGE) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.INTRO) {
                IntroScreen(
                    language = prefs.language,
                    onContinue = {
                        viewModel.markIntroSeen()
                        navController.navigate(viewModel.dashboardRouteFor(prefs)) {
                            popUpTo(NavRoutes.INTRO) { inclusive = true }
                        }
                    }
                )
            }

            composable(NavRoutes.AUTH) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(viewModel.dashboardRouteFor(prefs)) {
                            popUpTo(NavRoutes.AUTH) { inclusive = true }
                        }
                    }
                )
            }

            // ── Onboarding ────────────────────────────────────────
            composable(NavRoutes.ONBOARDING) {
                val routes by viewModel.routes.collectAsStateWithLifecycle()
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
                    language = prefs.language,
                    onNavigateToPing = { routeId ->
                        navController.navigate(NavRoutes.ping(routeId))
                    },
                    onNavigateToAlerts = { routeId, routeName ->
                        navController.navigate(NavRoutes.alerts(routeId, routeName))
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoutes.SETTINGS)
                    },
                    onNavigateToSearch = {
                        navController.navigate(NavRoutes.ROUTE_SEARCH)
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
                val routes by viewModel.routes.collectAsStateWithLifecycle()
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
                val routes by viewModel.routes.collectAsStateWithLifecycle()
                SettingsScreen(
                    routes = routes,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── Route Search ────────────────────────────────────────
            composable(NavRoutes.ROUTE_SEARCH) {
                val routes by viewModel.routes.collectAsStateWithLifecycle()
                RouteSearchScreen(
                    language = prefs.language,
                    routes = routes,
                    onNavigateBack = { navController.popBackStack() },
                    onRouteSelected = { route ->
                        // Pass selected route ID back to HOME via savedStateHandle
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("selectedRouteId", route.id)
                        navController.popBackStack()
                    },
                    onNavigateToAlerts = { routeId, routeName ->
                        navController.navigate(NavRoutes.alerts(routeId, routeName))
                    }
                )
            }
        }
    }
}
