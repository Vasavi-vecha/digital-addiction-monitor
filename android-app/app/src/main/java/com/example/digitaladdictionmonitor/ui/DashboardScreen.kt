package com.example.digitaladdictionmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.digitaladdictionmonitor.model.BaselineDeltaDto
import com.example.digitaladdictionmonitor.model.DashboardResponse
import com.example.digitaladdictionmonitor.model.SuggestionDto
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Digital Addiction Monitor") },
                actions = {
                    IconButton(onClick = { viewModel.syncAndRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync now")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is DashboardUiState.PermissionNeeded -> PermissionNeededContent(
                    onGrantClick = { viewModel.openUsageAccessSettings() }
                )
                is DashboardUiState.Loading -> LoadingContent()
                is DashboardUiState.NoDataYet -> NoDataYetContent(
                    onSyncClick = { viewModel.syncAndRefresh() }
                )
                is DashboardUiState.Error -> ErrorContent(
                    message = state.message,
                    onRetryClick = { viewModel.syncAndRefresh() }
                )
                is DashboardUiState.Loaded -> DashboardContent(state.dashboard, state.statusMessage)
            }
        }
    }
}

@Composable
private fun PermissionNeededContent(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Usage access needed", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "This app reads your app-usage history on-device to compute a personal " +
                "digital-wellbeing score. Nothing is shared except what you sync.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClick) { Text("Grant usage access") }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoDataYetContent(onSyncClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No data synced yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap sync to upload your on-device usage history to the backend and see your dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSyncClick) { Text("Sync now") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetryClick) { Text("Retry") }
    }
}

@Composable
private fun DashboardContent(dashboard: DashboardResponse, statusMessage: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (statusMessage != null) {
            item { Text(statusMessage, style = MaterialTheme.typography.bodySmall) }
        }
        item { RiskScoreCard(dashboard) }
        item { FocusMetricsCard(dashboard) }
        item { CategoryTotalsCard(dashboard.weeklyCategoryTotalsMinutes) }
        if (dashboard.baselineDeltas.isNotEmpty()) {
            item { SectionHeader("This week vs. your own 4-week average") }
            items(dashboard.baselineDeltas) { delta -> BaselineDeltaRow(delta) }
        }
        if (dashboard.suggestions.isNotEmpty()) {
            item { SectionHeader("Suggestions") }
            items(dashboard.suggestions) { suggestion -> SuggestionCard(suggestion) }
        }
    }
}

@Composable
private fun RiskScoreCard(dashboard: DashboardResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dashboard.riskScore.roundToInt().toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text("/ 100 risk score", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaChip(dashboard.clusterPersona)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Trend: ${dashboard.trendDirection}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PersonaChip(persona: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            persona,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun FocusMetricsCard(dashboard: DashboardResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            StatTile(
                label = "Context switches / hr",
                value = String.format("%.1f", dashboard.focusMetrics.contextSwitchRate),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Deep-focus minutes",
                value = dashboard.focusMetrics.deepFocusMinutes.roundToInt().toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CategoryTotalsCard(totals: Map<String, Double>) {
    if (totals.isEmpty()) return
    val maxMinutes = totals.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("This week by category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            totals.entries.sortedByDescending { it.value }.forEach { (category, minutes) ->
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                        Text("${minutes.roundToInt()} min", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    val fraction = (minutes / maxMinutes).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BaselineDeltaRow(delta: BaselineDeltaDto) {
    val worsening = delta.pctChange > 0
    val color = if (worsening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                delta.metric.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${if (worsening) "+" else ""}${delta.pctChange.roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: SuggestionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(suggestion.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Impact score: ${String.format("%.1f", abs(suggestion.expectedImpact))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
