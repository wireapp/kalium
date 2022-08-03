plugins {
    kotlin("jvm")
    java
    application
}

group = "com.wire.kalium.testservice"
version = "0.0.1-SNAPSHOT"

object Versions {
    // dropwizard-swagger:2.0.0-1 does not support dropwizard >= 2.0.11
    const val dropwizard = "2.0.10"
}

val mainFunctionClassName = "com.wire.kalium.testservice.TestserviceApplication"

application {
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class){
    // FIXME: Find solution for hardcoded path to native libraries
    jvmArgs = listOf("-Djava.library.path=/Users/sven/workspace/kalium/native/libs/")
    args = listOf("server", "config.yml")
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
}

tasks.jar {
    manifest.attributes["Main-Class"] = mainFunctionClassName
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    add("implementation", "io.dropwizard:dropwizard-core:${Versions.dropwizard}")
    add("implementation", "com.smoketurner:dropwizard-swagger:2.0.0-1")
    add("implementation", project(":network"))
    add("implementation", project(":cryptography"))
    add("implementation", project(":logic"))
}
