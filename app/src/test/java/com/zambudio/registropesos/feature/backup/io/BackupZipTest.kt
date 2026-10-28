package com.zambudio.registropesos.feature.backup.io

import com.zambudio.registropesos.domain.usecase.backup.SetsCsvWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupZipTest {
    @get:Rule val temp = TemporaryFolder()

    private val json = """{"schemaVersion":1,"dbVersion":1,"appVersion":"0.1.0","exportedAt":0}"""
    private val csv = SetsCsvWriter.HEADER + "\r\n2026-10-01,10:00,Día 1,Pecho,Press banca,1,80,8,101.5\r\n"

    @Test
    fun `writer and reader round-trip json, csv with BOM and images`() {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val image = temp.newFile("exercise_a.jpg").apply { writeBytes(imageBytes) }
        val zip = ByteArrayOutputStream()

        val result = BackupZipWriter().write(
            zip,
            json,
            csv,
            listOf(BackupImageSource("images/exercise_a.jpg", image)),
        )

        assertEquals(1, result.imagesWritten)
        assertEquals(0, result.imagesMissing)
        val bytes = zip.toByteArray()
        val entries = entriesOf(bytes)
        assertEquals(setOf("backup.json", "sets.csv", "images/exercise_a.jpg"), entries.keys)
        val csvText = entries.getValue("sets.csv").toString(Charsets.UTF_8)
        assertTrue("sets.csv debe empezar con BOM", csvText.startsWith(SetsCsvWriter.UTF8_BOM))
        assertEquals(csv, csvText.removePrefix(SetsCsvWriter.UTF8_BOM))

        val reader = BackupZipReader()
        assertEquals(json, reader.readBackupJson(ByteArrayInputStream(bytes)))
        val images = mutableMapOf<String, ByteArray>()
        val wanted = setOf("images/exercise_a.jpg", "images/missing.jpg")
        val found = reader.forEachImage(ByteArrayInputStream(bytes), wanted) { path, data -> images[path] = data }
        assertEquals(setOf("images/exercise_a.jpg"), found)
        assertArrayEquals(imageBytes, images.getValue("images/exercise_a.jpg"))
    }

    @Test
    fun `writer skips images that do not exist and counts them`() {
        val zip = ByteArrayOutputStream()
        val result = BackupZipWriter().write(
            zip,
            json,
            csv,
            listOf(BackupImageSource("images/nope.jpg", temp.root.resolve("nope.jpg"))),
        )
        assertEquals(0, result.imagesWritten)
        assertEquals(1, result.imagesMissing)
        assertFalse(entriesOf(zip.toByteArray()).containsKey("images/nope.jpg"))
    }

    @Test
    fun `reader rejects entries with parent traversal`() {
        val bytes = zipOf("backup.json" to json, "images/../../evil.sh" to "x")
        assertThrows(InvalidBackupFileException::class.java) {
            BackupZipReader().readBackupJson(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `reader rejects absolute and windows paths`() {
        listOf("/data/evil", "\\evil", "C:\\evil", "..\\evil").forEach { name ->
            val bytes = zipOf("backup.json" to json, name to "x")
            assertThrows(name, InvalidBackupFileException::class.java) {
                BackupZipReader().readBackupJson(ByteArrayInputStream(bytes))
            }
        }
    }

    @Test
    fun `reader fails when backup json is missing`() {
        val bytes = zipOf("sets.csv" to csv)
        assertThrows(InvalidBackupFileException::class.java) {
            BackupZipReader().readBackupJson(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `reader fails on something that is not a zip`() {
        assertThrows(InvalidBackupFileException::class.java) {
            BackupZipReader().readBackupJson(ByteArrayInputStream("no soy un zip".toByteArray()))
        }
    }

    @Test
    fun `reader enforces maximum entry size`() {
        val bytes = zipOf("backup.json" to "x".repeat(2_000))
        assertThrows(InvalidBackupFileException::class.java) {
            BackupZipReader(maxJsonBytes = 1_000).readBackupJson(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `safe entry names`() {
        assertTrue(BackupZipFormat.isSafeEntryName("images/exercise_1.jpg"))
        assertTrue(BackupZipFormat.isSafeEntryName("backup.json"))
        assertTrue(BackupZipFormat.isSafeEntryName("images/a..b.jpg"))
        assertFalse(BackupZipFormat.isSafeEntryName("../x"))
        assertFalse(BackupZipFormat.isSafeEntryName("images/../../x"))
        assertFalse(BackupZipFormat.isSafeEntryName(""))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun entriesOf(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }
}
