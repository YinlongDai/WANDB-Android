package io.github.yinlongdai.runwatch.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.yinlongdai.runwatch.MainActivity
import io.github.yinlongdai.runwatch.R
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * Every ~15 minutes (Android's minimum for periodic work), checks the recent
 * runs of starred projects and notifies when a run that was running or pending
 * has crashed or failed (optionally also when it finished). See [AlertLogic].
 */
class RunAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.alertsEnabled) return Result.success()
        val api = prefs.api() ?: return Result.success()
        val previous = prefs.knownStates
        val next = mutableMapOf<String, String>()

        for (path in prefs.starred) {
            val entity = path.substringBefore('/')
            val project = path.substringAfter('/')
            val page = try {
                api.runs(entity, project, perPage = 50)
            } catch (e: ApiException) {
                if (e.isAuthError) return Result.success()
                // Keep the old states for this project so a transient error doesn't cause missed alerts.
                previous.filterKeys { it == path || it.startsWith("$path/") }.let(next::putAll)
                continue
            }
            // Runs that were active last time but have scrolled past the newest page.
            val runs = page.runs.toMutableList()
            val missing = AlertLogic.missingActiveRuns(previous, path, page.runs)
            // Over the cap: keep their old state and look them up on a later check.
            val unresolved = missing.drop(MAX_EXTRA_FETCHES).toMutableList()
            for (name in missing.take(MAX_EXTRA_FETCHES)) {
                try {
                    runs += api.runState(entity, project, name)
                } catch (e: ApiException) {
                    if (e.isAuthError) return Result.success()
                    if (!e.isNotFound) unresolved += name // deleted runs are dropped
                }
            }
            val outcome = AlertLogic.evaluate(previous, path, runs, prefs.notifyOnFinish, unresolved)
            next.putAll(outcome.states)
            for (alert in outcome.alerts) {
                val title = when (alert.kind) {
                    AlertLogic.Kind.FAILED -> "Run ${alert.run.state}: ${alert.run.displayName}"
                    AlertLogic.Kind.FINISHED -> "Run finished: ${alert.run.displayName}"
                }
                notify(alert.key, title, path)
            }
        }
        prefs.knownStates = next
        return Result.success()
    }

    private fun notify(key: String, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_RUN_PATH, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            ctx, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(key.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "run_alerts"
        private const val WORK_NAME = "run_alerts"

        /** Cap on per-run lookups for active runs past the newest page, per project per check. */
        private const val MAX_EXTRA_FETCHES = 20

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Run alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Runs in starred projects that crash, fail or finish"
                    }
                )
            }
        }

        /** Schedules or cancels the periodic check to match the current settings. */
        fun sync(context: Context) {
            val prefs = Prefs(context)
            val wm = WorkManager.getInstance(context)
            if (!prefs.alertsEnabled || prefs.apiKey == null || prefs.starred.isEmpty()) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RunAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
