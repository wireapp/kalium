package com.wire.kalium.network.api

import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.util.toMap

class KaliumKtorResult<BodyType : Any>(private val httpResponse: HttpResponse, private val body: BodyType) :
    KaliumHttpResult<BodyType> {
    override val httpStatusCode: Int
        get() = httpResponse.status.value
    override val headers: Map<String, List<String>>
        get() = httpResponse.headers.toMap()
    override val resultBody: BodyType
        get() = body
}

suspend inline fun <reified BodyType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): KaliumHttpResult<BodyType> {
    try {
        val result = performRequest()
        return KaliumKtorResult(result, result.receive())
    } catch (e: ResponseException) {
        when (e) {
            is RedirectResponseException -> {
                // 300..399
                throw e
            }
            is ClientRequestException -> {
                // 400..499
                throw e
            }
            is ServerResponseException -> {
                // 500..599
                throw e
            }
            else -> {
                // other ResponseException
                throw e
            }
        }
    }
}

fun HttpResponse.isSuccessful(): Boolean = this.status.value in 200..299
