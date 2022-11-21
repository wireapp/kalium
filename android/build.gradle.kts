@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
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
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
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
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }

    packagingOptions {
        resources.pickFirsts.add("google/protobuf/*.proto")
    }

//    sourceSets { map { it.java.srcDir("src/${it.name}/kotlin") } }
}

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))

    implementation(libs.bundles.android)
}
