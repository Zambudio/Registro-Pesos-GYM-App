package com.zambudio.registropesos.data.catalog

import android.content.Context
import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.DefaultMuscleGroups
import com.zambudio.registropesos.data.local.entity.ExerciseEntity
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.domain.repository.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/** assets/catalog/catalog.json, generado por tools/catalog/extract-catalog.mjs. */
@Serializable
data class CatalogFile(
    val version: Int,
    val groups: List<CatalogGroup>,
    val exercises: List<CatalogExercise>,
)

@Serializable
data class CatalogGroup(val id: String, val name: String)

@Serializable
data class CatalogExercise(
    val id: String,
    val groupId: String,
    val group: String,
    val name: String,
    /** Fichero dentro de assets/catalog/images. */
    val image: String,
)

data class CatalogSeedResult(val groupsCreated: Int, val exercisesCreated: Int, val exercisesSkipped: Int)

/**
 * Carga el catálogo de ejercicios incluido en la app (nombres y fotos del Excel de rutinas,
 * ADR-019). Se ejecuta en cada arranque y es idempotente:
 * - Un ejercicio del catálogo se crea solo si no existe ya su id fijo ni otro ejercicio con el
 *   mismo nombre en su grupo. Nunca se modifica uno existente ni su foto.
 * - El grupo se busca por id fijo y luego por nombre (sin mayúsculas); si no hay ninguno y no se
 *   borró antes (tombstone), se crea con el id fijo. Si el grupo se borró, sus ejercicios no se crean.
 * - La foto se copia antes de insertar el ejercicio: si el proceso muere entre medias, el
 *   siguiente arranque la reescribe con el mismo nombre y crea la fila.
 * Las marcas de tiempo son las del seed (0): en un merge de backup nunca ganan a una edición real.
 */
class CatalogSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val imageStore: ImageStore,
) {
    private val groupDao = db.muscleGroupDao()
    private val exerciseDao = db.exerciseDao()

    suspend fun seed(): CatalogSeedResult {
        val catalog = readCatalog()
        val (groupIds, groupsCreated) = resolveGroups(catalog.groups)
        val created = catalog.exercises.count { item -> groupIds[item.groupId]?.let { seedExercise(item, it) } == true }
        return CatalogSeedResult(groupsCreated, created, catalog.exercises.size - created)
    }

    /** Crea el ejercicio con su foto si no existe ya; devuelve si lo ha creado. */
    private suspend fun seedExercise(item: CatalogExercise, groupId: String): Boolean {
        if (exists(item, groupId)) return false
        val imageName = "exercise_${item.id}.jpg"
        // Una foto ilegible no impide sembrar el resto: ese ejercicio se reintenta en el próximo arranque.
        try {
            imageStore.importRaw(imageName, readAsset("$IMAGES_DIR/${item.image}"))
        } catch (ignored: IOException) {
            return false
        }
        return db.withTransaction {
            // Doble comprobación dentro de la transacción: otro arranque pudo crearlo ya.
            if (exists(item, groupId)) {
                false
            } else {
                exerciseDao.insert(item.toEntity(groupId, imageName))
                true
            }
        }
    }

    fun readCatalog(): CatalogFile = json.decodeFromString(CatalogFile.serializer(), String(readAsset(CATALOG_FILE)))

    private fun readAsset(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }

    /** Id local de cada grupo del catálogo (ausente si el usuario lo borró). */
    private suspend fun resolveGroups(groups: List<CatalogGroup>): Pair<Map<String, String>, Int> =
        db.withTransaction {
            var created = 0
            val existing = groupDao.getAll()
            val byName = existing.associateBy { normalize(it.name) }
            var nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val resolved = mutableMapOf<String, String>()
            for (group in groups) {
                val local = groupDao.getById(group.id) ?: byName[normalize(group.name)]
                when {
                    local != null -> resolved[group.id] = local.id
                    db.tombstoneDao().get(TombstoneType.MUSCLE_GROUP, group.id) != null -> Unit
                    else -> {
                        groupDao.insert(
                            MuscleGroupEntity(
                                id = group.id,
                                name = group.name,
                                sortOrder = nextOrder++,
                                isDefault = true,
                                createdAt = DefaultMuscleGroups.SEED_TIMESTAMP,
                                updatedAt = DefaultMuscleGroups.SEED_TIMESTAMP,
                            ),
                        )
                        resolved[group.id] = group.id
                        created++
                    }
                }
            }
            resolved to created
        }

    private suspend fun exists(item: CatalogExercise, groupId: String): Boolean =
        exerciseDao.getById(item.id) != null ||
            exerciseDao.getByGroup(groupId).any { normalize(it.name) == normalize(item.name) }

    private fun CatalogExercise.toEntity(groupId: String, imageName: String) = ExerciseEntity(
        id = id,
        muscleGroupId = groupId,
        name = name,
        notes = null,
        imagePath = imageName,
        isArchived = false,
        createdAt = DefaultMuscleGroups.SEED_TIMESTAMP,
        updatedAt = DefaultMuscleGroups.SEED_TIMESTAMP,
    )

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    companion object {
        const val CATALOG_FILE = "catalog/catalog.json"
        const val IMAGES_DIR = "catalog/images"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
