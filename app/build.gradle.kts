import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.release.lint.baseline)
}

android {
    // Generate the R class in the same package as the original aShell app.  This
    // ensures that imports like `in.hridayan.ashell.R` continue to resolve
    // correctly, while still allowing the applicationId to use a different
    // namespace (eu.eus) for installation.  Do not change this unless you
    // update all package references throughout the app.
    namespace = "in.hridayan.ashell"
    compileSdk = 36

    defaultConfig {
        // The applicationId uniquely identifies this app on a device and in the Google Play
        // ecosystem. Use the simple and memorable ID provided by the user.
        applicationId = "eu.eus"
        minSdk = 28
        targetSdk = 36
        // Reset versioning for the new application. Starting with versionCode 1 and
        // a simple semantic version string. Bump these values as you release updates.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            if (System.getenv("CI")?.toBoolean() == true) {
                val keystorePath = System.getenv("KEYSTORE_PATH")
                val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
                val keyAlias = System.getenv("KEY_ALIAS")
                val keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword

                if (
                    !keystorePath.isNullOrBlank() &&
                    !keystorePassword.isNullOrBlank() &&
                    !keyAlias.isNullOrBlank() &&
                    !keyPassword.isNullOrBlank()
                ) {
                    storeFile = file(keystorePath)
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("CI")?.toBoolean() == true)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // All ADB classes are provided by the libadb and adblib modules. Remove any duplicate sources
    // from the application module by deleting the corresponding directory in the project tree. We no
    // longer configure an exclusion here because the duplicate sources are not included.
}

dependencies {
    implementation(project(":libadb"))
    implementation(project(":adblib"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.material)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.navigation.compose)

    implementation(libs.room.ktx)
    implementation(libs.androidx.documentfile)
    ksp(libs.room.compiler)

    implementation(libs.serialization.json)
    implementation(libs.gson)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.androidx.compose.animation)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)

    implementation(libs.jmdns)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.slf4j.android)

    implementation(libs.androidx.security.crypto)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu.core)

    implementation(libs.lottie.compose)
    implementation(libs.nayuki.qrcode)
    implementation(libs.sun.security.android)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.shapeindicators)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
