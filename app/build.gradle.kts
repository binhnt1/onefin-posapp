plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.onefin.posapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.onefin.posapp"
        minSdk = 26
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true

        dataBinding = true
        viewBinding = true

        // Tắt các feature không dùng
        aidl = true
        renderScript = false
        resValues = true
        shaders = false
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        @Suppress("DEPRECATION")
        jvmTarget = "17"

        @Suppress("DEPRECATION")
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    kapt {
        correctErrorTypes = true
        generateStubs = false
        useBuildCache = true

        arguments {
            arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
        }
    }

    flavorDimensions += "customer"
    productFlavors {

        // Flavor cho khách hàng A
        create("sgpt") {
            dimension = "customer"
            versionNameSuffix = "-sgpt"
            resValue("string", "app_name", "Onefin Payment")
            buildConfigField("String", "SDK_TYPE", "\"sunmi\"")
            buildConfigField("String", "USERNAME", "\"sgpt@yopmail.com\"")
            buildConfigField("String", "APP_KEY", "\"21f7d3f50ab94f23a0b0b3e081fa8ced\"")
        }

        // Flavor cho khách hàng B
        create("mailinh") {
            dimension = "customer"
            versionNameSuffix = "-mailinh"
            resValue("string", "app_name", "Onefin Payment")
            buildConfigField("String", "SDK_TYPE", "\"sunmi\"")
            buildConfigField("String", "USERNAME", "\"mailinh@yopmail.com\"")
            buildConfigField("String", "APP_KEY", "\"85ba2fcfe7fa4511a37a8f017a282fa8\"")
        }

        // Flavor cho khách hàng C
        create("megatech") {
            dimension = "customer"
            versionNameSuffix = "-megatech"
            resValue("string", "app_name", "Onefin Payment")
            buildConfigField("String", "SDK_TYPE", "\"sunmi\"")
            buildConfigField("String", "USERNAME", "\"mcmegatech@yopmail.com\"")
            buildConfigField("String", "APP_KEY", "\"909c326357b74a36a9dafadfdf6cb5bf\"")
        }

        // Flavor mặc định (không có APP_KEY - phải login thủ công)
        create("default") {
            dimension = "customer"
            resValue("string", "app_name", "Onefin Payment")
            buildConfigField("String", "APP_KEY", "\"\"")
            buildConfigField("String", "SDK_TYPE", "\"sunmi\"")
            buildConfigField("String", "USERNAME", "\"\"")
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.github.devnied.emvnfccard:library:3.0.1")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ===== JETPACK COMPOSE =====
    val bomVersion = "2024.10.00"
    implementation(platform("androidx.compose:compose-bom:$bomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose Integration
    val activityVersion = "1.9.0"
    implementation("androidx.activity:activity-compose:$activityVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // ===== ARCHITECTURE COMPONENTS =====
    val lifecycleVersion = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion")

    val navVersion = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")
    implementation("androidx.navigation:navigation-compose:$navVersion")

    // ===== DEPENDENCY INJECTION =====
    val hiltVersion = "2.57.2"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ===== NETWORKING =====
    val retrofitVersion = "2.9.0"
    val okhttpVersion = "4.12.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.github.chuckerteam.chucker:library:3.5.2")

    // ===== OneFin SDKs =====
    implementation(files("libs/PayLib-release-2.0.07.aar"))

    implementation(files("libs/onefin-1.0.0.aar"))
//    implementation(files("libs/paylib-1.0.0.aar"))
    implementation(files("libs/payment-1.0.0.aar"))
    implementation(files("libs/printerlibrary-1.0.19.aar"))

    // ===== COROUTINES =====
    val coroutinesVersion = "1.7.3"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // ===== LOCAL STORAGE =====
    val roomVersion = "2.8.3"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ===== SECURITY =====
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")

    // ===== IMAGE LOADING =====
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ===== ANIMATIONS =====
    implementation("com.airbnb.android:lottie-compose:6.3.0")

    // ===== TIME & LOGGING =====
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.6")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ===== SPLASH SCREEN =====
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ===== WORK MANAGER =====
    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // ===== RABBITMQ =====
    implementation("com.rabbitmq:amqp-client:5.21.0")

    // ===== DEPENDENCY INJECTION (Koin) =====
    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("androidx.databinding:databinding-runtime:8.6.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
