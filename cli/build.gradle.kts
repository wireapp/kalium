plugins {
    kotlin("jvm")
    application
}
val mainFunctionClassName = "com.wire.kalium.cli.CLIApplicationKt"

application{
    mainClass.set(mainFunctionClassName)
}

tasks.named("run", JavaExec::class){
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
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))

    implementation(libs.cliKt)
    implementation(libs.ktor.utils)
    implementation(libs.ktor.okHttp)
    implementation(libs.okhttp.loggingInterceptor)
    implementation(libs.coroutines.core)
}
