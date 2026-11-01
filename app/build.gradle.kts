import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Release signing is read from keystore.properties (git-ignored) when present, so
// `./gradlew assembleRelease` produces a signed APK for the Play Store. CI and
// debug builds work without it.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.noor.shoplocal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noor.shoplocal"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Enable per-app language selection (Android 13+ system integration).
        resourceConfigurations += listOf("en", "zu", "af")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX + Material UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle + coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking — external library used to talk to the REST API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coil — external image-loading library for product photos served by the API
    implementation("io.coil-kt:coil:2.5.0")

    // osmdroid — OpenStreetMap map view for the "artisan map" (no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Room — local SQLite database for offline mode + queued actions to sync.
    // The compiler is scoped to the app variants (not the pure-JVM unit-test
    // variant, which has no Room code) to keep KSP fast and avoid a Windows
    // file-lock on the unit-test KSP cache.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    add("kspDebug", "androidx.room:room-compiler:2.6.1")
    add("kspRelease", "androidx.room:room-compiler:2.6.1")

    // Firebase — Cloud Messaging (real-time push) + Firestore (NoSQL store/read)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Custom Tabs — used for the Google SSO sign-in flow
    implementation("androidx.browser:browser:1.7.0")

    // ---- Testing ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
