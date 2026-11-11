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

        // 광고(AdMob) ID. 지금은 구글이 공개한 "테스트용" ID 라서 진짜 광고·수익은 없고 테스트 광고만 나온다.
        // 출시 전에 AdMob 콘솔에서 만든 앱 ID·광고 단위 ID 로 바꾼다(비밀 값이 아니라 코드에 둬도 된다).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
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
        buildConfig = true // BuildConfig.ADMOB_REWARDED_UNIT_ID
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
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Firebase: 버전은 BoM 하나로 맞춘다
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage) // 프로필 사진 올리기
    implementation(libs.firebase.functions) // 서버 함수 부르기(새 여정 만들기 createJourney)
    implementation(libs.firebase.messaging) // 촬영 알림 푸시 받기(FCM)
    implementation(libs.kotlinx.coroutines.play.services) // Firebase Task 를 await() 로 기다리기

    // 구글 로그인: 안드로이드 표준 로그인 창(Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // 구글 프로필 사진 같은 인터넷 이미지 표시
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location) // 현재 위치 → 도시 추천(S02)

    // S04 앱 내 카메라(CameraX): 미리보기, 10초 영상 녹화, 16:9 잘라 찍기
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // L01 위치 확인 지도(MapLibre + OpenFreeMap 타일)
    implementation(libs.maplibre.android)

    // S17 추억 영상: 구성원 영상 3개 동시 재생(Media3 ExoPlayer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.play.services.ads) // 홈 "광고 보고 코인 받기"(보상형 광고, 하루 5번 · 2코인)
    implementation(libs.work.runtime.ktx) // 오프라인에서 찍은 기록을 인터넷이 연결되면 자동으로 올리기(앱이 꺼져 있어도)
}
