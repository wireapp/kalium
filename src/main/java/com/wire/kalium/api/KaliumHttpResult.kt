package com.wire.kalium.api

interface KaliumHttpResult<out BodyType : Any> {
    val httpStatusCode: Int

    val isSuccessful: Boolean
        get() = (httpStatusCode in 200..299)

    val headers: Map<String, String?>

    val resultBody: BodyType
}
