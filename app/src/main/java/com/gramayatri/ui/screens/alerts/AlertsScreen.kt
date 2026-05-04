package com.gramayatri.ui.screens.alerts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gramayatri.data.model.*
import com.gramayatri.ui.theme.GramaColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    routeId: String,
    routeName: String,
    onNavigateBack: () -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(routeId) { viewModel.loadAlerts(routeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts — $routeName", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.alerts.isEmpty() -> EmptyAlertsState(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.alerts) { alert ->
                    AlertCard(alert = alert)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: BusAlert) {
    val (bgColor, accentColor) = when (alert.type) {
        AlertType.CANCELLED -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        AlertType.DELAY -> Pair(GramaColors.AlertBg, GramaColors.AlertOrange)
        AlertType.EXTRA -> Pair(GramaColors.LeafContainer, GramaColors.LeafGreen)
        AlertType.ROUTE_CHANGE -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        AlertType.GENERAL -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val timeFormat = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = alert.type.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = alert.type.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = timeFormat.format(Date(alert.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyAlertsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No active alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "All buses running normally",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
