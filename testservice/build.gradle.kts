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

application {
    mainClass.set("com.wire.kalium.testservice.TestserviceApplication")
}

tasks.named("run", JavaExec::class){
    args = listOf("server", "config.yml")
    isIgnoreExitValue = true
    standardInput = System.`in`
    standardOutput = System.out
}

dependencies {
    add("implementation", "io.dropwizard:dropwizard-core:${Versions.dropwizard}")
    add("implementation", project(":network"))
    add("implementation", project(":cryptography"))
    add("implementation", project(":logic"))
}
