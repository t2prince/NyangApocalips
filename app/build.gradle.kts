import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 판 번호 (version.properties — Tools/aab_build.sh 가 올린다)
val versionProps = Properties().apply {
    rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// 플레이 업로드 서명키 — 저장소 밖 ~/.nyangapo/build.env 에서만 읽는다 (없으면 release 는 서명 없이 굽힌다)
val signEnv = Properties().apply {
    val f = file(System.getProperty("user.home") + "/.nyangapo/build.env")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jamcat.nayngapo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jamcat.nayngapo"
        minSdk = 29
        targetSdk = 36
        versionCode = (versionProps.getProperty("VERSION_CODE") ?: "1").toInt()
        versionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 나누(PlayNANOO) 세이브 동기화 키 — 저장소 밖 ~/.nyangapo/build.env 의 NANOO_GAME_ID·NANOO_SERVICE_KEY.
        // 없으면 빈 값으로 굽히고 앱은 나누를 쓰지 않는다 (Secret Key 는 앱에 넣지 않는다)
        buildConfigField("String", "NANOO_GAME_ID", "\"${signEnv.getProperty("NANOO_GAME_ID") ?: ""}\"")
        buildConfigField("String", "NANOO_SERVICE_KEY", "\"${signEnv.getProperty("NANOO_SERVICE_KEY") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (signEnv.getProperty("NYANGAPO_KEYSTORE") != null) {
            create("upload") {
                storeFile = file(signEnv.getProperty("NYANGAPO_KEYSTORE"))
                storePassword = signEnv.getProperty("NYANGAPO_KEYSTORE_PASS")
                keyAlias = signEnv.getProperty("NYANGAPO_KEY_ALIAS")
                keyPassword = signEnv.getProperty("NYANGAPO_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}