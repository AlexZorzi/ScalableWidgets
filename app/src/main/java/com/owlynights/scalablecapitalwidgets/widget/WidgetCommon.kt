package com.owlynights.scalablecapitalwidgets.widget

import android.content.Context
import com.owlynights.scalablecapitalwidgets.data.WidgetTap
import com.owlynights.scalablecapitalwidgets.MainActivity
import androidx.glance.text.TextAlign
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.Action
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalState
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.GlanceId
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.owlynights.scalablecapitalwidgets.AppGraph
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.data.ChartSeries
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.PortfolioSnapshot
import com.owlynights.scalablecapitalwidgets.ui.periodShortLabel
import com.owlynights.scalablecapitalwidgets.work.RefreshWorker

/** Everything a widget needs to draw itself. */
data class WidgetModel(
    val auth: AuthState,
    val snapshot: PortfolioSnapshot?,
    val settings: AppSettings,
    /** Rendered by the app itself, where collection (lazy list) views stay empty. */
    val inApp: Boolean = false,
    /** Current time, ticking once a minute; drives the "5 min ago" label. */
    val now: Long = System.currentTimeMillis(),
    val syncing: Boolean = false,
    /** Part of the model so that switching the app language recomposes every text. */
    val language: String = "",
) {
    /** "Updating…" while a refresh runs, otherwise how long ago the data was fetched. */
    fun updatedText(context: Context): String = when {
        syncing -> context.getString(R.string.widget_syncing)
        snapshot == null -> ""
        else -> updatedAgo(context, snapshot.fetchedAt, now)
    }

    companion object {
        fun preview() = WidgetModel(
            auth = AuthState.SignedIn("preview", 0),
            snapshot = SampleData.snapshot(),
            settings = AppSettings(),
        )
    }
}

/** Glance state the app passes when it renders a widget for its own gallery. */
data class InAppPreview(val demo: Boolean)

/** Collects the live app state, so widgets redraw as soon as the snapshot changes. */
@Composable
fun rememberWidgetModel(context: Context): WidgetModel {
    val inApp = LocalState.current as? InAppPreview
    if (inApp?.demo == true) return WidgetModel.preview().copy(inApp = true)
    val graph = AppGraph.get(context)
    val auth by graph.sessions.state.collectAsState()
    val snapshot by graph.snapshots.snapshot.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val now by graph.clock.collectAsState()
    val syncing by graph.repository.syncing.collectAsState()
    val language by graph.language.collectAsState()
    return WidgetModel(auth, snapshot, settings, inApp = inApp != null, now = now, syncing = syncing, language = language)
}

/** "just now", "5 min ago", "2 h 5 min ago", "3 days ago" — no seconds. */
fun updatedAgo(context: Context, fetchedAt: Long, now: Long): String {
    val minutes = ((now - fetchedAt) / 60_000).coerceAtLeast(0)
    val hours = minutes / 60
    val res = context.resources
    return when {
        minutes < 1 -> res.getString(R.string.updated_just_now)
        minutes < 60 -> res.getString(R.string.updated_minutes, minutes)
        hours < 24 && minutes % 60 == 0L -> res.getString(R.string.updated_hours, hours)
        hours < 24 -> res.getString(R.string.updated_hours_minutes, hours, minutes % 60)
        else -> (hours / 24).toInt().let { days -> res.getQuantityString(R.plurals.updated_days, days, days) }
    }
}

/**
 * What tapping the widget does. Until there is data to show, a tap always opens the app,
 * since that is where the user signs in.
 */
fun widgetTapAction(context: Context, model: WidgetModel): Action {
    if (model.auth !is AuthState.SignedIn || model.snapshot == null || model.inApp) {
        return actionStartActivity<MainActivity>()
    }
    return when (model.settings.widgetTap) {
        WidgetTap.OPEN_APP -> actionStartActivity<MainActivity>()
        WidgetTap.REFRESH -> actionRunCallback<RefreshAction>()
        WidgetTap.OPEN_SCALABLE -> actionStartActivity(scalableAppIntent(context))
    }
}

/** Scalable's own app if installed, otherwise its website. */
fun scalableAppIntent(context: Context): Intent =
    (context.packageManager.getLaunchIntentForPackage(SCALABLE_PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://de.scalable.capital/")))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val SCALABLE_PACKAGE = "capital.scalable.droid"

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        WidgetKind.entries.forEach { it.create().updateAll(context) }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

data class SparkColors(
    val baseline: ColorProvider,
    val past: ColorProvider,
    val up: ColorProvider,
    val down: ColorProvider,
    val fillUp: ColorProvider? = null,
    val fillDown: ColorProvider? = null,
)

/**
 * Stacks the sparkline masks and tints each layer with its theme colour. The bitmaps are
 * stretched to [modifier]'s bounds, so a chart can fill whatever space a layout leaves it.
 */
@Composable
fun SparklineImage(layers: SparklineLayers, colors: SparkColors, modifier: GlanceModifier) {
    Box(modifier = modifier) {
        @Composable
        fun Layer(bitmap: android.graphics.Bitmap?, color: ColorProvider?) {
            if (bitmap == null || color == null) return
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(color),
            )
        }
        Layer(layers.fillUp, colors.fillUp)
        Layer(layers.fillDown, colors.fillDown)
        Layer(layers.baseline, colors.baseline)
        Layer(layers.past, colors.past)
        Layer(layers.up, colors.up)
        Layer(layers.down, colors.down)
    }
}

@Composable
fun SparklineImage(layers: SparklineLayers, colors: SparkColors, width: Dp, height: Dp) =
    SparklineImage(layers, colors, GlanceModifier.size(width, height))

fun Dp.toPx(context: Context): Int = (value * context.resources.displayMetrics.density).toInt()

/** Layout families a resizable widget switches between. */
enum class WidgetShape {
    SMALL, TALL, WIDE, LARGE;

    companion object {
        fun of(size: DpSize): WidgetShape = when {
            // Both dimensions matter: a 3×4 widget is wide-ish but far from short.
            size.width >= 220.dp && size.height >= 220.dp -> LARGE
            size.width >= 230.dp -> WIDE
            size.height >= 200.dp -> TALL
            else -> SMALL
        }
    }
}

/** Approximate height of one line of text, used to size charts that fill the leftover space. */
fun lineHeight(context: Context, fontSize: TextUnit): Dp =
    (fontSize.value * 1.3f * context.resources.configuration.fontScale).dp

/**
 * Largest font size up to [max] at which [text] fits on one line in [width], so big numbers
 * shrink in tight widgets instead of being cut off. Uses a conservative average glyph width.
 */
fun fitFontSize(context: Context, text: String, width: Dp, max: TextUnit, min: TextUnit = 14.sp): TextUnit {
    val emPerChar = 0.62f * context.resources.configuration.fontScale
    val fitting = width.value / (text.length.coerceAtLeast(1) * emPerChar)
    return fitting.coerceIn(min.value, max.value).sp
}

/** Renders a chart for a slot of roughly [width] × [height]; it is stretched to the real slot. */
fun renderChart(
    context: Context,
    series: ChartSeries?,
    width: Dp,
    height: Dp,
    intraday: Boolean,
    splitUpDown: Boolean,
    withFill: Boolean,
    stroke: Dp = 1.6.dp,
    dashedBaseline: Boolean = false,
): SparklineLayers = Sparkline.render(
    series = series,
    widthPx = width.coerceAtLeast(24.dp).toPx(context),
    heightPx = height.coerceAtLeast(16.dp).toPx(context),
    strokePx = stroke.value * context.resources.displayMetrics.density,
    intraday = intraday,
    splitUpDown = splitUpDown,
    withFill = withFill,
    dashedBaseline = dashedBaseline,
)

/**
 * How a tall, narrow widget splits its free height between the chart and the list of other
 * periods. Charts never get taller than they are wide: stretched charts exaggerate every swing.
 */
class TallChartPlan(context: Context, facts: PortfolioFacts, width: Dp, available: Dp) {
    private val listHeight = 10.dp + (lineHeight(context, 12.sp) + 4.dp) * facts.summary.size
    val showList = facts.summary.isNotEmpty() && available - listHeight >= width * 0.6f
    private val slot = if (showList) available - listHeight else available
    val chartHeight = min(slot, width * 0.95f)
    /** When capped, the chart gets a fixed height and a spacer takes the rest. */
    val capped = slot > width * 0.95f
}

/** What the portfolio widgets show, prepared once for every layout. */
class PortfolioFacts(context: Context, model: WidgetModel) {
    private val snapshot: PortfolioSnapshot = model.snapshot!!
    private val period: Period = model.settings.period

    val amount: Double? = snapshot.absoluteReturn(period)
    val up: Boolean = (amount ?: 0.0) >= 0
    val amountText: String = amount?.let { Format.signedMoney(it, snapshot.currency) } ?: "–"
    val label: String = context.getString(periodLabel(period))
    val totalText: String? = snapshot.totalValue?.let { Format.money(it, snapshot.currency) }
    val updated: String = model.updatedText(context)
    val series: ChartSeries? = snapshot.chart(period)
    val intraday: Boolean = period == Period.TODAY

    /** Other periods' returns for the summary row of large widgets, e.g. "1M" to +120.0. */
    val summary: List<Pair<String, Double>> = SUMMARY_PERIODS
        .filter { it != period }
        .mapNotNull { other ->
            snapshot.absoluteReturn(other)?.let { context.getString(periodShortLabel(other)) to it }
        }
        .take(4)

    private companion object {
        val SUMMARY_PERIODS = listOf(
            Period.TODAY, Period.WEEK, Period.MONTH, Period.YEAR_TO_DATE, Period.YEAR,
        )
    }
}

/** The small grey "5 min ago" line. */
@Composable
fun UpdatedAgo(
    text: String,
    color: ColorProvider,
    alignEnd: Boolean = false,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = 12.sp,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

/** Other periods' returns as a compact list, for tall portfolio widgets. */
@Composable
fun SummaryList(facts: PortfolioFacts, labelColor: ColorProvider, upColor: ColorProvider, downColor: ColorProvider) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
        facts.summary.forEach { (label, value) ->
            SummaryLine(label, value, labelColor, if (value >= 0) upColor else downColor)
        }
    }
}

/** "1M        +€120" — one row of the period list in tall portfolio widgets. */
@Composable
private fun SummaryLine(label: String, value: Double, labelColor: ColorProvider, valueColor: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = labelColor, fontSize = 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = Format.compactSignedMoney(value),
            style = TextStyle(color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}
