package com.owlynights.scalablecapitalwidgets.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.owlynights.scalablecapitalwidgets.BuildConfig
import com.owlynights.scalablecapitalwidgets.R

private const val PAYPAL_URL = "https://www.paypal.com/paypalme/alexzorzi"
private const val KOFI_URL = "https://ko-fi.com/allesss"

/** A license text shipped in res/raw. */
private enum class License(@param:StringRes val title: Int, @param:RawRes val text: Int) {
    GPL3(R.string.about_license_gpl, R.raw.license_gpl3),
    APACHE2(R.string.about_license_apache, R.raw.license_apache2),
}

/** Third-party software credited on the About page; Apache 2.0 unless it links its own terms. */
private data class Credit(val name: String, @param:StringRes val detail: Int, val termsUrl: String? = null)

private val CREDITS = listOf(
    Credit("Scalable CLI", R.string.about_credit_cli),
    Credit("Android Jetpack", R.string.about_credit_jetpack),
    Credit("Kotlin & kotlinx.coroutines", R.string.about_credit_kotlin),
    Credit("Firebase Analytics & Crashlytics", R.string.about_credit_firebase, "https://firebase.google.com/terms"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var openLicense by rememberSaveable { mutableStateOf<License?>(null) }
    openLicense?.let { license ->
        BackHandler { openLicense = null }
        LicenseScreen(license, onBack = { openLicense = null })
        return
    }
    var showOfficial by rememberSaveable { mutableStateOf(false) }
    if (showOfficial) {
        BackHandler { showOfficial = false }
        OfficialSourceScreen(onBack = { showOfficial = false })
        return
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_scalable_logo),
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SupportCard(onOpen = { openLink(context, it) }) }

            item {
                AboutRow(
                    icon = R.drawable.ic_download,
                    title = stringResource(R.string.about_official_entry),
                    subtitle = stringResource(R.string.about_official_summary),
                    onClick = { showOfficial = true },
                )
            }

            item { SectionTitle(R.string.about_license_section) }
            item {
                AboutRow(
                    icon = R.drawable.ic_gavel,
                    title = stringResource(R.string.about_license_gpl),
                    subtitle = stringResource(R.string.about_license_gpl_summary),
                    onClick = { openLicense = License.GPL3 },
                )
            }

            item { SectionTitle(R.string.about_credits_section) }
            items(CREDITS) { credit ->
                AboutRow(
                    icon = R.drawable.ic_info,
                    title = credit.name,
                    subtitle = stringResource(credit.detail),
                    onClick = {
                        val terms = credit.termsUrl
                        if (terms != null) openLink(context, terms) else openLicense = License.APACHE2
                    },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_about) + "\n\n" + stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SupportCard(onOpen: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_favorite),
                    null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.about_support_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            DonationButtons(onOpen)
        }
    }
}

/** PayPal and Ko-fi buttons, shared by the About page and the introduction. */
@Composable
fun DonationButtons(onOpen: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onOpen(PAYPAL_URL) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.ic_favorite), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("PayPal")
        }
        FilledTonalButton(
            onClick = { onOpen(KOFI_URL) },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.weight(1f),
        ) {
            Icon(painterResource(R.drawable.ic_coffee), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ko-fi")
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

/** Full license text, split into paragraphs so long texts scroll smoothly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseScreen(license: License, onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(license) {
        context.resources.openRawResource(license.text).bufferedReader().use { it.readText() }
            .split(Regex("\\n\\s*\\n"))
            // The files are hard-wrapped at ~72 columns; reflow so the screen wraps them instead.
            .map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }
            .filter { it.isNotBlank() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(license.title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(paragraphs) { paragraph ->
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Opens a link in whatever handles it (e.g. the PayPal app), falling back to the browser. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
    }
}
