import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    kotlin("plugin.serialization") version "1.5.31"

}

group = "com.wire.kalium"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val cryptoboxVersion = "1.0.0"
    val genericMessageProtoVersion = "1.35.0"
    val jacksonVersion = "2.12.3"
    val javaxValidationVersion = "2.0.1.Final"
    val junitVersion = "5.7.1"
    val kotlinVersion = "1.5.31"
    val jakartaVersion = "2.1.6"
    val jerseyVersion = "2.32"
    val tyrusVersion = "1.13.1"
    val ktxSerializationVersion = "1.3.0"

    implementation("com.wire:generic-message-proto:$genericMessageProtoVersion")
    implementation("com.wire:cryptobox4j:$cryptoboxVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("javax.validation:validation-api:$javaxValidationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ktxSerializationVersion")
    testImplementation(kotlin("test"))


    // web service
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:$jakartaVersion")
    implementation("org.glassfish.jersey.core:jersey-client:$jerseyVersion")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:$tyrusVersion")
    // not really sure if .jersey.inject one is needed
    //implementation("org.glassfish.jersey.inject:jersey-hk2:$tyrusVersion")
    // cli
    implementation("com.github.ajalt.clikt:clikt:3.2.0")

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
