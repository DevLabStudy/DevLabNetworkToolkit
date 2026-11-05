plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFiles = listOf(
        rootProject.file("local.properties"),
        project.file("local.properties")
    ).distinct()
    localPropertiesFiles.firstOrNull { it.isFile }?.inputStream()?.use(::load)
}

val supabaseAnonKey = localProperties.getProperty("supabaseAnonKey", "").trim()
if (supabaseAnonKey.isBlank()) {
    logger.warn(
        "Supabase anon key is not configured. Create local.properties in the project root " +
            "with supabaseAnonKey=... before building a usable APK."
    )
}

val supabaseUrl = localProperties.getProperty(
    "supabaseUrl",
    "https://clizjpihxmtoaywvosom.supabase.co"
).trimEnd('/')

fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "ovh.devlabstudy.devlabnetworktoolkit"
    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ovh.devlabstudy.devlabnetworktoolkit"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${escapeBuildConfig(supabaseUrl)}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${escapeBuildConfig(supabaseAnonKey)}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.android.billingclient:billing-ktx:6.2.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}