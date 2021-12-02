package com.wire.kalium.network.api

import io.ktor.http.Headers

interface KaliumHttpResult<out BodyType : Any> {
    val httpStatusCode: Int

    val isSuccessful: Boolean
        get() = (httpStatusCode in 200..299)

    val headers: Headers

    val resultBody: BodyType
}
