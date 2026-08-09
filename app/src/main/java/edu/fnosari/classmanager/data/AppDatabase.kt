package edu.fnosari.classmanager.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SchoolClass::class, TimetableSlot::class, Student::class, Note::class,
        CustomField::class, Reminder::class, SeparationConstraint::class,
        Grouping::class, GroupingGroup::class, GroupingMember::class,
        Room::class, Desk::class, SeatingPlan::class, SeatAssignment::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun timetableDao(): TimetableDao
    abstract fun noteDao(): NoteDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun reminderDao(): ReminderDao
    abstract fun groupDao(): GroupDao
    abstract fun seatingDao(): SeatingDao

    companion object {
        const val DB_NAME = "classmanager.db"
        fun build(context: Context): AppDatabase =
            androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).build()
    }
}
