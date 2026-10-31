import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// URL server default: -PBASE_URL=..., env BASE_URL, atau local.properties (BASE_URL=...)
// Kalau kosong, aplikasi akan menanyakan URL saat pertama dibuka.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val baseUrl: String = (findProperty("BASE_URL") as String?)
    ?: System.getenv("BASE_URL")
    ?: localProps.getProperty("BASE_URL")
    ?: ""

// Nama file APK: fr3velora-release.apk
base.archivesName.set("fr3velora")

android {
    namespace = "fr3velora.xyz"
    compileSdk = 35

    defaultConfig {
        // Package sama dengan APK listener lama, sesuai EXPECTED_PACKAGE di server (/api/app/upload)
        applicationId = "com.aistudio.danalistener.tnlahb"
        minSdk = 29
        targetSdk = 35
        versionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 100
        versionName = (findProperty("VERSION_NAME") as String?) ?: "2.0.0"
        buildConfigField("String", "DEFAULT_BASE_URL", "\"${baseUrl.trim().trimEnd('/')}\"")
        // Paket aplikasi DANA yang notifikasinya dibaca (pisahkan dengan koma).
        // Cek paket yang benar lewat menu "Log notifikasi" (mode debug).
        buildConfigField("String", "DANA_PACKAGES", "\"id.dana,id.dana.business,id.dana.merchant\"")
    }

    // Keystore tetap supaya APK bisa di-update tanpa uninstall. Opsional: kalau tidak diisi, pakai debug key.
    val ksPath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (ksPath != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
