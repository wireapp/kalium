package com.wire.kalium.network.api


interface KaliumHttpResult<out BodyType : Any> {
    val httpStatusCode: Int

    val isSuccessful: Boolean
        get() = (httpStatusCode in 200..299)

    val headers: Map<String, List<String>>

    val resultBody: BodyType
}
