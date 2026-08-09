package edu.fnosari.classmanager.backup

import android.content.Context
import edu.fnosari.classmanager.AppContainer
import edu.fnosari.classmanager.data.AppDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class BackupCheck {
    object Ok : BackupCheck()
    data class Invalid(val reason: String) : BackupCheck()
}

class BackupManager(private val context: Context, private val container: AppContainer) {

    /** Writes the backup zip; when [password] is non-blank the whole zip is AES-GCM encrypted. */
    suspend fun writeBackup(out: OutputStream, password: String? = null) = withContext(Dispatchers.IO) {
        if (password.isNullOrBlank()) {
            writeZip(out)
        } else {
            val buffer = java.io.ByteArrayOutputStream()
            writeZip(buffer)
            out.write(BackupCrypto.encrypt(buffer.toByteArray(), password))
            out.flush()
        }
    }

    private suspend fun writeZip(out: OutputStream) = withContext(Dispatchers.IO) {
        // flush WAL into the main db file
        container.db.query("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(
                JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("appVersion", 1)
                    .put("createdAt", LocalDate.now().toString())
                    .toString().toByteArray()
            )
            z.closeEntry()
            z.putNextEntry(ZipEntry("classmanager.db"))
            dbFile.inputStream().use { it.copyTo(z) }
            z.closeEntry()
            z.putNextEntry(ZipEntry("settings.json"))
            val settings = JSONObject()
            container.settings.weekARef.first()?.let { settings.put("weekARef", it) }
            settings.put("digestTime", container.settings.digestTime.first())
            settings.put("language", container.settings.language.first())
            settings.put("theme", container.settings.theme.first())
            container.settings.csvMapping.first()?.let { (l, f) ->
                settings.put("csvLast", l)
                settings.put("csvFirst", f)
            }
            z.write(settings.toString().toByteArray())
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
        // extract to temp first; current data untouched until swap
        val tmp = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val target = File(tmp, e.name)
                    if (!target.canonicalPath.startsWith(tmp.canonicalPath)) {
                        throw IOException("zip path traversal")
                    }
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
        File(tmp, "photos").listFiles()?.forEach {
            it.copyTo(File(container.photosDir, it.name), overwrite = true)
        }
        // settings.json is optional: pre-v6 backups don't carry it
        val settingsFile = File(tmp, "settings.json")
        if (settingsFile.exists()) {
            runCatching {
                val json = JSONObject(settingsFile.readText())
                if (json.has("weekARef")) container.settings.setWeekARef(json.getString("weekARef"))
                if (json.has("digestTime")) container.settings.setDigestTime(json.getString("digestTime"))
                if (json.has("language")) container.settings.setLanguage(json.getString("language"))
                if (json.has("theme")) container.settings.setTheme(json.getString("theme"))
                if (json.has("csvLast") && json.has("csvFirst")) {
                    container.settings.setCsvMapping(json.getInt("csvLast"), json.getInt("csvFirst"))
                }
            }
        }
        tmp.deleteRecursively()
        container.reopenDb()
        container.alarms.rescheduleAll()
    }

    companion object {
        const val SCHEMA_VERSION = 6

        fun validate(zipBytes: ByteArray): BackupCheck {
            val entries = mutableMapOf<String, ByteArray>()
            try {
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
                    var e = z.nextEntry ?: return BackupCheck.Invalid("not_a_zip")
                    while (true) {
                        if (!e.isDirectory && (e.name == "manifest.json" || e.name == "classmanager.db")) {
                            entries[e.name] = z.readBytes()
                        }
                        e = z.nextEntry ?: break
                    }
                }
            } catch (ex: Exception) {
                return BackupCheck.Invalid("not_a_zip")
            }
            val manifest = entries["manifest.json"] ?: return BackupCheck.Invalid("missing_manifest")
            val db = entries["classmanager.db"]
            if (db == null || db.size < 16 ||
                !String(db, 0, 15, Charsets.ISO_8859_1).startsWith("SQLite format 3")
            ) {
                return BackupCheck.Invalid("missing_db")
            }
            // regex instead of JSONObject: org.json is an unusable stub in plain JVM unit tests
            val schema = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")
                .find(String(manifest))?.groupValues?.get(1)?.toIntOrNull()
                ?: return BackupCheck.Invalid("missing_manifest")
            if (schema > SCHEMA_VERSION) return BackupCheck.Invalid("bad_schema_version")
            return BackupCheck.Ok
        }
    }
}
