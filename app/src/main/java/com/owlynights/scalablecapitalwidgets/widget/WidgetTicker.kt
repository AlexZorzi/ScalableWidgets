package com.owlynights.scalablecapitalwidgets.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.owlynights.scalablecapitalwidgets.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Advances the widgets' "5 min ago" label once a minute. The alarm is non-waking, so it only
 * fires while the device is awake and costs nothing with the screen off. Only redraws the
 * widgets from the stored snapshot; it never touches the network.
 */
object WidgetTicker {
    private const val INTERVAL_MS = 60_000L

    fun start(context: Context) {
        context.getSystemService(AlarmManager::class.java).setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun stop(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    /** Starts the ticker if any widget is on a home screen, stops it otherwise. */
    suspend fun sync(context: Context) {
        if (hasWidgets(context)) start(context) else stop(context)
    }

    suspend fun hasWidgets(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return WidgetKind.entries.any { manager.getGlanceIds(it.create().javaClass).isNotEmpty() }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!WidgetTicker.hasWidgets(app)) {
                    WidgetTicker.stop(app)
                    return@launch
                }
                AppGraph.get(app).clock.value = System.currentTimeMillis()
                WidgetUpdater.updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
