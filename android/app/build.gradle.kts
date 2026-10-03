plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // app/google-services.json 을 읽어 Firebase 설정과 R.string.default_web_client_id 를 만든다
    alias(libs.plugins.google.services)
}

android {
    // 패키지 이름: Firebase 콘솔에 Android 앱을 등록할 때 이 값과 똑같이 적는다.
    // 플레이스토어에 한 번 올리면 바꿀 수 없으니 확정한 뒤 등록한다.
    namespace = "com.logus.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.logus.app"
        minSdk = 26 // Android 8.0 이상
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase: 버전은 BoM 하나로 맞춘다
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services) // Firebase Task 를 await() 로 기다리기

    // 구글 로그인: 안드로이드 표준 로그인 창(Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
}
