plugins {
    kotlin("jvm")
    application
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.wire.kalium.cli.CLIApplication"
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))

    implementation(Dependencies.Cli.cliKt)
    implementation(Dependencies.Ktor.utils)
    implementation(Dependencies.Ktor.okHttp)
    implementation(Dependencies.OkHttp.loggingInterceptor)
    implementation(Dependencies.Coroutines.core)
}
