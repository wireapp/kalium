plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":network"))

    implementation(Dependencies.Cli.cliKt)
    implementation(Dependencies.Ktor.core)
    implementation(Dependencies.Ktor.okHttp)
    implementation(Dependencies.OkHttp.loggingInterceptor)
}
