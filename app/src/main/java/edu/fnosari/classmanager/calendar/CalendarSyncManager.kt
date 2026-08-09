package edu.fnosari.classmanager.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.domain.occurrencesOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Pushes the timetable (recurring slots, minus cancellations, plus one-offs) into a
 * dedicated local device calendar. Re-running replaces all previously synced events.
 * Caller must hold READ_CALENDAR and WRITE_CALENDAR.
 */
class CalendarSyncManager(private val context: Context, private val container: AppContainer) {

    suspend fun sync(weeksAhead: Long = 8): Int = withContext(Dispatchers.IO) {
        val calId = findOrCreateCalendar()
        clearEvents(calId)

        val db = container.db
        val classes = db.classDao().all().first().associateBy { it.id }
        val slots = db.timetableDao().allSlots().first()
        val cancellations = db.timetableDao().allCancellations().first()
        val oneOffs = db.timetableDao().allOneOffs().first()
        val rooms = db.seatingDao().rooms().first().associate { it.id to it.name }
        val weekARef = container.settings.weekARef.first()?.let(LocalDate::parse)

        val zone = ZoneId.systemDefault()
        var count = 0
        val today = LocalDate.now()
        for (offset in 0 until weeksAhead * 7) {
            val date = today.plusDays(offset)
            for (occ in occurrencesOn(date, slots, cancellations, oneOffs, weekARef)) {
                val cls = classes[occ.classId] ?: continue
                val start = LocalDateTime.of(date, LocalTime.parse(occ.startTime))
                    .atZone(zone).toInstant().toEpochMilli()
                val end = LocalDateTime.of(date, LocalTime.parse(occ.endTime))
                    .atZone(zone).toInstant().toEpochMilli()
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calId)
                    put(CalendarContract.Events.TITLE, cls.name)
                    put(CalendarContract.Events.DESCRIPTION, cls.level)
                    put(CalendarContract.Events.EVENT_LOCATION, occ.roomId?.let { rooms[it] } ?: "")
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, end)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                }
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                count++
            }
        }
        count
    }

    private fun findOrCreateCalendar(): Long {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF16866F.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val inserted = context.contentResolver.insert(uri, values)
            ?: error("cannot create calendar")
        return ContentUris.parseId(inserted)
    }

    private fun clearEvents(calId: Long) {
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CALENDAR_ID} = ?",
            arrayOf(calId.toString()),
        )
    }

    companion object {
        const val ACCOUNT_NAME = "MaClasse"
        const val CALENDAR_NAME = "MaClasse"
    }
}
