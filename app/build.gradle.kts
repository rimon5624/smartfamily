plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val appVersionName: String = providers.gradleProperty("APP_VERSION_NAME").get()
val appVersionCode: Int = providers.gradleProperty("APP_VERSION_CODE").get().toInt()

// Release signing comes only from environment variables set by CI from GitHub Secrets.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val hasReleaseKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.rimon.smartfamily"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rimon.smartfamily"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The family's Supabase project. The anon key is public by design: row-level security
        // on the server decides what each signed-in user can read or write.
        buildConfigField("String", "DEFAULT_SUPABASE_URL", "\"https://giqafbtcyimbifyjvpgo.supabase.co\"")
        buildConfigField(
            "String", "DEFAULT_SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdpcWFmYnRjeWltYmlmeWp2cGdvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxMDExMTMsImV4cCI6MjExMDY3NzExM30.k_U-_iC6Z5zP6ZQqC3WtczQpuVqxQsIgFjlEkdx5aeY\"",
        )
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS_RESOLVED") ?: System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // on-emulator walkthrough of every screen (CI)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Local encrypted database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Networking (Supabase REST: Auth + PostgREST)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // FTP for the chains published on url.retail.publishedprices.co.il (price comparison)
    implementation("commons-net:commons-net:3.11.1")
    // QR codes (Wi-Fi for guests, family invites) — Apache 2.0, generated on the phone
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Barcode scanning through Google Play services (no camera permission needed)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Hebrew calendar, holidays and zmanim (LGPL 2.1), computed on the phone
    implementation("com.kosherjava:zmanim:2.5.0")

    // End-to-end encryption for the family chat (HPKE X25519 + AES-GCM)
    implementation("com.google.crypto.tink:tink-android:1.15.0")

    // Argon2id for backup passwords
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
