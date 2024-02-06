package com.wire.kalium.monkeys.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ShutDownUrl
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText

fun Application.configureAdministration() {
    install(ShutDownUrl.ApplicationCallPlugin) {
        shutDownUrl = "/shutdown"
        exitCodeSupplier = { 0 }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause is UninitializedPropertyAccessException) {
                call.respondText(text = "Please init the monkey first through the /set api", status = HttpStatusCode.UnprocessableEntity)
            } else {
                call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
