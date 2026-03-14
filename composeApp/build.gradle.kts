import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization")
}

val ktorVersion = "3.0.3"
val secrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) {
        load(secretsFile.inputStream())
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.camera:camera-camera2:1.3.4")
            implementation("androidx.camera:camera-lifecycle:1.3.4")
            implementation("androidx.camera:camera-view:1.3.4")
            implementation("com.google.mlkit:barcode-scanning:17.3.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
androidMain.dependencies {
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("io.ktor:ktor-client-okhttp:${ktorVersion}")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("com.google.maps.android:maps-compose-utils:6.1.2")

    // Coil for SVG support
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
}

        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
            implementation("io.ktor:ktor-client-logging:$ktorVersion")
         //   implementation(project.dependencies.platform(libs.supabase.bom))
            // Supabase
            implementation("io.github.jan-tennert.supabase:auth-kt:3.0.3")
            implementation("io.github.jan-tennert.supabase:postgrest-kt:3.0.3")
            implementation("io.github.jan-tennert.supabase:realtime-kt:3.0.3")

            // Kotlin serialization
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }

        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    val generateSecrets = tasks.register("generateSecrets") {
        val projectID = "goyvtbimpgvyzydxkdlh"
        val supabaseUrl = "https://$projectID.supabase.co"
        val supabaseAnonKey = secrets["SUPABASE_ANON_KEY"]?.toString() ?: "YOUR_SUPABASE_ANON_KEY"

        inputs.property("supabaseUrl", supabaseUrl)
        inputs.property("supabaseAnonKey", supabaseAnonKey)

        val outputDir = layout.buildDirectory.dir("generated/secrets/commonMain/kotlin")
        outputs.dir(outputDir)

        doLast {
            val outputFile = outputDir.get().file("com/example/voltloop/Secrets.kt").asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText("""
                package com.example.voltloop

                object Secrets {
                    const val SUPABASE_URL = "$supabaseUrl"
                    const val SUPABASE_ANON_KEY = "$supabaseAnonKey"
                    const val GOOGLE_MAPS_API_KEY = "AIzaSyAMNxRu8E4kluchvUXLY975AzFlFIZfvU0"
                }
            """.trimIndent())
        }
    }

    sourceSets.commonMain.configure {
        kotlin.srcDir(generateSecrets)
    }
}

android {
    namespace = "com.example.voltloop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.voltloop"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets["main"].res.filter.exclude("**/one_battery.svg")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}