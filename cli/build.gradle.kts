plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":network"))
    implementation(project(":cryptography"))
    implementation(project(":logic"))
    implementation(project(":logger"))

    implementation(Dependencies.Cli.cliKt)
    //implementation(Dependencies.Ktor.core)
    //implementation(Dependencies.Ktor.core2)
    implementation(Dependencies.Ktor.utils)
    implementation(Dependencies.Ktor.okHttp)
    implementation(Dependencies.OkHttp.loggingInterceptor)
}
