package com.owlynights.scalablecapitalwidgets

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.ui.AboutScreen
import com.owlynights.scalablecapitalwidgets.ui.CustomListScreen
import com.owlynights.scalablecapitalwidgets.ui.HomeScreen
import com.owlynights.scalablecapitalwidgets.ui.LoginScreen
import com.owlynights.scalablecapitalwidgets.ui.LoginUi
import com.owlynights.scalablecapitalwidgets.ui.MainViewModel
import com.owlynights.scalablecapitalwidgets.ui.OnboardingScreen
import com.owlynights.scalablecapitalwidgets.ui.TERMS_VERSION
import com.owlynights.scalablecapitalwidgets.ui.SettingsScreen
import com.owlynights.scalablecapitalwidgets.ui.publishWidgetPreviews
import com.owlynights.scalablecapitalwidgets.telemetry.Telemetry
import com.owlynights.scalablecapitalwidgets.ui.theme.ScalableCapitalWidgetsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { runCatching { publishWidgetPreviews(applicationContext) } }
        setContent {
            ScalableCapitalWidgetsTheme {
                App(viewModel, onRecreate = ::recreate)
            }
        }
    }
}

@Composable
private fun App(viewModel: MainViewModel, onRecreate: () -> Unit) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCustomList by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val search by viewModel.search.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    val screen = when {
        settings.acceptedTermsVersion < TERMS_VERSION -> "intro"
        auth !is AuthState.SignedIn || login !is LoginUi.Idle -> "login"
        showCustomList -> "custom_list"
        showAbout -> "about"
        showSettings -> "settings"
        else -> "home"
    }
    val context = LocalContext.current
    LaunchedEffect(screen) { Telemetry.screen(context, screen) }

    if (settings.acceptedTermsVersion < TERMS_VERSION) {
        OnboardingScreen(
            initialAnalytics = settings.analyticsEnabled,
            initialCrashReports = settings.crashReportsEnabled,
            onAccept = viewModel::acceptTerms,
        )
        return
    }

    val state = auth
    if (state !is AuthState.SignedIn || login !is LoginUi.Idle) {
        LoginScreen(
            login = login,
            expired = state is AuthState.Expired,
            onConnect = viewModel::startLogin,
            onCancel = viewModel::cancelLogin,
        )
    } else if (showCustomList) {
        val close = {
            showCustomList = false
            viewModel.finishCustomListEditing()
        }
        BackHandler(onBack = close)
        CustomListScreen(
            settings = settings,
            snapshot = snapshot,
            search = search,
            onBack = close,
            onQueryChange = viewModel::searchTitles,
            onToggle = viewModel::toggleCustomTitle,
            onMove = viewModel::moveCustomTitle,
        )
    } else if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
    } else if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            settings = settings,
            loginAt = state.loginAt,
            onBack = { showSettings = false },
            onSourceChange = viewModel::setListSource,
            onPeriodChange = viewModel::setPeriod,
            onRefreshMinutesChange = viewModel::setRefreshMinutes,
            onPortfolioChange = viewModel::selectPortfolio,
            onEditCustomList = { showCustomList = true },
            onWidgetTapChange = viewModel::setWidgetTap,
            language = language,
            onLanguageChange = { viewModel.setLanguage(it, onRecreate) },
            onOpenAbout = { showAbout = true },
            onAnalyticsChange = viewModel::setAnalyticsEnabled,
            onCrashReportsChange = viewModel::setCrashReportsEnabled,
            onLogout = {
                showSettings = false
                viewModel.logout()
            },
        )
    } else {
        HomeScreen(
            snapshot = snapshot,
            settings = settings,
            loginAt = state.loginAt,
            refreshing = refreshing,
            error = error,
            onRefresh = viewModel::refresh,
            onDismissError = viewModel::dismissError,
            onOpenSettings = { showSettings = true },
            onPeriodChange = viewModel::setPeriod,
            onSourceChange = viewModel::setListSource,
            onRelogin = viewModel::startLogin,
            onEditCustomList = { showCustomList = true },
        )
    }
}
