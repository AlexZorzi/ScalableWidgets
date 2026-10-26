package com.owlynights.scalablecapitalwidgets.data

import com.owlynights.scalablecapitalwidgets.api.Queries
import com.owlynights.scalablecapitalwidgets.api.RateLimitedException
import com.owlynights.scalablecapitalwidgets.api.ReloginRequiredException
import com.owlynights.scalablecapitalwidgets.api.ScalableConfig
import com.owlynights.scalablecapitalwidgets.api.ScalableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** Fetches portfolio data with the CLI's read-only queries and stores it as a snapshot. */
class PortfolioRepository(
    private val sessions: SessionManager,
    private val settings: SettingsStore,
    private val snapshots: SnapshotStore,
) {
    private val _syncing = MutableStateFlow(false)
    /** True while a refresh runs, so widgets can say "Updating…". */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** [onStarted] runs once [syncing] is set, e.g. to redraw widgets with "Updating…". */
    suspend fun refresh(onStarted: suspend () -> Unit = {}): PortfolioSnapshot {
        _syncing.value = true
        try {
            onStarted()
            return fetchSnapshot()
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun fetchSnapshot(): PortfolioSnapshot {
        val (accountId, portfolioId) = resolveIds()
        val portfolioVars = JSONObject()
            .put("accountId", accountId)
            .put("portfolioId", portfolioId)
        val marketVars = JSONObject(portfolioVars.toString())
            .put("includeYearToDate", true)
            .put("quoteSource", ScalableConfig.QUOTE_SOURCE)

        val overview = sessions.withAuth { client, token ->
            client.execute(
                token,
                Queries.BROKER_OVERVIEW,
                JSONObject(portfolioVars.toString()).put("includeYearToDate", true),
                "BrokerOverview",
            )
        }.portfolio()?.optJSONObject("valuation")
            ?: throw ScalableException("Broker response invalid: missing valuation")

        // Everything below is optional: a failing chart must not hide the portfolio value.
        val charts = optional {
            sessions.withAuth { client, token ->
                client.execute(token, Queries.BROKER_PORTFOLIO_CHART, portfolioVars, "BrokerPortfolioChart")
            }.portfolio()?.optJSONArray("timeseries").mapObjects { series ->
                series.optString("timeframe") to series.toChartSeries("valuation")
            }.toMap()
        }.orEmpty()

        val holdings = optional {
            sessions.withAuth { client, token ->
                client.execute(token, Queries.BROKER_HOLDINGS, marketVars, "BrokerHoldings")
            }.portfolio()?.optJSONObject("inventory")?.optJSONArray("items")
                .mapObjects(::parseHolding)
                .sortedByDescending { it.valuation ?: 0.0 }
        }.orEmpty()

        val watchlist = optional {
            sessions.withAuth { client, token ->
                client.execute(token, Queries.BROKER_WATCHLIST, marketVars, "BrokerWatchlist")
            }.portfolio()?.optJSONObject("watchlist")?.optJSONArray("items")
                .mapObjects(::parseWatchlistItem)
        }.orEmpty()

        val source = settings.current.listSource
        val previous = snapshots.load()
        val custom = customPositions(marketVars, holdings, watchlist, previous?.custom)
        val snapshot = PortfolioSnapshot(
            fetchedAt = System.currentTimeMillis(),
            currency = "EUR",
            totalValue = overview.optDoubleOrNull("valuation"),
            securitiesValue = overview.optDoubleOrNull("securitiesValuation"),
            cryptoValue = overview.optDoubleOrNull("cryptoValuation"),
            returns = overview.optJSONArray("timeWeightedReturnByTimeframe").mapObjects { item ->
                item.optDoubleOrNull("simpleAbsoluteReturn")?.let { item.optString("timeframe") to it }
            }.filterNotNull().toMap(),
            charts = charts.mapValues { (_, series) -> series.downsample(MAX_PORTFOLIO_POINTS) },
            holdings = withSeries(holdings, fetch = source == ListSource.HOLDINGS, previous?.holdings),
            watchlist = withSeries(watchlist, fetch = source == ListSource.WATCHLIST, previous?.watchlist),
            custom = withSeries(custom, fetch = source == ListSource.CUSTOM, previous?.custom),
        )
        snapshots.save(snapshot)
        return snapshot
    }

    /**
     * "My list" in the user's order. Titles in the holdings or watchlist reuse that data
     * (holdings first, for the position size); any other title is quoted on its own.
     */
    private suspend fun customPositions(
        marketVars: JSONObject,
        holdings: List<Position>,
        watchlist: List<Position>,
        previous: List<Position>?,
    ): List<Position> {
        val picks = settings.current.customList
        if (picks.isEmpty()) return emptyList()
        val known = (watchlist + holdings).associateBy { it.isin }
        val previousByIsin = previous.orEmpty().associateBy { it.isin }
        return picks.map { pick ->
            known[pick.isin]
                ?: optional { quote(marketVars, pick.isin) }
                ?: previousByIsin[pick.isin]
                ?: Position(pick.isin, pick.name, null, null, null, null, null, outdated = true, series = null)
        }
    }

    private suspend fun quote(marketVars: JSONObject, isin: String): Position? =
        sessions.withAuth { client, token ->
            client.execute(token, Queries.BROKER_QUOTE, JSONObject(marketVars.toString()).put("isin", isin), "BrokerQuote")
        }.portfolio()?.optJSONObject("security")?.let(::parseWatchlistItem)

    /** Finds securities by name, ISIN or ticker, for adding to "My list". */
    suspend fun search(term: String): List<Position> {
        val (accountId, portfolioId) = resolveIds()
        val vars = JSONObject()
            .put("accountId", accountId)
            .put("portfolioId", portfolioId)
            .put("includeYearToDate", true)
            .put("quoteSource", ScalableConfig.QUOTE_SOURCE)
            .put("searchTerm", term)
        return sessions.withAuth { client, token ->
            client.execute(token, Queries.BROKER_SEARCH, vars, "BrokerSecuritySearch")
        }.portfolio()?.optJSONObject("simpleSecuritySearch")?.optJSONArray("items")
            .mapObjects(::parseWatchlistItem)
            .filter { it.isin.isNotBlank() }
    }

    /** Adds intraday charts to the first positions; the others keep their previous chart. */
    private suspend fun withSeries(
        positions: List<Position>,
        fetch: Boolean,
        previous: List<Position>?,
    ): List<Position> {
        val previousByIsin = previous.orEmpty().associateBy { it.isin }
        return positions.mapIndexed { index, position ->
            val series = if (fetch && index < SERIES_PER_LIST) {
                optional { securitySeries(position.isin) }
            } else null
            position.copy(series = series ?: previousByIsin[position.isin]?.series)
        }
    }

    private suspend fun securitySeries(isin: String): ChartSeries? {
        val data = sessions.withAuth { client, token ->
            client.execute(
                token,
                Queries.BROKER_CHART,
                JSONObject()
                    .put("isin", isin)
                    .put("timeFrames", JSONArray().put(Period.TODAY.chartKey))
                    .put("includeYearToDate", true),
                "BrokerChart",
            )
        }
        return data.optJSONArray("timeSeriesBySecurity")
            .mapObjects { it.toChartSeries("midPrice") }
            .firstOrNull()
            ?.downsample(MAX_SECURITY_POINTS)
    }

    private suspend fun resolveIds(): Pair<String, String> {
        val current = settings.current
        if (current.accountId != null && current.portfolioId != null) {
            return current.accountId to current.portfolioId
        }
        val personId = sessions.currentSession()?.personId
            ?: throw ScalableException("Not signed in")
        val account = sessions.withAuth { client, token ->
            client.execute(token, Queries.RESOLVE_BROKER_IDS, JSONObject().put("id", personId), "ResolveBrokerIds")
        }.optJSONObject("account")
        val accountId = account?.optStringOrNull("id") ?: personId
        val portfolioIds = account?.optJSONArray("brokerPortfolios")
            .mapObjects { it.optStringOrNull("id")?.trim() }
            .filterNotNull()
            .distinct()
        val portfolioId = current.portfolioId?.takeIf { it in portfolioIds }
            ?: portfolioIds.firstOrNull()
            ?: throw ScalableException("No Scalable Broker portfolio is available for this account")
        settings.update {
            it.copy(accountId = accountId, portfolioId = portfolioId, portfolioIds = portfolioIds)
        }
        return accountId to portfolioId
    }

    suspend fun loadFirstName() {
        val personId = sessions.currentSession()?.personId ?: return
        val firstName = optional {
            sessions.withAuth { client, token ->
                client.execute(token, Queries.WHOAMI, JSONObject().put("id", personId), "WhoAmI")
            }.optJSONObject("personOverview")?.optJSONObject("personalDetails")?.optStringOrNull("firstName")
        }
        if (firstName != null) settings.update { it.copy(firstName = firstName) }
    }

    private fun parseHolding(item: JSONObject): Position {
        val quote = item.optJSONObject("quoteTick")
        val position = item.optJSONObject("inventory")?.optJSONObject("position")
        val quantity = position?.optDoubleOrNull("filled")
        val price = quote?.optDoubleOrNull("midPrice")
        val performance = item.optJSONObject("portfolioIsinPerformance")
        return Position(
            isin = item.optString("isin"),
            name = item.optString("name").ifBlank { item.optString("isin") },
            type = item.optStringOrNull("type"),
            price = price,
            currency = quote?.optStringOrNull("currency") ?: performance?.optStringOrNull("currency"),
            quantity = quantity,
            valuation = performance?.optDoubleOrNull("valuation")
                ?: if (price != null && quantity != null) price * quantity else null,
            outdated = quote?.optBoolean("isOutdated") ?: false,
            series = null,
        )
    }

    private fun parseWatchlistItem(item: JSONObject): Position {
        val quote = item.optJSONObject("quoteTick")
        return Position(
            isin = item.optString("isin"),
            name = item.optString("name").ifBlank { item.optString("isin") },
            type = item.optStringOrNull("type"),
            price = quote?.optDoubleOrNull("midPrice"),
            currency = quote?.optStringOrNull("currency"),
            quantity = null,
            valuation = null,
            outdated = quote?.optBoolean("isOutdated") ?: false,
            series = null,
        )
    }

    private fun JSONObject.portfolio(): JSONObject? =
        optJSONObject("account")?.optJSONObject("brokerPortfolio")

    private fun JSONObject.toChartSeries(valueKey: String): ChartSeries {
        val reference = optJSONObject("closingReferencePoint")
        return ChartSeries(
            reference = reference?.optDoubleOrNull(valueKey),
            referenceTime = reference?.timestamp(),
            points = optJSONArray("dataPoints").mapObjects { point ->
                val time = point.timestamp() ?: return@mapObjects null
                val value = point.optDoubleOrNull(valueKey) ?: return@mapObjects null
                ChartPoint(time, value)
            }.filterNotNull().sortedBy { it.time },
        )
    }

    private fun JSONObject.timestamp(): Long? = optJSONObject("timestampUtc")?.optStringOrNull("time")
        ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    private fun ChartSeries.downsample(max: Int): ChartSeries {
        if (points.size <= max) return this
        val step = points.size.toDouble() / (max - 1)
        val sampled = (0 until max - 1).map { points[(it * step).toInt()] } + points.last()
        return copy(points = sampled)
    }

    /** Runs a non-essential request; rate limits still abort the refresh so it can back off. */
    private inline fun <T> optional(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is RateLimitedException || e is ReloginRequiredException || e is CancellationException) throw e
        null
    }

    private companion object {
        const val SERIES_PER_LIST = 8
        const val MAX_SECURITY_POINTS = 120
        const val MAX_PORTFOLIO_POINTS = 160
    }
}
