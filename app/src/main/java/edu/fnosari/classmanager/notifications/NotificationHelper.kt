package edu.fnosari.classmanager.notifications

import android.Manifest
import android.annotation.SuppressLint
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
import edu.fnosari.classmanager.MainActivity
import edu.fnosari.classmanager.R
import edu.fnosari.classmanager.data.Reminder
import edu.fnosari.classmanager.data.Student

object NotificationHelper {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DIGEST = "digest"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST,
                context.getString(R.string.channel_digest),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private fun contentIntent(context: Context, studentId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, studentId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_STUDENT_ID, studentId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @SuppressLint("MissingPermission") // hasPermission() checks POST_NOTIFICATIONS
    fun showReminder(context: Context, reminder: Reminder, student: Student) {
        if (!hasPermission(context)) return
        val doneIntent = PendingIntent.getBroadcast(
            context, reminder.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_MARK_DONE)
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${student.firstName} ${student.lastName}")
            .setContentText(reminder.text)
            .setContentIntent(contentIntent(context, student.id))
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.mark_done), doneIntent)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), n)
    }

    @SuppressLint("MissingPermission") // hasPermission() checks POST_NOTIFICATIONS
    fun showDigest(context: Context, lines: List<String>) {
        if (!hasPermission(context) || lines.isEmpty()) return
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.digest_title, lines.size))
            .setStyle(style)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(-1, n)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
