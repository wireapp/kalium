plugins {
    Plugins.androidApplication(this)
    Plugins.androidKotlin(this)
}

android {
    setCompileSdkVersion(Android.Sdk.target)
    defaultConfig {
        applicationId = "com.wire.kalium.sample"
        targetSdk = Android.Sdk.target
        minSdk = Android.Sdk.min

        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = Android.testRunner
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Versions.compose
    }

    buildTypes {
        debug {}
        release {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

//    sourceSets { map { it.java.srcDir("src/${it.name}/kotlin") } }
}

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))

    implementation(Dependencies.Android.appCompat)
    implementation(Dependencies.Android.activityCompose)
    implementation(Dependencies.Android.composeMaterial)
    implementation(Dependencies.Android.coroutines)
    implementation(Dependencies.Android.ktor)
    implementation(Dependencies.Ktor.okHttp)
}
