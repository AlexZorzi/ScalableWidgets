package com.owlynights.scalablecapitalwidgets

import android.app.Application
import android.content.Context
import com.owlynights.scalablecapitalwidgets.telemetry.Telemetry

class ScalableApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Telemetry.apply(this, AppGraph.get(this).settings.current)
    }
}
