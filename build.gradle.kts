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

// El repo vive en una unidad SMB (Z:). Las salidas de build van a disco local NTFS
// para evitar bloqueos de fichero y lentitud. Ver docs/DECISIONES.md (ADR-002).
// Se puede cambiar con -Pgym.buildRoot=... o la variable GYM_BUILD_ROOT.
val buildRoot: String = (findProperty("gym.buildRoot") as String?)
    ?: System.getenv("GYM_BUILD_ROOT")
    ?: "C:/build/gym"

allprojects {
    val dirName = if (this == rootProject) "_root" else name
    layout.buildDirectory.set(file("$buildRoot/$dirName"))
}
