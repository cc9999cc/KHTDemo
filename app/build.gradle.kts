plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id("com.chaquo.python")
}

android {
    namespace = "com.ccc.khtdemo"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.ccc.khtdemo"
        minSdk = 28
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

//        python {
//            pip {
//                install "matplotlib"
//            }
//        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // MediaPipe Library
//    implementation("com.google.mediapipe:tasks-vision:0.10.0")
    // CameraX  library
    val cameraxVersion = "1.3.0-alpha05"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation(fileTree("libs").include("*.aar","*.jar"))
    // MediaPipe deps
    implementation("com.google.flogger:flogger:0.3.1")
    implementation("com.google.flogger:flogger-system-backend:0.3.1")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:27.0.1-android")
    implementation("com.google.guava:guava:27.0.1-android")
    implementation("com.google.protobuf:protobuf-java:3.11.4")
}