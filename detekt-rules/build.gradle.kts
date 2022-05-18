plugins {
    id("kotlin")
}

dependencies {
    compileOnly(Dependencies.Detekt.detektApi)
    testImplementation(Dependencies.Detekt.detektApi)
    testImplementation(Dependencies.Detekt.detektTest)
}
