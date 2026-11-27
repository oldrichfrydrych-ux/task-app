plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cz.olda.tasks"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cz.olda.tasks"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signs the release build with the same debug keystore Android Studio already uses, so
    // `gradlew installRelease` just works and upgrades the installed app in place instead of
    // asking for an uninstall. This app never goes to the Play Store, so there is no release
    // key to protect; "android" is the published password of every Android debug keystore,
    // not a secret, and the keystore itself is not in the repository. Guarded by exists() so
    // the build still works on a machine that has never run Android Studio.
    val debugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")

    signingConfigs {
        create("localDebugKey") {
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("localDebugKey")
            }
            optimization {
                enable = true
                // No packageScope. Listing androidx/kotlin/kotlinx here let R8 repackage
                // library classes across their own package boundaries, and the app died on
                // launch with "Class kotlin.collections.g extended by class xl is
                // inaccessible" - package-private access broken by the move. Verified by
                // building, signing and launching: with this line, it crashes; without it,
                // it starts.
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}