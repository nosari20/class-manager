package edu.fnosari.classmanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WeekParityTag { BOTH, A, B }
enum class ReminderType { NEXT_LESSON, MORNING_DIGEST, FIXED_DATETIME }

@Entity(tableName = "school_class")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val level: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "timetable_slot",
    foreignKeys = [
        ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
            childColumns = ["classId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Room::class, parentColumns = ["id"],
            childColumns = ["roomId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("classId"), Index("roomId")],
)
data class TimetableSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val dayOfWeek: Int,          // ISO 1=Mon..7=Sun
    val startTime: String,       // "HH:mm"
    val endTime: String,         // "HH:mm"
    val weekParity: WeekParityTag = WeekParityTag.BOTH,
    val roomId: Long? = null,
)

@Entity(
    tableName = "student",
    foreignKeys = [ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
        childColumns = ["classId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId")],
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val lastName: String,
    val firstName: String,
    val photoPath: String? = null,          // relative: "photos/<uuid>.jpg"
    val pickedInCurrentCycle: Boolean = false,
    val absentTodayDate: String? = null,    // "yyyy-MM-dd" when marked absent
)

@Entity(
    tableName = "note",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "custom_field",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class CustomField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val key: String,
    val value: String,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val text: String,
    val type: ReminderType,
    val dueAt: Long,             // epoch millis; for MORNING_DIGEST = start of due day
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "separation_constraint",
    foreignKeys = [
        ForeignKey(entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentAId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentBId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId"), Index("studentAId"), Index("studentBId")],
)
data class SeparationConstraint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val studentAId: Long,
    val studentBId: Long,
)

@Entity(
    tableName = "grouping",
    foreignKeys = [ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
        childColumns = ["classId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId")],
)
data class Grouping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "grouping_group",
    foreignKeys = [ForeignKey(entity = Grouping::class, parentColumns = ["id"],
        childColumns = ["groupingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupingId")],
)
data class GroupingGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupingId: Long,
    @ColumnInfo(name = "groupIndex") val index: Int,
)

@Entity(
    tableName = "slot_cancellation",
    foreignKeys = [ForeignKey(entity = TimetableSlot::class, parentColumns = ["id"],
        childColumns = ["slotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("slotId")],
)
data class SlotCancellation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long,
    val date: String,            // "yyyy-MM-dd" occurrence suppressed on this date
)

@Entity(
    tableName = "one_off_slot",
    foreignKeys = [
        ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
            childColumns = ["classId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Room::class, parentColumns = ["id"],
            childColumns = ["roomId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("classId"), Index("roomId")],
)
data class OneOffSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val date: String,            // "yyyy-MM-dd"
    val startTime: String,       // "HH:mm"
    val endTime: String,
    val roomId: Long? = null,
)

@Entity(tableName = "room")
data class Room(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "desk",
    foreignKeys = [ForeignKey(entity = Room::class, parentColumns = ["id"],
        childColumns = ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
data class Desk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val x: Float,   // normalized 0..1 across room canvas
    val y: Float,
    @ColumnInfo(defaultValue = "1") val seats: Int = 1,   // 1 or 2
    @ColumnInfo(defaultValue = "0") val vertical: Boolean = false,   // 2-seat table rotated 90°
)

@Entity(
    tableName = "seating_plan",
    foreignKeys = [
        ForeignKey(entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Room::class, parentColumns = ["id"], childColumns = ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId"), Index("roomId")],
)
data class SeatingPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val roomId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "seat_assignment",
    primaryKeys = ["planId", "deskId", "seatIndex"],
    foreignKeys = [
        ForeignKey(entity = SeatingPlan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Desk::class, parentColumns = ["id"], childColumns = ["deskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deskId"), Index("studentId")],
)
data class SeatAssignment(
    val planId: Long,
    val deskId: Long,
    @ColumnInfo(defaultValue = "0") val seatIndex: Int = 0,   // 0 = left, 1 = right
    val studentId: Long,
)

@Entity(
    tableName = "grouping_member",
    primaryKeys = ["groupId", "studentId"],
    foreignKeys = [
        ForeignKey(entity = GroupingGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")],
)
data class GroupingMember(
    val groupId: Long,
    val studentId: Long,
)
