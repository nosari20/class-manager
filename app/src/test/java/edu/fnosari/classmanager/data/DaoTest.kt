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

    @Test fun seatingRoundTripAndCascades() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "3eA", level = "3e"))
        val s1 = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        val roomId = db.seatingDao().insertRoom(Room(name = "Salle 102"))
        val deskId = db.seatingDao().insertDesk(Desk(roomId = roomId, x = 0.5f, y = 0.5f))
        val planId = db.seatingDao().insertPlan(SeatingPlan(classId = c, roomId = roomId, name = "Plan A"))
        db.seatingDao().assign(SeatAssignment(planId = planId, deskId = deskId, studentId = s1))
        assertEquals(1, db.seatingDao().assignments(planId).first().size)
        // replace on same desk
        val s2 = db.studentDao().insert(Student(classId = c, lastName = "B", firstName = "b"))
        db.seatingDao().assign(SeatAssignment(planId = planId, deskId = deskId, studentId = s2))
        assertEquals(s2, db.seatingDao().assignments(planId).first().single().studentId)
        // deleting room cascades desks, plans stay? plan has FK to room -> cascade too
        db.seatingDao().deleteRoom(db.seatingDao().roomById(roomId)!!)
        assertEquals(0, db.seatingDao().plansFor(c).first().size)
    }

    @Test fun checklistTracksWhoHandedIn() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "5eB", level = "5e"))
        val s1 = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        val s2 = db.studentDao().insert(Student(classId = c, lastName = "B", firstName = "b"))
        val id = db.checklistDao().insert(Checklist(classId = c, title = "Autorisation signée"))

        assertEquals(0, db.checklistDao().entries(id).first().size)
        db.checklistDao().check(ChecklistEntry(checklistId = id, studentId = s1))
        assertEquals(setOf(s1), db.checklistDao().entries(id).first().map { it.studentId }.toSet())
        // checking twice must not create a second row
        db.checklistDao().check(ChecklistEntry(checklistId = id, studentId = s1))
        assertEquals(1, db.checklistDao().entries(id).first().size)

        db.checklistDao().check(ChecklistEntry(checklistId = id, studentId = s2))
        assertEquals(2, db.checklistDao().progressFor(c).first().single().doneCount)

        db.checklistDao().uncheck(id, s1)
        assertEquals(setOf(s2), db.checklistDao().entries(id).first().map { it.studentId }.toSet())
        db.checklistDao().clear(id)
        assertEquals(0, db.checklistDao().entries(id).first().size)
    }

    @Test fun checklistEntriesFollowDeletedStudentsAndClasses() = runBlocking {
        val c = db.classDao().insert(SchoolClass(name = "6eA", level = "6e"))
        val s = db.studentDao().insert(Student(classId = c, lastName = "A", firstName = "a"))
        val id = db.checklistDao().insert(Checklist(classId = c, title = "Carnet"))
        db.checklistDao().check(ChecklistEntry(checklistId = id, studentId = s))

        // a student who leaves takes their tick with them
        db.studentDao().delete(db.studentDao().byId(s)!!)
        assertEquals(0, db.checklistDao().entries(id).first().size)

        // deleting the class removes its checklists
        db.classDao().delete(db.classDao().byId(c)!!)
        assertEquals(0, db.checklistDao().checklistsFor(c).first().size)
        assertNull(db.checklistDao().byId(id))
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
