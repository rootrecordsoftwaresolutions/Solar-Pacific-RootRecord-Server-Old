import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
}

// Copy google-services.json.example ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ google-services.json after registering com.rootrecord.rootmc in Firebase.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.rootrecord.rootmc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rootrecord.rootmc"
        minSdk = 24
        targetSdk = 35
        versionCode = 31
        versionName = "1.0.31"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { props.load(it) }
        val admobAppId = props.getProperty("ADMOB_APPLICATION_ID")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "ca-app-pub-8245496571119619~4685237071"
        val admobBanner = props.getProperty("ADMOB_BANNER_AD_UNIT_ID")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "ca-app-pub-8245496571119619/2059073739"
        val admobInterstitial = props.getProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "ca-app-pub-8245496571119619/8432910391"
        manifestPlaceholders["admobApplicationId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$admobBanner\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$admobInterstitial\"")
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { props.load(it) }
            val storeFilePath = props.getProperty("RELEASE_STORE_FILE", "keystore/blocknotes-upload.jks")
            val storePass = props.getProperty("RELEASE_STORE_PASSWORD", "") ?: ""
            val keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "blocknotes-upload")
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
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
    implementation(libs.play.services.ads)
    implementation(libs.browser)
    implementation(libs.reorderable)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
