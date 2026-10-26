package com.owlynights.scalablecapitalwidgets.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.owlynights.scalablecapitalwidgets.AppGraph
import com.owlynights.scalablecapitalwidgets.api.RateLimitedException
import com.owlynights.scalablecapitalwidgets.api.ReloginRequiredException
import com.owlynights.scalablecapitalwidgets.data.AuthState
import com.owlynights.scalablecapitalwidgets.telemetry.Telemetry
import com.owlynights.scalablecapitalwidgets.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Refreshes the snapshot and redraws all widgets. Regular reads also keep the Scalable
 * session alive (it ends after 24 hours without requests).
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        if (graph.sessions.state.value !is AuthState.SignedIn) {
            WidgetUpdater.updateAll(applicationContext)
            return Result.success()
        }
        val trigger = inputData.getString(KEY_TRIGGER) ?: "background"
        val started = System.currentTimeMillis()
        var outcome = "success"
        return try {
            graph.repository.refresh { WidgetUpdater.updateAll(applicationContext) }
            graph.clock.value = System.currentTimeMillis()
            Result.success()
        } catch (e: ReloginRequiredException) {
            outcome = "relogin_required"
            Result.success()
        } catch (e: RateLimitedException) {
            outcome = "rate_limited"
            Result.retry()
        } catch (e: Exception) {
            outcome = Telemetry.refreshFailure(applicationContext, e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            Telemetry.refresh(applicationContext, trigger, outcome, System.currentTimeMillis() - started)
            WidgetUpdater.updateAll(applicationContext)
        }
    }

    companion object {
        private const val PERIODIC = "portfolio-refresh"
        private const val ONE_TIME = "portfolio-refresh-now"
        private const val KEY_TRIGGER = "trigger"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context, minutes: Int) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(
                minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
            ).setConstraints(networkConstraint).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_TRIGGER to "widget_tap"))
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(ONE_TIME)
            }
        }
    }
}
