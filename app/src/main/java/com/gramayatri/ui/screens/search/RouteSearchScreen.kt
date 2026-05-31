package com.gramayatri.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gramayatri.data.model.*
import com.gramayatri.ui.i18n.AppText
import com.gramayatri.ui.theme.GramaColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    language: AppLanguage,
    routes: List<Route>,
    onNavigateBack: () -> Unit,
    onRouteSelected: (Route) -> Unit,
    onNavigateToAlerts: (String, String) -> Unit,
    viewModel: RouteSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = AppText.searchBuses(language),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = AppText.busTypeKSRTC(language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ─── Search Fields ──────────────────────────────────────────
            SearchFieldsSection(
                language = language,
                fromQuery = uiState.fromQuery,
                toQuery = uiState.toQuery,
                routes = routes,
                showFromSuggestions = uiState.showFromSuggestions,
                showToSuggestions = uiState.showToSuggestions,
                fromSuggestions = uiState.fromSuggestions,
                toSuggestions = uiState.toSuggestions,
                onFromChanged = { viewModel.updateFromQuery(it, routes) },
                onToChanged = { viewModel.updateToQuery(it, routes) },
                onFromSelected = { viewModel.selectFromStop(it) },
                onToSelected = { viewModel.selectToStop(it) },
                onSwap = { viewModel.swapFromTo() },
                onClearFrom = { viewModel.clearFrom() },
                onClearTo = { viewModel.clearTo() }
            )

            // ─── Date & Time Selection ──────────────────────────────────
            DateTimeSelectionSection(
                language = language,
                selectedDate = uiState.selectedDate,
                selectedTime = uiState.selectedTime,
                onDateSelected = { viewModel.selectDate(it) },
                onTimeSelected = { viewModel.selectTime(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Search Button ──────────────────────────────────────────
            Button(
                onClick = { viewModel.searchRoutes(routes) },
                enabled = uiState.fromStop.isNotBlank() && uiState.toStop.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppText.searchBuses(language),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Results ────────────────────────────────────────────────
            if (uiState.hasSearched) {
                if (uiState.matchingRoutes.isEmpty()) {
                    EmptySearchState(
                        message = AppText.noBusesFound(language),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    SearchResultsList(
                        language = language,
                        routes = uiState.matchingRoutes,
                        fromStop = uiState.fromStop,
                        toStop = uiState.toStop,
                        selectedDate = uiState.selectedDate,
                        selectedTime = uiState.selectedTime,
                        onRouteSelected = onRouteSelected,
                        onNavigateToAlerts = onNavigateToAlerts,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Welcome placeholder
                SearchWelcomePlaceholder(
                    language = language,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── Search Fields Section ──────────────────────────────────────────────────

@Composable
private fun SearchFieldsSection(
    language: AppLanguage,
    fromQuery: String,
    toQuery: String,
    routes: List<Route>,
    showFromSuggestions: Boolean,
    showToSuggestions: Boolean,
    fromSuggestions: List<String>,
    toSuggestions: List<String>,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit,
    onFromSelected: (String) -> Unit,
    onToSelected: (String) -> Unit,
    onSwap: () -> Unit,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // From field
        OutlinedTextField(
            value = fromQuery,
            onValueChange = onFromChanged,
            label = { Text(AppText.from(language)) },
            placeholder = { Text("e.g. Bengaluru, Mysuru...") },
            leadingIcon = {
                Icon(
                    Icons.Default.TripOrigin,
                    contentDescription = null,
                    tint = GramaColors.SaffronDeep,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (fromQuery.isNotEmpty()) {
                    IconButton(onClick = onClearFrom) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GramaColors.SaffronDeep,
                cursorColor = GramaColors.SaffronDeep
            )
        )

        // From suggestions dropdown
        AnimatedVisibility(visible = showFromSuggestions && fromSuggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                LazyColumn {
                    items(fromSuggestions) { suggestion ->
                        SuggestionItem(
                            text = suggestion,
                            onClick = { onFromSelected(suggestion) }
                        )
                    }
                }
            }
        }

        // Swap button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onSwap,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Swap",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // To field
        OutlinedTextField(
            value = toQuery,
            onValueChange = onToChanged,
            label = { Text(AppText.to(language)) },
            placeholder = { Text("e.g. Mangaluru, Hubli...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = GramaColors.LeafGreen,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (toQuery.isNotEmpty()) {
                    IconButton(onClick = onClearTo) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GramaColors.LeafGreen,
                cursorColor = GramaColors.LeafGreen
            )
        )

        // To suggestions dropdown
        AnimatedVisibility(visible = showToSuggestions && toSuggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                LazyColumn {
                    items(toSuggestions) { suggestion ->
                        SuggestionItem(
                            text = suggestion,
                            onClick = { onToSelected(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Date & Time Selection ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeSelectionSection(
    language: AppLanguage,
    selectedDate: Long?,
    selectedTime: Int?,  // minutes from midnight
    onDateSelected: (Long) -> Unit,
    onTimeSelected: (Int) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    val displayDate = if (selectedDate != null) dateFormat.format(Date(selectedDate)) else AppText.selectDate(language)

    val displayTime = if (selectedTime != null) {
        val h = selectedTime / 60
        val m = selectedTime % 60
        String.format("%02d:%02d", h, m)
    } else {
        AppText.selectTime(language)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date picker button
        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = AppText.selectDate(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Time picker button
        OutlinedCard(
            onClick = { showTimePicker = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = AppText.departureTime(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayTime,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        val initialHour = if (selectedTime != null) selectedTime / 60 else 8
        val initialMinute = if (selectedTime != null) selectedTime % 60 else 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(AppText.selectTime(language), fontWeight = FontWeight.Bold) },
            text = { TimeInput(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val totalMinutes = timePickerState.hour * 60 + timePickerState.minute
                        onTimeSelected(totalMinutes)
                        showTimePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─── Search Results ─────────────────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    language: AppLanguage,
    routes: List<Route>,
    fromStop: String,
    toStop: String,
    selectedDate: Long?,
    selectedTime: Int?,
    onRouteSelected: (Route) -> Unit,
    onNavigateToAlerts: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Results header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${routes.size} ${AppText.busesFound(language, routes.size)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$fromStop → $toStop",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(routes) { route ->
                RouteResultCard(
                    language = language,
                    route = route,
                    fromStop = fromStop,
                    toStop = toStop,
                    onRouteSelected = onRouteSelected,
                    onNavigateToAlerts = onNavigateToAlerts
                )
            }
        }
    }
}

@Composable
private fun RouteResultCard(
    language: AppLanguage,
    route: Route,
    fromStop: String,
    toStop: String,
    onRouteSelected: (Route) -> Unit,
    onNavigateToAlerts: (String, String) -> Unit
) {
    // Generate schedule info based on route distance
    val estDurationMinutes = route.duration.takeIf { it > 0 } ?: route.stops.sumOf { it.avgTravelTimeFromPrevMinutes }
    val estDistanceKm = route.distance.takeIf { it > 0 } ?: (estDurationMinutes * 0.5).toInt()

    // Generate sample departure times (every 30 min to 2 hours depending on route type)
    val freqMinutes = when {
        estDistanceKm < 50 -> 20  // short routes: frequent
        estDistanceKm < 100 -> 30
        estDistanceKm < 200 -> 45
        else -> 60              // long routes: hourly
    }

    val sampleTimes = generateSampleTimes(freqMinutes)

    OutlinedCard(
        onClick = { onRouteSelected(route) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Route header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GramaColors.SaffronDeep,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = route.number,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route path
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(GramaColors.SaffronDeep, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(GramaColors.LeafGreen, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = route.origin,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = route.destination,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GramaColors.LeafGreen
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    // Distance
                    Text(
                        text = "${estDistanceKm} km",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Duration
                    val hours = estDurationMinutes / 60
                    val mins = estDurationMinutes % 60
                    val durText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    Text(
                        text = durText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Service frequency
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Every ${freqMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${route.stops.size} stops",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sample times
            if (sampleTimes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AppText.departureTime(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sampleTimes.take(5).forEach { time ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (sampleTimes.size > 5) {
                        Text(
                            text = "+${sampleTimes.size - 5} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            // Via stops preview
            if (route.stops.size > 2) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${AppText.viaStops(language)}: ${route.stops.drop(1).dropLast(1).take(3).joinToString(", ") { it.name }}${if (route.stops.size > 5) "..." else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRouteSelected(route) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Track Bus", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onNavigateToAlerts(route.id, route.name) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alerts", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Placeholders ────────────────────────────────────────────────────────

@Composable
private fun EmptySearchState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚌", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try different cities or check the spelling.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SearchWelcomePlaceholder(language: AppLanguage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🚍", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (language == AppLanguage.KANNADA) "ನಿಮ್ಮ ಮಾರ್ಗವನ್ನು ಹುಡುಕಿ" else "Find your route",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (language == AppLanguage.KANNADA)
                "ಮೂಲ ಮತ್ತು ಗಮ್ಯಸ್ಥಾನ ನಮೂದಿಸಿ, ದಿನಾಂಕ ಮತ್ತು ಸಮಯ ಆಯ್ಕೆಮಾಡಿ — KSRTC ಬಸ್‌ಗಳನ್ನು ಹುಡುಕಿ"
            else
                "Enter source and destination, pick date and time — find KSRTC buses across Karnataka",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Tips
        val tips = if (language == AppLanguage.KANNADA) listOf(
            "💡 ಹೆಚ್ಚಿನ ಫಲಿತಾಂಶಗಳಿಗಾಗಿ ನಗರದ ಹೆಸರುಗಳನ್ನು ನಮೂದಿಸಿ",
            "💡 ನಿಖರ ಸಮಯಗಳಿಗಾಗಿ ನಿರ್ಗಮನ ಸಮಯ ಆಯ್ಕೆಮಾಡಿ",
            "💡 ಬಸ್‌ನ ಲೈವ್ ಟ್ರ್ಯಾಕಿಂಗ್ ನೋಡಲು Track Bus ಒತ್ತಿರಿ"
        ) else listOf(
            "💡 Enter city names for best results",
            "💡 Select departure time for accurate schedules",
            "💡 Press Track Bus to see live location"
        )
        tips.forEach { tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = tip, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────

private fun generateSampleTimes(freqMinutes: Int): List<String> {
    val times = mutableListOf<String>()
    var hour = 6
    var minute = 0
    while (hour < 22) {
        times.add(String.format("%02d:%02d", hour, minute))
        minute += freqMinutes
        if (minute >= 60) {
            hour += minute / 60
            minute %= 60
        }
    }
    return times
}
