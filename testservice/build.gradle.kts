plugins {
    kotlin("jvm")
    java
    application
}

group = "com.wire.kalium.testservice"
version = "0.0.1-SNAPSHOT"

object Versions {
    const val dropwizard = "2.1.0"
}

val mainFunctionClassName = "com.wire.kalium.testservice.TestserviceApplication"

application {
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class){
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
    add("implementation", project(":network"))
    add("implementation", project(":cryptography"))
    add("implementation", project(":logic"))
}
