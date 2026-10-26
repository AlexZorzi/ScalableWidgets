package com.owlynights.scalablecapitalwidgets.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Last fetched portfolio data, shared by the app and the widgets. */
class SnapshotStore(context: Context) {
    private val file = File(context.filesDir, "snapshot.json")
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<PortfolioSnapshot?> = _snapshot.asStateFlow()

    /** Re-reads the file; widgets run in short-lived processes where it may have changed. */
    fun load(): PortfolioSnapshot? = read().also { _snapshot.value = it }

    fun save(snapshot: PortfolioSnapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(snapshot.toJson())
        tmp.renameTo(file)
        _snapshot.value = snapshot
    }

    fun clear() {
        file.delete()
        _snapshot.value = null
    }

    private fun read(): PortfolioSnapshot? =
        file.takeIf { it.exists() }?.readText()?.let(PortfolioSnapshot::fromJson)
}
