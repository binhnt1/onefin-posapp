plugins {
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.9.5" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
