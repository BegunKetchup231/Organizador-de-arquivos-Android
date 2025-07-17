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
    // Dependências AndroidX e Material
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // Dependencia para a tela de configurações (apenas a versão ktx é necessária)
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Dependências específicas para o app
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.guava:guava:32.1.3-android")

    // Anúncios
    implementation("com.google.android.gms:play-services-ads:24.4.0")

    // Dependências de Teste
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}