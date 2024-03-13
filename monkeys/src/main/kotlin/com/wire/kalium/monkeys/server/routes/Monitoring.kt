package com.wire.kalium.monkeys.server.routes

import com.wire.kalium.monkeys.configureMicrometer
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        logger = LoggerFactory.getLogger("com.wire.monkeys.server")
        level = Level.INFO
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }

    configureMicrometer("/metrics")
}
