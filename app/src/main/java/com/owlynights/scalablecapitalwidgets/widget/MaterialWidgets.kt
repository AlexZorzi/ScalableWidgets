package com.owlynights.scalablecapitalwidgets.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.owlynights.scalablecapitalwidgets.MainActivity
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.Position
import com.owlynights.scalablecapitalwidgets.ui.theme.DarkColors
import com.owlynights.scalablecapitalwidgets.ui.theme.LightColors

/** Semantic gain colours in Material 3 tonal style; losses use the theme's error roles. */
private object Gain {
    val color = ColorProvider(day = Color(0xFF1B6D35), night = Color(0xFF8AD89B))
    val container = ColorProvider(day = Color(0xFFB4F1C1), night = Color(0xFF00522A))
    val onContainer = ColorProvider(day = Color(0xFF00210D), night = Color(0xFFB4F1C1))
}

class MaterialPortfolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val model = rememberWidgetModel(context)
            // A language switch must rebuild everything; unchanged rows would otherwise be skipped.
            key(model.language) { WidgetTheme { MaterialPortfolioContent(model) } }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WidgetTheme { MaterialPortfolioContent(WidgetModel.preview()) } }
    }
}

class MaterialPositionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val model = rememberWidgetModel(context)
            // A language switch must rebuild everything; unchanged rows would otherwise be skipped.
            key(model.language) { WidgetTheme { MaterialPositionsContent(model) } }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WidgetTheme { MaterialPositionsContent(WidgetModel.preview()) } }
    }
}

/** Dynamic colours on Android 12+, the app's mint scheme before that. */
@Composable
private fun WidgetTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors), content = content)
    }
}

@Composable
private fun MaterialSurface(padding: Dp, onClick: Action, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(
                ImageProvider(R.drawable.widget_background_rounded),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            )
            .clickable(onClick)
            .padding(padding),
    ) {
        content()
    }
}

@Composable
private fun Pill(text: String, background: ColorProvider, foreground: ColorProvider, fontSize: TextUnit = 12.sp) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.shape_pill), colorFilter = ColorFilter.tint(background))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = foreground, fontSize = fontSize, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun MaterialLogo(size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.ic_scalable_logo),
        contentDescription = LocalContext.current.getString(R.string.app_name),
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

@Composable
private fun materialSpark() = SparkColors(
    baseline = GlanceTheme.colors.outline,
    past = GlanceTheme.colors.outline,
    up = Gain.color,
    down = GlanceTheme.colors.error,
    fillUp = Gain.color,
    fillDown = GlanceTheme.colors.error,
)

@Composable
private fun MaterialStateMessage(model: WidgetModel): Boolean {
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
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .background(ImageProvider(R.drawable.shape_pill), colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_scalable_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Text(
            text = context.getString(title),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = context.getString(body),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center),
        )
    }
    return true
}

@Composable
private fun MaterialPortfolioContent(model: WidgetModel) {
    MaterialSurface(padding = 14.dp, onClick = widgetTapAction(LocalContext.current, model)) {
        if (MaterialStateMessage(model)) return@MaterialSurface
        val context = LocalContext.current
        val size = LocalSize.current
        val facts = PortfolioFacts(context, model)
        val inner = DpSize(size.width - 28.dp, size.height - 28.dp)
        when (WidgetShape.of(size)) {
            WidgetShape.SMALL, WidgetShape.TALL -> MaterialPortfolioColumn(facts, inner)
            WidgetShape.WIDE -> MaterialPortfolioWide(facts, inner)
            WidgetShape.LARGE -> MaterialPortfolioLarge(facts, inner)
        }
    }
}

@Composable
private fun MaterialHeader(facts: PortfolioFacts) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill(
            text = facts.label,
            background = GlanceTheme.colors.secondaryContainer,
            foreground = GlanceTheme.colors.onSecondaryContainer,
        )
        Spacer(GlanceModifier.defaultWeight())
        MaterialLogo(20.dp)
    }
}

@Composable
private fun MaterialAmount(facts: PortfolioFacts, fontSize: TextUnit) {
    Text(
        text = facts.amountText,
        style = TextStyle(
            color = if (facts.up) Gain.color else GlanceTheme.colors.error,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
private fun MaterialTotal(facts: PortfolioFacts, fontSize: TextUnit = 13.sp) {
    facts.totalText?.let {
        Text(
            text = it,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize),
            maxLines = 1,
        )
    }
}

private fun materialChart(context: Context, facts: PortfolioFacts, width: Dp, height: Dp, stroke: Dp = 2.dp) =
    renderChart(
        context, facts.series, width, height, facts.intraday,
        splitUpDown = true, withFill = true, stroke = stroke, dashedBaseline = true,
    )

/** Small and tall sizes: the chart fills whatever is left between header and numbers. */
@Composable
private fun MaterialPortfolioColumn(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val amountSize = fitFontSize(context, facts.amountText, inner.width, if (inner.width < 140.dp) 20.sp else 26.sp)
    val available = inner.height - 26.dp - 10.dp - 8.dp - lineHeight(context, amountSize) -
        lineHeight(context, 13.sp) - 4.dp - 16.dp
    val plan = TallChartPlan(context, facts, inner.width, available)
    // Below ~40dp a chart is just noise; keep the numbers instead.
    val layers = if (plan.chartHeight >= 40.dp) materialChart(context, facts, inner.width, plan.chartHeight) else null
    Column(modifier = GlanceModifier.fillMaxSize()) {
        MaterialHeader(facts)
        if (layers != null && !layers.isEmpty) {
            Spacer(GlanceModifier.height(10.dp))
            if (plan.capped) {
                Spacer(GlanceModifier.defaultWeight())
                SparklineImage(layers, materialSpark(), GlanceModifier.fillMaxWidth().height(plan.chartHeight))
            } else {
                SparklineImage(layers, materialSpark(), GlanceModifier.fillMaxWidth().defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        // Glance drops anything past 10 children per column, so the bottom block is grouped.
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            MaterialAmount(facts, amountSize)
            MaterialTotal(facts)
            if (plan.showList && layers != null) {
                SummaryList(facts, GlanceTheme.colors.onSurfaceVariant, Gain.color, GlanceTheme.colors.error)
            }
            Spacer(GlanceModifier.height(4.dp))
            UpdatedAgo(facts.updated, GlanceTheme.colors.onSurfaceVariant)
        }
    }
}

/** Short and wide: numbers on the left, chart on the right. */
@Composable
private fun MaterialPortfolioWide(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val chartWidth = inner.width * 0.52f
    val layers = materialChart(context, facts, chartWidth, inner.height)
    Row(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            MaterialHeader(facts)
            Spacer(GlanceModifier.defaultWeight())
            MaterialAmount(facts, fitFontSize(context, facts.amountText, inner.width - chartWidth - 12.dp, 24.sp))
            MaterialTotal(facts)
            Spacer(GlanceModifier.height(4.dp))
            UpdatedAgo(facts.updated, GlanceTheme.colors.onSurfaceVariant)
        }
        Spacer(GlanceModifier.width(12.dp))
        SparklineImage(layers, materialSpark(), GlanceModifier.width(chartWidth).fillMaxHeight())
    }
}

/** Large: big numbers, a full-width chart and a tonal row with the other periods. */
@Composable
private fun MaterialPortfolioLarge(facts: PortfolioFacts, inner: DpSize) {
    val context = LocalContext.current
    val summaryHeight = if (facts.summary.isEmpty()) 0.dp
    else 12.dp + 20.dp + lineHeight(context, 11.sp) + lineHeight(context, 13.sp)
    val amountSize = fitFontSize(context, facts.amountText, inner.width, 34.sp)
    val chartHeight = inner.height - 26.dp - 10.dp - lineHeight(context, amountSize) -
        lineHeight(context, 14.sp) - 12.dp - summaryHeight - 8.dp - 16.dp
    val layers = materialChart(context, facts, inner.width, chartHeight, stroke = 2.4.dp)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        MaterialHeader(facts)
        Spacer(GlanceModifier.height(10.dp))
        MaterialAmount(facts, amountSize)
        MaterialTotal(facts, 14.sp)
        Spacer(GlanceModifier.height(12.dp))
        SparklineImage(layers, materialSpark(), GlanceModifier.fillMaxWidth().defaultWeight())
        if (facts.summary.isNotEmpty()) {
            Spacer(GlanceModifier.height(12.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(
                        ImageProvider(R.drawable.shape_rounded_16),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.surface),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                val wholeFrom = if (inner.width < 300.dp) 0.0 else 1000.0
                val cellWidth = (inner.width - 24.dp) / facts.summary.size
                facts.summary.forEach { (label, value) ->
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                            maxLines = 1,
                        )
                        Text(
                            text = Format.compactSignedMoney(value, wholeFrom = wholeFrom),
                            style = TextStyle(
                                color = if (value >= 0) Gain.color else GlanceTheme.colors.error,
                                fontSize = fitFontSize(context, Format.compactSignedMoney(value, wholeFrom = wholeFrom), cellWidth, 13.sp, 9.sp),
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        UpdatedAgo(facts.updated, GlanceTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun MaterialPositionsContent(model: WidgetModel) {
    MaterialSurface(padding = 12.dp, onClick = widgetTapAction(LocalContext.current, model)) {
        if (MaterialStateMessage(model)) return@MaterialSurface
        val context = LocalContext.current
        val snapshot = model.snapshot!!
        val source = model.settings.listSource
        val positions = snapshot.positions(source)
        val compact = LocalSize.current.width < 250.dp

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(listTitle(source)),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    UpdatedAgo(model.updatedText(context), GlanceTheme.colors.onSurfaceVariant)
                }
                Box(
                    modifier = GlanceModifier
                        .size(36.dp)
                        .background(
                            ImageProvider(R.drawable.shape_pill),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
                        )
                        .clickable(actionRunCallback<RefreshAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = context.getString(R.string.action_refresh),
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    )
                }
            }
            if (positions.isEmpty()) {
                Text(
                    text = context.getString(emptyListText(source)),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(start = 6.dp),
                )
            }
            if (model.inApp) {
                val fitting = ((LocalSize.current.height.value - 80f) / (if (compact) 66f else 52f)).toInt().coerceAtLeast(1)
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    positions.take(fitting).forEach { position ->
                        Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            MaterialPositionRow(position, compact)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(positions, itemId = { it.isin.hashCode().toLong() }) { position ->
                        Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            MaterialPositionRow(position, compact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialPositionRow(position: Position, compact: Boolean) {
    val context = LocalContext.current
    val change = position.dayChangeRatio
    val up = (change ?: 0.0) >= 0
    val sparkWidth = 56.dp
    val sparkHeight = 26.dp
    val layers = if (compact) null else Sparkline.render(
        series = position.series,
        widthPx = sparkWidth.toPx(context),
        heightPx = sparkHeight.toPx(context),
        strokePx = 1.6.dp.value * context.resources.displayMetrics.density,
        intraday = true,
        splitUpDown = true,
        withFill = true,
    )

    val card = GlanceModifier
        .fillMaxWidth()
        .background(ImageProvider(R.drawable.shape_rounded_16), colorFilter = ColorFilter.tint(GlanceTheme.colors.surface))
    val pill: @Composable () -> Unit = {
        if (change != null) {
            Pill(
                text = Format.signedPercent(change),
                background = if (up) Gain.container else GlanceTheme.colors.errorContainer,
                foreground = if (up) Gain.onContainer else GlanceTheme.colors.onErrorContainer,
                fontSize = if (compact) 11.sp else 12.sp,
            )
        }
    }
    val name: @Composable () -> Unit = {
        Text(
            text = position.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
    val price = position.price?.let { Format.money(it, position.currency ?: "EUR") }

    if (compact) {
        // Narrow widgets: the name gets the full width, price and change go underneath.
        Column(modifier = card.padding(horizontal = 12.dp, vertical = 8.dp)) {
            name()
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = price.orEmpty(),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                pill()
            }
        }
        return
    }

    Row(
        modifier = card.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            name()
            price?.let {
                Text(
                    text = it,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        if (layers != null && !layers.isEmpty) {
            Spacer(GlanceModifier.width(8.dp))
            SparklineImage(layers, materialSpark(), sparkWidth, sparkHeight)
        }
        Spacer(GlanceModifier.width(8.dp))
        pill()
    }
}
