package edu.fnosari.classmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import edu.fnosari.classmanager.appContainer
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = context.appContainer.db
                when (intent.action) {
                    ACTION_MARK_DONE -> {
                        db.reminderDao().markDone(id)
                        NotificationManagerCompat.from(context).cancel(id.toInt())
                    }
                    ACTION_FIRE -> {
                        val r = db.reminderDao().byId(id) ?: return@launch
                        if (r.done) return@launch
                        val s = db.studentDao().byId(r.studentId) ?: return@launch
                        NotificationHelper.showReminder(context, r, s)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "edu.fnosari.classmanager.REMINDER_FIRE"
        const val ACTION_MARK_DONE = "edu.fnosari.classmanager.REMINDER_DONE"
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val zone = ZoneId.systemDefault()
                val dayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val due = container.db.reminderDao().dueBetween(dayStart, dayEnd)
                val lines = due.mapNotNull { r ->
                    container.db.studentDao().byId(r.studentId)
                        ?.let { "${it.firstName} ${it.lastName}: ${r.text}" }
                }
                NotificationHelper.showDigest(context, lines)
                container.alarms.scheduleDailyDigest() // chain next day
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.appContainer.alarms.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
