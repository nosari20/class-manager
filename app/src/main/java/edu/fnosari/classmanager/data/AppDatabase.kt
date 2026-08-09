package edu.fnosari.classmanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SchoolClass::class, TimetableSlot::class, Student::class, Note::class,
        CustomField::class, Reminder::class, SeparationConstraint::class,
        Grouping::class, GroupingGroup::class, GroupingMember::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun timetableDao(): TimetableDao
    abstract fun noteDao(): NoteDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun reminderDao(): ReminderDao
    abstract fun groupDao(): GroupDao

    companion object {
        const val DB_NAME = "classmanager.db"
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).build()
    }
}
