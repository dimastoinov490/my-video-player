import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

// Автоматический номер версии: берет номер из GitHub Actions, а для локальных сборок ставит 1
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

android {
    namespace = "com.example.videoplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videoplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    }

    signingConfigs {
        create("debugConfig") {
            val keystoreFile = file("${rootDir}/debug.keystore")
            val base64File = file("${rootDir}/debug.keystore.base64")
            if (!keystoreFile.exists() && base64File.exists()) {
                try {
                    val bytes = Base64.getDecoder().decode(base64File.readText().trim())
                    keystoreFile.writeBytes(bytes)
                } catch (_: Exception) {
                    // ignore
                }
            }
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasReleaseSigning) {
            create("releaseConfig") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            val debugCfg = signingConfigs.findByName("debugConfig")
            if (debugCfg?.storeFile != null && debugCfg.storeFile!!.exists()) {
                signingConfig = debugCfg
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseConfig")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("")

    implementation("org.videolan.android:libvlc-all:3.7.7")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("org.slf4j:slf4j-android:1.7.36")
    implementation("commons-net:commons-net:3.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
