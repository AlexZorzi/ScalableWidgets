package com.owlynights.scalablecapitalwidgets.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What tapping a portfolio widget does. */
enum class WidgetTap { OPEN_APP, REFRESH, OPEN_SCALABLE }

data class AppSettings(
    val listSource: ListSource = ListSource.HOLDINGS,
    val period: Period = Period.TODAY,
    val refreshMinutes: Int = 30,
    val accountId: String? = null,
    val portfolioId: String? = null,
    val portfolioIds: List<String> = emptyList(),
    val firstName: String? = null,
    val customList: List<PickedTitle> = emptyList(),
    val widgetTap: WidgetTap = WidgetTap.OPEN_APP,
    /** Version of the introduction terms the user accepted; 0 = never. */
    val acceptedTermsVersion: Int = 0,
    /** Anonymous usage statistics via Firebase Analytics; asked in the introduction. */
    val analyticsEnabled: Boolean = true,
    /** Crash reports via Firebase Crashlytics; asked in the introduction. */
    val crashReportsEnabled: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString("listSource", next.listSource.name)
            putString("period", next.period.name)
            putInt("refreshMinutes", next.refreshMinutes)
            putString("accountId", next.accountId)
            putString("portfolioId", next.portfolioId)
            putString("portfolioIds", next.portfolioIds.joinToString(","))
            putString("firstName", next.firstName)
            putString("widgetTap", next.widgetTap.name)
            putInt("acceptedTermsVersion", next.acceptedTermsVersion)
            putBoolean("analyticsEnabled", next.analyticsEnabled)
            putBoolean("crashReportsEnabled", next.crashReportsEnabled)
            putString("customList", JSONArray().apply {
                next.customList.forEach { put(JSONObject().put("isin", it.isin).put("name", it.name)) }
            }.toString())
        }
        _settings.value = next
    }

    /** Forgets account-specific values on logout, keeping display preferences and "My list". */
    fun clearAccount() = update {
        it.copy(accountId = null, portfolioId = null, portfolioIds = emptyList(), firstName = null)
    }

    private fun read() = AppSettings(
        listSource = enumOrDefault(prefs.getString("listSource", null), ListSource.HOLDINGS),
        period = enumOrDefault(prefs.getString("period", null), Period.TODAY),
        refreshMinutes = prefs.getInt("refreshMinutes", 30),
        accountId = prefs.getString("accountId", null),
        portfolioId = prefs.getString("portfolioId", null),
        portfolioIds = prefs.getString("portfolioIds", null)
            ?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        firstName = prefs.getString("firstName", null),
        widgetTap = enumOrDefault(prefs.getString("widgetTap", null), WidgetTap.OPEN_APP),
        acceptedTermsVersion = prefs.getInt("acceptedTermsVersion", 0),
        analyticsEnabled = prefs.getBoolean("analyticsEnabled", true),
        crashReportsEnabled = prefs.getBoolean("crashReportsEnabled", true),
        customList = prefs.getString("customList", null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() }
            .mapObjects { PickedTitle(it.getString("isin"), it.optString("name")) },
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
