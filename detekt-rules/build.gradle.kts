plugins {
    id("kotlin")
}

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:1.19.0")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-api:1.19.0")
    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:1.19.0")
}
