package com.owlynights.scalablecapitalwidgets.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlynights.scalablecapitalwidgets.R
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.data.PickedTitle
import com.owlynights.scalablecapitalwidgets.data.PortfolioSnapshot
import com.owlynights.scalablecapitalwidgets.data.Position
import com.owlynights.scalablecapitalwidgets.widget.Format

/** Charts are fetched for this many titles per list (see PortfolioRepository). */
private const val TITLES_WITH_CHARTS = 8

/** Pick exactly which titles "My list" shows: search anything, or tick holdings and watchlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomListScreen(
    settings: AppSettings,
    snapshot: PortfolioSnapshot?,
    search: SearchUi,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggle: (isin: String, name: String) -> Unit,
    onMove: (isin: String, delta: Int) -> Unit,
) {
    val picked = settings.customList
    val pickedIsins = picked.mapTo(HashSet()) { it.isin }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.custom_search_hint)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    trailingIcon = {
                        if (search.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (search.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }

            if (search.query.isNotBlank()) {
                header(R.string.custom_results)
                when {
                    search.error != null -> hint(R.string.custom_search_error, search.error)
                    !search.loading && search.query.trim().length >= 2 && search.results.isEmpty() ->
                        hint(R.string.custom_no_results)
                }
                items(search.results, key = { "search-${it.isin}" }) { position ->
                    PickRow(position.name, subtitle(position), position.isin in pickedIsins) {
                        onToggle(position.isin, position.name)
                    }
                }
                return@LazyColumn
            }

            header(R.string.custom_selected, count = picked.size)
            if (picked.isEmpty()) {
                hint(R.string.custom_empty)
            } else {
                if (picked.size > TITLES_WITH_CHARTS) {
                    hint(R.string.custom_charts_hint, TITLES_WITH_CHARTS)
                }
                items(picked, key = { "picked-${it.isin}" }) { title ->
                    val index = picked.indexOf(title)
                    PickedRow(
                        title = title,
                        price = snapshot?.custom?.firstOrNull { it.isin == title.isin }?.let(::subtitle),
                        canMoveUp = index > 0,
                        canMoveDown = index < picked.lastIndex,
                        onMove = { delta -> onMove(title.isin, delta) },
                        onRemove = { onToggle(title.isin, title.name) },
                    )
                }
            }

            val holdings = snapshot?.holdings.orEmpty()
            if (holdings.isNotEmpty()) {
                header(R.string.custom_from_holdings)
                items(holdings, key = { "holding-${it.isin}" }) { position ->
                    PickRow(position.name, subtitle(position), position.isin in pickedIsins) {
                        onToggle(position.isin, position.name)
                    }
                }
            }

            val watchlist = snapshot?.watchlist.orEmpty()
            if (watchlist.isNotEmpty()) {
                header(R.string.custom_from_watchlist)
                items(watchlist, key = { "watch-${it.isin}" }) { position ->
                    PickRow(position.name, subtitle(position), position.isin in pickedIsins) {
                        onToggle(position.isin, position.name)
                    }
                }
            }
        }
    }
}

private fun subtitle(position: Position): String =
    listOfNotNull(position.price?.let { Format.money(it, position.currency ?: "EUR") }, position.isin)
        .joinToString(" · ")

private fun LazyListScope.header(title: Int, count: Int? = null) {
    item {
        Text(
            text = stringResource(title) + (count?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.hint(text: Int, vararg args: Any) {
    item {
        Text(
            text = stringResource(text, *args),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PickRow(name: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
    )
}

@Composable
private fun PickedRow(
    title: PickedTitle,
    price: String?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(title.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(price ?: title.isin, maxLines = 1) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_move_up))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.action_move_down))
                }
                IconButton(onClick = onRemove) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_remove))
                }
            }
        },
    )
}
