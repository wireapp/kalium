
buildscript {
    val kotlinVersion = "1.5.31"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // keeping this here to allow AS to automatically update
        classpath("com.android.tools.build:gradle:7.0.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
    }
}

repositories {
    google()
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    val cryptoboxVersion = "1.0.0"
    val genericMessageProtoVersion = "1.35.0"
    val jacksonVersion = "2.12.3"
    val javaxValidationVersion = "2.0.1.Final"
    val kotestVersion = "4.6.3"
    val junitVersion = "5.7.1"
    val kotlinVersion = "1.5.31"
    val mockkVersion = "1.12.1"
    val jakartaVersion = "2.1.6"
    val jerseyVersion = "2.32"
    val tyrusVersion = "1.13.1"
    val ktxSerializationVersion = "1.3.0"
    val coroutinesVersion = "1.5.2"
    val junit4Version = "4.13"

//    implementation("com.wire:generic-message-proto:$genericMessageProtoVersion")
//    implementation("com.wire:cryptobox4j:$cryptoboxVersion")
//    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
//    //implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
//    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", jacksonVersion)
//    implementation("com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:$jacksonVersion")
//    implementation("org.glassfish.jersey.inject:jersey-hk2:2.28")
//
//    implementation("javax.validation:validation-api:$javaxValidationVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ktxSerializationVersion")
//    testImplementation(kotlin("test"))
//    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
//    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
//    testImplementation("io.kotest:kotest-property:$kotestVersion")
//
//    testImplementation("io.mockk:mockk:$mockkVersion")
//
//    // web service
//    implementation("jakarta.ws.rs:jakarta.ws.rs-api:$jakartaVersion")
//    implementation("org.glassfish.jersey.core:jersey-client:$jerseyVersion")
//    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:$tyrusVersion")
//    // not really sure if .jersey.inject one is needed
//    implementation("org.glassfish.jersey.inject:jersey-hk2:$tyrusVersion")
//    // cli
//    implementation("com.github.ajalt.clikt:clikt:3.2.0")
//
//
//
//
//
//    // Failed to load class "org.slf4j.impl.StaticLoggerBinder". error
//    //implementation ("io.ktor:ktor-client-logging:$ktorVersion")

//    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
//    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.0")
//
//    // coroutines
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
//
//    // TEST
//    // Unit tests dependencies
//    testImplementation(kotlin("test"))
//    testImplementation("junit:junit:$junit4Version")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
//    testImplementation("io.ktor:ktor-client-mock:$coroutinesVersion")
}
