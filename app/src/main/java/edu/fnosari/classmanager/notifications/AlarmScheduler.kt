package edu.fnosari.classmanager.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.Reminder
import edu.fnosari.classmanager.data.ReminderType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class AlarmScheduler(
    private val context: Context,
    private val container: AppContainer,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    private fun reminderPending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setExactCompat(triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleReminder(r: Reminder) {
        if (r.done || r.type == ReminderType.MORNING_DIGEST) return // digest items fire via daily digest
        val fireAt = when (r.type) {
            ReminderType.NEXT_LESSON -> r.dueAt - 5 * 60_000L // 5 min before slot start
            else -> r.dueAt
        }
        if (fireAt > System.currentTimeMillis()) setExactCompat(fireAt, reminderPending(r.id))
    }

    fun cancelReminder(id: Long) = am.cancel(reminderPending(id))

    suspend fun scheduleDailyDigest() {
        val t = LocalTime.parse(container.settings.digestTime.first())
        var next = LocalDate.now().atTime(t)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val pi = PendingIntent.getBroadcast(
            context, -1,
            Intent(context, DigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExactCompat(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    suspend fun rescheduleAll() {
        container.db.reminderDao().allPending().forEach { scheduleReminder(it) }
        scheduleDailyDigest()
    }
}
