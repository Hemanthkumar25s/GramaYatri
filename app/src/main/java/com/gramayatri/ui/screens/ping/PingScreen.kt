package com.gramayatri.ui.screens.ping

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.google.accompanist.permissions.*
import com.gramayatri.data.location.BusTrackingService
import com.gramayatri.data.model.*
import com.gramayatri.data.repository.LocalCacheRepository
import com.gramayatri.ui.theme.GramaColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PingScreen(
    route: Route,
    userName: String,
    onNavigateBack: () -> Unit,
    viewModel: PingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Location permissions for 'On the Bus' feature
    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Navigate back on success and start service if needed
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            if (uiState.selectedPingType == PingType.ON_THE_BUS) {
                if (locationPermissionState.allPermissionsGranted) {
                    val intent = Intent(context, BusTrackingService::class.java).apply {
                        action = BusTrackingService.ACTION_START
                        putExtra(BusTrackingService.EXTRA_ROUTE_ID, route.id)
                        putExtra(BusTrackingService.EXTRA_USER_NAME, userName)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
            kotlinx.coroutines.delay(1500)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Bus Location", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Success state overlay
            AnimatedVisibility(
                visible = uiState.success,
                enter = fadeIn() + scaleIn()
            ) {
                SuccessBanner()
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Step 1: Select stop
            Text(
                text = "1. Where is the bus?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            StopSelector(
                stops = route.stops,
                selectedStop = uiState.selectedStop,
                onStopSelected = { viewModel.selectStop(it) }
            )

            Divider()

            // Step 2: Select ping type
            Text(
                text = "2. What happened?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            PingTypeSelector(
                selectedType = uiState.selectedPingType,
                onTypeSelected = { viewModel.selectPingType(it) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Error message
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Submit button
            val canSubmit = uiState.selectedStop != null &&
                    !uiState.isSubmitting &&
                    !uiState.rateLimited

            // If "On the bus" is selected, we need to check permissions
            if (uiState.selectedPingType == PingType.ON_THE_BUS && !locationPermissionState.allPermissionsGranted) {
                Button(
                    onClick = { locationPermissionState.launchMultiplePermissionRequest() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Location Permission")
                }
            } else {
                Button(
                    onClick = { viewModel.submitPing(route, userName) },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (uiState.rateLimited) {
                        Text(
                            text = "Wait ${uiState.rateLimitRemainingSeconds}s before next ping",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.selectedPingType == PingType.ON_THE_BUS) "Start Live Broadcast" else "Submit Ping",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StopSelector(
    stops: List<Stop>,
    selectedStop: Stop?,
    onStopSelected: (Stop) -> Unit
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(stops) { stop ->
            val isSelected = stop.id == selectedStop?.id
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onStopSelected(stop) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onStopSelected(stop) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PingTypeSelector(
    selectedType: PingType,
    onTypeSelected: (PingType) -> Unit
) {
    // Only show the primary ping types (not cancelled/extra — those are admin alerts)
    val primaryTypes = listOf(
        PingType.BUS_AT_STOP,
        PingType.ON_THE_BUS,
        PingType.BUS_LEFT_STOP,
        PingType.BUS_DELAYED
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        primaryTypes.forEach { type ->
            val isSelected = type == selectedType
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        onClick = { onTypeSelected(type) }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = type.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessBanner() {
    Surface(
        color = GramaColors.LeafContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = GramaColors.LeafGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ping submitted! Thank you 🙏",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GramaColors.LeafGreen
            )
        }
    }
}
