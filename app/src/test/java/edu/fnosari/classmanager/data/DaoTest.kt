package edu.fnosari.classmanager.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun classCrudAndCascade() = runBlocking {
        val classId = db.classDao().insert(SchoolClass(name = "5eB", level = "5e"))
        val studentId = db.studentDao().insert(Student(classId = classId, lastName = "Martin", firstName = "Emma"))
        db.noteDao().insert(Note(studentId = studentId, text = "obs"))
        val reminderId = db.reminderDao().insert(Reminder(studentId = studentId, text = "call parents",
            type = ReminderType.FIXED_DATETIME, dueAt = 1000L))
        assertEquals(1, db.studentDao().studentsIn(classId).first().size)
        db.classDao().delete(db.classDao().byId(classId)!!)
        assertEquals(0, db.studentDao().studentsIn(classId).first().size)
        assertNull(db.reminderDao().byId(reminderId))
    }

    @Test fun pickerCycleQueries() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "6eA", level = "6e"))
        val a = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        db.studentDao().insert(Student(classId = c, lastName = "B", firstName = "b"))
        db.studentDao().setPicked(a, true)
        assertEquals(1, db.studentDao().eligibleForPick(c, "2026-08-09").size)
        db.studentDao().resetCycle(c)
        assertEquals(2, db.studentDao().eligibleForPick(c, "2026-08-09").size)
    }

    @Test fun absentExcludedFromPick() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "6eA", level = "6e"))
        val a = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        db.studentDao().insert(Student(classId = c, lastName = "B", firstName = "b"))
        db.studentDao().setAbsent(a, "2026-08-09")
        assertEquals(1, db.studentDao().eligibleForPick(c, "2026-08-09").size)
        // stale absence from a previous day no longer excludes
        assertEquals(2, db.studentDao().eligibleForPick(c, "2026-08-10").size)
    }

    @Test fun groupingRoundTrip() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "4eC", level = "4e"))
        val s1 = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        val s2 = db.studentDao().insert(Student(classId = c, lastName = "B", firstName = "b"))
        db.groupDao().insertConstraint(SeparationConstraint(classId = c, studentAId = s1, studentBId = s2))
        assertEquals(1, db.groupDao().constraintsFor(c).first().size)
        val gId = db.groupDao().insertGrouping(Grouping(classId = c, name = "TP 1"))
        val g1 = db.groupDao().insertGroup(GroupingGroup(groupingId = gId, index = 0))
        db.groupDao().insertMember(GroupingMember(groupId = g1, studentId = s1))
        assertEquals(1, db.groupDao().membersOf(g1).size)
    }
}
