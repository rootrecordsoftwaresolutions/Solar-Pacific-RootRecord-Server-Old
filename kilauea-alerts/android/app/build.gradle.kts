import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.rootrecord.kilauea.alerts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rootrecord.kilauea"
        minSdk = 24
        targetSdk = 35
        versionCode = 47
        versionName = "1.0.47"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { props.load(it) }
        val ytKey = props.getProperty("YOUTUBE_API_KEY", "") ?: ""
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$ytKey\"")
        // Production banner unit; debug buildType overrides with Google's sample banner ID.
        buildConfigField(
            "String",
            "ADMOB_BANNER_AD_UNIT_ID",
            "\"ca-app-pub-8245496571119619/7991546983\"",
        )
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_AD_UNIT_ID",
            "\"ca-app-pub-8245496571119619/4317875333\"",
        )
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { props.load(it) }
            val storeFilePath = props.getProperty("RELEASE_STORE_FILE", "keystore/kilauea-upload.jks")
            val storePass = props.getProperty("RELEASE_STORE_PASSWORD", "") ?: ""
            val keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "kilauea-upload")
            val keyPass = props.getProperty("RELEASE_KEY_PASSWORD", storePass) ?: ""

            storeFile = rootProject.file(storeFilePath)
            storePassword = storePass
            this.keyAlias = keyAlias
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"ca-app-pub-3940256099942544/6300978111\"",
            )
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"ca-app-pub-3940256099942544/1033173712\"",
            )
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
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics) {
        exclude(group = "com.google.firebase", module = "firebase-analytics-ktx")
    }
    implementation(libs.firebase.messaging) {
        exclude(group = "com.google.firebase", module = "firebase-messaging-ktx")
    }

    implementation(libs.play.services.location)
    implementation(libs.play.services.ads)
    implementation(libs.browser)

    implementation(libs.accompanist.permissions)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
