package com.bahikhata.app.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bahikhata.app.data.Repo
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * v1.0.44 — automatic SMS reminders to parties whose due date has passed, daily or weekly at
 * the hour chosen in Settings. OFF by default. Works offline (SMS goes from the SIM).
 * A party is never reminded twice within 20 hours, and at most 50 SMS go per run.
 */
object AutoRemind {
    private const val WORK = "auto_sms_reminder"

    /** Call after the settings change and on app start. */
    fun sync(ctx: Context) {
        try {
            val wm = WorkManager.getInstance(ctx.applicationContext)
            if (!S.autoRem) { wm.cancelUniqueWork(WORK); return }
            val days = if (S.autoRemFreq == "weekly") 7L else 1L
            val req = PeriodicWorkRequestBuilder<AutoRemindWorker>(days, TimeUnit.DAYS)
                .setInitialDelay(delayToNext(S.autoRemHour, days == 7L), TimeUnit.MILLISECONDS)
                .addTag(WORK)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        } catch (e: Throwable) { }
    }

    private fun delayToNext(hour: Int, weekly: Boolean): Long {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (!t.after(now)) t.add(Calendar.DAY_OF_YEAR, 1)
        return (t.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    /** People the automatic run would message right now. */
    fun targets(): List<com.bahikhata.app.data.Party> {
        val overdue = Repo.overdueParties()
        val last = ReminderLog.lastAll()
        val cutoff = System.currentTimeMillis() - 20L * 3_600_000L
        return Repo.parties().filter { p ->
            p.balance > 0.005 && p.phone.isNotBlank() && p.id in overdue && (last[p.id] ?: 0L) < cutoff
        }.take(50)
    }
}

class AutoRemindWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext
            Repo.init(app)
            if (!S.autoRem || !S.smsDirect || !SmsSender.hasPermission(app)) return Result.success()
            val shop = Repo.businessName()
            var sent = 0
            var fails = 0
            for ((i, p) in AutoRemind.targets().withIndex()) {
                if (i > 0) delay(2_000)
                val r = SmsSender.send(app, p.phone, SmsTexts.reminder(p, shop))
                if (r is SmsSender.Result.Failed) { fails++; if (fails >= 3 && sent == 0) break }
                else { sent++; ReminderLog.add(p.id, "Auto SMS") }
            }
            Repo.setSetting("auto_rem_last", "${System.currentTimeMillis()}:$sent:$fails")
            Result.success()
        } catch (e: Throwable) {
            Result.success()
        }
    }
}
