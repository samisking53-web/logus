// 최상위 빌드 파일: 플러그인 버전만 정하고, 실제 설정은 app/build.gradle.kts 에 있다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
