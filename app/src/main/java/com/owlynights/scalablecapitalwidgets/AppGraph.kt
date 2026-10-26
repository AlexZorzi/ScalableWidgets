package com.owlynights.scalablecapitalwidgets

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import com.owlynights.scalablecapitalwidgets.data.PortfolioRepository
import com.owlynights.scalablecapitalwidgets.data.SessionManager
import com.owlynights.scalablecapitalwidgets.data.SettingsStore
import com.owlynights.scalablecapitalwidgets.data.SnapshotStore

/** Process-wide singletons shared by the app UI, the widgets and the refresh worker. */
class AppGraph private constructor(context: Context) {
    val sessions = SessionManager(context)
    val settings = SettingsStore(context)
    val snapshots = SnapshotStore(context)
    val repository = PortfolioRepository(sessions, settings, snapshots)

    /** "Now" for the widgets' "5 min ago" labels; advanced once a minute by WidgetTicker. */
    val clock = MutableStateFlow(System.currentTimeMillis())

    /** The app language; widgets read it so a change redraws all their text. */
    val language = MutableStateFlow(AppLanguage.current(context))

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
