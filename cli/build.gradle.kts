plugins {
    kotlin("jvm")
    application
}
val mainFunctionClassName = "com.wire.kalium.cli.CLIApplicationKt"

application{
    mainClass.set(mainFunctionClassName)
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
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))

    implementation(Dependencies.Cli.cliKt)
    implementation(Dependencies.Ktor.utils)
    implementation(Dependencies.Ktor.okHttp)
    implementation(Dependencies.OkHttp.loggingInterceptor)
    implementation(Dependencies.Coroutines.core)
}
