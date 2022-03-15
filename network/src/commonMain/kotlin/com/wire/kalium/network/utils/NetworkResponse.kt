package com.wire.kalium.network.utils

import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.util.toMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class NetworkResponse<out T : Any> {
    data class Success<out T : Any>(
        val value: T,
        // TODO: to be deleted since data in the headers have to be extracted in network and not exposed
        val headers: Map<String, String?>, val httpCode: Int
    ) : NetworkResponse<T>() {
        internal constructor(value: T, httpResponse: HttpResponse) : this(
            value,
            // small issue here where keys are converted to small case letters
            // this is an issue for ktor to solve
            httpResponse.headers.toMap()
                .mapValues { headerEntry -> headerEntry.value.firstOrNull() }, // Ignore header duplication on purpose
            httpResponse.status.value
        )

        val cookies: Map<String, String> by lazy {
            // don't use HttpHeaders.SetCookie until the case sensitivity issue is solved
            this.headers["set-cookie"]?.splitSetCookieHeader()?.flatMap { it.splitSetCookieHeader() }
                ?.map { parseServerSetCookieHeader(it) }?.associate {
                    it.name to it.value
                } ?: mapOf()
        }
    }

    data class Error(val kException: KaliumException) : NetworkResponse<Nothing>()
}

@OptIn(ExperimentalContracts::class)
// TODO: make internal
fun <T : Any> NetworkResponse<T>.isSuccessful(): Boolean {
    contract {
        returns(true) implies (this@isSuccessful is NetworkResponse.Success)
        returns(false) implies (this@isSuccessful is NetworkResponse.Error)
    }
    return this@isSuccessful is NetworkResponse.Success
}
