package com.owlynights.scalablecapitalwidgets.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.owlynights.scalablecapitalwidgets.AppGraph
import com.owlynights.scalablecapitalwidgets.data.AppSettings
import com.owlynights.scalablecapitalwidgets.ui.TERMS_VERSION

/**
 * The only place that talks to Firebase. Nothing is collected before the user accepted the
 * introduction, or when they switched it off; without google-services.json it does nothing.
 *
 * Events describe how the app is used, never what is in the account: no portfolio values,
 * titles, ISINs, names or account IDs.
 */
object Telemetry {

    /** Applies the user's choices to Firebase; call at start-up and whenever they change. */
    fun apply(context: Context, settings: AppSettings) {
        if (!available(context)) return
        val consented = settings.acceptedTermsVersion >= TERMS_VERSION
        val analytics = consented && settings.analyticsEnabled
        val crashReports = consented && settings.crashReportsEnabled
        FirebaseAnalytics.getInstance(context).apply {
            setAnalyticsCollectionEnabled(analytics)
            // Opting out also drops the app-instance ID, so a later opt-in starts fresh.
            if (!analytics) resetAnalyticsData()
        }
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(crashReports)
            if (!crashReports) deleteUnsentReports()
        }
    }

    fun event(context: Context, name: String, vararg params: Pair<String, Any?>) {
        if (!analyticsAllowed(context)) return
        val bundle = Bundle()
        // Analytics accepts strings and numbers; anything else is sent as text.
        for ((key, value) in params) {
            when (value) {
                null -> Unit
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    fun screen(context: Context, name: String) =
        event(context, FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name)

    /**
     * One refresh: where it came from ("background", "widget_tap", "app"), how it ended and how
     * long it took, rounded to seconds.
     */
    fun refresh(context: Context, trigger: String, result: String, durationMs: Long) =
        event(context, "refresh", "trigger" to trigger, "result" to result, "seconds" to durationMs / 1000)

    /**
     * Classifies a failed refresh. Network trouble is expected on phones; anything else is
     * also sent to Crashlytics as a non-fatal so bugs show up.
     */
    fun refreshFailure(context: Context, error: Exception): String {
        if (error is java.io.IOException) return "network"
        nonFatal(context, error)
        return "error"
    }

    /** Reports an unexpected, handled error so it shows up in Crashlytics. */
    fun nonFatal(context: Context, error: Throwable) {
        val settings = AppGraph.get(context).settings.current
        if (!available(context) || !settings.crashReportsEnabled || settings.acceptedTermsVersion < TERMS_VERSION) return
        FirebaseCrashlytics.getInstance().recordException(error)
    }

    private fun analyticsAllowed(context: Context): Boolean {
        val settings = AppGraph.get(context).settings.current
        return available(context) && settings.analyticsEnabled && settings.acceptedTermsVersion >= TERMS_VERSION
    }

    private fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
