plugins {
    kotlin("jvm")
    application
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
