plugins {
    id("com.android.application")
    // kotlin.android REMOVIDO: built-in do AGP 9.0+ (https://kotl.in/gradle/agp-built-in-kotlin)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.enhikyy.girassol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.enhikyy.girassol"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // kotlinOptions REMOVIDO: bloco nao existe mais com Kotlin built-in do AGP 9.
    // jvmTarget acompanha compileOptions (Java 17) automaticamente.

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-text-google-fonts")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Haze 2.x (API corrigida: hazeBlur/HazeInput, NÃO hazeChild/hazeEffect da 1.x)
    implementation("dev.chrisbanes.haze:haze:2.1.0")
    // hazeBlur/HazeInput/HazeBlurStyle vivem no modulo haze-blur (Haze 2.x, migrating-2.0.md)
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")

    // LLM: llama.cpp pronto via Llamatik (decisao do prompt M0: usar o que existe; nativo proprio so se faltar recurso)
    implementation("com.llamatik:core:2.4.0")
    // hazeBlur/HazeInput/HazeBlurStyle vivem no modulo haze-blur (Haze 2.x, migrating-2.0.md)
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
