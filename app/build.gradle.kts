plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.organizadordearquivos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.organizadordearquivos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Você pode adicionar um bloco 'debug' aqui se quiser, mas não é obrigatório agora
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Corrected
        targetCompatibility = JavaVersion.VERSION_11 // Corrected
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Dependências AndroidX e Material (versões hardcoded para garantir que funcionem)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Dependências específicas para o app (DocumentFile e Coroutines)
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Dependências de Teste (mantidas como libs. pois costumam funcionar bem)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}