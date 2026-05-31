package com.gramayatri.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.text.font.FontStyle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gramayatri.data.model.*
import com.gramayatri.domain.usecase.EtaCalculator
import com.gramayatri.ui.i18n.AppText
import com.gramayatri.ui.theme.GramaColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    language: AppLanguage,
    onNavigateToPing: (String) -> Unit,
    onNavigateToAlerts: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val etaCalculator = remember { EtaCalculator() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isMapView by remember { mutableStateOf(false) }
    var fromQuery by remember { mutableStateOf("") }
    var toQuery by remember { mutableStateOf("") }
    var insideBus by remember { mutableStateOf(false) }
    var showProximityAlert by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "🚌 Grama-Yatri",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Community Bus Tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (uiState.selectedRoute != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${uiState.selectedRoute!!.number} • ${uiState.selectedRoute!!.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Divider
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                Spacer(modifier = Modifier.height(8.dp))

                // Drawer items
                DrawerItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    onClick = {
                        scope.launch { drawerState.close() }
                    }
                )
                DrawerItem(
                    icon = Icons.Default.Lightbulb,
                    label = "Suggestions",
                    onClick = {
                        scope.launch { drawerState.close() }
                        openFeedback(context, "suggestion")
                    }
                )
                DrawerItem(
                    icon = Icons.Default.BugReport,
                    label = "Report a Bug",
                    onClick = {
                        scope.launch { drawerState.close() }
                        openFeedback(context, "bug")
                    }
                )
                DrawerItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
                DrawerItem(
                    icon = Icons.Default.Info,
                    label = "About",
                    onClick = {
                        scope.launch { drawerState.close() }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Footer
                Text(
                    text = "v1.0.0 • Made with ❤️ for rural India",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    ) {
        // ─── Proximity Alert Banner ────────────────────────────────────────
    val proximityAlert = viewModel.proximityAlert.collectAsStateWithLifecycle().value

    Scaffold(
            topBar = {
                HomeTopBar(
                    routes = uiState.routes,
                    selectedRoute = uiState.selectedRoute,
                    isOffline = uiState.isOffline,
                    isMapView = isMapView,
                    onSearchClick = onNavigateToSearch,
                    onRouteSelected = { viewModel.switchRoute(it) },
                    onMenuClick = {
                        scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                    },
                    onToggleView = { isMapView = !isMapView },
                    alertCount = if (uiState.activeAlert != null) 1 else 0
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // In-app proximity alert
                    AnimatedVisibility(visible = proximityAlert != null) {
                        proximityAlert?.let { alert ->
                            Surface(
                                color = GramaColors.LeafContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.dismissProximityAlert() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsBus,
                                        contentDescription = null,
                                        tint = GramaColors.LeafGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alert,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GramaColors.LeafGreen
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissProximityAlert() },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            uiState.isLoading -> HomeSkeletonLoader()
                            uiState.error != null && uiState.stopEtas.isEmpty() -> ErrorState(
                                message = uiState.error!!,
                                onRetry = { viewModel.retry() }
                            )
                            uiState.selectedRoute == null -> EmptyRoutesState()
                            isMapView -> LiveMapContent(
                                route = uiState.selectedRoute!!,
                                liveLocation = uiState.liveBusLocation,
                                activePing = uiState.activePing
                            )
                            else -> RouteTimelineContent(
                                uiState = uiState,
                                language = language,
                                fromQuery = fromQuery,
                                toQuery = toQuery,
                                insideBus = insideBus,
                                onFromChanged = { fromQuery = it },
                                onToChanged = { toQuery = it },
                                onInsideBusChanged = { insideBus = it },
                                onRouteSelected = { viewModel.switchRoute(it) },
                                etaCalculator = etaCalculator,
                                onConfirmPing = { pingId, confirmed ->
                                    viewModel.confirmPing(pingId, confirmed)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openFeedback(context: android.content.Context, type: String) {
    val subject = if (type == "bug") "Bug Report - Grama-Yatri" else "Suggestion - Grama-Yatri"
    val email = "gramayatri.feedback@gmail.com"
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "\n\n---\nApp: Grama-Yatri v1.0.0\n")
        }
        context.startActivity(Intent.createChooser(intent, "Send feedback"))
    } catch (e: Exception) {
        // Fallback: open browser to a feedback form URL
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://forms.gle/gramayatri-feedback"))
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    routes: List<Route>,
    selectedRoute: Route?,
    isOffline: Boolean,
    isMapView: Boolean,
    onRouteSelected: (Route) -> Unit,
    onMenuClick: () -> Unit,
    onToggleView: () -> Unit,
    alertCount: Int,
    onSearchClick: () -> Unit = {}
) {
    var showRouteMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = "🚌 Grama-Yatri",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedRoute != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showRouteMenu = true }
                    ) {
                        Text(
                            text = "${selectedRoute.number} • ${selectedRoute.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch route",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation menu"
                )
            }
        },
        actions = {
            // Search button
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search buses"
                )
            }

            // View toggle
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isMapView) Icons.Default.List else Icons.Default.Map,
                    contentDescription = "Toggle view"
                )
            }

            // Offline badge
            AnimatedVisibility(visible = isOffline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Alerts icon with badge
            BadgedBox(
                badge = {
                    if (alertCount > 0) Badge(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                }
            ) {
                IconButton(onClick = { showRouteMenu = true }) {
                    // Actually navigate to alerts on click — simplified
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )

    // Route switcher dropdown
    if (showRouteMenu && routes.size > 1) {
        RouteSwitcherDialog(
            routes = routes,
            selectedRoute = selectedRoute,
            onRouteSelected = {
                onRouteSelected(it)
                showRouteMenu = false
            },
            onDismiss = { showRouteMenu = false }
        )
    }
}

// ─── Main Content ──────────────────────────────────────────────────────────

@Composable
private fun RouteTimelineContent(
    uiState: HomeUiState,
    language: AppLanguage,
    fromQuery: String,
    toQuery: String,
    insideBus: Boolean,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
    onInsideBusChanged: (Boolean) -> Unit,
    onRouteSelected: (Route) -> Unit,
    etaCalculator: EtaCalculator,
    onConfirmPing: (String, Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)
    ) {
        // ── Live Bus Info Card ─────────────────────────────────────
        item {
            LiveBusInfoCard(
                liveLocation = uiState.liveBusLocation,
                selectedRoute = uiState.selectedRoute,
                lastSyncTime = uiState.lastSyncTime,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        item {
            PassengerSearchCard(
                language = language,
                routes = uiState.routes,
                fromQuery = fromQuery,
                toQuery = toQuery,
                insideBus = insideBus,
                onFromChanged = onFromChanged,
                onToChanged = onToChanged,
                onInsideBusChanged = onInsideBusChanged,
                onRouteSelected = onRouteSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Active alert banner
        uiState.activeAlert?.let { alert ->
            item {
                AlertBanner(alert = alert, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        if (uiState.liveBusLocation == null && uiState.selectedRoute != null) {
            item {
                NoLiveDataBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Route info header
        uiState.selectedRoute?.let { route ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${route.origin}  →  ${route.destination}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.lastSyncTime?.let { time ->
                        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                        Text(
                            text = fmt.format(Date(time)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Stop timeline
        if (uiState.stopEtas.isNotEmpty()) {
            itemsIndexed(uiState.stopEtas) { index, stopEta ->
                StopTimelineItem(
                    stopEta = stopEta,
                    isFirst = index == 0,
                    isLast = index == uiState.stopEtas.lastIndex,
                    etaCalculator = etaCalculator
                )
            }
        } else if (uiState.selectedRoute != null && !uiState.isLoading) {
            itemsIndexed(uiState.selectedRoute!!.stops) { index, stop ->
                StopTimelineItem(
                    stopEta = StopEta(stop = stop, etaMinutes = null, etaTimestamp = null),
                    isFirst = index == 0,
                    isLast = index == uiState.selectedRoute!!.stops.lastIndex,
                    etaCalculator = etaCalculator
                )
            }
        }
    }
}

// ─── Live Bus Info Card ──────────────────────────────────────────────────

@Composable
private fun LiveBusInfoCard(
    liveLocation: LiveBusLocation?,
    selectedRoute: Route?,
    lastSyncTime: Long?,
    modifier: Modifier = Modifier
) {
    if (liveLocation == null && selectedRoute == null) return

    val location = liveLocation
    val hasLiveData = location != null && location.isActive
    val ageMinutes = if (location != null)
        ((System.currentTimeMillis() - location.timestamp) / 60_000).toInt() else null

    val sourceLabel = when (location?.source) {
        LocationSource.TICKET_MACHINE -> "Ticket Machine"
        LocationSource.DRIVER -> "Driver"
        LocationSource.PASSENGER -> "Passenger"
        null -> null
    }

    val sourceIcon = when (location?.source) {
        LocationSource.TICKET_MACHINE -> "🖥️"
        LocationSource.DRIVER -> "👨‍✈️"
        LocationSource.PASSENGER -> "📱"
        null -> null
    }

    val sourceBadgeColor = when (location?.source) {
        LocationSource.TICKET_MACHINE -> GramaColors.LeafGreen
        LocationSource.DRIVER -> GramaColors.SaffronDeep
        LocationSource.PASSENGER -> GramaColors.SkyBlue
        null -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color = if (hasLiveData) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (hasLiveData) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasLiveData) {
                        // Pulse dot
                        val pulseAnim = rememberInfiniteTransition(label = "pulseLive")
                        val alpha by pulseAnim.animateFloat(
                            initialValue = 0.4f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                            label = "pulseAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(GramaColors.LeafGreen.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (hasLiveData) "Live Bus Data" else "Waiting for Bus Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (lastSyncTime != null) {
                    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                    Text(
                        text = fmt.format(Date(lastSyncTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (hasLiveData && location != null) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Source badge
                    Surface(
                        color = sourceBadgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = sourceIcon ?: "", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sourceLabel ?: "Unknown",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sourceBadgeColor
                            )
                        }
                    }

                    // Speed
                    if (location.speed > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${(location.speed * 3.6f).toInt()} km/h",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Age
                    if (ageMinutes != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = when {
                                    ageMinutes < 1 -> "just now"
                                    ageMinutes == 1 -> "1 min ago"
                                    else -> "$ageMinutes min ago"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Driver name
                if (location.driverName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = location.driverName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Trip ID
                if (location.tripId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Trip: ${location.tripId.takeLast(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Stop Timeline Item ────────────────────────────────────────────────────

@Composable
private fun StopTimelineItem(
    stopEta: StopEta,
    isFirst: Boolean,
    isLast: Boolean,
    etaCalculator: EtaCalculator
) {
    val etaText = etaCalculator.formatEta(stopEta)
    val accessibleDesc = etaCalculator.formatEtaAccessible(stopEta)

    val dotColor = when {
        stopEta.isCurrentLocation -> GramaColors.BusHere
        stopEta.isBusPassed -> GramaColors.BusPassed
        stopEta.etaMinutes != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val lineColor = if (stopEta.isBusPassed || stopEta.isCurrentLocation)
        MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    // Pulse animation for current bus location
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = EaseInOutSine),
            RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibleDesc }
            .padding(horizontal = 16.dp)
    ) {
        // Timeline column (dot + line)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(lineColor)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bus indicator dot
            if (stopEta.isCurrentLocation) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(20.dp * pulseScale)
                            .background(
                                GramaColors.BusHere.copy(alpha = 0.25f),
                                CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(GramaColors.BusHere, CircleShape)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(dotColor, CircleShape)
                        .border(
                            2.dp,
                            if (stopEta.etaMinutes != null) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                            CircleShape
                        )
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(lineColor)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Stop info row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stopEta.stop.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (stopEta.isCurrentLocation) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        stopEta.isBusPassed && !stopEta.isCurrentLocation ->
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (stopEta.isCurrentLocation) {
                    Text(
                        text = "🚌 Bus is here!",
                        style = MaterialTheme.typography.labelSmall,
                        color = GramaColors.BusHere
                    )
                }
            }

            // ETA chip
            EtaChip(stopEta = stopEta, etaText = etaText)
        }
    }
}

// ─── ETA Chip ─────────────────────────────────────────────────────────────

@Composable
private fun EtaChip(stopEta: StopEta, etaText: String) {
    val (chipColor, textColor) = when {
        stopEta.isCurrentLocation -> Pair(GramaColors.BusHere, Color.White)
        stopEta.isBusPassed -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        stopEta.confidence == EtaConfidence.HIGH -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        stopEta.confidence == EtaConfidence.MEDIUM -> Pair(
            GramaColors.AlertBg,
            GramaColors.AlertOrange
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = chipColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = etaText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ─── FAB ──────────────────────────────────────────────────────────────────

@Composable
private fun PingFab(onClick: () -> Unit, enabled: Boolean) {
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = if (enabled)
            MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled)
            MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
        text = { Text("Ping Bus", fontWeight = FontWeight.Bold) }
    )
}

// ─── Sub-components ────────────────────────────────────────────────────────

@Composable
private fun PassengerSearchCard(
    language: AppLanguage,
    routes: List<Route>,
    fromQuery: String,
    toQuery: String,
    insideBus: Boolean,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
    onInsideBusChanged: (Boolean) -> Unit,
    onRouteSelected: (Route) -> Unit,
    modifier: Modifier = Modifier
) {
    val matchingRoutes = remember(routes, fromQuery, toQuery) {
        routes.filter { route ->
            val fromMatch = fromQuery.isBlank() ||
                    route.origin.contains(fromQuery, ignoreCase = true) ||
                    route.stops.any { it.name.contains(fromQuery, ignoreCase = true) }
            val toMatch = toQuery.isBlank() ||
                    route.destination.contains(toQuery, ignoreCase = true) ||
                    route.stops.any { it.name.contains(toQuery, ignoreCase = true) }
            fromMatch && toMatch
        }
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find buses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = fromQuery,
                onValueChange = onFromChanged,
                label = { Text(AppText.from(language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = toQuery,
                onValueChange = onToChanged,
                label = { Text(AppText.to(language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = AppText.busesFound(language, matchingRoutes.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            matchingRoutes.take(3).forEach { route ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onRouteSelected(route) }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsBus, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${route.number} - ${route.name}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${route.origin} to ${route.destination}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppText.insideBus(language), fontWeight = FontWeight.Medium)
                    Text(
                        AppText.privateTracking(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = insideBus, onCheckedChange = onInsideBusChanged)
            }
            if (insideBus) {
                LocalRideTracker(language = language)
            }
        }
    }
}

@Composable
private fun LocalRideTracker(language: AppLanguage) {
    val context = LocalContext.current
    var speedKmh by remember { mutableStateOf(0) }
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000)
                .setMinUpdateIntervalMillis(1_500)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        speedKmh = (location.speed * 3.6f).toInt().coerceAtLeast(0)
                    }
                }
            }

            try {
                client.requestLocationUpdates(request, callback, context.mainLooper)
            } catch (e: SecurityException) {
                speedKmh = 0
            }

            onDispose {
                client.removeLocationUpdates(callback)
            }
        }
    }

    Text(
        text = if (hasPermission) {
            AppText.speed(language, speedKmh)
        } else {
            "Grant location permission to show your speed"
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AlertBanner(alert: BusAlert, modifier: Modifier = Modifier) {
    val (bgColor, icon) = when (alert.type) {
        AlertType.CANCELLED -> Pair(MaterialTheme.colorScheme.errorContainer, "❌")
        AlertType.DELAY -> Pair(GramaColors.AlertBg, "⏱️")
        AlertType.EXTRA -> Pair(GramaColors.LeafContainer, "➕")
        else -> Pair(MaterialTheme.colorScheme.primaryContainer, "📢")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActivePingCard(
    ping: BusPing,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = GramaColors.BusHere,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live report by ${ping.userName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${ping.type.emoji} ${ping.type.label} at ${ping.stopName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("✓ Confirm (${ping.confirmationCount})", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("✗ Wrong (${ping.denialCount})", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun NoLiveDataBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.SignalCellularConnectedNoInternet0Bar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "No verified live data yet",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Waiting for ticket-machine GPS. Pull down to refresh or ping the bus from a stop.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RouteSwitcherDialog(
    routes: List<Route>,
    selectedRoute: Route?,
    onRouteSelected: (Route) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Route") },
        text = {
            LazyColumn {
                items(routes) { route ->
                    ListItem(
                        headlineContent = { Text("${route.number} • ${route.name}") },
                        supportingContent = { Text("${route.origin} → ${route.destination}") },
                        leadingContent = {
                            if (route.id == selectedRoute?.id) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.clickable { onRouteSelected(route) }
                    )
                    if (route != routes.last()) Divider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun HomeSkeletonLoader() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Skeleton for live bus info card
        SkeletonBox(width = 300.dp, height = 60.dp)
        SkeletonBox(width = 300.dp, height = 160.dp)
        Spacer(modifier = Modifier.height(8.dp))
        repeat(6) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index > 0) SkeletonBox(width = 2.dp, height = 16.dp)
                    SkeletonBox(width = 14.dp, height = 14.dp, isCircle = true)
                    if (index < 5) SkeletonBox(width = 2.dp, height = 16.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                SkeletonBox(width = 160.dp, height = 16.dp)
                Spacer(modifier = Modifier.weight(1f))
                SkeletonBox(width = 60.dp, height = 28.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBox(
    width: Dp,
    height: Dp,
    isCircle: Boolean = false
) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(if (isCircle) CircleShape else RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("😕", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't load routes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyRoutesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚌", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No routes available",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Check back later or contact your local transit authority.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
