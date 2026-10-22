import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("kotlin.RequiresOptIn")
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

val keystorePropertiesFile = rootProject.file("jks.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasLocalSigningCredentials =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { key ->
        val value = keystoreProperties.getProperty(key)
        !value.isNullOrBlank() && !value.startsWith("REPLACE_WITH_")
    }
val hasEnvironmentSigningCredentials =
    listOf("KEY_ALIAS", "KEY_PASSWORD", "KEYSTORE_PASSWORD").all { name ->
        !providers.environmentVariable(name).orNull.isNullOrBlank()
    }
val hasSigningCredentials = hasLocalSigningCredentials || hasEnvironmentSigningCredentials

android {
    namespace = "com.valhalla.bolt"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valhalla.bolt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1010
        versionName = "1.01.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasLocalSigningCredentials) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            } else if (hasEnvironmentSigningCredentials) {
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                storeFile = file(
                    providers.environmentVariable("KEYSTORE_FILE_PATH").getOrElse("release.jks")
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // An empty signing config breaks release builds on machines without credentials.
            signingConfig = signingConfigs.getByName("release").takeIf { hasSigningCredentials }
            if (!hasSigningCredentials) {
                logger.lifecycle(
                    "Release signing is not configured; fill in jks.properties or signing environment variables. " +
                        "Release artifacts will be unsigned."
                )
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols += "**/libbolt_busybox.so"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.documentfile)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
