plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
}

// Opcional: sacar las salidas de build a otra carpeta con -Pgym.buildRoot=... o la variable
// GYM_BUILD_ROOT. Sin ninguna de las dos se usa el build/ estándar de cada proyecto.
// Ojo: KSP exige que el proyecto y el build estén en la misma unidad. Ver ADR-002 y ADR-017.
val buildRoot: String? = (findProperty("gym.buildRoot") as String?)
    ?: System.getenv("GYM_BUILD_ROOT")

if (buildRoot != null) {
    allprojects {
        val dirName = if (this == rootProject) "_root" else name
        layout.buildDirectory.set(file("$buildRoot/$dirName"))
    }
}
