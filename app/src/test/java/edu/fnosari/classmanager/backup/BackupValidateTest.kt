package edu.fnosari.classmanager.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidateTest {
    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private val sqliteHeader = "SQLite format 3 ".toByteArray(Charsets.ISO_8859_1)

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
