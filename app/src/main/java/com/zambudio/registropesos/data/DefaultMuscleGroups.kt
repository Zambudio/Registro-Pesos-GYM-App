package com.zambudio.registropesos.data

/**
 * Grupos musculares sembrados al crear la BD. Los ids son constantes (ADR-005) para que dos
 * instalaciones no dupliquen "Pecho" al importar un backup. NO cambiar nunca estos valores.
 */
object DefaultMuscleGroups {
    /**
     * Marca de tiempo fija del seed: un grupo por defecto sin tocar nunca gana un merge
     * frente a una edición real hecha en otro dispositivo.
     */
    const val SEED_TIMESTAMP: Long = 0L

    data class Seed(val id: String, val name: String, val sortOrder: Int)

    val all: List<Seed> = listOf(
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000001", "Pecho", 0),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000002", "Espalda", 1),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000003", "Hombros", 2),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000004", "Bíceps", 3),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000005", "Tríceps", 4),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000006", "Cuádriceps", 5),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000007", "Isquios", 6),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000008", "Glúteos", 7),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000009", "Gemelos", 8),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000010", "Abdomen", 9),
        Seed("8d6e3b1c-5a2f-4c71-9e40-000000000011", "Cardio", 10),
    )
}
