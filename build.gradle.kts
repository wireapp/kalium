import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
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
    val jdbiVersion = "3.20.0"
    val protobufVersion = "3.17.0"
    val junitVersion = "5.7.1"
    val kotlinVersion = "1.5.31"

    implementation("com.wire:generic-message-proto:$genericMessageProtoVersion")
    implementation("com.wire:cryptobox4j:$cryptoboxVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("javax.validation:validation-api:$javaxValidationVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("org.jdbi:jdbi3-core:$jdbiVersion")
    implementation("org.jdbi:jdbi3-sqlobject:$jdbiVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
    testImplementation("org.postgresql:postgresql:42.2.20")
    testImplementation("org.flywaydb:flyway-core:7.8.2")
    testImplementation("org.slf4j:slf4j-simple:1.7.30")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
