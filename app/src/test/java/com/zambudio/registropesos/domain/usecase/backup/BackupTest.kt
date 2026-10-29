package com.zambudio.registropesos.domain.usecase.backup

import com.zambudio.registropesos.domain.backup.BACKUP_SCHEMA_VERSION
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ExerciseDto
import com.zambudio.registropesos.domain.backup.MuscleGroupDto
import com.zambudio.registropesos.domain.backup.RoutineBlockDto
import com.zambudio.registropesos.domain.backup.RoutineDto
import com.zambudio.registropesos.domain.backup.RoutineExerciseDto
import com.zambudio.registropesos.domain.backup.SessionDto
import com.zambudio.registropesos.domain.backup.SetDto
import com.zambudio.registropesos.domain.backup.TombstoneDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
private val day1 = ZonedDateTime.of(2026, 1, 5, 18, 30, 0, 0, madrid).toInstant().toEpochMilli()
private val day2 = ZonedDateTime.of(2026, 1, 7, 9, 5, 0, 0, madrid).toInstant().toEpochMilli()

private fun setDto(
    id: String,
    sessionId: String,
    exerciseName: String,
    weightKg: Double?,
    reps: Int?,
    setIndex: Int,
    exerciseOrder: Int = 0,
) = SetDto(
    id = id,
    sessionId = sessionId,
    exerciseId = "ex-$exerciseName",
    exerciseNameSnapshot = exerciseName,
    muscleGroupNameSnapshot = "Pecho",
    exerciseOrder = exerciseOrder,
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    isDone = true,
    completedAt = null,
    createdAt = 1,
    updatedAt = 2,
)

private fun sessionDto(id: String, startedAt: Long, routineName: String, status: String = "FINISHED") = SessionDto(
    id = id,
    routineId = null,
    routineNameSnapshot = routineName,
    startedAt = startedAt,
    finishedAt = startedAt + 3_600_000,
    status = status,
    notes = null,
    createdAt = startedAt,
    updatedAt = startedAt,
)

private val fullSnapshot = BackupSnapshot(
    dbVersion = 1,
    appVersion = "0.1.0",
    exportedAt = 1_700_000_000_000,
    muscleGroups = listOf(MuscleGroupDto("g1", "Pecho", 0, true, 1, 2)),
    exercises = listOf(
        ExerciseDto("e1", "g1", "Press \"banca\", plano", "Notas\ncon salto", "images/exercise_e1.jpg", false, 1, 2),
        ExerciseDto("e2", "g1", "Aperturas", null, null, true, 1, 3),
    ),
    routines = listOf(RoutineDto("r1", "Día 1", "4 ejer pecho", 0, 1, 2)),
    routineBlocks = listOf(RoutineBlockDto("b1", "r1", "g1", 0, 1, 2)),
    routineExercises = listOf(RoutineExerciseDto("re1", "b1", "e1", 0, 4, 1, 2)),
    sessions = listOf(sessionDto("s1", day1, "Día 1"), sessionDto("s2", day2, "Día 2", status = "DISCARDED")),
    sets = listOf(
        setDto("x1", "s1", "Press banca", 82.5, 8, 0),
        setDto("x2", "s1", "Press banca", null, null, 1),
    ),
    tombstones = listOf(TombstoneDto("ROUTINE", "r-old", 99)),
)

class BackupSerializerTest {
    @Test
    fun roundTrip_keepsEverything() {
        assertEquals(fullSnapshot, BackupSerializer.decode(BackupSerializer.encode(fullSnapshot)))
    }

    @Test
    fun encode_includesDefaultsAndIsPretty() {
        val json = BackupSerializer.encode(BackupSnapshot(dbVersion = 1, appVersion = "x", exportedAt = 0))
        assertTrue(json.contains("\"schemaVersion\": $BACKUP_SCHEMA_VERSION"))
        assertTrue(json.contains("\"tombstones\": ["))
        assertTrue(json.contains('\n'))
    }

    @Test
    fun decode_ignoresUnknownKeys_andFillsMissingLists() {
        val snapshot = BackupSerializer.decode(
            """{"dbVersion":1,"appVersion":"0.1.0","exportedAt":5,"futureField":{"a":1}}""",
        )
        assertEquals(BackupSnapshot(dbVersion = 1, appVersion = "0.1.0", exportedAt = 5), snapshot)
    }

    /** backup.json tal como lo escribía la v0.1.0 (schemaVersion 1). */
    @Test
    fun decode_v1Backup_fillsNewFieldsWithDefaults() {
        val snapshot = BackupSerializer.decode(
            """
            {"schemaVersion":1,"dbVersion":1,"appVersion":"0.1.0","exportedAt":5,
             "sessions":[{"id":"s1","routineId":null,"routineNameSnapshot":"Día 1","startedAt":1,
               "finishedAt":2,"status":"FINISHED","notes":null,"createdAt":1,"updatedAt":2}],
             "sets":[{"id":"x1","sessionId":"s1","exerciseId":"e1","exerciseNameSnapshot":"Press",
               "muscleGroupNameSnapshot":"Pecho","exerciseOrder":0,"setIndex":0,"weightKg":80.0,"reps":8,
               "isDone":true,"completedAt":2,"createdAt":1,"updatedAt":2}]}
            """.trimIndent(),
        )
        assertEquals(1, snapshot.schemaVersion)
        assertTrue(snapshot.sessionGroups.isEmpty())
        assertEquals(null, snapshot.sessions.single().routineCommentSnapshot)
        assertTrue(snapshot.sets.single().isDone)
    }

    @Test(expected = UnsupportedBackupVersionException::class)
    fun decode_newerSchema_isRejected() {
        BackupSerializer.decode("""{"schemaVersion":999,"dbVersion":1,"appVersion":"9","exportedAt":5}""")
    }
}

class SetsCsvWriterTest {
    private val snapshot = BackupSnapshot(
        dbVersion = 1,
        appVersion = "0.1.0",
        exportedAt = 0,
        sessions = listOf(
            sessionDto("late", day2, "Pecho, \"duro\"\nDía 2"),
            sessionDto("early", day1, "Día 1"),
            sessionDto("gone", day1, "Descartada", status = "DISCARDED"),
            sessionDto("now", day2 + 1, "En curso", status = "IN_PROGRESS"),
        ),
        sets = listOf(
            setDto("b", "early", "Press banca", 85.0, 6, setIndex = 1),
            setDto("a", "early", "Press banca", 82.5, 8, setIndex = 0),
            setDto("c", "late", "Fondos", null, 12, setIndex = 0),
            setDto("z", "gone", "Nada", 10.0, 10, setIndex = 0),
            setDto("y", "now", "Nada", 10.0, 10, setIndex = 0),
        ),
    )

    @Test
    fun writesHeaderAndOneRowPerFinishedSet_inChronologicalOrder() {
        val csv = SetsCsvWriter.write(snapshot, madrid)
        assertEquals(
            SetsCsvWriter.HEADER + "\r\n" +
                "2026-01-05,18:30,Día 1,Pecho,Press banca,1,82.5,8,104.5\r\n" +
                "2026-01-05,18:30,Día 1,Pecho,Press banca,2,85,6,102\r\n" +
                "2026-01-07,09:05,\"Pecho, \"\"duro\"\"\nDía 2\",Pecho,Fondos,1,,12,\r\n",
            csv,
        )
    }

    @Test
    fun decimals_alwaysUseDot() {
        val csv = SetsCsvWriter.write(snapshot, madrid)
        assertTrue(csv.contains(",82.5,"))
        assertFalse(csv.contains("82,5"))
    }

    @Test
    fun escape_quotesOnlyWhenNeeded() {
        assertEquals("simple", SetsCsvWriter.escape("simple"))
        assertEquals("\"a,b\"", SetsCsvWriter.escape("a,b"))
        assertEquals("\"di \"\"hola\"\"\"", SetsCsvWriter.escape("di \"hola\""))
        assertEquals("\"l1\nl2\"", SetsCsvWriter.escape("l1\nl2"))
        assertEquals("\"l1\r\nl2\"", SetsCsvWriter.escape("l1\r\nl2"))
    }

    @Test
    fun formatDecimal_isLocaleIndependent() {
        assertEquals("82.5", SetsCsvWriter.formatDecimal(82.5))
        assertEquals("80", SetsCsvWriter.formatDecimal(80.0))
        assertEquals("0", SetsCsvWriter.formatDecimal(0.0))
        assertEquals("1.25", SetsCsvWriter.formatDecimal(1.25))
    }

    @Test
    fun emptySnapshot_onlyHeader() {
        val csv = SetsCsvWriter.write(BackupSnapshot(dbVersion = 1, appVersion = "x", exportedAt = 0), madrid)
        assertEquals(SetsCsvWriter.HEADER + "\r\n", csv)
    }

    @Test
    fun bom_isUtf8ByteOrderMark() {
        val bytes = SetsCsvWriter.UTF8_BOM.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xFF }
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes)
    }
}
