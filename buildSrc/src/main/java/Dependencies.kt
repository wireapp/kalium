object Versions {
    const val ktor = "1.6.4"
    const val coroutines = "1.5.2"
}

object Dependencies {

    object Ktor {
        const val core = "io.ktor:ktor-client-core:${Versions.ktor}"
        const val json = "io.ktor:ktor-client-json:${Versions.ktor}"
        const val serialization = "io.ktor:ktor-client-serialization:${Versions.ktor}"
        const val logging = "io.ktor:ktor-client-logging:${Versions.ktor}"
        const val auth = "io.ktor:ktor-client-auth:${Versions.ktor}"
        const val webSocket = "io.ktor:ktor-client-websockets:${Versions.ktor}"
    }

    object Coroutines {
        const val core = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}"
    }
}
