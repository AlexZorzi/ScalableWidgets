package com.owlynights.scalablecapitalwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.owlynights.scalablecapitalwidgets.AppGraph
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.telemetry.Telemetry
import com.owlynights.scalablecapitalwidgets.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts periodic refreshes and the minute ticker as soon as a widget is placed. */
abstract class PortfolioWidgetReceiver : GlanceAppWidgetReceiver() {
    private val kind: String
        get() = WidgetKind.entries.first { it.receiver == javaClass }.name.lowercase()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Telemetry.event(context, "widget_added", "kind" to kind)
        RefreshWorker.schedule(context, AppGraph.get(context).settings.current.refreshMinutes)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also runs after a reboot, which clears alarms.
        WidgetTicker.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Telemetry.event(context, "widget_removed", "kind" to kind)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetTicker.sync(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

class ClassicPortfolioReceiver : PortfolioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassicPortfolioWidget()
}

class ClassicPositionsReceiver : PortfolioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassicPositionsWidget()
}

class MaterialPortfolioReceiver : PortfolioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MaterialPortfolioWidget()
}

class MaterialPositionsReceiver : PortfolioWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MaterialPositionsWidget()
}

enum class WidgetKind(
    val receiver: Class<out GlanceAppWidgetReceiver>,
    val create: () -> GlanceAppWidget,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    /** Size used for the in-app preview, roughly a 2×2 or 4×4 home-screen cell block. */
    val previewSize: DpSize,
) {
    CLASSIC_PORTFOLIO(
        ClassicPortfolioReceiver::class.java, ::ClassicPortfolioWidget,
        R.string.widget_classic_portfolio_name, R.string.widget_classic_portfolio_description,
        DpSize(165.dp, 165.dp),
    ),
    CLASSIC_POSITIONS(
        ClassicPositionsReceiver::class.java, ::ClassicPositionsWidget,
        R.string.widget_classic_positions_name, R.string.widget_classic_positions_description,
        DpSize(300.dp, 320.dp),
    ),
    MATERIAL_PORTFOLIO(
        MaterialPortfolioReceiver::class.java, ::MaterialPortfolioWidget,
        R.string.widget_material_portfolio_name, R.string.widget_material_portfolio_description,
        DpSize(165.dp, 200.dp),
    ),
    MATERIAL_POSITIONS(
        MaterialPositionsReceiver::class.java, ::MaterialPositionsWidget,
        R.string.widget_material_positions_name, R.string.widget_material_positions_description,
        DpSize(300.dp, 320.dp),
    ),
}
