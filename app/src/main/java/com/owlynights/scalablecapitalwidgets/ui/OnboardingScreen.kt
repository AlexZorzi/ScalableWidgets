package com.owlynights.scalablecapitalwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.R
import kotlinx.coroutines.launch

/**
 * Bump when the terms on the last page change, so everyone is asked to accept again.
 * 1: initial terms. 2: adds the analytics and crash report choices. 3: official download page.
 */
const val TERMS_VERSION = 3

private class IntroPage(
    @param:DrawableRes val icon: Int,
    @param:StringRes val title: Int,
    @param:StringRes val intro: Int,
    val points: List<Pair<Int, Int>>,
)

private val PAGES = listOf(
    IntroPage(
        R.drawable.ic_scalable_logo, R.string.intro_welcome_title, R.string.intro_welcome_body,
        listOf(
            R.drawable.ic_add to R.string.intro_welcome_widgets,
            R.drawable.ic_edit to R.string.intro_welcome_lists,
            R.drawable.ic_refresh to R.string.intro_welcome_refresh,
            R.drawable.ic_shield to R.string.intro_welcome_readonly,
        ),
    ),
    IntroPage(
        R.drawable.ic_shield, R.string.intro_security_title, R.string.intro_security_body,
        listOf(
            R.drawable.ic_open_in_new to R.string.intro_security_login,
            R.drawable.ic_shield to R.string.intro_security_key,
            R.drawable.ic_info to R.string.intro_security_tokens,
            R.drawable.ic_close to R.string.intro_security_servers,
            R.drawable.ic_logout to R.string.intro_security_expiry,
        ),
    ),
    IntroPage(R.drawable.ic_download, R.string.official_title, R.string.official_intro, OFFICIAL_SOURCE_POINTS),
    IntroPage(R.drawable.ic_favorite, R.string.intro_support_title, R.string.intro_support_body, emptyList()),
    IntroPage(
        R.drawable.ic_gavel, R.string.intro_terms_title, R.string.intro_terms_body,
        listOf(
            R.drawable.ic_info to R.string.intro_terms_unofficial,
            R.drawable.ic_refresh to R.string.intro_terms_api,
            R.drawable.ic_close to R.string.intro_terms_values,
            R.drawable.ic_gavel to R.string.intro_terms_liability,
            R.drawable.ic_shield to R.string.intro_terms_risk,
        ),
    ),
)

/**
 * Shown once before first use: what the app does, how the login is secured, an invitation
 * to donate, and the terms the user has to accept.
 */
@Composable
fun OnboardingScreen(
    initialAnalytics: Boolean,
    initialCrashReports: Boolean,
    onAccept: (analytics: Boolean, crashReports: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    var accepted by rememberSaveable { mutableStateOf(false) }
    var analytics by rememberSaveable { mutableStateOf(initialAnalytics) }
    var crashReports by rememberSaveable { mutableStateOf(initialCrashReports) }
    val isLast = pager.currentPage == PAGES.lastIndex

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Scaffold(
        bottomBar = {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (isLast) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it })
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = accepted, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.intro_terms_accept), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageDots(count = PAGES.size, current = pager.currentPage)
                    Spacer(Modifier.weight(1f))
                    if (pager.currentPage > 0) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                            Text(stringResource(R.string.action_back))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = {
                            if (isLast) onAccept(analytics, crashReports)
                            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        enabled = !isLast || accepted,
                    ) {
                        Text(stringResource(if (isLast) R.string.intro_accept_continue else R.string.intro_next))
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { index ->
            val page = PAGES[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(page.icon),
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(page.intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                page.points.forEach { (icon, text) -> Point(icon, text) }
                if (index == PAGES.lastIndex) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 16.dp)) {
                            Text(
                                stringResource(R.string.intro_privacy_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Text(
                                stringResource(R.string.intro_privacy_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                            PrivacySwitch(
                                R.string.privacy_analytics, R.string.privacy_analytics_summary,
                                checked = analytics, onCheckedChange = { analytics = it },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            PrivacySwitch(
                                R.string.privacy_crashes, R.string.privacy_crashes_summary,
                                checked = crashReports, onCheckedChange = { crashReports = it },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        }
                    }
                }
                if (page.icon == R.drawable.ic_download) OfficialSourceCard()
                if (page.icon == R.drawable.ic_favorite) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            DonationButtons(onOpen = { openLink(context, it) })
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.intro_support_later),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A switch row for the analytics / crash report choices, used here and in Settings. */
@Composable
fun PrivacySwitch(
    @StringRes title: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
fun Point(@DrawableRes icon: Int, @StringRes text: Int) {
    Row(modifier = Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(icon),
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(Modifier.size(width = width, height = 8.dp).clip(CircleShape).background(color))
        }
    }
}
