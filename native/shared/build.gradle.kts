import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iOS targets need a macOS host (Xcode toolchain); the Swift app consumes this framework on a Mac/CI.
    val isMac = System.getProperty("os.name").startsWith("Mac")
    if (isMac) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "PlutonCore"
                isStatic = true
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            implementation("app.cash.sqldelight:runtime:2.4.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("app.cash.sqldelight:sqlite-driver:2.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        if (isMac) {
            iosMain.dependencies {
                implementation("app.cash.sqldelight:native-driver:2.4.1")
            }
        }
    }
}

sqldelight {
    databases {
        create("PlutonDb") {
            packageName.set("io.github.vykosmolt.pluton.core.db")
        }
    }
}

android {
    namespace = "io.github.vykosmolt.pluton.core"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
