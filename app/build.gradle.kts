plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
    // Safe Args (biblioteca que realiza o envio de dados entre as telas de forma segura)
    id("androidx.navigation.safeargs.kotlin")
    // Hilt
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    // Parcelize
    id("kotlin-parcelize") // serve para você poder empacotar objetos em Bundle/Intent
    // Kotlin Serialization plugin
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.luizeduardobrandao.appreceitascha"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luizeduardobrandao.appreceitascha"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle, LiveData & ViewModel
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")

    // Navigation e Fragment
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    // App Check: debug só em debug (evita mandar provider debug pra release)
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    // Firebase Functions (Callable)
    implementation("com.google.firebase:firebase-functions")
    // Google Sign-In (login com conta Google/Gmail)
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    // Coroutines
    // Importa o BOM das coroutines
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2"))
    // A extensão para Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services")

    // Unit tests (src/test/java)
    testImplementation(libs.junit)                                          // JUnit
    testImplementation("org.mockito:mockito-core:4.5.1")                    // Mockito
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")           // Mockito‑Kotlin
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")     // Coroutines Test
    testImplementation("androidx.arch.core:core-testing:2.2.0")             // LiveData / Arch Components testing

    // Android instrumented tests (src/androidTest/java)
    androidTestImplementation(libs.androidx.junit)                              // AndroidX JUnit
    androidTestImplementation(libs.androidx.espresso.core)                      // Espresso
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.56.2")  // Hilt testing
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.56.2")            // KSP for Hilt in androidTest
    testImplementation(kotlin("test"))

    // Imagens
    implementation("io.coil-kt:coil:2.7.0")

    // Converter de kotlinx-serialization para Retrofit
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // --- JSON (kotlinx-serialization) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Lottie (carregamento)
    implementation("com.airbnb.android:lottie:6.4.0")

    // Permissão de Localização
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Google Play Billing (KTX) – versão compatível com as regras atuais do Google
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    // Desugaring do Java Time
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Root Detection - Segurança
    implementation("com.scottyab:rootbeer-lib:0.1.1")
}