package com.zambudio.registropesos.domain.usecase.backup

import com.zambudio.registropesos.domain.backup.BACKUP_SCHEMA_VERSION
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import kotlinx.serialization.json.Json

/** El backup viene de una versión de la app más nueva que esta y no se sabe leer. */
class UnsupportedBackupVersionException(val schemaVersion: Int) :
    IllegalArgumentException("Versión de backup no soportada: $schemaVersion (máx. $BACKUP_SCHEMA_VERSION)")

/** Serialización de backup.json. Ignora claves desconocidas para tolerar campos añadidos. */
object BackupSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(BackupSnapshot.serializer(), snapshot)

    /**
     * @throws kotlinx.serialization.SerializationException si el JSON no es válido.
     * @throws UnsupportedBackupVersionException si `schemaVersion` es mayor que la soportada.
     */
    fun decode(text: String): BackupSnapshot {
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), text)
        if (snapshot.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw UnsupportedBackupVersionException(snapshot.schemaVersion)
        }
        return snapshot
    }
}
