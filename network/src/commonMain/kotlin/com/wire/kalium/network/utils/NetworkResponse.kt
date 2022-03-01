package com.wire.kalium.network.utils

import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.util.toMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class NetworkResponse<out T : Any> {
    data class Success<out T : Any>(
        val value: T,
        val headers: Map<String, String?>,
        val httpCode: Int
    ) : NetworkResponse<T>() {
        internal constructor(value: T, httpResponse: HttpResponse) : this(
            value,
            httpResponse.headers.toMap()
                .mapValues { headerEntry -> headerEntry.value.firstOrNull() }, // Ignore header duplication on purpose
            httpResponse.status.value
        )

        val cookies: Map<String, String> by lazy {
            this.headers[HttpHeaders.SetCookie]?.splitSetCookieHeader()?.flatMap { it.splitSetCookieHeader() }
                ?.map { parseServerSetCookieHeader(it) }?.associate {
                    it.name to it.value
                } ?: mapOf()
        }
    }

    data class Error<out E : KaliumException>(val kException: KaliumException) : NetworkResponse<E>()
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> NetworkResponse<T>.isSuccessful(): Boolean {
    contract {
        returns(true) implies (this@isSuccessful is NetworkResponse.Success)
        returns(false) implies (this@isSuccessful is NetworkResponse.Error)
    }
    return this@isSuccessful is NetworkResponse.Success
}
