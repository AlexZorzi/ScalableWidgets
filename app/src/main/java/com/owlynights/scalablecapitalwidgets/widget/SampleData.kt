package com.owlynights.scalablecapitalwidgets.widget

import com.owlynights.scalablecapitalwidgets.data.ChartPoint
import com.owlynights.scalablecapitalwidgets.data.ChartSeries
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.PortfolioSnapshot
import com.owlynights.scalablecapitalwidgets.data.Position
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** Demo portfolio used for widget-picker previews (values from Scalable's iOS widget ad). */
object SampleData {

    fun snapshot(now: Long = System.currentTimeMillis()): PortfolioSnapshot {
        val positions = listOf(
            sample("IE00B4L5Y983", "iShares Core MSCI World UCITS ETF", 77.97, 0.0194, 1),
            sample("IE0032077012", "Invesco EQQQ NASDAQ-100 UCITS ETF", 346.45, 0.0247, 2),
            sample("IE00BM67HT60", "Xtrackers MSCI World Information Technology", 58.15, 0.0230, 3),
            sample("US0378331005", "Apple Inc", 131.63, 0.0377, 4),
            sample("DE0008404005", "Allianz SE NA", 221.93, 0.0681, 5),
        )
        val total = 24_580.34
        val todayReturn = 435.10
        return PortfolioSnapshot(
            fetchedAt = now - 230_000,
            currency = "EUR",
            totalValue = total,
            securitiesValue = total,
            cryptoValue = 0.0,
            returns = mapOf("ONE_DAY" to todayReturn),
            charts = mapOf(Period.TODAY.chartKey to intraday(total, todayReturn / (total - todayReturn), 9)),
            holdings = positions,
            watchlist = positions,
        )
    }

    private fun sample(isin: String, name: String, price: Double, change: Double, seed: Int) = Position(
        isin = isin,
        name = name,
        type = "ETF",
        price = price,
        currency = "EUR",
        quantity = 10.0,
        valuation = price * 10,
        outdated = false,
        series = intraday(price, change, seed),
    )

    /** Random walk over yesterday and today that ends [change] above the previous close. */
    private fun intraday(last: Double, change: Double, seed: Int): ChartSeries {
        val random = Random(seed)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val close = last / (1 + change)
        fun time(day: LocalDate, minute: Int) =
            day.atTime(LocalTime.of(8, 0)).plusMinutes(minute.toLong()).atZone(zone).toInstant().toEpochMilli()

        val steps = 40
        val yesterday = walk(random, steps, close * (1 - 0.008 * random.nextDouble()), close, close * 0.004)
            .mapIndexed { i, v -> ChartPoint(time(today.minusDays(1), i * 20), v) }
        val todayPoints = walk(random, steps, close, last, close * 0.006)
            .mapIndexed { i, v -> ChartPoint(time(today, i * 15), v) }

        return ChartSeries(
            reference = close,
            referenceTime = yesterday.last().time,
            points = yesterday + todayPoints,
        )
    }

    /** Random walk from [start] that is bent to land exactly on [end]. */
    private fun walk(random: Random, steps: Int, start: Double, end: Double, noise: Double): List<Double> {
        var value = start
        val raw = List(steps) {
            value += noise * (random.nextDouble() - 0.5)
            value
        }
        val correction = end - raw.last()
        return raw.mapIndexed { i, v -> v + correction * (i + 1) / steps }
    }
}
