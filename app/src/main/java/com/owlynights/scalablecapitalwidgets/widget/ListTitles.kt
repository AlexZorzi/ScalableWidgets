package com.owlynights.scalablecapitalwidgets.widget

import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.data.ListSource

fun listTitle(source: ListSource): Int = when (source) {
    ListSource.HOLDINGS -> R.string.widget_title_holdings
    ListSource.WATCHLIST -> R.string.widget_title_watchlist
    ListSource.CUSTOM -> R.string.widget_title_custom
}

/** Shown when the selected list has nothing in it. */
fun emptyListText(source: ListSource): Int = when (source) {
    ListSource.CUSTOM -> R.string.widget_custom_empty
    else -> R.string.widget_no_positions
}
