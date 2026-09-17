plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rootrecord.avaops"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rootrecord.avaops"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "OPS_API_BASE_URL", "\"http://10.0.2.2:8787\"")
            buildConfigField("String", "OPS_LOCAL_API_BASE_URL", "\"http://10.0.2.2:8787\"")
            buildConfigField("String", "OPS_CLOUDFLARE_API_BASE_URL", "\"\"")
            buildConfigField("String", "OPS_BLUETOOTH_DEVICE_ADDRESS", "\"\"")
            buildConfigField("String", "OPS_BLUETOOTH_DEVICE_NAME", "\"\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // OmniBook desk LAN fallback after Bluetooth; override in Connection Settings.
            buildConfigField("String", "OPS_API_BASE_URL", "\"http://192.168.1.66:8787\"")
            buildConfigField("String", "OPS_LOCAL_API_BASE_URL", "\"http://192.168.1.66:8787\"")
            buildConfigField("String", "OPS_CLOUDFLARE_API_BASE_URL", "\"\"")
            buildConfigField("String", "OPS_BLUETOOTH_DEVICE_ADDRESS", "\"\"")
            buildConfigField("String", "OPS_BLUETOOTH_DEVICE_NAME", "\"\"")
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

}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
