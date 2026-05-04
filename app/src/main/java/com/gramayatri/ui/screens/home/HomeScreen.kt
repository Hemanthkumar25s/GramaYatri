package com.gramayatri.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gramayatri.data.model.*
import com.gramayatri.domain.usecase.EtaCalculator
import com.gramayatri.ui.theme.GramaColors
import java.text.SimpleDateFormat
import java.util.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPing: (String) -> Unit,
    onNavigateToAlerts: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val etaCalculator = remember { EtaCalculator() }
    var isMapView by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HomeTopBar(
                routes = uiState.routes,
                selectedRoute = uiState.selectedRoute,
                isOffline = uiState.isOffline,
                isMapView = isMapView,
                onRouteSelected = { viewModel.switchRoute(it) },
                onAlertsClick = {
                    uiState.selectedRoute?.let { onNavigateToAlerts(it.id, it.name) }
                },
                onSettingsClick = onNavigateToSettings,
                onToggleView = { isMapView = !isMapView },
                alertCount = if (uiState.activeAlert != null) 1 else 0
            )
        },
        floatingActionButton = {
            PingFab(
                onClick = {
                    uiState.selectedRoute?.let { onNavigateToPing(it.id) }
                },
                enabled = !uiState.isOffline && uiState.selectedRoute != null
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    etaCalculator = etaCalculator,
                    onConfirmPing = { pingId, confirmed ->
                        viewModel.confirmPing(pingId, confirmed)
                    }
                )
            }
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
    onAlertsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleView: () -> Unit,
    alertCount: Int
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
        actions = {
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
                IconButton(onClick = onAlertsClick) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "View alerts"
                    )
                }
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings"
                )
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
    etaCalculator: EtaCalculator,
    onConfirmPing: (String, Boolean) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp)
    ) {
        // Active alert banner
        uiState.activeAlert?.let { alert ->
            item {
                AlertBanner(alert = alert, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        // Active ping info card
        uiState.activePing?.let { ping ->
            item {
                ActivePingCard(
                    ping = ping,
                    onConfirm = { onConfirmPing(ping.id, true) },
                    onDeny = { onConfirmPing(ping.id, false) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // No live data placeholder
        if (uiState.activePing == null && uiState.selectedRoute != null) {
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
                    Text(
                        text = "${route.origin}  →  ${route.destination}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.lastSyncTime?.let { time ->
                        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                        Text(
                            text = "Updated ${fmt.format(Date(time))}",
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
            // Render stops without ETA
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
                    text = "No live data yet",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Be the first to ping the bus location!",
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
