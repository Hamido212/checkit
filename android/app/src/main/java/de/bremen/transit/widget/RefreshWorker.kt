package de.bremen.transit.widget

import android.content.Context
import androidx.work.*
import androidx.glance.appwidget.updateAll
import de.bremen.transit.data.TransitDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = TransitDisplay.repository(applicationContext)
        val success = runCatching { repository.refresh(repository.selectedStop()) }.isSuccess
        BremenDepartureWidget().updateAll(applicationContext)
        if (success) Result.success() else Result.retry()
    }
    companion object {
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork("checkit-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        fun refresh(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("checkit-refresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RefreshWorker>().build())
        }
    }
}
