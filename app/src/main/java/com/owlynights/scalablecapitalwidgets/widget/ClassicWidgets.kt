package com.owlynights.scalablecapitalwidgets.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.owlynights.scalablecapitalwidgets.MainActivity
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.Position

/** Colours sampled from Scalable's iOS widgets, with a matching light variant. */
private object ClassicColors {
    val textPrimary = ColorProvider(day = Color(0xFF000000), night = Color(0xFFFFFFFF))
    val textSecondary = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF9D9D9E))
    val up = ColorProvider(day = Color(0xFF00A88E), night = Color(0xFF28EBCF))
    val down = ColorProvider(day = Color(0xFFE5603F), night = Color(0xFFF79880))
    val past = ColorProvider(day = Color(0xFFAEAEB2), night = Color(0xFF6C6C70))
    val baseline = ColorProvider(day = Color(0xFFD1D1D6), night = Color(0xFF3B3D41))
    val divider = ColorProvider(day = Color(0xFFE5E5EA), night = Color(0xFF373738))
    val logo = ColorProvider(day = Color(0xFFC7C7CC), night = Color(0xFF4E5051))

    val spark = SparkColors(baseline = baseline, past = past, up = up, down = down)
}

class ClassicPortfolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val model = rememberWidgetModel(context)
            // A language switch must rebuild everything; unchanged rows would otherwise be skipped.
            key(model.language) { ClassicPortfolioContent(model) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { ClassicPortfolioContent(WidgetModel.preview()) }
    }
}

class ClassicPositionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val model = rememberWidgetModel(context)
            // A language switch must rebuild everything; unchanged rows would otherwise be skipped.
            key(model.language) { ClassicPositionsContent(model) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { ClassicPositionsContent(WidgetModel.preview()) }
    }
}

@Composable
private fun ClassicSurface(onClick: Action, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(R.drawable.classic_widget_background))
            .clickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        content()
    }
}

@Composable
private fun ClassicLogo(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_scalable_logo),
        contentDescription = LocalContext.current.getString(R.string.app_name),
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(ClassicColors.logo),
    )
}

/** Shown instead of data while signed out, expired or before the first refresh. */
@Composable
private fun ClassicStateMessage(model: WidgetModel): Boolean {
    val (title, body) = when {
        model.auth is AuthState.SignedOut -> R.string.widget_connect_title to R.string.widget_connect_body
        model.auth is AuthState.Expired -> R.string.widget_expired_title to R.string.widget_expired_body
        model.snapshot == null -> R.string.widget_loading_title to R.string.widget_loading_body
        else -> return false
    }
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClassicLogo(26.dp)
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = context.getString(title),
            style = TextStyle(
                color = ClassicColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = context.getString(body),
            style = TextStyle(color = ClassicColors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
    return true
}

@Composable
private fun ClassicPortfolioContent(model: WidgetModel) {
    ClassicSurface(widgetTapAction(LocalContext.current, model)) {
        if (ClassicStateMessage(model)) return@ClassicSurface
        val context = LocalContext.current
        val size = LocalSize.current
        val facts = PortfolioFacts(context, model)
        val inner = DpSize(size.width - 32.dp, size.height - 28.dp)
        when (WidgetShape.of(size)) {
            WidgetShape.SMALL -> ClassicPortfolioSmall(facts, inner)
            WidgetShape.TALL -> ClassicPortfolioTall(facts, inner)
            WidgetShape.WIDE -> ClassicPortfolioWide(facts, inner)
            WidgetShape.LARGE -> ClassicPortfolioLarge(facts, inner)
        }
    }
}

@Composable
private fun ClassicAmount(facts: PortfolioFacts, fontSize: TextUnit) {
    Text(
        text = facts.amountText,
        style = TextStyle(
            color = if (facts.up) ClassicColors.up else ClassicColors.down,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

/** Large enough to read at a glance, small enough not to be cut off in narrow widgets. */
private fun classicAmountSize(facts: PortfolioFacts, width: Dp): TextUnit = when {
    width < 120.dp -> 18.sp
    width < 150.dp || facts.amountText.length > 12 -> 21.sp
    else -> 24.sp
}

@Composable
private fun ClassicPeriodLabel(facts: PortfolioFacts) {
    Text(
        text = facts.label,
        style = TextStyle(color = ClassicColors.textPrimary, fontSize = 16.sp),
        maxLines = 1,
    )
}

/** The iOS small widget: sparkline and logo on top, gain at the bottom. */
@Composable
private fun ClassicPortfolioSmall(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val sparkWidth = inner.width * 0.5f
    val layers = renderChart(context, facts.series, sparkWidth, 22.dp, facts.intraday, splitUpDown = true, withFill = false)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (!layers.isEmpty) SparklineImage(layers, ClassicColors.spark, sparkWidth, 22.dp)
            Spacer(GlanceModifier.defaultWeight())
            ClassicLogo(22.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        ClassicAmount(facts, fitFontSize(context, facts.amountText, inner.width, classicAmountSize(facts, inner.width)))
        ClassicPeriodLabel(facts)
        Spacer(GlanceModifier.height(10.dp))
        UpdatedAgo(facts.updated, ClassicColors.textSecondary)
    }
}

/** Narrow and tall: the chart takes the middle, the gain sits at the bottom. */
@Composable
private fun ClassicPortfolioTall(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val amountSize = fitFontSize(context, facts.amountText, inner.width, classicAmountSize(facts, inner.width))
    val available = inner.height - 22.dp - 12.dp - 14.dp -
        lineHeight(context, amountSize) - lineHeight(context, 16.sp) - 8.dp - 16.dp
    val plan = TallChartPlan(context, facts, inner.width, available)
    val layers = renderChart(context, facts.series, inner.width, plan.chartHeight, facts.intraday, splitUpDown = true, withFill = false)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = facts.totalText.orEmpty(),
                style = TextStyle(color = ClassicColors.textSecondary, fontSize = 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            ClassicLogo(22.dp)
        }
        Spacer(GlanceModifier.height(12.dp))
        if (plan.capped) {
            Spacer(GlanceModifier.defaultWeight())
            SparklineImage(layers, ClassicColors.spark, GlanceModifier.fillMaxWidth().height(plan.chartHeight))
        } else {
            SparklineImage(layers, ClassicColors.spark, GlanceModifier.fillMaxWidth().defaultWeight())
        }
        // Glance drops anything past 10 children per column, so the bottom block is grouped.
        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 14.dp)) {
            ClassicAmount(facts, amountSize)
            ClassicPeriodLabel(facts)
            if (plan.showList) {
                SummaryList(facts, ClassicColors.textSecondary, ClassicColors.up, ClassicColors.down)
            }
            Spacer(GlanceModifier.height(8.dp))
            UpdatedAgo(facts.updated, ClassicColors.textSecondary)
        }
    }
}

/** Short and wide: text on the left, the chart fills the right half. */
@Composable
private fun ClassicPortfolioWide(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val chartWidth = inner.width * 0.5f
    val layers = renderChart(context, facts.series, chartWidth, inner.height, facts.intraday, splitUpDown = true, withFill = false)
    Row(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            ClassicLogo(22.dp)
            Spacer(GlanceModifier.defaultWeight())
            ClassicAmount(facts, fitFontSize(context, facts.amountText, inner.width - chartWidth - 12.dp, 24.sp))
            ClassicPeriodLabel(facts)
            Spacer(GlanceModifier.height(8.dp))
            UpdatedAgo(facts.updated, ClassicColors.textSecondary)
        }
        Spacer(GlanceModifier.width(12.dp))
        SparklineImage(layers, ClassicColors.spark, GlanceModifier.width(chartWidth).fillMaxHeight())
    }
}

/** Large: header like the positions widget, big gain, full-width chart and other periods. */
@Composable
private fun ClassicPortfolioLarge(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val summaryHeight = if (facts.summary.isEmpty()) 0.dp
    else 12.dp + 1.dp + 10.dp + lineHeight(context, 12.sp) + lineHeight(context, 14.sp)
    val amountSize = fitFontSize(context, facts.amountText, inner.width, 34.sp)
    val chartHeight = inner.height - 40.dp - lineHeight(context, amountSize) - lineHeight(context, 15.sp) -
        14.dp - summaryHeight
    val layers = renderChart(
        context, facts.series, inner.width, chartHeight, facts.intraday,
        splitUpDown = true, withFill = false, stroke = 2.dp,
    )
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ClassicLogo(26.dp)
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.width(140.dp)) {
                Text(
                    text = facts.label,
                    style = TextStyle(
                        color = ClassicColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                )
                UpdatedAgo(facts.updated, ClassicColors.textSecondary, alignEnd = true)
            }
        }
        ClassicAmount(facts, amountSize)
        facts.totalText?.let {
            Text(
                text = it,
                style = TextStyle(color = ClassicColors.textSecondary, fontSize = 15.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(14.dp))
        SparklineImage(layers, ClassicColors.spark, GlanceModifier.fillMaxWidth().defaultWeight())
        if (facts.summary.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ClassicColors.divider)) {}
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val wholeFrom = if (inner.width < 300.dp) 0.0 else 1000.0
                val cellWidth = inner.width / facts.summary.size
                facts.summary.forEach { (label, value) ->
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = label,
                            style = TextStyle(color = ClassicColors.textSecondary, fontSize = 12.sp),
                            maxLines = 1,
                        )
                        Text(
                            text = Format.compactSignedMoney(value, wholeFrom = wholeFrom),
                            style = TextStyle(
                                color = if (value >= 0) ClassicColors.up else ClassicColors.down,
                                fontSize = fitFontSize(context, Format.compactSignedMoney(value, wholeFrom = wholeFrom), cellWidth, 14.sp, 9.sp),
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicPositionsContent(model: WidgetModel) {
    ClassicSurface(widgetTapAction(LocalContext.current, model)) {
        if (ClassicStateMessage(model)) return@ClassicSurface
        val context = LocalContext.current
        val snapshot = model.snapshot!!
        val size = LocalSize.current
        val positions = snapshot.positions(model.settings.listSource)

        val inner = DpSize(size.width - 32.dp, size.height - 28.dp)
        // Two cells wide: names get their own line. Two cells high: a one-line header.
        val narrow = inner.width < 200.dp
        val compactHeader = size.height < 200.dp
        val headerHeight = if (compactHeader) 30.dp else 48.dp
        val minRowHeight = when {
            narrow -> 52.dp
            compactHeader -> 42.dp
            else -> 50.dp
        }
        val available = inner.height - headerHeight
        val rows = (available / minRowHeight).toInt().coerceIn(1, 7).coerceAtMost(positions.size.coerceAtLeast(1))
        val rowHeight = available / rows

        Column(modifier = GlanceModifier.fillMaxSize()) {
            if (compactHeader) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(headerHeight),
                    verticalAlignment = Alignment.Top,
                ) {
                    ClassicLogo(20.dp)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.period_today),
                        style = TextStyle(color = ClassicColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (!narrow) {
                        UpdatedAgo(model.updatedText(context), ClassicColors.textSecondary, alignEnd = true, modifier = GlanceModifier.width(110.dp))
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(headerHeight),
                    verticalAlignment = Alignment.Top,
                ) {
                    ClassicLogo(if (narrow) 22.dp else 26.dp)
                    Spacer(GlanceModifier.defaultWeight())
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = GlanceModifier.width(min(140.dp, inner.width - 34.dp)),
                    ) {
                        Text(
                            text = context.getString(R.string.period_today),
                            style = TextStyle(
                                color = ClassicColors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                        UpdatedAgo(model.updatedText(context), ClassicColors.textSecondary, alignEnd = true)
                    }
                }
            }
            if (positions.isEmpty()) {
                Text(
                    text = context.getString(emptyListText(model.settings.listSource)),
                    style = TextStyle(color = ClassicColors.textSecondary, fontSize = 13.sp),
                )
            }
            // Divider and row form one child each: Glance keeps at most 10 children per column.
            positions.take(rows).forEachIndexed { index, position ->
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    if (index > 0) {
                        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ClassicColors.divider)) {}
                    }
                    if (narrow) {
                        ClassicPositionStacked(position, rowHeight - 1.dp)
                    } else {
                        ClassicPositionRow(position, rowHeight - 1.dp, inner.width)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicPositionRow(position: Position, height: Dp, width: Dp) {
    val context = LocalContext.current
    val change = position.dayChangeRatio
    val up = (change ?: 0.0) >= 0
    // Narrow widgets drop the sparkline so names stay readable.
    val showSpark = width >= 240.dp
    val sparkWidth = min(110.dp, width * 0.26f)
    val sparkHeight = min(34.dp, height * 0.62f)
    val layers = if (showSpark) {
        renderChart(context, position.series, sparkWidth, sparkHeight, intraday = true, splitUpDown = true, withFill = false, stroke = 1.4.dp)
    } else null

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.name,
            style = TextStyle(color = ClassicColors.textPrimary, fontSize = 15.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        if (layers != null) {
            if (layers.isEmpty) {
                Spacer(GlanceModifier.width(sparkWidth))
            } else {
                SparklineImage(layers, ClassicColors.spark, sparkWidth, sparkHeight)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.width(76.dp)) {
            position.price?.let { price ->
                val (whole, fraction) = Format.priceParts(price)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = whole,
                        style = TextStyle(color = ClassicColors.textPrimary, fontSize = 17.sp),
                        maxLines = 1,
                    )
                    Text(
                        text = fraction,
                        style = TextStyle(color = ClassicColors.textPrimary, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
            if (change != null) {
                Text(
                    text = Format.signedPercent(change),
                    style = TextStyle(
                        color = if (up) ClassicColors.up else ClassicColors.down,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Narrow widgets: the name on its own line, price and change underneath. */
@Composable
private fun ClassicPositionStacked(position: Position, height: Dp) {
    val change = position.dayChangeRatio
    Column(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.name,
            style = TextStyle(color = ClassicColors.textPrimary, fontSize = 14.sp),
            maxLines = 1,
        )
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            position.price?.let { price ->
                val (whole, fraction) = Format.priceParts(price)
                Text(
                    text = whole,
                    style = TextStyle(color = ClassicColors.textPrimary, fontSize = 15.sp),
                    maxLines = 1,
                )
                Text(
                    text = fraction,
                    style = TextStyle(color = ClassicColors.textPrimary, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            if (change != null) {
                Text(
                    text = Format.signedPercent(change),
                    style = TextStyle(
                        color = if (change >= 0) ClassicColors.up else ClassicColors.down,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

fun periodLabel(period: Period): Int = when (period) {
    Period.TODAY -> R.string.period_today
    Period.WEEK -> R.string.period_week
    Period.MONTH -> R.string.period_month
    Period.THREE_MONTHS -> R.string.period_three_months
    Period.SIX_MONTHS -> R.string.period_six_months
    Period.YEAR_TO_DATE -> R.string.period_ytd
    Period.YEAR -> R.string.period_year
    Period.MAX -> R.string.period_max
}
