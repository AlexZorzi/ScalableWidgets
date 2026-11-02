package com.owlynights.scalablecapitalwidgets.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.R
import java.security.MessageDigest

/** The only official place to download the app. */
const val RELEASES_URL = "https://github.com/AlexZorzi/ScalableWidgets/releases"

/** SHA-256 of the release signing certificate (CN=Alex Zorzi). */
private const val OFFICIAL_CERT_SHA256 = "b621b7f8859499f706cdd1ea216508d741ae43c9c0c6dd3c36b7a58bf8218ac3"

/** Icon and text of each point on the official-source page, shared by the intro and About. */
val OFFICIAL_SOURCE_POINTS = listOf(
    R.drawable.ic_download to R.string.official_point_updates,
    R.drawable.ic_close to R.string.official_point_copies,
    R.drawable.ic_shield to R.string.official_point_signature,
)

/**
 * Whether this copy is signed with the official release key. A modified copy could patch
 * this out, so it is a hint for honest mistakes (forks, mirrors), not a security boundary.
 */
fun isOfficialBuild(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    info.signingInfo?.apkContentsSigners.orEmpty().any { signer ->
        MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) } == OFFICIAL_CERT_SHA256
    }
}.getOrDefault(false)

/** Button to the releases page, whether this copy is official, and the certificate fingerprint. */
@Composable
fun OfficialSourceCard() {
    val context = LocalContext.current
    val official = remember { isOfficialBuild(context) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (official) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(if (official) R.string.official_status_ok else R.string.official_status_unofficial),
                style = MaterialTheme.typography.titleSmall,
                color = if (official) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { openLink(context, RELEASES_URL) },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ic_open_in_new), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.official_open))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.official_fingerprint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                OFFICIAL_CERT_SHA256.uppercase().chunked(2).joinToString(":"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The official-source page on its own, opened from About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialSourceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_official_entry)) },
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
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            item {
                Text(stringResource(R.string.official_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.official_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OFFICIAL_SOURCE_POINTS.forEach { (icon, text) -> Point(icon, text) }
                OfficialSourceCard()
            }
        }
    }
}
