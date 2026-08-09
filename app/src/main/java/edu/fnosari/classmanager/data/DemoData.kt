package edu.fnosari.classmanager.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Inserts sample classes, students, rooms, seating and reminders so the app can be
 * explored without real data. Adds on top of existing data, never deletes.
 */
object DemoData {

    private val CLASS_6E = listOf(
        "Aïcha" to "Diallo", "Lucas" to "Martin", "Mei" to "Chen", "Mehdi" to "Benali",
        "Chloé" to "Bernard", "Kofi" to "Mensah", "Lina" to "Nguyen", "Ivan" to "Petrov",
        "Fatou" to "Ndiaye", "Hugo" to "Lefèvre", "Sofia" to "Rodriguez", "Omar" to "Haddad",
    )
    private val CLASS_5E = listOf(
        "Elif" to "Yilmaz", "Nathan" to "Dubois", "Priya" to "Sharma", "Mamadou" to "Traoré",
        "Sara" to "Cohen", "Dimitri" to "Ivanov", "Amara" to "Okafor", "Louane" to "Petit",
        "Youssef" to "El Amrani", "Hanae" to "Tanaka", "Gabriel" to "Moreau", "Nadia" to "Karimi",
    )
    private val CLASS_4E = listOf(
        "Noa" to "Levy", "Enzo" to "Garcia", "Khadija" to "Sow", "Milan" to "Kovac",
        "Jade" to "Rousseau", "Tariq" to "Aziz", "Ana" to "Silva", "Bakary" to "Keita",
        "Léa" to "Fontaine", "Minh" to "Pham", "Zineb" to "Alaoui", "Adam" to "Nowak",
    )

    suspend fun seed(db: AppDatabase) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        // rooms
        val salle204 = db.seatingDao().insertRoom(Room(name = "Salle 204 (démo)"))
        val salleU = db.seatingDao().insertRoom(Room(name = "Salle Arts (démo)"))
        val gridDesks = mutableListOf<Long>()
        for (row in 0..2) for (col in 0..1) {
            gridDesks += db.seatingDao().insertDesk(
                Desk(roomId = salle204, x = 0.3f + col * 0.4f, y = 0.35f + row * 0.22f, seats = 2)
            )
        }
        // U shape: rotated tables on the sides, horizontal at the bottom
        listOf(0.32f, 0.58f).forEach { y ->
            db.seatingDao().insertDesk(Desk(roomId = salleU, x = 0.14f, y = y, seats = 2, vertical = true))
            db.seatingDao().insertDesk(Desk(roomId = salleU, x = 0.86f, y = y, seats = 2, vertical = true))
        }
        listOf(0.35f, 0.65f).forEach { x ->
            db.seatingDao().insertDesk(Desk(roomId = salleU, x = x, y = 0.82f, seats = 2))
        }

        // classes + students
        val classes = listOf(
            Triple("6eA (démo)", "6e", CLASS_6E),
            Triple("5eD (démo)", "5e", CLASS_5E),
            Triple("4eC (démo)", "4e", CLASS_4E),
        )
        val classIds = mutableListOf<Long>()
        val studentIds = mutableMapOf<Long, List<Long>>()
        for ((name, level, roster) in classes) {
            val classId = db.classDao().insert(SchoolClass(name = name, level = level))
            classIds += classId
            studentIds[classId] = roster.map { (first, last) ->
                db.studentDao().insert(Student(classId = classId, firstName = first, lastName = last))
            }
        }

        // timetable: each class gets a slot today plus two other weekdays
        val todayDow = today.dayOfWeek.value
        val times = listOf("08:30" to "09:30", "10:00" to "11:00", "14:00" to "15:00")
        classIds.forEachIndexed { i, classId ->
            val (start, end) = times[i]
            db.timetableDao().insert(TimetableSlot(
                classId = classId, dayOfWeek = todayDow, startTime = start, endTime = end,
                roomId = if (i == 0) salle204 else salleU,
            ))
            listOf(1, 3).forEach { offset ->
                val dow = (todayDow - 1 + offset) % 7 + 1
                db.timetableDao().insert(TimetableSlot(
                    classId = classId, dayOfWeek = dow, startTime = start, endTime = end,
                    roomId = salle204,
                ))
            }
        }

        // seating plan for the first class in Salle 204
        val firstClass = classIds[0]
        val plan = db.seatingDao().insertPlan(
            SeatingPlan(classId = firstClass, roomId = salle204, name = "Plan démo")
        )
        studentIds[firstClass]!!.forEachIndexed { i, studentId ->
            val desk = gridDesks.getOrNull(i / 2) ?: return@forEachIndexed
            db.seatingDao().assign(SeatAssignment(
                planId = plan, deskId = desk, seatIndex = i % 2, studentId = studentId,
            ))
        }

        // separation constraint + student details on the first class
        val roster = studentIds[firstClass]!!
        db.groupDao().insertConstraint(SeparationConstraint(
            classId = firstClass, studentAId = roster[0], studentBId = roster[1],
        ))
        db.noteDao().insert(Note(studentId = roster[0], text = "Très bonne participation à l'oral"))
        db.noteDao().insert(Note(studentId = roster[2], text = "Doit rattraper l'évaluation de grammaire"))
        db.customFieldDao().insert(CustomField(studentId = roster[0], key = "LV2", value = "Espagnol"))

        // reminders visible on the Today tab
        val fivePm = LocalDateTime.of(today, java.time.LocalTime.of(17, 0))
            .atZone(zone).toInstant().toEpochMilli()
        db.reminderDao().insert(Reminder(
            studentId = roster[2], text = "Rendre la copie corrigée",
            type = ReminderType.FIXED_DATETIME, dueAt = fivePm,
        ))
        db.reminderDao().insert(Reminder(
            studentId = roster[0], text = "Prévenir des félicitations du conseil",
            type = ReminderType.MORNING_DIGEST,
            dueAt = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        ))
    }
}
