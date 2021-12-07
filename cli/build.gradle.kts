plugins {
    kotlin("jvm")
    application
}

dependencies {
    val cryptoboxVersion = "1.0.0"
    val genericMessageProtoVersion = "1.35.0"

    implementation(project(":network"))

    implementation(Dependencies.Cli.cliKt)
    implementation(Dependencies.OkHttp.loggingInterceptor)

    // ktor
    implementation(Dependencies.Ktor.core)
    implementation(Dependencies.Ktor.okHttp)
    implementation(Dependencies.Ktor.json)
    implementation(Dependencies.Ktor.serialization)

    // to be removed later when the crypto module is done
    implementation("com.wire:generic-message-proto:$genericMessageProtoVersion")
    implementation("com.wire:cryptobox4j:$cryptoboxVersion")
}
