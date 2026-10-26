package com.owlynights.scalablecapitalwidgets.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owlynights.scalablecapitalwidgets.AppGraph
import com.owlynights.scalablecapitalwidgets.AppLanguage
import com.owlynights.scalablecapitalwidgets.api.RateLimitedException
import com.owlynights.scalablecapitalwidgets.api.ReloginRequiredException
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.data.ListSource
import com.owlynights.scalablecapitalwidgets.data.LoginStage
import com.owlynights.scalablecapitalwidgets.data.Period
import com.owlynights.scalablecapitalwidgets.data.PickedTitle
import com.owlynights.scalablecapitalwidgets.data.PortfolioSnapshot
import com.owlynights.scalablecapitalwidgets.data.Position
import com.owlynights.scalablecapitalwidgets.data.WidgetTap
import com.owlynights.scalablecapitalwidgets.telemetry.Telemetry
import com.owlynights.scalablecapitalwidgets.widget.WidgetTicker
import com.owlynights.scalablecapitalwidgets.widget.WidgetUpdater
import com.owlynights.scalablecapitalwidgets.work.RefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginUi {
    data object Idle : LoginUi
    data object Starting : LoginUi
    data class AwaitingBrowser(val userCode: String, val url: String) : LoginUi
    data object AwaitingSecondFactor : LoginUi
    data object LoadingPortfolio : LoginUi
    data class Failed(val message: String) : LoginUi
}

data class SearchUi(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Position> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.get(application)

    val auth: StateFlow<AuthState> = graph.sessions.state
    val snapshot: StateFlow<PortfolioSnapshot?> = graph.snapshots.snapshot
    val settings: StateFlow<AppSettings> = graph.settings.settings

    private val _login = MutableStateFlow<LoginUi>(LoginUi.Idle)
    val login: StateFlow<LoginUi> = _login.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search.asStateFlow()

    private var loginJob: Job? = null
    private var searchJob: Job? = null
    private var customListChanged = false

    init {
        viewModelScope.launch { runCatching { WidgetTicker.sync(application) } }
        if (auth.value is AuthState.SignedIn) {
            RefreshWorker.schedule(application, settings.value.refreshMinutes)
            val age = System.currentTimeMillis() - (snapshot.value?.fetchedAt ?: 0)
            if (age > 2 * 60_000) refresh()
        }
    }

    fun startLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _login.value = LoginUi.Starting
            Telemetry.event(getApplication(), "login_step", "step" to "started")
            try {
                val pending = graph.sessions.beginLogin()
                _login.value = LoginUi.AwaitingBrowser(pending.deviceCode.userCode, pending.loginUrl)
                graph.sessions.completeLogin(pending) { stage ->
                    _login.value = when (stage) {
                        LoginStage.WaitingForBrowser -> LoginUi.AwaitingBrowser(pending.deviceCode.userCode, pending.loginUrl)
                        LoginStage.WaitingForSecondFactor -> {
                            Telemetry.event(getApplication(), "login_step", "step" to "second_factor")
                            LoginUi.AwaitingSecondFactor
                        }
                        LoginStage.LoadingPortfolio -> LoginUi.LoadingPortfolio
                    }
                }
                Telemetry.event(getApplication(), "login_step", "step" to "success")
                graph.settings.clearAccount()
                runCatching { graph.repository.loadFirstName() }
                refreshNow()
                RefreshWorker.schedule(getApplication(), settings.value.refreshMinutes)
                _login.value = LoginUi.Idle
            } catch (e: CancellationException) {
                _login.value = LoginUi.Idle
                throw e
            } catch (e: Exception) {
                // Only the error type: messages can contain server details.
                Telemetry.event(getApplication(), "login_step", "step" to "failed", "error" to e.javaClass.simpleName)
                _login.value = LoginUi.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _login.value = LoginUi.Idle
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        if (_refreshing.value) return
        _refreshing.value = true
        _error.value = null
        val started = System.currentTimeMillis()
        var result = "success"
        try {
            graph.repository.refresh { WidgetUpdater.updateAll(getApplication()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReloginRequiredException) {
            result = "relogin_required"
            _error.value = null
        } catch (e: RateLimitedException) {
            result = "rate_limited"
            _error.value = e.message
        } catch (e: Exception) {
            result = Telemetry.refreshFailure(getApplication(), e)
            _error.value = e.message ?: e.javaClass.simpleName
        } finally {
            Telemetry.refresh(getApplication(), "app", result, System.currentTimeMillis() - started)
            _refreshing.value = false
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun logout() {
        viewModelScope.launch {
            RefreshWorker.cancel(getApplication())
            graph.sessions.logout()
            graph.settings.clearAccount()
            graph.snapshots.clear()
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun setListSource(source: ListSource) {
        if (source == settings.value.listSource) return
        graph.settings.update { it.copy(listSource = source) }
        Telemetry.event(getApplication(), "setting_changed", "setting" to "list_source", "value" to source.name.lowercase())
        refresh()
    }

    /** Searches securities for "My list", debounced while the user types. */
    fun searchTitles(query: String) {
        _search.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        val term = query.trim()
        if (term.length < 2) {
            _search.update { it.copy(loading = false, results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _search.update { it.copy(loading = true) }
            try {
                val results = graph.repository.search(term)
                _search.update { it.copy(loading = false, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(loading = false, results = emptyList(), error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun toggleCustomTitle(isin: String, name: String) {
        graph.settings.update { settings ->
            val list = settings.customList
            settings.copy(
                customList = if (list.any { it.isin == isin }) list.filterNot { it.isin == isin }
                else list + PickedTitle(isin, name)
            )
        }
        customListChanged = true
    }

    fun moveCustomTitle(isin: String, delta: Int) {
        graph.settings.update { settings ->
            val list = settings.customList.toMutableList()
            val from = list.indexOfFirst { it.isin == isin }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@update settings
            list.add(to, list.removeAt(from))
            settings.copy(customList = list)
        }
        customListChanged = true
    }

    /** Leaving the editor: show the list and fetch prices and charts for new picks. */
    fun finishCustomListEditing() {
        searchJob?.cancel()
        _search.value = SearchUi()
        val switchToCustom = settings.value.listSource != ListSource.CUSTOM && settings.value.customList.isNotEmpty()
        if (switchToCustom) graph.settings.update { it.copy(listSource = ListSource.CUSTOM) }
        if (customListChanged || switchToCustom) {
            // How many titles people pick, never which ones.
            Telemetry.event(getApplication(), "custom_list_saved", "count" to settings.value.customList.size.toLong())
            refresh()
        }
        customListChanged = false
    }

    fun setPeriod(period: Period) {
        graph.settings.update { it.copy(period = period) }
        Telemetry.event(getApplication(), "setting_changed", "setting" to "period", "value" to period.name.lowercase())
        viewModelScope.launch { WidgetUpdater.updateAll(getApplication()) }
    }

    fun acceptTerms(analytics: Boolean, crashReports: Boolean) {
        graph.settings.update {
            it.copy(acceptedTermsVersion = TERMS_VERSION, analyticsEnabled = analytics, crashReportsEnabled = crashReports)
        }
        Telemetry.apply(getApplication(), settings.value)
        Telemetry.event(getApplication(), "intro_accepted", "crash_reports" to crashReports)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        graph.settings.update { it.copy(analyticsEnabled = enabled) }
        Telemetry.apply(getApplication(), settings.value)
    }

    fun setCrashReportsEnabled(enabled: Boolean) {
        graph.settings.update { it.copy(crashReportsEnabled = enabled) }
        Telemetry.apply(getApplication(), settings.value)
    }

    fun setWidgetTap(tap: WidgetTap) {
        graph.settings.update { it.copy(widgetTap = tap) }
        Telemetry.event(getApplication(), "setting_changed", "setting" to "widget_tap", "value" to tap.name.lowercase())
        viewModelScope.launch { WidgetUpdater.updateAll(getApplication()) }
    }

    private val _language = MutableStateFlow(graph.language.value)
    val language: StateFlow<String> = _language.asStateFlow()

    /**
     * Android 13+ recreates the activity in the new language by itself; older versions need
     * [onRecreate]. Widgets are redrawn so their labels switch too.
     */
    fun setLanguage(tag: String, onRecreate: () -> Unit) {
        if (tag == _language.value) return
        _language.value = tag
        AppLanguage.set(getApplication(), tag)
        Telemetry.event(getApplication(), "setting_changed", "setting" to "language", "value" to tag.ifEmpty { "system" })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onRecreate()
        viewModelScope.launch {
            // Give the new configuration a moment to reach the app's resources.
            delay(500)
            graph.language.value = tag
            WidgetUpdater.updateAll(getApplication())
        }
    }

    fun setRefreshMinutes(minutes: Int) {
        graph.settings.update { it.copy(refreshMinutes = minutes) }
        Telemetry.event(getApplication(), "setting_changed", "setting" to "refresh_minutes", "value" to minutes.toLong())
        RefreshWorker.schedule(getApplication(), minutes)
    }

    fun selectPortfolio(portfolioId: String) {
        if (portfolioId == settings.value.portfolioId) return
        graph.settings.update { it.copy(portfolioId = portfolioId) }
        refresh()
    }
}
