plugins {
    kotlin("jvm")
    java
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.wire.kalium.testservice"
version = "0.0.1-SNAPSHOT"

object Versions {
    const val dropwizard = "2.1.2"
    const val prometheus_simpleclient = "0.1.0"
}

val mainFunctionClassName = "com.wire.kalium.testservice.TestserviceApplication"

application {
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class) {
    jvmArgs = listOf("-Djava.library.path=/usr/local/lib/:../native/libs")
    args = listOf("server", "config.yml")
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.shadowJar {
    archiveBaseName.set("testservice")
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to mainFunctionClassName))
    }
}

repositories {
    // to fetch a version of dropwizard-swagger via git reference
    maven(url = "https://jitpack.io")
}

dependencies {
    add("implementation", "io.dropwizard:dropwizard-core:${Versions.dropwizard}")
    add("implementation", "com.github.smoketurner:dropwizard-swagger:72e8441e4a")

    // prometheus metrics
    add("implementation", "io.prometheus:simpleclient_dropwizard:${Versions.prometheus_simpleclient}")
    add("implementation", "io.prometheus:simpleclient_servlet:${Versions.prometheus_simpleclient}")

    add("implementation", project(":network"))
    add("implementation", project(":cryptography"))
    add("implementation", project(":logic"))

    // Okio
    implementation(Dependencies.Okio.core)
}
