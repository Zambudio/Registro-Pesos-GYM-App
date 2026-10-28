package com.zambudio.registropesos.feature.progress

import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.SessionVolumePoint
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.usecase.CalculateE1rm
import com.zambudio.registropesos.domain.usecase.ProgressRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val zone = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 10, 29).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private val chest = group("g1", "Pecho", 0)
    private val back = group("g2", "Espalda", 1)
    private val bench = exercise("e1", "g1", "Press banca")
    private val flies = exercise("e2", "g1", "Aperturas")
    private val oldPress = exercise("e3", "g1", "Press declinado", archived = true)
    private val unusedArchived = exercise("e4", "g1", "Pullover", archived = true)
    private val row = exercise("e5", "g2", "Remo")

    private val groups = FakeMuscleGroupRepository(listOf(back, chest))
    private val exercises = FakeExerciseRepository(listOf(bench, flies, oldPress, unusedArchived, row))
    private val progress = FakeProgressRepository()

    private fun TestScope.viewModel(): ProgressViewModel {
        val viewModel = ProgressViewModel(groups, exercises, progress) { now }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `without history shows groups, default selection and no records`() = runTest {
        val state = viewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(listOf("g1", "g2"), state.groups.map { it.id })
        assertEquals("g1", state.exerciseGroupId)
        assertEquals("g1", state.chartGroupId)
        assertNull(state.selectedExercise)
        assertTrue(state.records.isEmpty())
        assertEquals(ProgressRange.M3, state.range)
    }

    @Test
    fun `exercise options include archived exercises only when they have history`() = runTest {
        progress.flat.value = listOf(flat("e3", 60.0, 10, now - 100 * day))

        val options = viewModel().state.value.exercises

        assertEquals(listOf("Aperturas", "Press banca", "Press declinado"), options.map { it.name })
        assertTrue(options.last().isArchived)
    }

    @Test
    fun `selecting an exercise loads its points for the range and its all-time record`() = runTest {
        progress.flat.value = listOf(
            flat("e1", 80.0, 8, now - 200 * day),
            flat("e1", 85.0, 3, now - 2 * day),
        )
        progress.exercisePoints["e1"] = listOf(point("s2", now - 2 * day, 85.0))
        val viewModel = viewModel()

        viewModel.selectExercise("e1")

        val state = viewModel.state.value
        assertEquals("e1", state.selectedExercise?.id)
        assertEquals(listOf("s2"), state.exercisePoints.map { it.sessionId })
        assertEquals("e1" to ProgressRange.M3.fromMillis(now, zone), progress.exerciseCalls.last())
        val record = checkNotNull(state.selectedRecord)
        assertEquals(85.0, record.bestWeightKg, 0.0)
        assertEquals(now - 2 * day, record.bestWeightAt)
        assertEquals(CalculateE1rm(80.0, 8), record.bestE1rmKg, 0.0)
        assertEquals(now - 200 * day, record.bestE1rmAt)
    }

    @Test
    fun `changing range or exercise queries again`() = runTest {
        progress.exercisePoints["e1"] = listOf(point("s1", now - day, 80.0))
        progress.exercisePoints["e2"] = listOf(point("s1", now - day, 20.0), point("s3", now, 22.0))
        val viewModel = viewModel()
        viewModel.selectExercise("e1")

        viewModel.selectRange(ProgressRange.ALL)
        assertEquals("e1" to 0L, progress.exerciseCalls.last())

        viewModel.selectExercise("e2")
        assertEquals("e2" to 0L, progress.exerciseCalls.last())
        assertEquals(listOf(20.0, 22.0), viewModel.state.value.exercisePoints.map { it.maxWeightKg })

        viewModel.selectRange(ProgressRange.M1)
        assertEquals("e2" to ProgressRange.M1.fromMillis(now, zone), progress.exerciseCalls.last())
        assertEquals(ProgressRange.M1, viewModel.state.value.range)
    }

    @Test
    fun `exercise without finished sessions has no points and no record`() = runTest {
        val viewModel = viewModel()

        viewModel.selectExercise("e2")

        val state = viewModel.state.value
        assertEquals("e2", state.selectedExercise?.id)
        assertTrue(state.exercisePoints.isEmpty())
        assertNull(state.selectedRecord)
    }

    @Test
    fun `changing group clears the exercise and clearing goes back to records`() = runTest {
        val viewModel = viewModel()
        viewModel.selectExercise("e1")

        viewModel.selectExerciseGroup("g2")
        assertNull(viewModel.state.value.selectedExercise)
        assertEquals(listOf("Remo"), viewModel.state.value.exercises.map { it.name })

        viewModel.openExercise("e1", "g1")
        assertEquals("g1", viewModel.state.value.exerciseGroupId)
        assertEquals("e1", viewModel.state.value.selectedExercise?.id)

        viewModel.clearExercise()
        assertNull(viewModel.state.value.selectedExercise)
    }

    @Test
    fun `record cards cover every exercise with history ordered by group`() = runTest {
        progress.flat.value = listOf(
            flat("e5", 70.0, 10, now - 3 * day),
            flat("e1", 80.0, 8, now - 2 * day),
            flat("e3", 60.0, 10, now - day),
        )

        val records = viewModel().state.value.records

        assertEquals(listOf("e1", "e3", "e5"), records.map { it.exerciseId })
        assertEquals(listOf("Pecho", "Pecho", "Espalda"), records.map { it.groupName })
        assertEquals("g2", records.last().muscleGroupId)
    }

    @Test
    fun `group tab aggregates weekly volume up to now`() = runTest {
        progress.groupVolumes["g2"] = listOf(
            SessionVolumePoint("s1", now - 14 * day, 1_000.0),
            SessionVolumePoint("s2", now - 13 * day, 500.0),
        )
        val viewModel = viewModel()
        viewModel.selectChartGroup("g2")
        assertTrue("sin pestaña de grupo no se consulta", progress.groupCalls.isEmpty())

        viewModel.selectTab(ProgressTab.GROUP)

        val weekly = viewModel.state.value.weekly
        assertEquals("g2" to ProgressRange.M3.fromMillis(now, zone), progress.groupCalls.last())
        assertEquals(1_500.0, weekly.sumOf { it.volumeKg }, 0.0)
        assertEquals(2, weekly.sumOf { it.sessionCount })
        // Las semanas sin sesiones hasta hoy salen a 0.
        assertEquals(0.0, weekly.last().volumeKg, 0.0)
        assertTrue(weekly.size >= 3)
    }

    @Test
    fun `group tab without data is empty`() = runTest {
        val viewModel = viewModel()
        viewModel.selectTab(ProgressTab.GROUP)

        assertEquals("g1", viewModel.state.value.chartGroupId)
        assertTrue(viewModel.state.value.weekly.isEmpty())
    }

    @Test
    fun `repository failure shows error and retry recovers`() = runTest {
        progress.failFlat = true
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isError)

        progress.failFlat = false
        viewModel.retry()

        assertFalse(viewModel.state.value.isError)
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun flat(exerciseId: String, weight: Double, reps: Int, at: Long) =
        FlatSet(exerciseId, "s$at", weight, reps, at)

    private fun point(sessionId: String, at: Long, max: Double) =
        ExerciseSessionPoint(sessionId, at, max, max * 10, CalculateE1rm(max, 5))
}

private fun group(id: String, name: String, order: Int) = MuscleGroup(id, name, order, true, 0L, 0L)

private fun exercise(id: String, groupId: String, name: String, archived: Boolean = false) =
    Exercise(id, groupId, name, null, null, archived, 0L, 0L)

private fun unused(): Nothing = error("No usado en estos tests")

private class FakeMuscleGroupRepository(groups: List<MuscleGroup>) : MuscleGroupRepository {
    val groups = MutableStateFlow(groups)
    override fun observeAll(): Flow<List<MuscleGroup>> = groups
    override fun observeExerciseCounts(): Flow<Map<String, Int>> = unused()
    override suspend fun getById(id: String): MuscleGroup? = unused()
    override suspend fun upsert(group: MuscleGroup) = unused()
    override suspend fun reorder(orderedIds: List<String>) = unused()
    override suspend fun delete(id: String): Result<Unit> = unused()
}

private class FakeExerciseRepository(exercises: List<Exercise>) : ExerciseRepository {
    val all = MutableStateFlow(exercises)
    override fun observeAll(includeArchived: Boolean): Flow<List<Exercise>> =
        all.map { list -> if (includeArchived) list else list.filterNot { it.isArchived } }

    override fun observeByGroup(groupId: String, includeArchived: Boolean): Flow<List<Exercise>> = unused()
    override fun observeById(id: String): Flow<Exercise?> = unused()
    override suspend fun getById(id: String): Exercise? = unused()
    override suspend fun upsert(exercise: Exercise) = unused()
    override suspend fun setArchived(id: String, archived: Boolean) = unused()
    override suspend fun hasHistory(id: String): Boolean = unused()
}

private class FakeProgressRepository : ProgressRepository {
    val flat = MutableStateFlow<List<FlatSet>>(emptyList())
    var failFlat = false
    val exercisePoints = mutableMapOf<String, List<ExerciseSessionPoint>>()
    val groupVolumes = mutableMapOf<String, List<SessionVolumePoint>>()
    val exerciseCalls = mutableListOf<Pair<String, Long>>()
    val groupCalls = mutableListOf<Pair<String, Long>>()

    override fun observeExercisePoints(exerciseId: String, fromMillis: Long): Flow<List<ExerciseSessionPoint>> {
        exerciseCalls += exerciseId to fromMillis
        return MutableStateFlow(exercisePoints[exerciseId].orEmpty().filter { it.startedAt >= fromMillis })
    }

    override fun observeGroupSessionVolumes(groupId: String, fromMillis: Long): Flow<List<SessionVolumePoint>> {
        groupCalls += groupId to fromMillis
        return MutableStateFlow(groupVolumes[groupId].orEmpty().filter { it.startedAt >= fromMillis })
    }

    override fun observeFinishedSetsFlat(exerciseId: String?): Flow<List<FlatSet>> = flow {
        if (failFlat) error("BD no disponible")
        flat.collect { emit(it) }
    }

    override suspend fun finishedSetsFlat(exerciseId: String?): List<FlatSet> = unused()
}
