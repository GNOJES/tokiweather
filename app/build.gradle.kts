import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// local.properties에서 API 키와 좌표 읽기
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.toki.weather"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.toki.weather"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // BuildConfig에 API 키와 좌표 주입
        buildConfigField("String", "KMA_API_KEY", "\"${localProperties.getProperty("KMA_API_KEY", "")}\"")
        buildConfigField("int", "DEFAULT_NX", localProperties.getProperty("DEFAULT_NX", "60"))
        buildConfigField("int", "DEFAULT_NY", localProperties.getProperty("DEFAULT_NY", "127"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)

    // Jetpack Compose for MainActivity (Settings UI)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
}
