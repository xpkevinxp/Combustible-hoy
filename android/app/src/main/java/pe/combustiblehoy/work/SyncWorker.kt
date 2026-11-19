package pe.combustiblehoy.work

import android.content.Context
import android.util.Log
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
import pe.combustiblehoy.app
import pe.combustiblehoy.widget.FavoritesWidget
import java.util.concurrent.TimeUnit

/**
 * Descarga los precios del día, actualiza favoritos y el widget, y notifica cambios.
 * Corre cada 6 horas (barato: si la fecha de datos no cambió solo baja meta.json).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val force = inputData.getBoolean(KEY_FORCE, false)
            val result = ctx.app.repository.sync(force)
            if (result.updated) {
                val s = ctx.app.settings.current()
                if (s.alertsEnabled) Notifications.showPriceChanges(ctx, result.changes)
            }
            FavoritesWidget.refreshAll(ctx)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Sync falló", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC = "price-sync"
        private const val ONE_SHOT = "price-sync-now"
        private const val KEY_FORCE = "force"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context, force: Boolean = true) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FORCE to force))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
