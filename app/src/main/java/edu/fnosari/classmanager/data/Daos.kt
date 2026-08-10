package edu.fnosari.classmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao interface ClassDao {
    @Insert suspend fun insert(c: SchoolClass): Long
    @Update suspend fun update(c: SchoolClass)
    @Delete suspend fun delete(c: SchoolClass)
    @Query("SELECT * FROM school_class WHERE id = :id") suspend fun byId(id: Long): SchoolClass?
    @Query("SELECT * FROM school_class ORDER BY name") fun all(): Flow<List<SchoolClass>>
    @Query("SELECT COUNT(*) FROM student WHERE classId = :classId") fun studentCount(classId: Long): Flow<Int>
}

@Dao interface StudentDao {
    @Insert suspend fun insert(s: Student): Long
    @Insert suspend fun insertAll(s: List<Student>)
    @Update suspend fun update(s: Student)
    @Delete suspend fun delete(s: Student)
    @Query("SELECT * FROM student WHERE id = :id") suspend fun byId(id: Long): Student?
    @Query("SELECT * FROM student WHERE id = :id") fun byIdFlow(id: Long): Flow<Student?>
    @Query("SELECT * FROM student ORDER BY lastName, firstName")
    fun allStudents(): Flow<List<Student>>
    @Query("SELECT * FROM student WHERE classId = :classId ORDER BY lastName, firstName")
    fun studentsIn(classId: Long): Flow<List<Student>>
    @Query("SELECT * FROM student WHERE classId = :classId ORDER BY lastName, firstName")
    suspend fun studentsInOnce(classId: Long): List<Student>
    @Query("UPDATE student SET pickedInCurrentCycle = :picked WHERE id = :id")
    suspend fun setPicked(id: Long, picked: Boolean)
    @Query("UPDATE student SET pickedInCurrentCycle = 0 WHERE classId = :classId")
    suspend fun resetCycle(classId: Long)
    @Query("UPDATE student SET absentTodayDate = :date WHERE id = :id")
    suspend fun setAbsent(id: Long, date: String?)
    @Query("""SELECT * FROM student WHERE classId = :classId
              AND pickedInCurrentCycle = 0
              AND (absentTodayDate IS NULL OR absentTodayDate != :today)""")
    suspend fun eligibleForPick(classId: Long, today: String): List<Student>
}

@Dao interface TimetableDao {
    @Insert suspend fun insert(t: TimetableSlot): Long
    @Delete suspend fun delete(t: TimetableSlot)
    @Query("SELECT * FROM timetable_slot WHERE classId = :classId ORDER BY dayOfWeek, startTime")
    fun slotsFor(classId: Long): Flow<List<TimetableSlot>>
    @Query("SELECT * FROM timetable_slot WHERE classId = :classId")
    suspend fun slotsForOnce(classId: Long): List<TimetableSlot>
    @Query("SELECT * FROM timetable_slot")
    fun allSlots(): Flow<List<TimetableSlot>>

    @Insert suspend fun insertCancellation(c: SlotCancellation): Long
    @Delete suspend fun deleteCancellation(c: SlotCancellation)
    @Query("SELECT * FROM slot_cancellation")
    fun allCancellations(): Flow<List<SlotCancellation>>
    @Query("SELECT sc.* FROM slot_cancellation sc JOIN timetable_slot t ON t.id = sc.slotId WHERE t.classId = :classId")
    fun cancellationsFor(classId: Long): Flow<List<SlotCancellation>>

    @Insert suspend fun insertOneOff(o: OneOffSlot): Long
    @Delete suspend fun deleteOneOff(o: OneOffSlot)
    @Query("SELECT * FROM one_off_slot")
    fun allOneOffs(): Flow<List<OneOffSlot>>
    @Query("SELECT * FROM one_off_slot WHERE classId = :classId")
    fun oneOffsFor(classId: Long): Flow<List<OneOffSlot>>
}

@Dao interface NoteDao {
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Delete suspend fun delete(n: Note)
    @Query("SELECT * FROM note WHERE studentId = :studentId ORDER BY createdAt DESC")
    fun notesFor(studentId: Long): Flow<List<Note>>
}

@Dao interface CustomFieldDao {
    @Insert suspend fun insert(f: CustomField): Long
    @Update suspend fun update(f: CustomField)
    @Delete suspend fun delete(f: CustomField)
    @Query("SELECT * FROM custom_field WHERE studentId = :studentId ORDER BY `key`")
    fun fieldsFor(studentId: Long): Flow<List<CustomField>>
}

@Dao interface ReminderDao {
    @Insert suspend fun insert(r: Reminder): Long
    @Update suspend fun update(r: Reminder)
    @Delete suspend fun delete(r: Reminder)
    @Query("SELECT * FROM reminder WHERE id = :id") suspend fun byId(id: Long): Reminder?
    @Query("SELECT * FROM reminder WHERE studentId = :studentId ORDER BY done, dueAt")
    fun remindersFor(studentId: Long): Flow<List<Reminder>>
    @Query("SELECT * FROM reminder WHERE done = 0") suspend fun allPending(): List<Reminder>
    @Query("SELECT * FROM reminder WHERE done = 0 AND dueAt >= :dayStart AND dueAt < :dayEnd")
    suspend fun dueBetween(dayStart: Long, dayEnd: Long): List<Reminder>
    @Query("SELECT * FROM reminder WHERE dueAt >= :dayStart AND dueAt < :dayEnd ORDER BY done, dueAt")
    fun remindersBetween(dayStart: Long, dayEnd: Long): Flow<List<Reminder>>
    @Query("UPDATE reminder SET done = 1 WHERE id = :id") suspend fun markDone(id: Long)
}

@Dao interface SeatingDao {
    @Insert suspend fun insertRoom(r: Room): Long
    @Update suspend fun updateRoom(r: Room)
    @Delete suspend fun deleteRoom(r: Room)
    @Query("SELECT * FROM room ORDER BY name") fun rooms(): Flow<List<Room>>
    @Query("SELECT * FROM room WHERE id = :id") suspend fun roomById(id: Long): Room?

    @Insert suspend fun insertDesk(d: Desk): Long
    @Update suspend fun updateDesk(d: Desk)
    @Delete suspend fun deleteDesk(d: Desk)
    @Query("SELECT * FROM desk WHERE roomId = :roomId") fun desks(roomId: Long): Flow<List<Desk>>

    @Insert suspend fun insertPlan(p: SeatingPlan): Long
    @Update suspend fun updatePlan(p: SeatingPlan)
    @Delete suspend fun deletePlan(p: SeatingPlan)
    @Query("SELECT * FROM seating_plan WHERE classId = :classId ORDER BY createdAt DESC")
    fun plansFor(classId: Long): Flow<List<SeatingPlan>>
    @Query("SELECT * FROM seating_plan WHERE id = :id") suspend fun planById(id: Long): SeatingPlan?
    @Query("SELECT * FROM seating_plan WHERE classId = :classId AND roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestPlanFor(classId: Long, roomId: Long): SeatingPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun assign(a: SeatAssignment)
    @Query("DELETE FROM seat_assignment WHERE planId = :planId AND deskId = :deskId AND seatIndex = :seatIndex")
    suspend fun unassign(planId: Long, deskId: Long, seatIndex: Int)
    @Query("DELETE FROM seat_assignment WHERE deskId = :deskId AND seatIndex >= :fromIndex")
    suspend fun unassignSeatsFrom(deskId: Long, fromIndex: Int)
    @Query("DELETE FROM seat_assignment WHERE planId = :planId AND studentId = :studentId")
    suspend fun unassignStudent(planId: Long, studentId: Long)
    @Query("SELECT * FROM seat_assignment WHERE planId = :planId")
    fun assignments(planId: Long): Flow<List<SeatAssignment>>
}

/** How many students have handed in a given checklist. */
data class ChecklistProgress(val checklistId: Long, val doneCount: Int)

@Dao interface ChecklistDao {
    @Insert suspend fun insert(c: Checklist): Long
    @Update suspend fun update(c: Checklist)
    @Delete suspend fun delete(c: Checklist)
    @Query("SELECT * FROM checklist WHERE classId = :classId ORDER BY createdAt DESC")
    fun checklistsFor(classId: Long): Flow<List<Checklist>>
    @Query("SELECT * FROM checklist WHERE id = :id") fun byIdFlow(id: Long): Flow<Checklist?>
    @Query("SELECT * FROM checklist WHERE id = :id") suspend fun byId(id: Long): Checklist?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun check(e: ChecklistEntry)
    @Query("DELETE FROM checklist_entry WHERE checklistId = :checklistId AND studentId = :studentId")
    suspend fun uncheck(checklistId: Long, studentId: Long)
    @Query("DELETE FROM checklist_entry WHERE checklistId = :checklistId")
    suspend fun clear(checklistId: Long)
    @Query("SELECT * FROM checklist_entry WHERE checklistId = :checklistId")
    fun entries(checklistId: Long): Flow<List<ChecklistEntry>>
    @Query("""SELECT checklistId, COUNT(*) AS doneCount FROM checklist_entry
              WHERE checklistId IN (SELECT id FROM checklist WHERE classId = :classId)
              GROUP BY checklistId""")
    fun progressFor(classId: Long): Flow<List<ChecklistProgress>>
}

@Dao interface GroupDao {
    @Insert suspend fun insertConstraint(c: SeparationConstraint): Long
    @Delete suspend fun deleteConstraint(c: SeparationConstraint)
    @Query("SELECT * FROM separation_constraint WHERE classId = :classId")
    fun constraintsFor(classId: Long): Flow<List<SeparationConstraint>>
    @Query("SELECT * FROM separation_constraint WHERE classId = :classId")
    suspend fun constraintsForOnce(classId: Long): List<SeparationConstraint>

    @Insert suspend fun insertGrouping(g: Grouping): Long
    @Update suspend fun updateGrouping(g: Grouping)
    @Delete suspend fun deleteGrouping(g: Grouping)
    @Query("SELECT * FROM grouping WHERE classId = :classId ORDER BY createdAt DESC")
    fun groupingsFor(classId: Long): Flow<List<Grouping>>
    @Insert suspend fun insertGroup(g: GroupingGroup): Long
    @Query("SELECT * FROM grouping_group WHERE groupingId = :groupingId ORDER BY groupIndex")
    suspend fun groupsOf(groupingId: Long): List<GroupingGroup>
    @Insert suspend fun insertMember(m: GroupingMember)
    @Query("SELECT * FROM grouping_member WHERE groupId = :groupId")
    suspend fun membersOf(groupId: Long): List<GroupingMember>
}
