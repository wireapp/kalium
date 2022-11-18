plugins {
    id("kotlin")
}

dependencies {
    compileOnly(libs.bundles.detekt.core)
    testImplementation(libs.bundles.detekt.test)
}
