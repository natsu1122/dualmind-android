buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("com.android.tools:r8:9.5.28") }
}
plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.4.0" apply false
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.compose") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
