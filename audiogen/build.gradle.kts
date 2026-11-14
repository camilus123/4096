import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Synthesizes every sound in the game. Not part of the app: run it by hand when the audio
// should change (see tools/generate-audio.sh) and commit the files it produces.
plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("io.github.camilus123.game4096.audiogen.MainKt")
}
