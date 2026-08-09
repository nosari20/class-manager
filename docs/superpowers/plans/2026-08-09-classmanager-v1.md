# ClassManager v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offline Android app for teachers: class/student CRUD with photos, Pronote CSV import, fair random picker, constraint-based group generator, reminders with notifications, zip backup/restore.

**Architecture:** Single-module MVVM. Room (SQLite) for data, photo files in `filesDir/photos/`, DataStore for settings, Jetpack Compose + Navigation Compose UI (single Activity), AlarmManager exact alarms + BootReceiver for reminders. Manual DI via `AppContainer` — no Hilt. Pure-Kotlin domain logic (CSV parser, week parity, group generator) unit-tested with JUnit; DAOs tested with Robolectric.

**Tech Stack:** Kotlin 2.2.10, Compose BOM 2026.02.01, Material3, Room 2.8.2 (KSP), Navigation Compose 2.9.4, DataStore 1.1.7, Coil 2.7.0, Robolectric 4.15.1.

**Spec:** `docs/superpowers/specs/2026-08-09-classmanager-design.md` — read it first.

## Global Constraints

- minSdk = 29, targetSdk = 37, compileSdk = 37 (spec: Android 10+).
- Package: `edu.fnosari.classmanager`.
- All user-facing strings in `res/values/strings.xml` (English) AND `res/values-fr/strings.xml` (French). Never hardcode UI text in composables.
- Photos downscaled to max 512px longest side, JPEG quality 85, stored in `filesDir/photos/<uuid>.jpg`; DB stores relative path `photos/<uuid>.jpg`.
- Dates/times: `createdAt`/`dueAt` = epoch millis (Long). `dayOfWeek` = ISO int 1(Mon)–7(Sun). Slot times = `"HH:mm"` strings. Date-only fields = `"yyyy-MM-dd"` strings.
- Room `exportSchema = true`, schemas dir `app/schemas/`.
- Every DB write goes through a DAO suspend function; UI reads via `Flow`.
- Windows dev machine: run gradle as `.\gradlew.bat <task>` from project root.
- Tests: JVM unit tests via `.\gradlew.bat testDebugUnitTest --tests "<pattern>"`. No emulator assumed; instrumented tests are NOT part of this plan's gates.
- Commit after every task with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` trailer.

## File Structure (final state)

```
app/src/main/java/edu/fnosari/classmanager/
  ClassManagerApp.kt            — Application, owns AppContainer, creates notification channels
  AppContainer.kt               — manual DI: db, daos, settings, backup, alarms
  MainActivity.kt               — single activity, NavHost, deep-link extras
  data/
    Entities.kt                 — all Room entities + enums
    Daos.kt                     — ClassDao, StudentDao, TimetableDao, NoteDao, CustomFieldDao, ReminderDao, GroupDao
    AppDatabase.kt              — RoomDatabase v1
    SettingsRepository.kt       — DataStore: weekARef, digestTime, csvMapping
  domain/
    WeekParity.kt               — parity + next-lesson slot computation (pure)
    CsvParser.kt                — charset/separator detection, quoted parsing (pure)
    GroupGenerator.kt           — constraint group generation (pure)
  notifications/
    NotificationHelper.kt       — channels + notification builders
    AlarmScheduler.kt           — schedule/cancel reminder + digest alarms
    ReminderReceiver.kt         — fires single-reminder notification
    DigestReceiver.kt           — fires morning digest, reschedules next day
    BootReceiver.kt             — reschedules everything after reboot
  backup/
    BackupManager.kt            — zip write, validate, atomic restore
  ui/
    Nav.kt                      — routes + NavHost
    theme/                      — (exists)
    classlist/ClassListScreen.kt + ClassListViewModel.kt
    classdetail/ClassDetailScreen.kt + ClassDetailViewModel.kt
    timetable/TimetableEditor.kt          (composables used inside class detail)
    student/StudentDetailScreen.kt + StudentDetailViewModel.kt
    picker/PickerScreen.kt + PickerViewModel.kt
    groups/GroupsScreen.kt + GroupsViewModel.kt
    csv/CsvImportScreen.kt + CsvImportViewModel.kt
    settings/SettingsScreen.kt + SettingsViewModel.kt
    common/PhotoUtil.kt         — pick/capture/downscale helpers
app/src/test/java/edu/fnosari/classmanager/
  domain/WeekParityTest.kt, CsvParserTest.kt, GroupGeneratorTest.kt
  data/DaoTest.kt               — Robolectric, in-memory Room
  backup/BackupValidateTest.kt
```

Code blocks below omit `import` lines for brevity — resolve with IDE/standard AndroidX imports. Everything else is literal.

---

### Task 1: Gradle setup — minSdk 29, Room/KSP, Navigation, DataStore, Coil, Robolectric

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (root)

**Interfaces:**
- Produces: buildable project with all dependencies later tasks import. Version catalog aliases named exactly as below.

- [ ] **Step 1: Add versions/libraries/plugins to `gradle/libs.versions.toml`**

Append to `[versions]`:

```toml
ksp = "2.2.10-2.0.2"
room = "2.8.2"
navigationCompose = "2.9.4"
datastore = "1.1.7"
coil = "2.7.0"
robolectric = "4.15.1"
coroutinesTest = "1.10.2"
lifecycleViewmodelCompose = "2.9.4"
androidxTestCore = "1.7.0"
```

Change existing: `lifecycleRuntimeKtx = "2.9.4"`.

Append to `[libraries]`:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
```

Append to `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

If dependency resolution fails on an exact version at Step 4, bump to the nearest available stable — do not downgrade Kotlin/AGP.

- [ ] **Step 2: Update `app/build.gradle.kts`**

Plugins block — add: `alias(libs.plugins.ksp)`.
`defaultConfig`: change `minSdk = 35` → `minSdk = 29`.
Inside `android {}` add:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

Add to `dependencies {}`:

```kotlin
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
```

Add Room schema export inside `android {}`:

```kotlin
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
```

(If AGP rejects `ksp {}` inside `android {}`, place it at top level of the file.)

- [ ] **Step 3: Root `build.gradle.kts` — declare ksp plugin `apply false`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

(Keep whatever aliases already exist; just add ksp.)

- [ ] **Step 4: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts build.gradle.kts
git commit -m "build: minSdk 29, add Room/KSP, Navigation, DataStore, Coil, Robolectric"
```

---

### Task 2: Room entities, database, DAOs

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/data/Entities.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/data/Daos.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/data/AppDatabase.kt`
- Test: `app/src/test/java/edu/fnosari/classmanager/data/DaoTest.kt`

**Interfaces:**
- Produces: all entities/DAOs below, `AppDatabase.build(context)`. Later tasks use these exact names.

- [ ] **Step 1: Write failing DAO test (Robolectric, in-memory)**

```kotlin
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
        db.reminderDao().insert(Reminder(studentId = studentId, text = "call parents",
            type = ReminderType.FIXED_DATETIME, dueAt = 1000L))
        assertEquals(1, db.studentDao().studentsIn(classId).first().size)
        db.classDao().delete(db.classDao().byId(classId)!!)
        assertEquals(0, db.studentDao().studentsIn(classId).first().size)
        assertNull(db.reminderDao().byId(1L))
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.data.DaoTest"`
Expected: compilation FAILURE (entities don't exist).

- [ ] **Step 3: Write `Entities.kt`**

```kotlin
package edu.fnosari.classmanager.data

enum class WeekParityTag { BOTH, A, B }
enum class ReminderType { NEXT_LESSON, MORNING_DIGEST, FIXED_DATETIME }

@Entity(tableName = "school_class")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val level: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "timetable_slot",
    foreignKeys = [ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
        childColumns = ["classId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId")])
data class TimetableSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val dayOfWeek: Int,          // ISO 1=Mon..7=Sun
    val startTime: String,       // "HH:mm"
    val endTime: String,         // "HH:mm"
    val weekParity: WeekParityTag = WeekParityTag.BOTH,
)

@Entity(tableName = "student",
    foreignKeys = [ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
        childColumns = ["classId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId")])
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val lastName: String,
    val firstName: String,
    val photoPath: String? = null,          // relative: "photos/<uuid>.jpg"
    val pickedInCurrentCycle: Boolean = false,
    val absentTodayDate: String? = null,    // "yyyy-MM-dd" when marked absent
)

@Entity(tableName = "note",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_field",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")])
data class CustomField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val key: String,
    val value: String,
)

@Entity(tableName = "reminder",
    foreignKeys = [ForeignKey(entity = Student::class, parentColumns = ["id"],
        childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val text: String,
    val type: ReminderType,
    val dueAt: Long,             // epoch millis; for MORNING_DIGEST = start of due day
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "separation_constraint",
    foreignKeys = [
        ForeignKey(entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentAId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentBId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId"), Index("studentAId"), Index("studentBId")])
data class SeparationConstraint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val studentAId: Long,
    val studentBId: Long,
)

@Entity(tableName = "grouping",
    foreignKeys = [ForeignKey(entity = SchoolClass::class, parentColumns = ["id"],
        childColumns = ["classId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("classId")])
data class Grouping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "grouping_group",
    foreignKeys = [ForeignKey(entity = Grouping::class, parentColumns = ["id"],
        childColumns = ["groupingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupingId")])
data class GroupingGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupingId: Long,
    @ColumnInfo(name = "groupIndex") val index: Int,
)

@Entity(tableName = "grouping_member",
    primaryKeys = ["groupId", "studentId"],
    foreignKeys = [
        ForeignKey(entity = GroupingGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("studentId")])
data class GroupingMember(
    val groupId: Long,
    val studentId: Long,
)
```

- [ ] **Step 4: Write `Daos.kt`**

```kotlin
package edu.fnosari.classmanager.data

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
    @Query("SELECT * FROM custom_field WHERE studentId = :studentId ORDER BY key")
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
    @Query("UPDATE reminder SET done = 1 WHERE id = :id") suspend fun markDone(id: Long)
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
```

- [ ] **Step 5: Write `AppDatabase.kt`**

```kotlin
package edu.fnosari.classmanager.data

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
```

Room stores enums by name automatically via built-in enum converter — no TypeConverter needed.

- [ ] **Step 6: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.data.DaoTest"`
Expected: 3 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/edu/fnosari/classmanager/data app/src/test app/schemas
git commit -m "feat: Room entities, DAOs, database with cascade deletes"
```

---

### Task 3: Settings repository (DataStore)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/data/SettingsRepository.kt`

**Interfaces:**
- Produces:
  - `class SettingsRepository(context: Context)`
  - `val weekARef: Flow<String?>` (`"yyyy-MM-dd"` Monday of a week declared A, null = not set)
  - `val digestTime: Flow<String>` (default `"07:00"`)
  - `val csvMapping: Flow<Pair<Int, Int>?>` (lastName col, firstName col)
  - `suspend fun setWeekARef(date: String)`, `suspend fun setDigestTime(t: String)`, `suspend fun setCsvMapping(last: Int, first: Int)`

- [ ] **Step 1: Implement**

```kotlin
package edu.fnosari.classmanager.data

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val WEEK_A_REF = stringPreferencesKey("week_a_ref")
        val DIGEST_TIME = stringPreferencesKey("digest_time")
        val CSV_LAST = intPreferencesKey("csv_last_col")
        val CSV_FIRST = intPreferencesKey("csv_first_col")
    }

    val weekARef: Flow<String?> = context.dataStore.data.map { it[Keys.WEEK_A_REF] }
    val digestTime: Flow<String> = context.dataStore.data.map { it[Keys.DIGEST_TIME] ?: "07:00" }
    val csvMapping: Flow<Pair<Int, Int>?> = context.dataStore.data.map { p ->
        val l = p[Keys.CSV_LAST]; val f = p[Keys.CSV_FIRST]
        if (l != null && f != null) l to f else null
    }

    suspend fun setWeekARef(date: String) { context.dataStore.edit { it[Keys.WEEK_A_REF] = date } }
    suspend fun setDigestTime(t: String) { context.dataStore.edit { it[Keys.DIGEST_TIME] = t } }
    suspend fun setCsvMapping(last: Int, first: Int) {
        context.dataStore.edit { it[Keys.CSV_LAST] = last; it[Keys.CSV_FIRST] = first }
    }
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/edu/fnosari/classmanager/data/SettingsRepository.kt
git commit -m "feat: settings repository (week A ref, digest time, csv mapping)"
```

---

### Task 4: Week parity + next-lesson computation (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/domain/WeekParity.kt`
- Test: `app/src/test/java/edu/fnosari/classmanager/domain/WeekParityTest.kt`

**Interfaces:**
- Consumes: `TimetableSlot`, `WeekParityTag` from Task 2.
- Produces:
  - `enum class Parity { A, B }`
  - `fun parityOf(date: LocalDate, weekARef: LocalDate): Parity`
  - `fun nextLessonStart(from: LocalDateTime, slots: List<TimetableSlot>, weekARef: LocalDate?): LocalDateTime?` — earliest future slot start within 15 days honoring parity; slots tagged A/B match all weeks when `weekARef == null`.

- [ ] **Step 1: Write failing tests**

```kotlin
class WeekParityTest {
    // 2026-08-03 is a Monday. Declare its week = A.
    private val refMonday = LocalDate.of(2026, 8, 3)

    @Test fun sameWeekIsA() {
        assertEquals(Parity.A, parityOf(LocalDate.of(2026, 8, 9), refMonday)) // Sunday same week
    }
    @Test fun nextWeekIsB() {
        assertEquals(Parity.B, parityOf(LocalDate.of(2026, 8, 10), refMonday)) // next Monday
    }
    @Test fun pastWeeksAlternateToo() {
        assertEquals(Parity.B, parityOf(LocalDate.of(2026, 7, 27), refMonday)) // Monday before ref
        assertEquals(Parity.A, parityOf(LocalDate.of(2026, 7, 20), refMonday))
    }
    @Test fun yearBoundaryStable() {
        // 2026-12-28 (Mon) .. 2027-01-03 belong to one week; parity must not jump inside it
        val p1 = parityOf(LocalDate.of(2026, 12, 28), refMonday)
        val p2 = parityOf(LocalDate.of(2027, 1, 3), refMonday)
        assertEquals(p1, p2)
    }

    private fun slot(day: Int, start: String, parity: WeekParityTag = WeekParityTag.BOTH) =
        TimetableSlot(classId = 1, dayOfWeek = day, startTime = start, endTime = "10:00", weekParity = parity)

    @Test fun nextLessonSameDayLaterSlot() {
        val from = LocalDateTime.of(2026, 8, 3, 7, 0) // Mon 07:00
        val next = nextLessonStart(from, listOf(slot(1, "08:00")), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 0), next)
    }
    @Test fun nextLessonSkipsPastSlotToday() {
        val from = LocalDateTime.of(2026, 8, 3, 9, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00")), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 0), next) // next Monday
    }
    @Test fun nextLessonHonorsParity() {
        // slot only on B weeks; from A-week Monday → lands 7 days later
        val from = LocalDateTime.of(2026, 8, 3, 7, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00", WeekParityTag.B)), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 0), next)
    }
    @Test fun parityIgnoredWhenNoRef() {
        val from = LocalDateTime.of(2026, 8, 3, 7, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00", WeekParityTag.B)), null)
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 0), next)
    }
    @Test fun noSlotsReturnsNull() {
        assertNull(nextLessonStart(LocalDateTime.of(2026, 8, 3, 7, 0), emptyList(), refMonday))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.WeekParityTest"`
Expected: compilation FAILURE.

- [ ] **Step 3: Implement `WeekParity.kt`**

```kotlin
package edu.fnosari.classmanager.domain

enum class Parity { A, B }

private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

fun parityOf(date: LocalDate, weekARef: LocalDate): Parity {
    val weeks = ChronoUnit.WEEKS.between(mondayOf(weekARef), mondayOf(date))
    return if (Math.floorMod(weeks, 2L) == 0L) Parity.A else Parity.B
}

private fun TimetableSlot.matchesParity(date: LocalDate, weekARef: LocalDate?): Boolean {
    if (weekParity == WeekParityTag.BOTH || weekARef == null) return true
    val p = parityOf(date, weekARef)
    return (weekParity == WeekParityTag.A && p == Parity.A) ||
           (weekParity == WeekParityTag.B && p == Parity.B)
}

fun nextLessonStart(
    from: LocalDateTime,
    slots: List<TimetableSlot>,
    weekARef: LocalDate?,
): LocalDateTime? {
    if (slots.isEmpty()) return null
    for (offset in 0..15L) {
        val date = from.toLocalDate().plusDays(offset)
        val candidates = slots
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.matchesParity(date, weekARef) }
            .map { LocalDateTime.of(date, LocalTime.parse(it.startTime)) }
            .filter { it.isAfter(from) }
            .sorted()
        if (candidates.isNotEmpty()) return candidates.first()
    }
    return null
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.WeekParityTest"`
Expected: 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/edu/fnosari/classmanager/domain/WeekParity.kt app/src/test/java/edu/fnosari/classmanager/domain/WeekParityTest.kt
git commit -m "feat: A/B week parity and next-lesson computation"
```

---

### Task 5: CSV parser (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/domain/CsvParser.kt`
- Test: `app/src/test/java/edu/fnosari/classmanager/domain/CsvParserTest.kt`

**Interfaces:**
- Produces:
  - `data class CsvTable(val headers: List<String>, val rows: List<List<String>>, val skippedLines: List<Int>)` — `skippedLines` = 1-based line numbers of rows whose field count ≠ header count.
  - `object CsvParser { fun parse(bytes: ByteArray): CsvTable; fun guessColumn(headers: List<String>, candidates: List<String>): Int? }`
  - `val LAST_NAME_HEADERS = listOf("nom", "nom de famille", "lastname", "last name", "name")`
  - `val FIRST_NAME_HEADERS = listOf("prénom", "prenom", "firstname", "first name")`

- [ ] **Step 1: Write failing tests**

```kotlin
class CsvParserTest {
    @Test fun commaSeparated() {
        val t = CsvParser.parse("Nom,Prénom\nMartin,Emma\nDurand,Léo".toByteArray())
        assertEquals(listOf("Nom", "Prénom"), t.headers)
        assertEquals(listOf("Martin", "Emma"), t.rows[0])
        assertEquals(2, t.rows.size)
    }
    @Test fun semicolonSeparated() {
        val t = CsvParser.parse("Nom;Prénom\nMartin;Emma".toByteArray())
        assertEquals(listOf("Martin", "Emma"), t.rows[0])
    }
    @Test fun semicolonWinsWhenBothPresent() {
        // Pronote style: semicolon separator, comma inside a field
        val t = CsvParser.parse("Nom;Prénom\nMartin, Jr;Emma".toByteArray())
        assertEquals(listOf("Martin, Jr", "Emma"), t.rows[0])
    }
    @Test fun quotedFieldsWithSeparatorAndEscapedQuotes() {
        val t = CsvParser.parse("Nom,Prénom\n\"Martin, Jr\",\"E\"\"mma\"".toByteArray())
        assertEquals(listOf("Martin, Jr", "E\"mma"), t.rows[0])
    }
    @Test fun quotedFieldWithNewline() {
        val t = CsvParser.parse("Nom,Info\nMartin,\"line1\nline2\"".toByteArray())
        assertEquals(listOf("Martin", "line1\nline2"), t.rows[0])
    }
    @Test fun windows1252Fallback() {
        val bytes = "Nom;Prénom\nDurand;Léo".toByteArray(Charset.forName("windows-1252"))
        val t = CsvParser.parse(bytes)
        assertEquals("Prénom", t.headers[1])
        assertEquals("Léo", t.rows[0][1])
    }
    @Test fun utf8BomStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Nom,Prénom\nA,B".toByteArray()
        assertEquals("Nom", CsvParser.parse(bytes).headers[0])
    }
    @Test fun malformedRowsSkippedAndReported() {
        val t = CsvParser.parse("Nom,Prénom\nMartin,Emma\nBrokenRowWithoutComma\nDurand,Léo".toByteArray())
        assertEquals(2, t.rows.size)
        assertEquals(listOf(3), t.skippedLines)
    }
    @Test fun emptyLinesIgnored() {
        val t = CsvParser.parse("Nom,Prénom\n\nMartin,Emma\n\n".toByteArray())
        assertEquals(1, t.rows.size)
        assertTrue(t.skippedLines.isEmpty())
    }
    @Test fun guessColumnFindsAccentAndCase() {
        assertEquals(1, CsvParser.guessColumn(listOf("Classe", "NOM", "Prénom"), LAST_NAME_HEADERS))
        assertEquals(2, CsvParser.guessColumn(listOf("Classe", "NOM", "Prénom"), FIRST_NAME_HEADERS))
        assertNull(CsvParser.guessColumn(listOf("x", "y"), LAST_NAME_HEADERS))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.CsvParserTest"`
Expected: compilation FAILURE.

- [ ] **Step 3: Implement `CsvParser.kt`**

```kotlin
package edu.fnosari.classmanager.domain

data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>,
    val skippedLines: List<Int>,
)

val LAST_NAME_HEADERS = listOf("nom", "nom de famille", "lastname", "last name", "name")
val FIRST_NAME_HEADERS = listOf("prénom", "prenom", "firstname", "first name")

object CsvParser {

    fun parse(bytes: ByteArray): CsvTable {
        val text = decode(bytes)
        val separator = detectSeparator(text)
        val records = splitRecords(text, separator)
        if (records.isEmpty()) return CsvTable(emptyList(), emptyList(), emptyList())
        val headers = records.first().fields.map { it.trim() }
        val rows = mutableListOf<List<String>>()
        val skipped = mutableListOf<Int>()
        for (rec in records.drop(1)) {
            if (rec.fields.size == 1 && rec.fields[0].isBlank()) continue
            if (rec.fields.size == headers.size) rows.add(rec.fields.map { it.trim() })
            else skipped.add(rec.lineNumber)
        }
        return CsvTable(headers, rows, skipped)
    }

    fun guessColumn(headers: List<String>, candidates: List<String>): Int? {
        val normalized = headers.map { it.trim().lowercase() }
        for (cand in candidates) {
            val i = normalized.indexOf(cand)
            if (i >= 0) return i
        }
        return null
    }

    private fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body)).toString()
        } catch (e: CharacterCodingException) {
            String(body, Charset.forName("windows-1252"))
        }
    }

    private fun detectSeparator(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var semis = 0; var commas = 0; var inQuotes = false
        for (ch in firstLine) when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ';' && !inQuotes -> semis++
            ch == ',' && !inQuotes -> commas++
        }
        return if (semis >= commas) ';' else ','
    }

    private class Record(val fields: List<String>, val lineNumber: Int)

    private fun splitRecords(text: String, sep: Char): List<Record> {
        val records = mutableListOf<Record>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordStartLine = 1
        var i = 0
        fun endField() { fields.add(field.toString()); field.clear() }
        fun endRecord() {
            endField()
            records.add(Record(fields, recordStartLine))
            fields = mutableListOf()
            recordStartLine = line
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                ch == sep && !inQuotes -> endField()
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    line++
                    endRecord()
                    recordStartLine = line
                }
                else -> {
                    if (ch == '\n') line++
                    field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.CsvParserTest"`
Expected: 10 tests PASS. If `emptyLinesIgnored` fails on trailing blank records, ensure blank single-field records are skipped (the `size == 1 && isBlank` guard).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/edu/fnosari/classmanager/domain/CsvParser.kt app/src/test/java/edu/fnosari/classmanager/domain/CsvParserTest.kt
git commit -m "feat: CSV parser with charset/separator detection and quoted fields"
```

---

### Task 6: Group generator (pure Kotlin, TDD)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/domain/GroupGenerator.kt`
- Test: `app/src/test/java/edu/fnosari/classmanager/domain/GroupGeneratorTest.kt`

**Interfaces:**
- Produces:
  - `enum class SplitMode { GROUPS_OF_N, N_GROUPS }`
  - `fun computeSizes(total: Int, mode: SplitMode, n: Int): List<Int>`
  - `sealed class GroupResult` with `data class Success(val groups: List<List<Long>>) : GroupResult()` and `data class Infeasible(val clashingPairs: List<Pair<Long, Long>>) : GroupResult()`
  - `fun generateGroups(studentIds: List<Long>, separations: Set<Pair<Long, Long>>, sizes: List<Int>, random: Random = Random.Default, maxAttempts: Int = 100): GroupResult`
  - Separations are unordered: `(a,b)` and `(b,a)` equivalent.

- [ ] **Step 1: Write failing tests**

```kotlin
class GroupGeneratorTest {
    @Test fun sizesGroupsOfN() {
        assertEquals(listOf(4, 4, 4), computeSizes(12, SplitMode.GROUPS_OF_N, 4))
        // remainder spread: 14 in groups of 4 -> 4,4,3,3 (never a group of 1-2 below n-1 when avoidable)
        assertEquals(listOf(4, 4, 3, 3), computeSizes(14, SplitMode.GROUPS_OF_N, 4))
        assertEquals(listOf(3, 3, 3, 2), computeSizes(11, SplitMode.GROUPS_OF_N, 3))
    }
    @Test fun sizesNGroups() {
        assertEquals(listOf(4, 4, 4), computeSizes(12, SplitMode.N_GROUPS, 3))
        assertEquals(listOf(5, 5, 4), computeSizes(14, SplitMode.N_GROUPS, 3))
    }
    @Test fun allStudentsPlacedExactlyOnce() {
        val ids = (1L..14L).toList()
        val r = generateGroups(ids, emptySet(), computeSizes(14, SplitMode.GROUPS_OF_N, 4),
            Random(42)) as GroupResult.Success
        assertEquals(ids.sorted(), r.groups.flatten().sorted())
        assertEquals(listOf(4, 4, 3, 3), r.groups.map { it.size })
    }
    @Test fun separationRespected() {
        val ids = (1L..8L).toList()
        val seps = setOf(1L to 2L, 3L to 4L)
        repeat(20) { seed ->
            val r = generateGroups(ids, seps, computeSizes(8, SplitMode.N_GROUPS, 2),
                Random(seed.toLong())) as GroupResult.Success
            for (g in r.groups) {
                assertFalse(g.contains(1L) && g.contains(2L))
                assertFalse(g.contains(3L) && g.contains(4L))
            }
        }
    }
    @Test fun reverseOrderPairAlsoRespected() {
        val ids = (1L..4L).toList()
        val r = generateGroups(ids, setOf(2L to 1L), listOf(2, 2), Random(1)) as GroupResult.Success
        for (g in r.groups) assertFalse(g.contains(1L) && g.contains(2L))
    }
    @Test fun infeasibleDetected() {
        // 3 mutually separated students, only 2 groups -> impossible
        val ids = listOf(1L, 2L, 3L, 4L)
        val seps = setOf(1L to 2L, 1L to 3L, 2L to 3L)
        val r = generateGroups(ids, seps, listOf(2, 2), Random(1))
        assertTrue(r is GroupResult.Infeasible)
        assertTrue((r as GroupResult.Infeasible).clashingPairs.isNotEmpty())
    }
    @Test fun tightButFeasibleSolved() {
        // 4 students, chain of separations, 2 groups of 2: 1-2, 2-3, 3-4 -> groups {1,3},{2,4} etc.
        val ids = listOf(1L, 2L, 3L, 4L)
        val seps = setOf(1L to 2L, 2L to 3L, 3L to 4L)
        val r = generateGroups(ids, seps, listOf(2, 2), Random(7))
        assertTrue(r is GroupResult.Success)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.GroupGeneratorTest"`
Expected: compilation FAILURE.

- [ ] **Step 3: Implement `GroupGenerator.kt`**

```kotlin
package edu.fnosari.classmanager.domain

enum class SplitMode { GROUPS_OF_N, N_GROUPS }

sealed class GroupResult {
    data class Success(val groups: List<List<Long>>) : GroupResult()
    data class Infeasible(val clashingPairs: List<Pair<Long, Long>>) : GroupResult()
}

fun computeSizes(total: Int, mode: SplitMode, n: Int): List<Int> {
    require(n > 0)
    if (total == 0) return emptyList()
    val groupCount = when (mode) {
        SplitMode.N_GROUPS -> minOf(n, total)
        SplitMode.GROUPS_OF_N -> (total + n - 1) / n
    }
    val base = total / groupCount
    val extra = total % groupCount
    return List(groupCount) { i -> base + if (i < extra) 1 else 0 }
}

fun generateGroups(
    studentIds: List<Long>,
    separations: Set<Pair<Long, Long>>,
    sizes: List<Int>,
    random: Random = Random.Default,
    maxAttempts: Int = 100,
): GroupResult {
    require(sizes.sum() == studentIds.size) { "sizes must sum to student count" }
    val forbidden = HashSet<Pair<Long, Long>>().apply {
        for ((a, b) in separations) { add(a to b); add(b to a) }
    }
    fun conflicts(id: Long, group: List<Long>) = group.any { (id to it) in forbidden }

    repeat(maxAttempts) {
        val order = studentIds.shuffled(random)
        val groups = sizes.map { mutableListOf<Long>() }
        if (assign(order, 0, groups, sizes, ::conflicts)) {
            return GroupResult.Success(groups.map { it.toList() })
        }
    }
    // Infeasible (or extremely unlucky): report constraints among the most-constrained students
    val degree = studentIds.associateWith { id -> separations.count { it.first == id || it.second == id } }
    val hot = studentIds.sortedByDescending { degree[it] ?: 0 }.take(5).toSet()
    val clashing = separations.filter { it.first in hot || it.second in hot }
    return GroupResult.Infeasible(clashing.ifEmpty { separations.toList() })
}

private fun assign(
    order: List<Long>,
    index: Int,
    groups: List<MutableList<Long>>,
    sizes: List<Int>,
    conflicts: (Long, List<Long>) -> Boolean,
): Boolean {
    if (index == order.size) return true
    val id = order[index]
    // try groups smallest-fill-first so sizes stay balanced
    val candidates = groups.indices
        .filter { groups[it].size < sizes[it] && !conflicts(id, groups[it]) }
        .sortedBy { groups[it].size }
    for (g in candidates) {
        groups[g].add(id)
        if (assign(order, index + 1, groups, sizes, conflicts)) return true
        groups[g].removeAt(groups[g].size - 1)
    }
    return false
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.domain.GroupGeneratorTest"`
Expected: 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/edu/fnosari/classmanager/domain/GroupGenerator.kt app/src/test/java/edu/fnosari/classmanager/domain/GroupGeneratorTest.kt
git commit -m "feat: constraint-based group generator with backtracking"
```

---

### Task 7: App skeleton — Application, AppContainer, navigation, strings

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ClassManagerApp.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/AppContainer.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-fr/strings.xml`

**Interfaces:**
- Consumes: `AppDatabase`, `SettingsRepository`.
- Produces:
  - `class ClassManagerApp : Application { val container: AppContainer }`
  - `class AppContainer(context: Context)` exposing `db: AppDatabase`, `settings: SettingsRepository`, `photosDir: File` (later tasks add `alarms`, `backup`)
  - `Context.appContainer` extension
  - Route constants in `Routes` object; `AppNavHost(navController, startStudentId: Long?)`
  - Screens registered as placeholder composables replaced by later tasks.

- [ ] **Step 1: `ClassManagerApp.kt` + `AppContainer.kt`**

```kotlin
package edu.fnosari.classmanager

class ClassManagerApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ClassManagerApp).container
```

```kotlin
package edu.fnosari.classmanager

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val photosDir: File = File(context.filesDir, "photos").apply { mkdirs() }
}
```

- [ ] **Step 2: `Nav.kt` routes + NavHost with placeholder screens**

```kotlin
package edu.fnosari.classmanager.ui

object Routes {
    const val CLASS_LIST = "classes"
    const val CLASS_DETAIL = "class/{classId}"
    const val STUDENT = "student/{studentId}"
    const val PICKER = "picker/{classId}"
    const val GROUPS = "groups/{classId}"
    const val CSV_IMPORT = "csvImport"
    const val SETTINGS = "settings"
    fun classDetail(id: Long) = "class/$id"
    fun student(id: Long) = "student/$id"
    fun picker(classId: Long) = "picker/$classId"
    fun groups(classId: Long) = "groups/$classId"
}

@Composable
fun AppNavHost(nav: NavHostController, startStudentId: Long?) {
    NavHost(navController = nav, startDestination = Routes.CLASS_LIST) {
        composable(Routes.CLASS_LIST) { PlaceholderScreen("Classes") }
        composable(Routes.CLASS_DETAIL,
            arguments = listOf(navArgument("classId") { type = NavType.LongType })) {
            PlaceholderScreen("Class ${it.arguments!!.getLong("classId")}")
        }
        composable(Routes.STUDENT,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })) {
            PlaceholderScreen("Student ${it.arguments!!.getLong("studentId")}")
        }
        composable(Routes.PICKER,
            arguments = listOf(navArgument("classId") { type = NavType.LongType })) {
            PlaceholderScreen("Picker")
        }
        composable(Routes.GROUPS,
            arguments = listOf(navArgument("classId") { type = NavType.LongType })) {
            PlaceholderScreen("Groups")
        }
        composable(Routes.CSV_IMPORT) { PlaceholderScreen("CSV import") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Settings") }
    }
    LaunchedEffect(startStudentId) {
        if (startStudentId != null && startStudentId > 0) nav.navigate(Routes.student(startStudentId))
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
```

(Placeholder composables here are scaffolding replaced by Tasks 8–15; each of those tasks swaps its `PlaceholderScreen` line for the real screen.)

- [ ] **Step 3: Rewrite `MainActivity.kt`**

```kotlin
package edu.fnosari.classmanager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startStudentId = intent.getLongExtra(EXTRA_STUDENT_ID, -1L).takeIf { it > 0 }
        setContent {
            ClassManagerTheme {
                val nav = rememberNavController()
                AppNavHost(nav, startStudentId)
            }
        }
    }
    companion object { const val EXTRA_STUDENT_ID = "studentId" }
}
```

- [ ] **Step 4: Manifest — register Application**

In `<application>` tag add `android:name=".ClassManagerApp"`.

- [ ] **Step 5: Strings — base set**

`res/values/strings.xml` (add; keep `app_name`):

```xml
<string name="classes_title">Classes</string>
<string name="new_class">New class</string>
<string name="import_csv">Import CSV</string>
<string name="settings">Settings</string>
<string name="students">Students</string>
<string name="timetable">Timetable</string>
<string name="random_picker">Random picker</string>
<string name="groups">Groups</string>
<string name="cancel">Cancel</string>
<string name="save">Save</string>
<string name="delete">Delete</string>
<string name="confirm_delete_class">Delete this class and all its students?</string>
```

`res/values-fr/strings.xml` (same keys, French):

```xml
<string name="classes_title">Classes</string>
<string name="new_class">Nouvelle classe</string>
<string name="import_csv">Importer un CSV</string>
<string name="settings">Réglages</string>
<string name="students">Élèves</string>
<string name="timetable">Emploi du temps</string>
<string name="random_picker">Tirage au sort</string>
<string name="groups">Groupes</string>
<string name="cancel">Annuler</string>
<string name="save">Enregistrer</string>
<string name="delete">Supprimer</string>
<string name="confirm_delete_class">Supprimer cette classe et tous ses élèves ?</string>
```

Later tasks add their own keys to BOTH files as they build UI — that instruction is implicit in every UI task below.

- [ ] **Step 6: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat: app skeleton - Application, AppContainer, NavHost, FR/EN strings"
```

---

### Task 8: Class list screen (CRUD classes)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/classlist/ClassListViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/classlist/ClassListScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt` (swap placeholder)

**Interfaces:**
- Consumes: `ClassDao`, `Routes`.
- Produces: `ClassListScreen(onOpenClass: (Long) -> Unit, onImportCsv: () -> Unit, onSettings: () -> Unit)`.

- [ ] **Step 1: ViewModel**

```kotlin
package edu.fnosari.classmanager.ui.classlist

data class ClassRow(val schoolClass: SchoolClass, val studentCount: Int)

class ClassListViewModel(private val db: AppDatabase) : ViewModel() {
    val classes: StateFlow<List<ClassRow>> = db.classDao().all()
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { c ->
                db.classDao().studentCount(c.id).map { ClassRow(c, it) }
            }) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, level: String) = viewModelScope.launch {
        db.classDao().insert(SchoolClass(name = name.trim(), level = level.trim()))
    }
    fun rename(c: SchoolClass, name: String, level: String) = viewModelScope.launch {
        db.classDao().update(c.copy(name = name.trim(), level = level.trim()))
    }
    fun delete(c: SchoolClass) = viewModelScope.launch { db.classDao().delete(c) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ClassListViewModel(container.db) }
        }
    }
}
```

Every subsequent ViewModel follows this exact `companion object { factory(container) }` pattern — obtain in composables with `viewModel(factory = XViewModel.factory(LocalContext.current.appContainer))`.

- [ ] **Step 2: Screen**

```kotlin
package edu.fnosari.classmanager.ui.classlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassListScreen(onOpenClass: (Long) -> Unit, onImportCsv: () -> Unit, onSettings: () -> Unit) {
    val vm: ClassListViewModel =
        viewModel(factory = ClassListViewModel.factory(LocalContext.current.appContainer))
    val classes by vm.classes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SchoolClass?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.classes_title)) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                })
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabMenu) {
                    SmallFloatingActionButton(onClick = { fabMenu = false; onImportCsv() }) {
                        Icon(Icons.Default.FileUpload, stringResource(R.string.import_csv))
                    }
                    Spacer(Modifier.height(8.dp))
                    SmallFloatingActionButton(onClick = { fabMenu = false; showCreate = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_class))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FloatingActionButton(onClick = { fabMenu = !fabMenu }) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(classes, key = { it.schoolClass.id }) { row ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = { onOpenClass(row.schoolClass.id) },
                        onLongClick = { editing = row.schoolClass })) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.schoolClass.name, style = MaterialTheme.typography.titleMedium)
                            Text("${row.schoolClass.level} — ${row.studentCount}",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { deleting = row.schoolClass }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    if (showCreate) ClassDialog(null, onDismiss = { showCreate = false }) { n, l ->
        vm.create(n, l); showCreate = false
    }
    editing?.let { c ->
        ClassDialog(c, onDismiss = { editing = null }) { n, l -> vm.rename(c, n, l); editing = null }
    }
    deleting?.let { c ->
        AlertDialog(onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_class)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(c); deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun ClassDialog(existing: SchoolClass?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var level by remember { mutableStateOf(existing?.level ?: "") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.new_class else R.string.classes_title)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.class_name)) })
                OutlinedTextField(level, { level = it }, label = { Text(stringResource(R.string.class_level)) })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, level) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
```

Add string keys `class_name` ("Class name"/"Nom de la classe") and `class_level` ("Level"/"Niveau") to both strings files.

- [ ] **Step 3: Wire into Nav.kt**

Replace the CLASS_LIST placeholder line with:

```kotlin
composable(Routes.CLASS_LIST) {
    ClassListScreen(
        onOpenClass = { nav.navigate(Routes.classDetail(it)) },
        onImportCsv = { nav.navigate(Routes.CSV_IMPORT) },
        onSettings = { nav.navigate(Routes.SETTINGS) })
}
```

- [ ] **Step 4: Build + manual check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL. If device/emulator available: install, create/rename/delete a class.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: class list screen with CRUD"
```

---

### Task 9: Photo utilities + class detail screen (students CRUD, timetable editor)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/common/PhotoUtil.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/classdetail/ClassDetailViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/classdetail/ClassDetailScreen.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/timetable/TimetableEditor.kt`
- Modify: `app/src/main/AndroidManifest.xml` (FileProvider)
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `StudentDao`, `TimetableDao`, `AppContainer.photosDir`.
- Produces:
  - `object PhotoUtil { fun importPhoto(context: Context, source: Uri, photosDir: File): String }` — decodes, downscales to max 512px, saves JPEG q85, returns relative path `"photos/<uuid>.jpg"`. `fun photoFile(context: Context, relPath: String): File`. `fun newCaptureUri(context: Context): Pair<Uri, File>` — FileProvider temp uri for camera.
  - `ClassDetailScreen(classId: Long, onOpenStudent: (Long) -> Unit, onPicker: () -> Unit, onGroups: () -> Unit, onBack: () -> Unit)` with tabs Students / Timetable.

- [ ] **Step 1: `PhotoUtil.kt`**

```kotlin
package edu.fnosari.classmanager.ui.common

object PhotoUtil {
    fun importPhoto(context: Context, source: Uri, photosDir: File): String {
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 512) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: throw IOException("cannot decode image")
        val scale = 512f / maxOf(bitmap.width, bitmap.height)
        val finalBmp = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
        ) else bitmap
        val name = "${UUID.randomUUID()}.jpg"
        File(photosDir, name).outputStream().use {
            finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        return "photos/$name"
    }

    fun photoFile(context: Context, relPath: String): File = File(context.filesDir, relPath)

    fun newCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }
}
```

- [ ] **Step 2: FileProvider in manifest + `file_paths.xml`**

`res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="capture" path="capture/" />
</paths>
```

In `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Also add (needed for camera capture, no runtime permission required for `TakePicture` contract):

```xml
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] **Step 3: `ClassDetailViewModel.kt`**

```kotlin
package edu.fnosari.classmanager.ui.classdetail

class ClassDetailViewModel(
    private val container: AppContainer,
    private val classId: Long,
) : ViewModel() {
    private val db = container.db
    val schoolClass: StateFlow<SchoolClass?> = flow { emit(db.classDao().byId(classId)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val students: StateFlow<List<Student>> = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val slots: StateFlow<List<TimetableSlot>> = db.timetableDao().slotsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addStudent(last: String, first: String, photoUri: Uri?, context: Context) =
        viewModelScope.launch(Dispatchers.IO) {
            val path = photoUri?.let {
                runCatching { PhotoUtil.importPhoto(context, it, container.photosDir) }.getOrNull()
            }
            db.studentDao().insert(Student(classId = classId,
                lastName = last.trim(), firstName = first.trim(), photoPath = path))
        }
    fun updateStudent(s: Student, last: String, first: String, photoUri: Uri?, context: Context) =
        viewModelScope.launch(Dispatchers.IO) {
            val path = photoUri?.let {
                runCatching { PhotoUtil.importPhoto(context, it, container.photosDir) }.getOrNull()
            } ?: s.photoPath
            db.studentDao().update(s.copy(lastName = last.trim(), firstName = first.trim(), photoPath = path))
        }
    fun deleteStudent(s: Student) = viewModelScope.launch {
        db.studentDao().delete(s)
        s.photoPath?.let { File(container.photosDir.parentFile, it).delete() }
    }
    fun addSlot(day: Int, start: String, end: String, parity: WeekParityTag) = viewModelScope.launch {
        db.timetableDao().insert(TimetableSlot(classId = classId, dayOfWeek = day,
            startTime = start, endTime = end, weekParity = parity))
    }
    fun deleteSlot(t: TimetableSlot) = viewModelScope.launch { db.timetableDao().delete(t) }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { ClassDetailViewModel(container, classId) }
        }
    }
}
```

- [ ] **Step 4: `ClassDetailScreen.kt`**

Structure (write fully, following Task 8 idioms):
- `Scaffold` with `TopAppBar` (class name title, back arrow → `onBack`), actions: picker icon (`Icons.Default.Casino` → `onPicker`), groups icon (`Icons.Default.Groups` → `onGroups`).
- `TabRow` with two tabs: `stringResource(R.string.students)`, `stringResource(R.string.timetable)`; `var tab by remember { mutableStateOf(0) }`.
- Students tab: `LazyVerticalGrid(GridCells.Adaptive(96.dp))` of student cells — `AsyncImage` (Coil) with `model = PhotoUtil.photoFile(context, path)` when `photoPath != null` else `Icons.Default.Person` placeholder in a `CircleShape` box, name below, click → `onOpenStudent(s.id)`, long-press → edit dialog. FAB `Icons.Default.PersonAdd` → add dialog.
- Student add/edit dialog: `OutlinedTextField` last name + first name; photo row with two buttons — gallery via `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` and camera via `ActivityResultContracts.TakePicture()` using `PhotoUtil.newCaptureUri` (remember the pending uri in state); preview selected image with `AsyncImage`; delete button when editing (deletes student after confirm dialog).
- Timetable tab: renders `TimetableEditor(slots, onAdd = vm::addSlot, onDelete = vm::deleteSlot)`.

- [ ] **Step 5: `TimetableEditor.kt`**

```kotlin
package edu.fnosari.classmanager.ui.timetable

@Composable
fun TimetableEditor(
    slots: List<TimetableSlot>,
    onAdd: (day: Int, start: String, end: String, parity: WeekParityTag) -> Unit,
    onDelete: (TimetableSlot) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(slots, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text("${dayName(s.dayOfWeek)} ${s.startTime}–${s.endTime}") },
                    supportingContent = {
                        if (s.weekParity != WeekParityTag.BOTH)
                            Text(stringResource(R.string.week_parity_label, s.weekParity.name))
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(s) }) { Icon(Icons.Default.Delete, null) }
                    })
            }
        }
        Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_slot))
        }
    }
    if (showAdd) SlotDialog(onDismiss = { showAdd = false }) { d, st, en, p ->
        onAdd(d, st, en, p); showAdd = false
    }
}

@Composable
private fun dayName(iso: Int): String {
    val ids = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu,
        R.string.fri, R.string.sat, R.string.sun)
    return stringResource(ids[iso - 1])
}
```

`SlotDialog`: day dropdown (`ExposedDropdownMenuBox` over the 7 day names), two time fields using `TimePickerDialog`-style inputs — simplest robust approach: two `OutlinedTextField`s prefilled "08:00"/"09:00" validated against `Regex("([01]\\d|2[0-3]):[0-5]\\d")`, save disabled until both match and end > start; parity segmented buttons BOTH/A/B (`SegmentedButtonRow`). Add string keys: `add_slot`, `week_parity_label` ("Week %1$s"/"Semaine %1$s"), `mon`..`sun` (Mon/Lun, Tue/Mar, Wed/Mer, Thu/Jeu, Fri/Ven, Sat/Sam, Sun/Dim) in both languages.

- [ ] **Step 6: Wire into Nav.kt**

```kotlin
composable(Routes.CLASS_DETAIL,
    arguments = listOf(navArgument("classId") { type = NavType.LongType })) {
    val classId = it.arguments!!.getLong("classId")
    ClassDetailScreen(classId,
        onOpenStudent = { id -> nav.navigate(Routes.student(id)) },
        onPicker = { nav.navigate(Routes.picker(classId)) },
        onGroups = { nav.navigate(Routes.groups(classId)) },
        onBack = { nav.popBackStack() })
}
```

- [ ] **Step 7: Build + manual check**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL. Manual: add student with gallery photo, with camera photo, edit, delete; add/delete timetable slots.

- [ ] **Step 8: Commit**

```bash
git add app/src/main
git commit -m "feat: class detail with student CRUD, photos, timetable editor"
```

---

### Task 10: Student detail screen (notes, custom fields; reminders list added in Task 14)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/student/StudentDetailViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/student/StudentDetailScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `StudentDao.byIdFlow`, `NoteDao`, `CustomFieldDao`.
- Produces: `StudentDetailScreen(studentId: Long, onBack: () -> Unit)`. ViewModel exposes `student: StateFlow<Student?>`, `notes: StateFlow<List<Note>>`, `fields: StateFlow<List<CustomField>>`, `reminders: StateFlow<List<Reminder>>` (wired to UI in Task 14) and `addNote/updateNote/deleteNote/addField/updateField/deleteField` — each a `viewModelScope.launch` DAO call mirroring Task 9's idiom.

- [ ] **Step 1: ViewModel**

```kotlin
package edu.fnosari.classmanager.ui.student

class StudentDetailViewModel(container: AppContainer, private val studentId: Long) : ViewModel() {
    private val db = container.db
    val student = db.studentDao().byIdFlow(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val notes = db.noteDao().notesFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fields = db.customFieldDao().fieldsFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders = db.reminderDao().remindersFor(studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(text: String) = viewModelScope.launch {
        db.noteDao().insert(Note(studentId = studentId, text = text.trim()))
    }
    fun updateNote(n: Note, text: String) = viewModelScope.launch { db.noteDao().update(n.copy(text = text.trim())) }
    fun deleteNote(n: Note) = viewModelScope.launch { db.noteDao().delete(n) }
    fun addField(key: String, value: String) = viewModelScope.launch {
        db.customFieldDao().insert(CustomField(studentId = studentId, key = key.trim(), value = value.trim()))
    }
    fun updateField(f: CustomField, key: String, value: String) = viewModelScope.launch {
        db.customFieldDao().update(f.copy(key = key.trim(), value = value.trim()))
    }
    fun deleteField(f: CustomField) = viewModelScope.launch { db.customFieldDao().delete(f) }

    companion object {
        fun factory(container: AppContainer, studentId: Long) = viewModelFactory {
            initializer { StudentDetailViewModel(container, studentId) }
        }
    }
}
```

- [ ] **Step 2: Screen**

Single `LazyColumn` in a `Scaffold` (top bar: "lastName firstName", back arrow):
1. Header item: 96dp round `AsyncImage`/person icon, name, class.
2. Section header "Custom fields" (`R.string.custom_fields`) with add `IconButton` → key/value dialog (two `OutlinedTextField`s). Items: `ListItem(headline=value, overline=key)`, click → edit dialog, trailing delete icon.
3. Section header "Notes" (`R.string.notes`) with add `IconButton` → multiline text dialog. Items: `Card` with note text + `DateFormat.getDateInstance().format(Date(createdAt))`, click → edit, trailing delete.
4. Section header "Reminders" (`R.string.reminders`) — placeholder empty list rendering; creation UI comes in Task 14.

Dialogs follow Task 8 `ClassDialog` idiom exactly (AlertDialog, save disabled on blank). String keys to add (both languages): `custom_fields` ("Custom fields"/"Informations"), `notes` ("Notes"/"Notes"), `reminders` ("Reminders"/"Rappels"), `add_note` ("Add note"/"Ajouter une note"), `add_field` ("Add field"/"Ajouter une information"), `field_key` ("Label"/"Intitulé"), `field_value` ("Value"/"Valeur").

- [ ] **Step 3: Wire into Nav.kt** — replace STUDENT placeholder:

```kotlin
composable(Routes.STUDENT,
    arguments = listOf(navArgument("studentId") { type = NavType.LongType })) {
    StudentDetailScreen(it.arguments!!.getLong("studentId"), onBack = { nav.popBackStack() })
}
```

- [ ] **Step 4: Build + manual check**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. Manual: add/edit/delete note and field.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: student detail with notes and custom fields"
```

---

### Task 11: Random picker screen

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/picker/PickerViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/picker/PickerScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `StudentDao.eligibleForPick/setPicked/resetCycle/setAbsent`, `studentsIn`.
- Produces: `PickerScreen(classId: Long, onBack: () -> Unit)`.

- [ ] **Step 1: ViewModel**

```kotlin
package edu.fnosari.classmanager.ui.picker

class PickerViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val db = container.db
    private fun today(): String = LocalDate.now().toString()

    val students = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _current = MutableStateFlow<Student?>(null)
    val current: StateFlow<Student?> = _current
    private val _cycleJustReset = MutableStateFlow(false)
    val cycleJustReset: StateFlow<Boolean> = _cycleJustReset

    fun pick() = viewModelScope.launch {
        var eligible = db.studentDao().eligibleForPick(classId, today())
        _cycleJustReset.value = false
        if (eligible.isEmpty()) {
            db.studentDao().resetCycle(classId)
            _cycleJustReset.value = true
            eligible = db.studentDao().eligibleForPick(classId, today())
        }
        val chosen = eligible.randomOrNull() ?: return@launch
        db.studentDao().setPicked(chosen.id, true)
        _current.value = chosen
    }
    fun resetCycle() = viewModelScope.launch {
        db.studentDao().resetCycle(classId); _current.value = null; _cycleJustReset.value = false
    }
    fun toggleAbsent(s: Student) = viewModelScope.launch {
        db.studentDao().setAbsent(s.id, if (s.absentTodayDate == today()) null else today())
    }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { PickerViewModel(container, classId) }
        }
    }
}
```

(`eligibleForPick` compares `absentTodayDate != today` — stale absence dates from previous days are automatically eligible again; no cleanup job needed.)

- [ ] **Step 2: Screen**

Layout in `Scaffold` (top bar back + reset `IconButton` `Icons.Default.RestartAlt` → `vm.resetCycle()`):
- Center: large `Card` (240dp) showing current pick — photo (`AsyncImage`, 160dp circle) + name in `headlineMedium`; `AnimatedContent` keyed on `current?.id` with `slideInVertically + fadeIn` transition for reveal effect. Empty state before first pick: dice icon + `R.string.tap_to_pick`.
- If `cycleJustReset`: `Text(stringResource(R.string.cycle_restarted))` badge above card.
- Cycle progress: `Text("${students.count { it.pickedInCurrentCycle }}/${students.size}")` + `LinearProgressIndicator`.
- Big `Button` `R.string.pick` → `vm.pick()`.
- Below, collapsible absence list: each student as `FilterChip(selected = s.absentTodayDate == today, label = name, onClick = { vm.toggleAbsent(s) })` in a `FlowRow`.

String keys (both languages): `pick` ("Pick"/"Tirer"), `tap_to_pick` ("Tap Pick to draw a student"/"Appuyez sur Tirer pour choisir un élève"), `cycle_restarted` ("Everyone was picked — new round!"/"Tout le monde est passé — nouveau tour !"), `absent_today` ("Absent today"/"Absents aujourd'hui"), `reset_cycle` ("Reset round"/"Réinitialiser le tour").

- [ ] **Step 3: Wire into Nav.kt** — replace PICKER placeholder with `PickerScreen(classId, onBack = { nav.popBackStack() })` following Task 9 Step 6 argument idiom.

- [ ] **Step 4: Build + manual check**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. Manual: picks exclude absent + already-picked; auto reset works.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: fair random picker with absence toggles"
```

---

### Task 12: Groups screen (constraints editor, generation, manual edit, saved groupings)

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/groups/GroupsViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/groups/GroupsScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `GroupDao`, `StudentDao`, `generateGroups`, `computeSizes`, `SplitMode`, `GroupResult`.
- Produces: `GroupsScreen(classId: Long, onBack: () -> Unit)`.

- [ ] **Step 1: ViewModel**

```kotlin
package edu.fnosari.classmanager.ui.groups

data class DraftGroups(val groups: List<List<Student>>, val violations: Set<Pair<Long, Long>>)

class GroupsViewModel(container: AppContainer, private val classId: Long) : ViewModel() {
    private val db = container.db
    val students = db.studentDao().studentsIn(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val constraints = db.groupDao().constraintsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupings = db.groupDao().groupingsFor(classId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var splitMode by mutableStateOf(SplitMode.GROUPS_OF_N)
    var n by mutableIntStateOf(4)
    var excludeAbsent by mutableStateOf(true)
    private val _draft = MutableStateFlow<DraftGroups?>(null)
    val draft: StateFlow<DraftGroups?> = _draft
    private val _infeasible = MutableStateFlow<List<Pair<Long, Long>>?>(null)
    val infeasible: StateFlow<List<Pair<Long, Long>>?> = _infeasible

    fun addConstraint(a: Long, b: Long) = viewModelScope.launch {
        if (a != b) db.groupDao().insertConstraint(SeparationConstraint(classId = classId, studentAId = a, studentBId = b))
    }
    fun removeConstraint(c: SeparationConstraint) = viewModelScope.launch { db.groupDao().deleteConstraint(c) }

    fun generate() = viewModelScope.launch {
        _infeasible.value = null
        val today = LocalDate.now().toString()
        val pool = students.value.filter { !excludeAbsent || it.absentTodayDate != today }
        if (pool.isEmpty()) return@launch
        val seps = constraints.value.map { it.studentAId to it.studentBId }.toSet()
        val sizes = computeSizes(pool.size, splitMode, n)
        when (val r = generateGroups(pool.map { it.id }, seps, sizes)) {
            is GroupResult.Success -> {
                val byId = pool.associateBy { it.id }
                _draft.value = DraftGroups(r.groups.map { g -> g.map { byId.getValue(it) } }, emptySet())
            }
            is GroupResult.Infeasible -> _infeasible.value = r.clashingPairs
        }
    }

    fun moveStudent(student: Student, toGroup: Int) {
        val d = _draft.value ?: return
        val groups = d.groups.map { it.toMutableList() }
        groups.forEach { it.remove(student) }
        groups[toGroup].add(student)
        val seps = constraints.value.map { it.studentAId to it.studentBId }.toSet()
        val violations = mutableSetOf<Pair<Long, Long>>()
        for (g in groups) for (a in g) for (b in g) {
            if (a.id < b.id && ((a.id to b.id) in seps || (b.id to a.id) in seps))
                violations.add(a.id to b.id)
        }
        _draft.value = DraftGroups(groups.map { it.toList() }, violations)
    }

    fun saveDraft(name: String) = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        val gId = db.groupDao().insertGrouping(Grouping(classId = classId, name = name.trim()))
        d.groups.forEachIndexed { i, members ->
            val groupId = db.groupDao().insertGroup(GroupingGroup(groupingId = gId, index = i))
            members.forEach { db.groupDao().insertMember(GroupingMember(groupId = groupId, studentId = it.id)) }
        }
        _draft.value = null
    }
    fun renameGrouping(g: Grouping, name: String) = viewModelScope.launch {
        db.groupDao().updateGrouping(g.copy(name = name.trim()))
    }
    fun deleteGrouping(g: Grouping) = viewModelScope.launch { db.groupDao().deleteGrouping(g) }
    suspend fun loadGrouping(g: Grouping): List<List<Student>> {
        val byId = students.value.associateBy { it.id }
        return db.groupDao().groupsOf(g.id).map { gg ->
            db.groupDao().membersOf(gg.id).mapNotNull { byId[it.studentId] }
        }
    }

    companion object {
        fun factory(container: AppContainer, classId: Long) = viewModelFactory {
            initializer { GroupsViewModel(container, classId) }
        }
    }
}
```

- [ ] **Step 2: Screen**

Three-section `LazyColumn` in `Scaffold` (top bar back, title `R.string.groups`):
1. **Constraints**: header + add `IconButton` → dialog with two student `ExposedDropdownMenuBox` pickers (from `students`) + save calling `vm.addConstraint`. Each constraint rendered as `InputChip("A ✕ B", trailingIcon = close → vm.removeConstraint)`.
2. **Generate**: `SingleChoiceSegmentedButtonRow` for split mode (`R.string.groups_of_n` "Groups of N"/"Groupes de N", `R.string.n_groups` "N groups"/"N groupes"); stepper row (−/+ IconButtons around `Text("$n")`, min 1 max 15); `Checkbox` + label `R.string.exclude_absent` ("Exclude absent"/"Exclure les absents"); `Button` `R.string.generate` → `vm.generate()`.
   - If `infeasible != null`: `Card(colors = error)` listing clashing pairs by student names + `R.string.infeasible_message` ("These constraints cannot all be satisfied:"/"Ces contraintes ne peuvent pas toutes être respectées :").
3. **Draft result** (when `draft != null`): one `Card` per group titled `stringResource(R.string.group_n, i+1)` ("Group %1$d"/"Groupe %1$d") listing members; member row click opens a "move to group" dropdown (menu of other group indices → `vm.moveStudent`); member pair in `draft.violations` rendered with `MaterialTheme.colorScheme.error` text color. Buttons row: `R.string.reshuffle` ("Reshuffle"/"Relancer") → `vm.generate()`, `R.string.save` → name dialog → `vm.saveDraft(name)`.
4. **Saved groupings**: list of `ListItem(headline = name, supporting = formatted date)`; click → load via `vm.loadGrouping` (in `rememberCoroutineScope().launch`) shown read-only in the same group-cards UI; long-press → rename dialog; trailing delete icon → confirm dialog → `vm.deleteGrouping`.

- [ ] **Step 3: Wire into Nav.kt** — replace GROUPS placeholder with `GroupsScreen(classId, onBack = { nav.popBackStack() })`.

- [ ] **Step 4: Build + manual check**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. Manual: add constraint, generate, verify pair separated, force infeasible (3 mutual constraints, 2 groups), manual move flags red, save + reload grouping.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: group generator screen with constraints and saved groupings"
```

---

### Task 13: CSV import screen

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/csv/CsvImportViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/csv/CsvImportScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `CsvParser`, `CsvTable`, `LAST_NAME_HEADERS`, `FIRST_NAME_HEADERS`, `SettingsRepository.csvMapping/setCsvMapping`, `ClassDao.insert`, `StudentDao.insertAll`.
- Produces: `CsvImportScreen(onDone: (classId: Long) -> Unit, onBack: () -> Unit)`.

- [ ] **Step 1: ViewModel**

```kotlin
package edu.fnosari.classmanager.ui.csv

class CsvImportViewModel(private val container: AppContainer) : ViewModel() {
    var table by mutableStateOf<CsvTable?>(null)
    var lastNameCol by mutableStateOf<Int?>(null)
    var firstNameCol by mutableStateOf<Int?>(null)
    var className by mutableStateOf("")
    var classLevel by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    fun load(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val t = CsvParser.parse(bytes)
            if (t.headers.isEmpty() || t.rows.isEmpty()) { error = "empty"; return@launch }
            val saved = container.settings.csvMapping.first()
            table = t
            lastNameCol = saved?.first?.takeIf { it < t.headers.size }
                ?: CsvParser.guessColumn(t.headers, LAST_NAME_HEADERS)
            firstNameCol = saved?.second?.takeIf { it < t.headers.size }
                ?: CsvParser.guessColumn(t.headers, FIRST_NAME_HEADERS)
            error = null
        } catch (e: Exception) { error = "read" }
    }

    fun preview(): List<Pair<String, String>> {
        val t = table ?: return emptyList()
        val l = lastNameCol ?: return emptyList()
        val f = firstNameCol ?: return emptyList()
        return t.rows.map { it.getOrElse(l) { "" } to it.getOrElse(f) { "" } }
    }

    fun import(onDone: (Long) -> Unit) = viewModelScope.launch {
        val t = table ?: return@launch
        val l = lastNameCol ?: return@launch
        val f = firstNameCol ?: return@launch
        val classId = container.db.classDao().insert(
            SchoolClass(name = className.trim(), level = classLevel.trim()))
        container.db.studentDao().insertAll(t.rows
            .filter { it.getOrElse(l) { "" }.isNotBlank() }
            .map { Student(classId = classId,
                lastName = it.getOrElse(l) { "" }.trim(), firstName = it.getOrElse(f) { "" }.trim()) })
        container.settings.setCsvMapping(l, f)
        onDone(classId)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CsvImportViewModel(container) }
        }
    }
}
```

- [ ] **Step 2: Screen**

Flow in one screen, `Scaffold` + `LazyColumn`:
1. If `table == null`: big `Button` `R.string.pick_csv_file` launching `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.load(context, it) } }` with mime types `arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")`. Show error text if `error != null` (`R.string.csv_error_empty`, `R.string.csv_error_read`).
2. When loaded: two `ExposedDropdownMenuBox` selectors labeled `R.string.column_last_name` / `R.string.column_first_name` over `table.headers` (selected = current col index).
3. Preview: first 10 of `vm.preview()` as `ListItem("$last $first")`; if `table.skippedLines.isNotEmpty()` show `stringResource(R.string.skipped_lines, table.skippedLines.joinToString())` in error color.
4. `OutlinedTextField`s for `className` / `classLevel` (reuse keys `class_name`, `class_level`).
5. `Button` `R.string.import_confirm` enabled when both columns set and `className.isNotBlank()` → `vm.import(onDone)`.

String keys (both languages): `pick_csv_file` ("Choose CSV file"/"Choisir un fichier CSV"), `column_last_name` ("Last-name column"/"Colonne nom"), `column_first_name` ("First-name column"/"Colonne prénom"), `skipped_lines` ("Skipped malformed lines: %1$s"/"Lignes ignorées : %1$s"), `import_confirm` ("Import"/"Importer"), `csv_error_empty` ("File is empty or has no data rows"/"Fichier vide ou sans données"), `csv_error_read` ("Cannot read this file"/"Impossible de lire ce fichier").

- [ ] **Step 3: Wire into Nav.kt** — replace CSV_IMPORT placeholder:

```kotlin
composable(Routes.CSV_IMPORT) {
    CsvImportScreen(
        onDone = { id -> nav.navigate(Routes.classDetail(id)) { popUpTo(Routes.CLASS_LIST) } },
        onBack = { nav.popBackStack() })
}
```

- [ ] **Step 4: Build + manual check**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. Manual: import a semicolon CSV with `Nom;Prénom` headers → columns pre-guessed → class created with students.

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git commit -m "feat: CSV import with column mapping and preview"
```

---

### Task 14: Notifications — channels, alarm scheduling, receivers, reminder creation UI

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/notifications/NotificationHelper.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/notifications/AlarmScheduler.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/notifications/ReminderReceiver.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/notifications/DigestReceiver.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/notifications/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/edu/fnosari/classmanager/AppContainer.kt` (add `alarms: AlarmScheduler`)
- Modify: `app/src/main/java/edu/fnosari/classmanager/ClassManagerApp.kt` (create channels, ensure digest scheduled)
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/student/StudentDetailViewModel.kt` + `StudentDetailScreen.kt` (reminder CRUD UI)

**Interfaces:**
- Consumes: `ReminderDao`, `TimetableDao`, `SettingsRepository`, `nextLessonStart`, `MainActivity.EXTRA_STUDENT_ID`.
- Produces:
  - `class AlarmScheduler(context, db, settings)`: `suspend fun scheduleReminder(r: Reminder)`, `fun cancelReminder(id: Long)`, `suspend fun scheduleDailyDigest()`, `suspend fun rescheduleAll()`
  - `object NotificationHelper`: `fun createChannels(context)`, `fun showReminder(context, reminder, student)`, `fun showDigest(context, lines: List<String>)`
  - StudentDetailViewModel gains `addReminder(text: String, type: ReminderType, fixedAt: Long?): ReminderCreateResult` where `enum class ReminderCreateResult { OK, NO_TIMETABLE }`, plus `markDone(r: Reminder)`, `deleteReminder(r: Reminder)`.

- [ ] **Step 1: Manifest additions**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Inside `<application>`:

```xml
<receiver android:name=".notifications.ReminderReceiver" android:exported="false" />
<receiver android:name=".notifications.DigestReceiver" android:exported="false" />
<receiver android:name=".notifications.BootReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 2: `NotificationHelper.kt`**

```kotlin
package edu.fnosari.classmanager.notifications

object NotificationHelper {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DIGEST = "digest"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DIGEST,
            context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun contentIntent(context: Context, studentId: Long): PendingIntent =
        PendingIntent.getActivity(context, studentId.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_STUDENT_ID, studentId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun showReminder(context: Context, reminder: Reminder, student: Student) {
        if (!hasPermission(context)) return
        val doneIntent = PendingIntent.getBroadcast(context, reminder.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_MARK_DONE)
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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

    fun showDigest(context: Context, lines: List<String>) {
        if (!hasPermission(context) || lines.isEmpty()) return
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.digest_title, lines.size))
            .setStyle(style)
            .setContentIntent(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(-1, n)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
```

- [ ] **Step 3: `AlarmScheduler.kt`**

```kotlin
package edu.fnosari.classmanager.notifications

class AlarmScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    private fun reminderPending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setExactCompat(triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    suspend fun scheduleReminder(r: Reminder) {
        if (r.done || r.type == ReminderType.MORNING_DIGEST) return  // digest items fire via daily digest
        val fireAt = when (r.type) {
            ReminderType.NEXT_LESSON -> r.dueAt - 5 * 60_000L  // 5 min before slot start
            else -> r.dueAt
        }
        if (fireAt > System.currentTimeMillis()) setExactCompat(fireAt, reminderPending(r.id))
    }

    fun cancelReminder(id: Long) = am.cancel(reminderPending(id))

    suspend fun scheduleDailyDigest() {
        val t = LocalTime.parse(settings.digestTime.first())
        var next = LocalDate.now().atTime(t)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val pi = PendingIntent.getBroadcast(context, -1,
            Intent(context, DigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        setExactCompat(next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    suspend fun rescheduleAll() {
        db.reminderDao().allPending().forEach { scheduleReminder(it) }
        scheduleDailyDigest()
    }
}
```

- [ ] **Step 4: Receivers**

```kotlin
package edu.fnosari.classmanager.notifications

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = context.appContainer.db
                when (intent.action) {
                    ACTION_MARK_DONE -> {
                        db.reminderDao().markDone(id)
                        NotificationManagerCompat.from(context).cancel(id.toInt())
                    }
                    ACTION_FIRE -> {
                        val r = db.reminderDao().byId(id) ?: return@launch
                        if (r.done) return@launch
                        val s = db.studentDao().byId(r.studentId) ?: return@launch
                        NotificationHelper.showReminder(context, r, s)
                    }
                }
            } finally { pending.finish() }
        }
    }
    companion object {
        const val ACTION_FIRE = "edu.fnosari.classmanager.REMINDER_FIRE"
        const val ACTION_MARK_DONE = "edu.fnosari.classmanager.REMINDER_DONE"
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.appContainer
                val zone = ZoneId.systemDefault()
                val dayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val due = container.db.reminderDao().dueBetween(dayStart, dayEnd)
                val lines = due.mapNotNull { r ->
                    container.db.studentDao().byId(r.studentId)
                        ?.let { "${it.firstName} ${it.lastName}: ${r.text}" }
                }
                NotificationHelper.showDigest(context, lines)
                container.alarms.scheduleDailyDigest()  // chain next day
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { context.appContainer.alarms.rescheduleAll() } finally { pending.finish() }
        }
    }
}
```

- [ ] **Step 5: Wire container + app startup**

`AppContainer`: add `val alarms: AlarmScheduler = AlarmScheduler(context, db, settings)` (constructor order: after db/settings).
`ClassManagerApp.onCreate`: after container init add

```kotlin
NotificationHelper.createChannels(this)
CoroutineScope(Dispatchers.IO).launch { container.alarms.scheduleDailyDigest() }
```

- [ ] **Step 6: Reminder creation in StudentDetail**

ViewModel additions:

```kotlin
enum class ReminderCreateResult { OK, NO_TIMETABLE }

suspend fun addReminder(text: String, type: ReminderType, fixedAt: Long?): ReminderCreateResult {
    val s = student.value ?: return ReminderCreateResult.OK
    val dueAt: Long = when (type) {
        ReminderType.FIXED_DATETIME, ReminderType.MORNING_DIGEST -> fixedAt!!
        ReminderType.NEXT_LESSON -> {
            val slots = db.timetableDao().slotsForOnce(s.classId)
            val ref = container.settings.weekARef.first()?.let(LocalDate::parse)
            val next = nextLessonStart(LocalDateTime.now(), slots, ref)
                ?: return ReminderCreateResult.NO_TIMETABLE
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
    val id = db.reminderDao().insert(Reminder(studentId = studentId, text = text.trim(), type = type, dueAt = dueAt))
    container.alarms.scheduleReminder(db.reminderDao().byId(id)!!)
    return ReminderCreateResult.OK
}
fun markDone(r: Reminder) = viewModelScope.launch {
    db.reminderDao().markDone(r.id); container.alarms.cancelReminder(r.id)
}
fun deleteReminder(r: Reminder) = viewModelScope.launch {
    db.reminderDao().delete(r); container.alarms.cancelReminder(r.id)
}
```

(Change the ViewModel constructor to keep `container` as a field since alarms/settings are needed.)

Screen additions — Reminders section from Task 10 becomes functional:
- Add `IconButton` → dialog: text field; type via 3 radio buttons `R.string.reminder_next_lesson` ("Next lesson"/"Prochain cours"), `R.string.reminder_fixed` ("Date & time"/"Date et heure"), `R.string.reminder_digest` ("Morning digest on a date"/"Rappel du matin à une date"); for FIXED show `DatePickerDialog` + `TimePickerDialog` (Material3 `DatePicker`/`TimePicker` in dialogs), for DIGEST a `DatePicker` only (dueAt = chosen date at start of day). Save calls `addReminder` in `rememberCoroutineScope`; on `NO_TIMETABLE` show `AlertDialog` with `R.string.no_timetable_message` ("This class has no timetable. Add slots first, or pick a date."/"Cette classe n'a pas d'emploi du temps. Ajoutez des créneaux, ou choisissez une date.").
- First reminder creation triggers `POST_NOTIFICATIONS` request on API 33+: `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` launched before saving.
- Item rendering: `ListItem(headline = text, supporting = DateFormat.getDateTimeInstance().format(Date(dueAt)))`, done items with `textDecoration = LineThrough`; trailing: done checkbox → `markDone`, delete icon → `deleteReminder`.

String keys also add: `channel_reminders` ("Reminders"/"Rappels"), `channel_digest` ("Daily digest"/"Récapitulatif quotidien"), `mark_done` ("Done"/"Fait"), `digest_title` ("%1$d reminders today"/"%1$d rappels aujourd'hui"), `add_reminder` ("Add reminder"/"Ajouter un rappel").

- [ ] **Step 7: Build + manual check**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL. Manual: create FIXED reminder 2 min ahead → notification fires, tap opens student page, Done action works; NEXT_LESSON with timetable computes plausible dueAt; digest fires at digest time (set time 2 min ahead in Task 15's settings screen once built, or temporarily via `adb shell` date is NOT reliable — test after Task 15).

- [ ] **Step 8: Commit**

```bash
git add app/src/main
git commit -m "feat: reminder notifications - exact alarms, digest, boot reschedule, deep links"
```

---

### Task 15: Backup / restore + settings screen

**Files:**
- Create: `app/src/main/java/edu/fnosari/classmanager/backup/BackupManager.kt`
- Test: `app/src/test/java/edu/fnosari/classmanager/backup/BackupValidateTest.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/edu/fnosari/classmanager/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/edu/fnosari/classmanager/AppContainer.kt` (add `backup: BackupManager`; make `db` a `var` reopenable — see Step 2)
- Modify: `app/src/main/java/edu/fnosari/classmanager/ui/Nav.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `SettingsRepository`, `AlarmScheduler.rescheduleAll`.
- Produces:
  - `class BackupManager(context, container)`: `suspend fun writeBackup(out: OutputStream)`, `fun validate(zipBytes: ByteArray): BackupCheck` (pure-testable), `suspend fun restore(zipBytes: ByteArray)`
  - `sealed class BackupCheck { object Ok : BackupCheck(); data class Invalid(val reason: String) : BackupCheck() }` with reason constants `"missing_manifest"`, `"missing_db"`, `"bad_schema_version"`, `"not_a_zip"`.

- [ ] **Step 1: Write failing validation tests**

```kotlin
class BackupValidateTest {
    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }
    private val sqliteHeader = "SQLite format 3 ".toByteArray(Charsets.ISO_8859_1)
    private fun manifest(schema: Int) =
        """{"schemaVersion":$schema,"appVersion":1,"createdAt":"2026-08-09"}""".toByteArray()

    @Test fun validZipOk() {
        val z = zip(mapOf("manifest.json" to manifest(1), "classmanager.db" to sqliteHeader))
        assertTrue(BackupManager.validate(z) is BackupCheck.Ok)
    }
    @Test fun notAZip() {
        val r = BackupManager.validate("garbage".toByteArray())
        assertEquals("not_a_zip", (r as BackupCheck.Invalid).reason)
    }
    @Test fun missingManifest() {
        val z = zip(mapOf("classmanager.db" to sqliteHeader))
        assertEquals("missing_manifest", (BackupManager.validate(z) as BackupCheck.Invalid).reason)
    }
    @Test fun missingDb() {
        val z = zip(mapOf("manifest.json" to manifest(1)))
        assertEquals("missing_db", (BackupManager.validate(z) as BackupCheck.Invalid).reason)
    }
    @Test fun newerSchemaRejected() {
        val z = zip(mapOf("manifest.json" to manifest(99), "classmanager.db" to sqliteHeader))
        assertEquals("bad_schema_version", (BackupManager.validate(z) as BackupCheck.Invalid).reason)
    }
    @Test fun dbWithoutSqliteHeaderRejected() {
        val z = zip(mapOf("manifest.json" to manifest(1), "classmanager.db" to "nope".toByteArray()))
        assertEquals("missing_db", (BackupManager.validate(z) as BackupCheck.Invalid).reason)
    }
}
```

- [ ] **Step 2: Run tests (expect compile fail), then implement `BackupManager.kt`**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.backup.BackupValidateTest"` — compilation FAILURE first.

```kotlin
package edu.fnosari.classmanager.backup

sealed class BackupCheck {
    object Ok : BackupCheck()
    data class Invalid(val reason: String) : BackupCheck()
}

class BackupManager(private val context: Context, private val container: AppContainer) {

    suspend fun writeBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        // flush WAL into main db file
        container.db.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("appVersion", 1)
                .put("createdAt", LocalDate.now().toString())
                .toString().toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("classmanager.db"))
            dbFile.inputStream().use { it.copyTo(z) }
            z.closeEntry()
            container.photosDir.listFiles()?.forEach { f ->
                z.putNextEntry(ZipEntry("photos/${f.name}"))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
    }

    suspend fun restore(zipBytes: ByteArray) = withContext(Dispatchers.IO) {
        require(validate(zipBytes) is BackupCheck.Ok)
        // extract to temp
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val target = File(tmp, e.name)
                    if (!target.canonicalPath.startsWith(tmp.canonicalPath)) throw IOException("zip path traversal")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { z.copyTo(it) }
                }
                e = z.nextEntry
            }
        }
        // swap: close db, replace files, reopen
        container.db.close()
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        File(dbFile.parentFile, "${AppDatabase.DB_NAME}-wal").delete()
        File(dbFile.parentFile, "${AppDatabase.DB_NAME}-shm").delete()
        File(tmp, "classmanager.db").copyTo(dbFile, overwrite = true)
        container.photosDir.deleteRecursively()
        container.photosDir.mkdirs()
        File(tmp, "photos").listFiles()?.forEach { it.copyTo(File(container.photosDir, it.name), overwrite = true) }
        tmp.deleteRecursively()
        container.reopenDb()
        container.alarms.rescheduleAll()
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun validate(zipBytes: ByteArray): BackupCheck {
            val entries = mutableMapOf<String, ByteArray>()
            try {
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
                    var e = z.nextEntry
                    if (e == null) return BackupCheck.Invalid("not_a_zip")
                    while (e != null) {
                        if (!e.isDirectory && (e.name == "manifest.json" || e.name == "classmanager.db"))
                            entries[e.name] = z.readBytes()
                        e = z.nextEntry
                    }
                }
            } catch (ex: Exception) { return BackupCheck.Invalid("not_a_zip") }
            val manifest = entries["manifest.json"] ?: return BackupCheck.Invalid("missing_manifest")
            val db = entries["classmanager.db"]
            if (db == null || db.size < 16 ||
                !String(db, 0, 15, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
                return BackupCheck.Invalid("missing_db")
            val schema = try { JSONObject(String(manifest)).getInt("schemaVersion") }
                catch (ex: Exception) { return BackupCheck.Invalid("missing_manifest") }
            if (schema > SCHEMA_VERSION) return BackupCheck.Invalid("bad_schema_version")
            return BackupCheck.Ok
        }
    }
}
```

`AppContainer` changes: `var db: AppDatabase = AppDatabase.build(context); private set` plus

```kotlin
private val appContext = context
val backup: BackupManager by lazy { BackupManager(appContext, this) }
fun reopenDb() { db = AppDatabase.build(appContext) }
```

Note: ViewModels hold DAO references from the old instance after restore — the Restore flow in the UI must pop navigation to CLASS_LIST after restore so all ViewModels are recreated (see Step 4). `AlarmScheduler` must also read `container.db` lazily — change its constructor to take `container: AppContainer` and use `container.db` at call time (adjust Task 14 code accordingly when executing: constructor `AlarmScheduler(context, container)`, body uses `container.db` and `container.settings`).

- [ ] **Step 3: Run tests to verify pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "edu.fnosari.classmanager.backup.BackupValidateTest"`
Expected: 6 tests PASS. (`validate` is a `companion object` fun on purpose — pure JVM testable, no Robolectric needed.)

- [ ] **Step 4: Settings screen**

ViewModel:

```kotlin
package edu.fnosari.classmanager.ui.settings

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val digestTime = container.settings.digestTime
        .stateIn(viewModelScope, SharingStarted.Eagerly, "07:00")
    val weekARef = container.settings.weekARef
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    var restoreState by mutableStateOf<String?>(null)  // null | "confirm" | "done" | error reason
    private var pendingRestore: ByteArray? = null

    fun setDigestTime(t: String) = viewModelScope.launch {
        container.settings.setDigestTime(t); container.alarms.scheduleDailyDigest()
    }
    fun setWeekARef(monday: String) = viewModelScope.launch { container.settings.setWeekARef(monday) }

    fun backupTo(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)!!.use { container.backup.writeBackup(it) }
    }
    fun startRestore(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        when (val check = BackupManager.validate(bytes)) {
            is BackupCheck.Ok -> { pendingRestore = bytes; restoreState = "confirm" }
            is BackupCheck.Invalid -> restoreState = check.reason
        }
    }
    fun confirmRestore() = viewModelScope.launch(Dispatchers.IO) {
        pendingRestore?.let { container.backup.restore(it) }
        pendingRestore = null; restoreState = "done"
    }
    fun dismissRestore() { pendingRestore = null; restoreState = null }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
```

Screen — `Scaffold` + `Column`, four `ListItem` rows:
1. Digest time: shows current, click → `TimePicker` dialog → `vm.setDigestTime("HH:mm")`.
2. Week A reference: shows `weekARef ?: stringResource(R.string.not_set)`; click → `DatePicker` dialog; on save normalize to that week's Monday (`date.minusDays(date.dayOfWeek.value - 1L).toString()`) → `vm.setWeekARef`. Supporting text `R.string.week_a_help` ("Pick any day of a week that is week A"/"Choisissez un jour d'une semaine A").
3. Backup: click → `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"))` launched with `"classmanager-backup-${LocalDate.now()}.zip"` → `vm.backupTo`.
4. Restore: click → `OpenDocument` picker (`application/zip`, `*/*`) → `vm.startRestore`.
   - `restoreState == "confirm"` → `AlertDialog` `R.string.restore_warning` ("This replaces ALL current data. Continue?"/"Ceci remplace TOUTES les données actuelles. Continuer ?") → `vm.confirmRestore()`.
   - `"done"` → dialog `R.string.restore_done` ("Restore complete"/"Restauration terminée"), on dismiss `onRestored()` (Nav pops to CLASS_LIST).
   - Error reasons → dialog mapping reason → `R.string.restore_invalid` ("This file is not a valid backup"/"Ce fichier n'est pas une sauvegarde valide") or `R.string.restore_newer` ("Backup was made with a newer app version"/"Sauvegarde créée avec une version plus récente de l'application").

Also add keys: `not_set` ("Not set"/"Non défini"), `backup` ("Backup"/"Sauvegarder"), `restore` ("Restore"/"Restaurer"), `digest_time` ("Morning digest time"/"Heure du rappel matinal"), `week_a_reference` ("Week A reference"/"Semaine A de référence").

`SettingsScreen(onBack: () -> Unit, onRestored: () -> Unit)`; Nav wiring:

```kotlin
composable(Routes.SETTINGS) {
    SettingsScreen(onBack = { nav.popBackStack() },
        onRestored = { nav.navigate(Routes.CLASS_LIST) { popUpTo(0) { inclusive = true } } })
}
```

- [ ] **Step 5: Build + full manual pass**

Run: `.\gradlew.bat assembleDebug` — BUILD SUCCESSFUL.
Manual round-trip: create data → backup to file → wipe app data (or delete classes) → restore → data + photos back, reminders rescheduled. Digest test: set digest time 2 min ahead → notification lists today's digest reminders.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: zip backup/restore with validation, settings screen"
```

---

### Task 16: Final QA sweep

**Files:**
- Modify: whatever the sweep flags.

- [ ] **Step 1: Full test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: all tests PASS.

- [ ] **Step 2: Release-ish build check**

Run: `.\gradlew.bat assembleDebug lint`
Expected: BUILD SUCCESSFUL; fix any lint errors (not warnings) it reports.

- [ ] **Step 3: String audit**

Grep UI files for hardcoded quoted text in `Text(...)`/`label = ...` — every user-visible literal must be `stringResource`. Verify `values-fr/strings.xml` has every key `values/strings.xml` has: missing-translation lint check covers this.

- [ ] **Step 4: Manual smoke script (device/emulator)**

1. Create class manually; import second class from semicolon CSV.
2. Add photos (gallery + camera) to two students.
3. Timetable: 2 slots, one tagged week B; set week A reference in settings.
4. Reminder NEXT_LESSON → verify computed time matches parity; FIXED 2-min → notification → tap → student page; Done action.
5. Picker: cycle through whole class with one absent; verify reset message.
6. Groups: constraint pair, generate groups of 4, verify separation; save; reload.
7. Backup → uninstall → reinstall → restore → everything back.
8. Reboot device → pending reminder still fires.

- [ ] **Step 5: Commit fixes**

```bash
git add -A
git commit -m "chore: QA fixes from final sweep"
```

---

## Self-Review Notes (already applied)

- Spec coverage: classes CRUD (T8), students+photos (T9), timetable+parity (T4/T9), CSV (T5/T13), picker (T11), groups (T6/T12), student page notes/fields (T10), reminders 3 types + digest + boot + deep link (T14), backup/restore (T15), FR/EN strings (T7 + per-task keys), minSdk 29 (T1).
- Type consistency: `AlarmScheduler` constructor changes in T15 Step 2 note — executor of T15 must apply it; T14 builds it first with (context, db, settings) then T15 refactors to (context, container).
- Known deliberate simplifications: no photo import in CSV (spec doesn't require), `absentToday` stored as date string (self-expiring), digest re-chains itself daily.

