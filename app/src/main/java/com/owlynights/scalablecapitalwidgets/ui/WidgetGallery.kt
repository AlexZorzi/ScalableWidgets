package com.owlynights.scalablecapitalwidgets.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import com.owlynights.scalablecapitalwidgets.BuildConfig
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.widget.InAppPreview
import com.owlynights.scalablecapitalwidgets.widget.WidgetKind
import kotlinx.coroutines.launch

/**
 * Live previews of all widgets with an "add to home screen" button each.
 * In [demo] mode the widgets render sample data and cannot be added.
 */
@Composable
fun WidgetGallery(refreshKey: Any?, demo: Boolean = false) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(WidgetKind.entries) { kind -> WidgetCard(kind, refreshKey, demo) }
    }
}

@Composable
private fun WidgetCard(kind: WidgetKind, refreshKey: Any?, demo: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canPin = remember {
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp).width(kind.previewSize.width)) {
            WidgetPreview(kind, refreshKey, demo)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(kind.title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(kind.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
            if (demo) return@Column
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                            receiver = kind.receiver,
                            preview = kind.create(),
                        )
                    }
                },
                enabled = canPin,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.widgets_add))
            }
        }
    }
}

/** Renders the real Glance widget to RemoteViews and inflates it in the app. */
@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetPreview(kind: WidgetKind, refreshKey: Any?, demo: Boolean) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var views by remember(kind) { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(kind, refreshKey, configuration.uiMode) {
        views = runCatching { kind.create().compose(
                context,
                size = kind.previewSize,
                state = InAppPreview(demo),
            ) }.getOrNull()
    }
    Box(
        modifier = Modifier.size(kind.previewSize),
        contentAlignment = Alignment.Center,
    ) {
        val current = views
        if (current == null) {
            CircularProgressIndicator()
        } else {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    runCatching { current.apply(context, frame) }.getOrNull()?.let { view ->
                        // Launchers stretch widgets to their cell size; do the same here.
                        frame.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                    }
                },
                modifier = Modifier.fillMaxWidth().size(kind.previewSize),
            )
        }
    }
}

/** Publishes generated widget-picker previews (Android 15+) once per app version. */
suspend fun publishWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < 35) return
    val prefs = context.getSharedPreferences("widget_previews", Context.MODE_PRIVATE)
    if (prefs.getInt("version", -1) == BuildConfig.VERSION_CODE) return
    val manager = GlanceAppWidgetManager(context)
    val allPublished = WidgetKind.entries.all { kind ->
        runCatching { manager.setWidgetPreviews(kind.receiver.kotlin) }.getOrNull() ==
            GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
    }
    if (allPublished) prefs.edit { putInt("version", BuildConfig.VERSION_CODE) }
}
