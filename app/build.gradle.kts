import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Upload key for Google Play (Play App Signing keeps the real signing key). Read from the
 * git-ignored keystore.properties at the project root, or from DRENGR_UPLOAD_* environment
 * variables on CI. Without either, release builds are produced unsigned. See RELEASE.md.
 */
val uploadKey: Map<String, String?> = run {
    val props = Properties()
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(props::load)
    fun value(name: String, env: String): String? = props.getProperty(name) ?: System.getenv(env)
    mapOf(
        "storeFile" to value("storeFile", "DRENGR_UPLOAD_STORE_FILE"),
        "storePassword" to value("storePassword", "DRENGR_UPLOAD_STORE_PASSWORD"),
        "keyAlias" to value("keyAlias", "DRENGR_UPLOAD_KEY_ALIAS"),
        "keyPassword" to value("keyPassword", "DRENGR_UPLOAD_KEY_PASSWORD"),
    )
}
val hasUploadKey = uploadKey.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.drengr.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drengr.launcher"
        minSdk = 26
        targetSdk = 35
        // Play needs a higher versionCode for every upload: bump both before each release.
        versionCode = 1
        versionName = "0.1.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(uploadKey.getValue("storeFile")!!)
                storePassword = uploadKey["storePassword"]
                keyAlias = uploadKey["keyAlias"]
                keyPassword = uploadKey["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    // Applies the libraries' startup (baseline) profiles on install, also outside Play.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    // android.jar only has stubs for org.json; unit tests need the real implementation.
    testImplementation(libs.org.json)
}
