package com.owlynights.scalablecapitalwidgets.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Performance periods. [overviewKeys] are the `timeWeightedReturnByTimeframe` names of the
 * overview query, [chartKey] the `timeseries` name of the portfolio chart query (the CLI's
 * `1d` chart is `TWO_DAYS`: yesterday plus today, split by the closing reference point).
 */
enum class Period(val overviewKeys: Set<String>, val chartKey: String) {
    TODAY(setOf("INTRADAY", "ONE_DAY", "TWO_DAYS"), "TWO_DAYS"),
    WEEK(setOf("ONE_WEEK"), "ONE_WEEK"),
    MONTH(setOf("ONE_MONTH"), "ONE_MONTH"),
    THREE_MONTHS(setOf("THREE_MONTHS"), "THREE_MONTHS"),
    SIX_MONTHS(setOf("SIX_MONTHS"), "SIX_MONTHS"),
    YEAR_TO_DATE(setOf("YEAR_TO_DATE"), "YEAR_TO_DATE"),
    YEAR(setOf("ONE_YEAR"), "ONE_YEAR"),
    MAX(setOf("MAX"), "MAX"),
}

enum class ListSource { HOLDINGS, WATCHLIST, CUSTOM }

/** A title the user picked for "My list"; the name is kept so the list renders before a refresh. */
data class PickedTitle(val isin: String, val name: String)

data class ChartPoint(val time: Long, val value: Double)

/** Index of the first point of the current trading day and the value changes refer to. */
data class SeriesView(val splitIndex: Int, val baseline: Double?)

data class ChartSeries(
    /** Previous close (or start value) the chart is compared against. */
    val reference: Double?,
    val referenceTime: Long?,
    val points: List<ChartPoint>,
) {
    val last: Double? get() = points.lastOrNull()?.value

    /**
     * How to read the series. For intraday (`TWO_DAYS`) data, points before the last trading
     * day are "past" (drawn grey, like the iOS widget) and the baseline is the previous close.
     */
    fun view(intraday: Boolean): SeriesView {
        if (points.isEmpty()) return SeriesView(0, reference)
        if (!intraday) return SeriesView(0, reference ?: points.first().value)

        val zone = ZoneId.systemDefault()
        fun day(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val lastDay = day(points.last().time)
        val split = points.indexOfFirst { day(it.time) == lastDay }
        if (split <= 0) return SeriesView(0, reference ?: points.first().value)

        val previous = points[split - 1]
        val referenceIsPreviousClose = reference != null && referenceTime != null &&
            day(referenceTime) == day(previous.time)
        return SeriesView(split, if (referenceIsPreviousClose) reference else previous.value)
    }

    fun change(intraday: Boolean): Double? {
        val last = last ?: return null
        val baseline = view(intraday).baseline ?: return null
        return last - baseline
    }

    fun changeRatio(intraday: Boolean): Double? {
        val change = change(intraday) ?: return null
        val baseline = view(intraday).baseline ?: return null
        return if (baseline == 0.0) null else change / baseline
    }

    fun toJson(): JSONObject = JSONObject()
        .put("reference", reference)
        .put("referenceTime", referenceTime)
        .put("points", JSONArray().apply {
            points.forEach { put(JSONArray().put(it.time).put(it.value)) }
        })

    companion object {
        fun fromJson(json: JSONObject): ChartSeries {
            val array = json.optJSONArray("points") ?: JSONArray()
            return ChartSeries(
                reference = json.optDoubleOrNull("reference"),
                referenceTime = json.optLongOrNull("referenceTime"),
                points = List(array.length()) { i ->
                    val point = array.getJSONArray(i)
                    ChartPoint(point.getLong(0), point.getDouble(1))
                },
            )
        }
    }
}

data class Position(
    val isin: String,
    val name: String,
    val type: String?,
    val price: Double?,
    val currency: String?,
    val quantity: Double?,
    val valuation: Double?,
    val outdated: Boolean,
    /** Intraday (two-day) price chart, only fetched for the positions shown in widgets. */
    val series: ChartSeries?,
) {
    val dayChangeRatio: Double? get() = series?.changeRatio(intraday = true)

    fun toJson(): JSONObject = JSONObject()
        .put("isin", isin)
        .put("name", name)
        .put("type", type)
        .put("price", price)
        .put("currency", currency)
        .put("quantity", quantity)
        .put("valuation", valuation)
        .put("outdated", outdated)
        .put("series", series?.toJson())

    companion object {
        fun fromJson(json: JSONObject) = Position(
            isin = json.getString("isin"),
            name = json.optString("name"),
            type = json.optStringOrNull("type"),
            price = json.optDoubleOrNull("price"),
            currency = json.optStringOrNull("currency"),
            quantity = json.optDoubleOrNull("quantity"),
            valuation = json.optDoubleOrNull("valuation"),
            outdated = json.optBoolean("outdated"),
            series = json.optJSONObject("series")?.let(ChartSeries::fromJson),
        )
    }
}

data class PortfolioSnapshot(
    /** When the app fetched this snapshot (epoch millis). */
    val fetchedAt: Long,
    val currency: String,
    val totalValue: Double?,
    val securitiesValue: Double?,
    val cryptoValue: Double?,
    /** `simpleAbsoluteReturn` by overview timeframe name. */
    val returns: Map<String, Double>,
    /** Portfolio valuation history by chart timeframe name. */
    val charts: Map<String, ChartSeries>,
    val holdings: List<Position>,
    val watchlist: List<Position>,
    /** "My list": the user's picks, in their order. */
    val custom: List<Position> = emptyList(),
) {
    fun absoluteReturn(period: Period): Double? =
        period.overviewKeys.firstNotNullOfOrNull { returns[it] }
            ?: chart(period)?.change(intraday = period == Period.TODAY)

    fun chart(period: Period): ChartSeries? = charts[period.chartKey]

    fun positions(source: ListSource): List<Position> = when (source) {
        ListSource.HOLDINGS -> holdings
        ListSource.WATCHLIST -> watchlist
        ListSource.CUSTOM -> custom
    }

    fun toJson(): String = JSONObject()
        .put("fetchedAt", fetchedAt)
        .put("currency", currency)
        .put("totalValue", totalValue)
        .put("securitiesValue", securitiesValue)
        .put("cryptoValue", cryptoValue)
        .put("returns", JSONObject().apply { returns.forEach { (k, v) -> put(k, v) } })
        .put("charts", JSONObject().apply { charts.forEach { (k, v) -> put(k, v.toJson()) } })
        .put("holdings", JSONArray().apply { holdings.forEach { put(it.toJson()) } })
        .put("watchlist", JSONArray().apply { watchlist.forEach { put(it.toJson()) } })
        .put("custom", JSONArray().apply { custom.forEach { put(it.toJson()) } })
        .toString()

    companion object {
        fun fromJson(raw: String): PortfolioSnapshot? = runCatching {
            val json = JSONObject(raw)
            val returns = json.optJSONObject("returns") ?: JSONObject()
            val charts = json.optJSONObject("charts") ?: JSONObject()
            PortfolioSnapshot(
                fetchedAt = json.getLong("fetchedAt"),
                currency = json.optString("currency", "EUR"),
                totalValue = json.optDoubleOrNull("totalValue"),
                securitiesValue = json.optDoubleOrNull("securitiesValue"),
                cryptoValue = json.optDoubleOrNull("cryptoValue"),
                returns = returns.keys().asSequence().associateWith { returns.getDouble(it) },
                charts = charts.keys().asSequence()
                    .associateWith { ChartSeries.fromJson(charts.getJSONObject(it)) },
                holdings = json.optJSONArray("holdings").mapObjects(Position::fromJson),
                watchlist = json.optJSONArray("watchlist").mapObjects(Position::fromJson),
                custom = json.optJSONArray("custom").mapObjects(Position::fromJson),
            )
        }.getOrNull()
    }
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    // Amounts arrive as numbers or as decimal strings.
    val value = opt(key)
    return if (value is Number) value.toDouble() else (value as? String)?.trim()?.toDoubleOrNull()
}

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }
}
