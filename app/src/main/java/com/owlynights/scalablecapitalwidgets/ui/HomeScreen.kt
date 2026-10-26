package com.owlynights.scalablecapitalwidgets.ui

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.api.ScalableConfig
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.data.ListSource
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.PortfolioSnapshot
import com.owlynights.scalablecapitalwidgets.data.Position
import com.owlynights.scalablecapitalwidgets.ui.theme.changeColor
import com.owlynights.scalablecapitalwidgets.widget.Format
import com.owlynights.scalablecapitalwidgets.widget.listTitle
import com.owlynights.scalablecapitalwidgets.widget.periodLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: PortfolioSnapshot?,
    settings: AppSettings,
    loginAt: Long,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
    onPeriodChange: (Period) -> Unit,
    onSourceChange: (ListSource) -> Unit,
    onRelogin: () -> Unit,
    onEditCustomList: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error)
            onDismissError()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        settings.firstName?.let { stringResource(R.string.home_greeting, it) }
                            ?: stringResource(R.string.home_title)
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (refreshing && snapshot == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
                }
                item { SessionBanner(loginAt, onRelogin) }
                if (snapshot != null) {
                    item { PortfolioCard(snapshot, settings.period, onPeriodChange) }
                }
                item { SectionHeader(stringResource(R.string.widgets_title), stringResource(R.string.widgets_subtitle)) }
                item { WidgetGallery(refreshKey = listOf(snapshot?.fetchedAt, settings)) }
                if (snapshot != null) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp).padding(top = 28.dp, bottom = 8.dp)) {
                            SourceSelector(settings.listSource, onSourceChange)
                            if (settings.listSource == ListSource.CUSTOM) {
                                Spacer(Modifier.height(12.dp))
                                FilledTonalButton(
                                    onClick = onEditCustomList,
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                ) {
                                    Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.custom_edit))
                                }
                            }
                        }
                    }
                    // A freshly picked title may not have been fetched yet; show it by name until then.
                    val positions = if (settings.listSource == ListSource.CUSTOM) {
                        val fetched = snapshot.custom.associateBy { it.isin }
                        settings.customList.map { pick ->
                            fetched[pick.isin]
                                ?: Position(pick.isin, pick.name, null, null, null, null, null, outdated = true, series = null)
                        }
                    } else {
                        snapshot.positions(settings.listSource)
                    }
                    if (positions.isEmpty()) {
                        item {
                            Text(
                                stringResource(
                                    if (settings.listSource == ListSource.CUSTOM) R.string.home_custom_empty
                                    else R.string.widget_no_positions
                                ),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(positions, key = { it.isin }) { position ->
                        PositionItem(position, settings.listSource)
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionBanner(loginAt: Long, onRelogin: () -> Unit) {
    val endsAt = (loginAt + ScalableConfig.MAX_SESSION_SECONDS) * 1000
    val remaining = endsAt - System.currentTimeMillis()
    if (remaining > DateUtils.DAY_IN_MILLIS) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    R.string.session_ending,
                    DateUtils.formatDateTime(
                        LocalContext.current, endsAt,
                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_WEEKDAY,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onRelogin) {
                Text(stringResource(R.string.session_relogin))
            }
        }
    }
}

@Composable
private fun PortfolioCard(snapshot: PortfolioSnapshot, period: Period, onPeriodChange: (Period) -> Unit) {
    val amount = snapshot.absoluteReturn(period)
    val up = (amount ?: 0.0) >= 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.home_portfolio_value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    snapshot.totalValue?.let { Format.money(it, snapshot.currency) } ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        amount?.let { Format.signedMoney(it, snapshot.currency) } ?: "–",
                        style = MaterialTheme.typography.titleMedium,
                        color = changeColor(up),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(periodLabel(period)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            snapshot.chart(period)?.let { series ->
                Spacer(Modifier.height(16.dp))
                PortfolioChart(
                    series = series,
                    intraday = period == Period.TODAY,
                    modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Period.entries.forEach { option ->
                    FilterChip(
                        selected = option == period,
                        onClick = { onPeriodChange(option) },
                        label = { Text(stringResource(periodShortLabel(option))) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.home_updated,
                    DateUtils.getRelativeTimeSpanString(snapshot.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSelector(source: ListSource, onSourceChange: (ListSource) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ListSource.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == source,
                onClick = { onSourceChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, ListSource.entries.size),
            ) {
                Text(stringResource(listTitle(option)), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PositionItem(position: Position, source: ListSource) {
    val change = position.dayChangeRatio
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(position.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val price = position.price?.let { Format.money(it, position.currency ?: "EUR") }
            val text = when {
                source == ListSource.HOLDINGS && position.quantity != null && price != null ->
                    "${Format.quantity(position.quantity)} × $price"
                price != null -> price
                else -> position.isin
            }
            Text(text, maxLines = 1)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val value = if (source == ListSource.HOLDINGS) position.valuation else position.price
                value?.let {
                    Text(
                        Format.money(it, position.currency ?: "EUR"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (change != null) {
                    Text(
                        Format.signedPercent(change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = changeColor(change >= 0),
                    )
                }
            }
        },
    )
}

fun periodShortLabel(period: Period): Int = when (period) {
    Period.TODAY -> R.string.period_short_today
    Period.WEEK -> R.string.period_short_week
    Period.MONTH -> R.string.period_short_month
    Period.THREE_MONTHS -> R.string.period_short_three_months
    Period.SIX_MONTHS -> R.string.period_short_six_months
    Period.YEAR_TO_DATE -> R.string.period_short_ytd
    Period.YEAR -> R.string.period_short_year
    Period.MAX -> R.string.period_short_max
}
