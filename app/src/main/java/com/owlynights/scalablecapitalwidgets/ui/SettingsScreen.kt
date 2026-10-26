package com.owlynights.scalablecapitalwidgets.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.AppLanguage
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.api.ScalableConfig
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.data.ListSource
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.WidgetTap
import com.owlynights.scalablecapitalwidgets.widget.periodLabel

private val REFRESH_OPTIONS = listOf(15, 30, 60, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    loginAt: Long,
    onBack: () -> Unit,
    onSourceChange: (ListSource) -> Unit,
    onPeriodChange: (Period) -> Unit,
    onRefreshMinutesChange: (Int) -> Unit,
    onPortfolioChange: (String) -> Unit,
    onEditCustomList: () -> Unit,
    onWidgetTapChange: (WidgetTap) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onAnalyticsChange: (Boolean) -> Unit,
    onCrashReportsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Group(stringResource(R.string.settings_widget_list)) {
                ListSource.entries.forEach { option ->
                    RadioRow(
                        label = stringResource(
                            when (option) {
                                ListSource.HOLDINGS -> R.string.settings_source_holdings
                                ListSource.WATCHLIST -> R.string.settings_source_watchlist
                                ListSource.CUSTOM -> R.string.settings_source_custom
                            }
                        ),
                        supporting = if (option == ListSource.CUSTOM && settings.customList.isNotEmpty()) {
                            settings.customList.joinToString(", ") { it.name }
                        } else null,
                        selected = settings.listSource == option,
                        onClick = { onSourceChange(option) },
                    )
                }
                TextButton(
                    onClick = onEditCustomList,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_edit), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.custom_edit))
                }
            }

            Group(stringResource(R.string.settings_period)) {
                Period.entries.forEach { option ->
                    RadioRow(
                        label = stringResource(periodLabel(option)),
                        selected = settings.period == option,
                        onClick = { onPeriodChange(option) },
                    )
                }
            }

            Group(stringResource(R.string.settings_widget_tap)) {
                WidgetTap.entries.forEach { option ->
                    RadioRow(
                        label = stringResource(
                            when (option) {
                                WidgetTap.OPEN_APP -> R.string.settings_tap_open_app
                                WidgetTap.REFRESH -> R.string.settings_tap_refresh
                                WidgetTap.OPEN_SCALABLE -> R.string.settings_tap_open_scalable
                            }
                        ),
                        selected = settings.widgetTap == option,
                        onClick = { onWidgetTapChange(option) },
                    )
                }
            }

            Group(stringResource(R.string.settings_language)) {
                AppLanguage.tags.forEach { tag ->
                    RadioRow(
                        label = if (tag.isEmpty()) stringResource(R.string.settings_language_system)
                        else AppLanguage.displayName(tag),
                        selected = language == tag,
                        onClick = { onLanguageChange(tag) },
                    )
                }
            }

            Group(stringResource(R.string.settings_refresh)) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    REFRESH_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = settings.refreshMinutes == minutes,
                            onClick = { onRefreshMinutesChange(minutes) },
                            label = { Text(stringResource(R.string.settings_refresh_minutes, minutes)) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_refresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (settings.portfolioIds.size > 1) {
                Group(stringResource(R.string.settings_portfolio)) {
                    settings.portfolioIds.forEachIndexed { index, id ->
                        RadioRow(
                            label = stringResource(R.string.settings_portfolio_item, index + 1),
                            supporting = id,
                            selected = settings.portfolioId == id,
                            onClick = { onPortfolioChange(id) },
                        )
                    }
                }
            }

            Group(stringResource(R.string.settings_account)) {
                val endsAt = (loginAt + ScalableConfig.MAX_SESSION_SECONDS) * 1000
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    headlineContent = {
                        Text(settings.firstName ?: stringResource(R.string.settings_account_connected))
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.settings_session_ends,
                                DateUtils.formatDateTime(
                                    LocalContext.current, endsAt,
                                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY,
                                ),
                            )
                        )
                    },
                )
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(painterResource(R.drawable.ic_logout), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_logout))
                }
            }

            Group(stringResource(R.string.settings_privacy)) {
                PrivacySwitch(
                    R.string.privacy_analytics, R.string.privacy_analytics_summary,
                    checked = settings.analyticsEnabled, onCheckedChange = onAnalyticsChange,
                )
                PrivacySwitch(
                    R.string.privacy_crashes, R.string.privacy_crashes_summary,
                    checked = settings.crashReportsEnabled, onCheckedChange = onCrashReportsChange,
                )
            }

            Group(stringResource(R.string.about_title)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                    leadingContent = { Icon(painterResource(R.drawable.ic_favorite), null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.about_support_title)) },
                    supportingContent = { Text(stringResource(R.string.about_support_short)) },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                    leadingContent = { Icon(painterResource(R.drawable.ic_info), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text(stringResource(R.string.about_entry)) },
                    supportingContent = { Text(stringResource(R.string.about_entry_summary)) },
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text(stringResource(R.string.settings_logout)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, supporting: String? = null) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it, maxLines = 1) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    )
}
