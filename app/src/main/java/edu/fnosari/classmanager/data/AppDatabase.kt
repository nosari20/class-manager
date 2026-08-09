package edu.fnosari.classmanager.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SchoolClass::class, TimetableSlot::class, Student::class, Note::class,
        CustomField::class, Reminder::class, SeparationConstraint::class,
        Grouping::class, GroupingGroup::class, GroupingMember::class,
        Room::class, Desk::class, SeatingPlan::class, SeatAssignment::class,
        SlotCancellation::class, OneOffSlot::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6)],
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

        // v3: desk.seats column; seat_assignment gains seatIndex in the primary key
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE desk ADD COLUMN seats INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "CREATE TABLE seat_assignment_new (" +
                        "planId INTEGER NOT NULL, deskId INTEGER NOT NULL, " +
                        "seatIndex INTEGER NOT NULL DEFAULT 0, studentId INTEGER NOT NULL, " +
                        "PRIMARY KEY(planId, deskId, seatIndex), " +
                        "FOREIGN KEY(planId) REFERENCES seating_plan(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(deskId) REFERENCES desk(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(studentId) REFERENCES student(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO seat_assignment_new (planId, deskId, seatIndex, studentId) " +
                        "SELECT planId, deskId, 0, studentId FROM seat_assignment"
                )
                db.execSQL("DROP TABLE seat_assignment")
                db.execSQL("ALTER TABLE seat_assignment_new RENAME TO seat_assignment")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seat_assignment_deskId ON seat_assignment (deskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_seat_assignment_studentId ON seat_assignment (studentId)")
            }
        }

        // v5: timetable_slot gains nullable roomId with SET_NULL FK — table rebuild
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE timetable_slot_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "classId INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, " +
                        "startTime TEXT NOT NULL, endTime TEXT NOT NULL, " +
                        "weekParity TEXT NOT NULL, roomId INTEGER, " +
                        "FOREIGN KEY(classId) REFERENCES school_class(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(roomId) REFERENCES room(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO timetable_slot_new (id, classId, dayOfWeek, startTime, endTime, weekParity) " +
                        "SELECT id, classId, dayOfWeek, startTime, endTime, weekParity FROM timetable_slot"
                )
                db.execSQL("DROP TABLE timetable_slot")
                db.execSQL("ALTER TABLE timetable_slot_new RENAME TO timetable_slot")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_slot_classId ON timetable_slot (classId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_timetable_slot_roomId ON timetable_slot (roomId)")
            }
        }

        fun build(context: Context): AppDatabase =
            androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_2_3, MIGRATION_4_5)
                .build()
    }
}
