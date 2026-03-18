plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val tuneHubApiKey = ((project.findProperty("TUNEHUB_API_KEY") as String?) ?: "")
    .replace("\"", "\\\"")
val appUpdateRepo = ((project.findProperty("APP_UPDATE_REPO") as String?) ?: "")
    .replace("\"", "\\\"")
val releaseStoreFile = ((project.findProperty("RELEASE_STORE_FILE") as String?) ?: "").trim()
val releaseStorePassword = ((project.findProperty("RELEASE_STORE_PASSWORD") as String?) ?: "").trim()
val releaseKeyAlias = ((project.findProperty("RELEASE_KEY_ALIAS") as String?) ?: "").trim()
val releaseKeyPassword = ((project.findProperty("RELEASE_KEY_PASSWORD") as String?) ?: "").trim()
val releaseSigningRequired = (((project.findProperty("RELEASE_SIGNING_REQUIRED") as String?) ?: "false")
    .trim()
    .equals("true", ignoreCase = true))
val hasCustomReleaseSigning = releaseStoreFile.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

if (releaseSigningRequired && !hasCustomReleaseSigning) {
    throw GradleException(
        "Missing release signing properties. " +
            "Please provide RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD."
    )
}

android {
    namespace = "com.music.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.music.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.4.0"
        buildConfigField("String", "TUNEHUB_API_KEY", "\"$tuneHubApiKey\"")
        buildConfigField("String", "APP_UPDATE_REPO", "\"$appUpdateRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCustomReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasCustomReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Fallback for local testing when release keystore is not configured.
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Coil
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Palette
    implementation(libs.palette.ktx)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.hilt)
    ksp(libs.hilt.work.compiler)

    // Metrics / JankStats
    implementation(libs.androidx.metrics.performance)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
