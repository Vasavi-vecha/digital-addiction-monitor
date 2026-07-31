package com.example.digitaladdictionmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.digitaladdictionmonitor.model.BaselineDeltaDto
import com.example.digitaladdictionmonitor.model.DashboardResponse
import com.example.digitaladdictionmonitor.model.SuggestionDto
import com.example.digitaladdictionmonitor.ui.components.AppLogo
import com.example.digitaladdictionmonitor.ui.theme.BrandGradientEnd
import com.example.digitaladdictionmonitor.ui.theme.BrandGradientStart
import com.example.digitaladdictionmonitor.ui.theme.CautionColor
import com.example.digitaladdictionmonitor.ui.theme.CautionContainer
import com.example.digitaladdictionmonitor.ui.theme.ConcernColor
import com.example.digitaladdictionmonitor.ui.theme.ConcernContainer
import com.example.digitaladdictionmonitor.ui.theme.GoodColor
import com.example.digitaladdictionmonitor.ui.theme.GoodContainer
import com.example.digitaladdictionmonitor.ui.theme.SuggestionAccentColors
import kotlin.math.roundToInt

private enum class Severity(val label: String, val color: Color, val container: Color) {
    GOOD("Doing great", GoodColor, GoodContainer),
    CAUTION("Worth keeping an eye on", CautionColor, CautionContainer),
    CONCERN("Let's work on this", ConcernColor, ConcernContainer),
}

// Same 35/65 split app/onset_service.py uses for its own low/moderate/high
// bands, so "risk score" reads consistently across the whole product.
private fun severityFor(riskScore: Double): Severity = when {
    riskScore < 35 -> Severity.GOOD
    riskScore < 65 -> Severity.CAUTION
    else -> Severity.CONCERN
}

private fun friendlyPersona(persona: String): String = when (persona) {
    "healthy", "healthy_2" -> "Healthy habits"
    "borderline" -> "Borderline"
    "high_risk" -> "High risk"
    else -> persona.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun friendlyTrend(trend: String): Triple<String, String, Color> = when (trend) {
    "increasing" -> Triple("↑", "Trending up", ConcernColor)
    "decreasing" -> Triple("↓", "Improving", GoodColor)
    else -> Triple("→", "Holding steady", MaterialTheme.colorScheme.onSurfaceVariant)
}

// Plain-English labels for the raw metric keys ml-service/backend use.
// Metrics not listed default to their underscore-replaced name.
private val METRIC_LABELS = mapOf(
    "total_screen_min" to "Total screen time",
    "screen_min_social" to "Social media",
    "screen_min_productivity" to "Productivity apps",
    "screen_min_entertainment" to "Entertainment",
    "screen_min_education" to "Education apps",
    "screen_min_other" to "Other apps",
    "night_ratio" to "Late-night use",
    "unlock_count_week" to "Phone unlocks",
    "context_switch_rate" to "App switching",
    "n_sessions" to "Number of app sessions",
    "deep_focus_block_count" to "Focus sessions",
    "deep_focus_minutes" to "Deep focus time",
    "weekend_weekday_delta_pct" to "Weekend vs. weekday use",
    "mean_session_sec" to "Average session length",
    "median_session_sec" to "Typical session length",
    "max_session_sec" to "Longest single session",
)

private fun friendlyMetric(metric: String): String =
    METRIC_LABELS[metric] ?: metric.replace('_', ' ').replaceFirstChar { it.uppercase() }

// Metrics where going UP is the bad direction (mirrors backend's
// SuggestionEngine.TEMPLATES worsening-direction map) -- a few metrics like
// deep-focus time are the other way around, where going up is good.
private val WORSENS_WHEN_UP = setOf(
    "total_screen_min", "screen_min_social", "screen_min_entertainment", "screen_min_other",
    "night_ratio", "unlock_count_week", "context_switch_rate", "n_sessions",
    "weekend_weekday_delta_pct", "mean_session_sec", "median_session_sec", "max_session_sec",
)
private val WORSENS_WHEN_DOWN = setOf(
    "screen_min_productivity", "screen_min_education", "deep_focus_block_count", "deep_focus_minutes",
)

private fun isWorsening(metric: String, pctChange: Double): Boolean = when (metric) {
    in WORSENS_WHEN_DOWN -> pctChange < 0
    in WORSENS_WHEN_UP -> pctChange > 0
    else -> pctChange > 0
}

private enum class DashboardTab(val label: String) {
    OVERVIEW("Overview"),
    RISK("Risk Level"),
    SUGGESTIONS("Suggestions"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Digital Wellbeing")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncAndRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState is DashboardUiState.Loaded) {
                DashboardBottomBar(selected = selectedTab, onSelect = { selectedTab = it })
            }
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
                is DashboardUiState.Loaded -> when (selectedTab) {
                    DashboardTab.OVERVIEW -> OverviewTab(state.dashboard, state.statusMessage)
                    DashboardTab.RISK -> RiskLevelTab(state.dashboard)
                    DashboardTab.SUGGESTIONS -> SuggestionsTab(state.dashboard)
                }
            }
        }
    }
}

@Composable
private fun DashboardBottomBar(selected: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == DashboardTab.OVERVIEW,
            onClick = { onSelect(DashboardTab.OVERVIEW) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(DashboardTab.OVERVIEW.label) }
        )
        NavigationBarItem(
            selected = selected == DashboardTab.RISK,
            onClick = { onSelect(DashboardTab.RISK) },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            label = { Text(DashboardTab.RISK.label) }
        )
        NavigationBarItem(
            selected = selected == DashboardTab.SUGGESTIONS,
            onClick = { onSelect(DashboardTab.SUGGESTIONS) },
            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = null) },
            label = { Text(DashboardTab.SUGGESTIONS.label) }
        )
    }
}

@Composable
private fun PermissionNeededContent(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("One quick permission", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "This app needs permission to see which apps you use, so it can show you your " +
                    "own habits. Nothing leaves your phone except what you choose to sync.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrantClick) { Text("Turn on permission") }
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
        Text("Let's get started", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the button below to load your usage data and see your first dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSyncClick) { Text("Load my data") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn't load your data", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetryClick) { Text("Try again") }
    }
}

// --- Page 1: Overview ------------------------------------------------------

@Composable
private fun OverviewTab(dashboard: DashboardResponse, statusMessage: String?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HomeHeroHeader() }
        if (statusMessage != null) {
            item { Text(statusMessage, style = MaterialTheme.typography.bodySmall) }
        }
        item { RiskSummaryCard(dashboard) }
        item { FocusMetricsCard(dashboard) }
        item { CategoryTotalsCard(dashboard.weeklyCategoryTotalsMinutes) }
    }
}

// Branded landing banner for the home tab -- logo + wordmark + tagline on
// the brand gradient, so opening the app reads as a designed product rather
// than a bare list of cards.
@Composable
private fun HomeHeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Digital Wellbeing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Understand your habits, on your terms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun RiskSummaryCard(dashboard: DashboardResponse) {
    val severity = severityFor(dashboard.riskScore)
    val (trendArrow, trendLabel, trendColor) = friendlyTrend(dashboard.trendDirection)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = severity.container)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                severity.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = severity.color
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Score: ${dashboard.riskScore.roundToInt()} / 100",
                style = MaterialTheme.typography.bodyMedium,
                color = severity.color
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaChip(friendlyPersona(dashboard.clusterPersona), severity.color)
                Spacer(Modifier.width(8.dp))
                Text(
                    "$trendArrow $trendLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = trendColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PersonaChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FocusMetricsCard(dashboard: DashboardResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            StatTile(
                label = "App switches per hour",
                value = String.format("%.1f", dashboard.focusMetrics.contextSwitchRate),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Minutes of deep focus",
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
            Text("Where your time went this week", style = MaterialTheme.typography.titleMedium)
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

// --- Page 2: Risk Level ------------------------------------------------------

@Composable
private fun RiskLevelTab(dashboard: DashboardResponse) {
    val severity = severityFor(dashboard.riskScore)
    val (trendArrow, trendLabel, trendColor) = friendlyTrend(dashboard.trendDirection)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = severity.container)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Your risk level", style = MaterialTheme.typography.titleMedium, color = severity.color)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            dashboard.riskScore.roundToInt().toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = severity.color
                        )
                        Text(
                            " / 100",
                            style = MaterialTheme.typography.titleMedium,
                            color = severity.color,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        severity.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = severity.color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    RiskGauge(score = dashboard.riskScore)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GaugeLegendDot("Good", GoodColor)
                        GaugeLegendDot("Caution", CautionColor)
                        GaugeLegendDot("Concern", ConcernColor)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonaChip(friendlyPersona(dashboard.clusterPersona), severity.color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$trendArrow $trendLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = trendColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (dashboard.baselineDeltas.isNotEmpty()) {
            item { SectionHeader("Compared to your own last 4 weeks") }
            items(dashboard.baselineDeltas) { delta -> BaselineDeltaRow(delta) }
        }
    }
}

@Composable
private fun GaugeLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(8.dp).height(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Gradient track spanning the same 0-35 / 35-65 / 65-100 bands severityFor()
// uses, with a marker at the current score so the color story and the
// number agree.
@Composable
private fun RiskGauge(score: Double) {
    val fraction = (score / 100.0).toFloat().coerceIn(0f, 1f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(GoodColor, CautionColor, ConcernColor)),
                    RoundedCornerShape(10.dp)
                )
        )
        Box(
            modifier = Modifier
                .offset(x = (maxWidth * fraction) - 2.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun BaselineDeltaRow(delta: BaselineDeltaDto) {
    val worsening = isWorsening(delta.metric, delta.pctChange)
    val color = if (worsening) ConcernColor else GoodColor
    val arrow = if (delta.pctChange >= 0) "↑" else "↓"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                friendlyMetric(delta.metric),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$arrow ${kotlin.math.abs(delta.pctChange.roundToInt())}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

// --- Page 3: Suggestions ------------------------------------------------------

@Composable
private fun SuggestionsTab(dashboard: DashboardResponse) {
    if (dashboard.suggestions.isEmpty()) {
        EmptySuggestionsContent()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("A few things you could try", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(dashboard.suggestions) { index, suggestion ->
            SuggestionCard(suggestion, rank = index + 1)
        }
    }
}

@Composable
private fun EmptySuggestionsContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Nothing to flag right now", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your habits look steady. Check back after your next sync.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

// Suggestions arrive already ranked by expectedImpact (backend's
// SuggestionEngine); rank drives the accent color/badge rather than
// exposing the raw z-score, which wouldn't mean anything to a user.
@Composable
private fun SuggestionCard(suggestion: SuggestionDto, rank: Int) {
    val accent = SuggestionAccentColors[(rank - 1).coerceIn(0, SuggestionAccentColors.lastIndex)]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(accent))
            Row(
                modifier = Modifier.padding(16.dp).weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f)) {
                    Text(
                        rank.toString(),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    suggestion.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}