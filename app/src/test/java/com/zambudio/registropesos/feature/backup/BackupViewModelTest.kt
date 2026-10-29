package com.zambudio.registropesos.feature.backup

import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ExerciseDto
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.backup.RoutineDto
import com.zambudio.registropesos.domain.backup.SessionDto
import com.zambudio.registropesos.domain.backup.TableReport
import com.zambudio.registropesos.domain.repository.BackupRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.usecase.backup.BackupSerializer
import com.zambudio.registropesos.domain.usecase.backup.SetsCsvWriter
import com.zambudio.registropesos.feature.backup.io.BackupFileStore
import com.zambudio.registropesos.feature.backup.io.BackupImageSource
import com.zambudio.registropesos.feature.backup.io.BackupZipReader
import com.zambudio.registropesos.feature.backup.io.BackupZipWriter
import com.zambudio.registropesos.feature.progress.MainDispatcherRule
import com.zambudio.registropesos.feature.workout.clearAsIfScreenClosed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val temp = TemporaryFolder()

    private lateinit var repository: FakeBackupRepository
    private lateinit var images: FakeImageStore
    private lateinit var files: FakeBackupFileStore
    private val now = 1_761_750_000_000L

    private val snapshot = BackupSnapshot(
        dbVersion = 1,
        appVersion = "0.1.0",
        exportedAt = now,
        exercises = listOf(
            ExerciseDto(
                "e1",
                "g1",
                "Press banca",
                imageFile = "images/exercise_e1.jpg",
                isArchived = false,
                createdAt = 0,
                updatedAt = 0,
            ),
            ExerciseDto("e2", "g1", "Aperturas", isArchived = false, createdAt = 0, updatedAt = 0),
        ),
        routines = listOf(RoutineDto("r1", "Día 1", sortOrder = 0, createdAt = 0, updatedAt = 0)),
        sessions = listOf(
            SessionDto(
                "s1",
                "r1",
                "Día 1",
                startedAt = now,
                finishedAt = now,
                status = "FINISHED",
                createdAt = 0,
                updatedAt = 0,
            ),
        ),
    )

    @Before
    fun setUp() {
        repository = FakeBackupRepository(snapshot)
        images = FakeImageStore(temp.newFolder("images"))
        files = FakeBackupFileStore(temp.newFolder("exports"))
    }

    private fun viewModel() = BackupViewModel(repository, images, files, { now }, mainRule.dispatcher)

    private fun TestScope.collectEvents(viewModel: BackupViewModel): List<BackupEvent> {
        val events = mutableListOf<BackupEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    @Test
    fun `export writes the zip with json, csv and images`() = runTest {
        images.directory.resolve("exercise_e1.jpg").writeBytes(byteArrayOf(9, 9, 9))
        val viewModel = viewModel()

        viewModel.export()

        val exported = checkNotNull(viewModel.state.value.exported)
        assertEquals(BackupViewModel.exportFileName(now, ZoneId.systemDefault()), exported.fileName)
        assertTrue(exported.fileName.matches(Regex("""gym-backup-\d{8}-\d{4}\.zip""")))
        assertEquals(0, exported.imagesMissing)
        assertNull(viewModel.state.value.operation)
        val zip = File(exported.filePath)
        val json = zip.inputStream().use { BackupZipReader().readBackupJson(it) }
        assertEquals(snapshot, BackupSerializer.decode(json))
        val found = zip.inputStream().use {
            BackupZipReader().forEachImage(it, setOf("images/exercise_e1.jpg")) { _, _ -> }
        }
        assertEquals(setOf("images/exercise_e1.jpg"), found)
    }

    @Test
    fun `export reports missing images without failing`() = runTest {
        val viewModel = viewModel()
        viewModel.export()
        assertEquals(1, viewModel.state.value.exported?.imagesMissing)
    }

    @Test
    fun `export failure emits an error and leaves no file`() = runTest {
        repository.exportError = IllegalStateException("BD rota")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.export()

        assertNull(viewModel.state.value.exported)
        assertNull(viewModel.state.value.operation)
        assertEquals(listOf(BackupEvent.Error(BackupError.EXPORT_FAILED)), events)
    }

    @Test
    fun `save copies the exported zip to the chosen document`() = runTest {
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.export()
        val destination = temp.root.resolve("drive.zip")

        viewModel.saveTo(destination.path)

        assertArrayEquals(File(viewModel.state.value.exported!!.filePath).readBytes(), destination.readBytes())
        assertEquals(listOf(BackupEvent.Saved), events)
    }

    @Test
    fun `share emits the provider uri`() = runTest {
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.export()

        viewModel.share()

        val share = events.single() as BackupEvent.Share
        assertTrue(share.uri.startsWith("content://test/gym-backup-"))
    }

    @Test
    fun `import shows a preview, then applies the snapshot and copies images`() = runTest {
        val zip = zipFile(snapshot, mapOf("images/exercise_e1.jpg" to byteArrayOf(7, 7)))
        repository.importReport = ImportReport(
            exercises = TableReport(inserted = 2),
            routines = TableReport(inserted = 1),
            sessions = TableReport(inserted = 1),
            warnings = listOf("aviso"),
            imagesToCopy = mapOf("e1" to "images/exercise_e1.jpg", "e9" to "images/not_in_zip.jpg"),
        )
        val viewModel = viewModel()

        viewModel.onImportFileChosen(zip.path)

        val preview = checkNotNull(viewModel.state.value.preview)
        assertEquals(ImportPreview(now, "0.1.0", exerciseCount = 2, routineCount = 1, sessionCount = 1), preview)
        assertNull(repository.imported)

        viewModel.confirmImport()

        assertEquals(snapshot, repository.imported)
        val outcome = checkNotNull(viewModel.state.value.outcome)
        assertEquals(repository.importReport, outcome.report)
        assertEquals(1, outcome.imagesCopied)
        assertEquals(1, outcome.imagesMissing)
        assertArrayEquals(byteArrayOf(7, 7), images.imported.getValue("images/exercise_e1.jpg"))
        assertNull(viewModel.state.value.preview)

        viewModel.dismissReport()
        assertNull(viewModel.state.value.outcome)
    }

    @Test
    fun `leaving the screen mid-import still finishes the import and copies images`() = runTest {
        val zip = zipFile(snapshot, mapOf("images/exercise_e1.jpg" to byteArrayOf(7, 7)))
        repository.importReport = ImportReport(imagesToCopy = mapOf("e1" to "images/exercise_e1.jpg"))
        val gate = CompletableDeferred<Unit>()
        repository.importGate = gate
        val viewModel = viewModel()
        viewModel.onImportFileChosen(zip.path)
        viewModel.confirmImport()
        assertNull("import en curso", repository.imported)

        viewModel.clearAsIfScreenClosed()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(snapshot, repository.imported)
        assertArrayEquals(byteArrayOf(7, 7), images.imported.getValue("images/exercise_e1.jpg"))
    }

    @Test
    fun `cancelling the preview does not import`() = runTest {
        val viewModel = viewModel()
        viewModel.onImportFileChosen(zipFile(snapshot).path)

        viewModel.cancelImport()
        viewModel.confirmImport()

        assertNull(viewModel.state.value.preview)
        assertNull(repository.imported)
    }

    @Test
    fun `import of a file that is not a backup emits invalid file`() = runTest {
        val notZip = temp.newFile("foto.jpg").apply { writeText("no es un zip") }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.onImportFileChosen(notZip.path)

        assertNull(viewModel.state.value.preview)
        assertEquals(listOf(BackupEvent.Error(BackupError.INVALID_FILE)), events)
    }

    @Test
    fun `import of a zip with broken json emits invalid file`() = runTest {
        val zip = temp.root.resolve("broken.zip")
        zip.outputStream().use { BackupZipWriter().write(it, "{ roto", SetsCsvWriter.HEADER, emptyList()) }
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.onImportFileChosen(zip.path)

        assertEquals(listOf(BackupEvent.Error(BackupError.INVALID_FILE)), events)
    }

    @Test
    fun `import of a newer backup format emits unsupported version`() = runTest {
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.onImportFileChosen(zipFile(snapshot.copy(schemaVersion = 99)).path)

        assertNull(viewModel.state.value.preview)
        assertEquals(listOf(BackupEvent.Error(BackupError.UNSUPPORTED_VERSION)), events)
    }

    @Test
    fun `repository rejection on import maps to unsupported version`() = runTest {
        repository.importError = IllegalArgumentException("formato v2")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.onImportFileChosen(zipFile(snapshot).path)

        viewModel.confirmImport()

        assertNull(viewModel.state.value.outcome)
        assertEquals(listOf(BackupEvent.Error(BackupError.UNSUPPORTED_VERSION)), events)
    }

    @Test
    fun `unexpected import failure maps to import failed`() = runTest {
        repository.importError = IllegalStateException("constraint")
        val viewModel = viewModel()
        val events = collectEvents(viewModel)
        viewModel.onImportFileChosen(zipFile(snapshot).path)

        viewModel.confirmImport()

        assertEquals(listOf(BackupEvent.Error(BackupError.IMPORT_FAILED)), events)
    }

    @Test
    fun `cancelled pickers do nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.onImportFileChosen(null)
        viewModel.saveTo(null)
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    private fun zipFile(snapshot: BackupSnapshot, imageEntries: Map<String, ByteArray> = emptyMap()): File {
        val sources = imageEntries.map { (path, bytes) ->
            BackupImageSource(path, temp.newFile().apply { writeBytes(bytes) })
        }
        val zip = temp.newFile("import-${System.nanoTime()}.zip")
        zip.outputStream().use {
            BackupZipWriter().write(it, BackupSerializer.encode(snapshot), SetsCsvWriter.HEADER, sources)
        }
        return zip
    }
}

private class FakeBackupRepository(private val snapshot: BackupSnapshot) : BackupRepository {
    var exportError: Exception? = null
    var importError: Exception? = null
    var importReport = ImportReport()
    var imported: BackupSnapshot? = null

    /** Si se fija, importSnapshot espera a que se complete (import "en curso"). */
    var importGate: CompletableDeferred<Unit>? = null

    override suspend fun exportSnapshot(appVersion: String, exportedAt: Long): BackupSnapshot {
        exportError?.let { throw it }
        return snapshot
    }

    override suspend fun importSnapshot(snapshot: BackupSnapshot): ImportReport {
        importGate?.await()
        importError?.let { throw it }
        imported = snapshot
        return importReport
    }
}

private class FakeImageStore(val directory: File) : ImageStore {
    val imported = mutableMapOf<String, ByteArray>()

    override fun resolve(relativePath: String): File = directory.resolve(File(relativePath).name)

    override suspend fun importRaw(relativePath: String, bytes: ByteArray) {
        imported[relativePath] = bytes
    }

    override suspend fun saveResized(sourceUri: String): String = error("No usado")
    override suspend fun delete(relativePath: String) = error("No usado")
    override fun listAll(): List<File> = error("No usado")
}

/** Los "Uri" de test son rutas de fichero. */
private class FakeBackupFileStore(private val directory: File) : BackupFileStore {
    override fun newExportFile(fileName: String): File = directory.resolve(fileName)
    override fun openInput(uri: String): InputStream =
        File(uri).takeIf { it.isFile }?.inputStream() ?: throw IOException(uri)
    override fun openOutput(uri: String): OutputStream = File(uri).outputStream()
    override fun shareUri(file: File): String = "content://test/${file.name}"
}
