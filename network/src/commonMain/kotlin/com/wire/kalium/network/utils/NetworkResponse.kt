/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
        // TODO(refactor): to be deleted since data in the headers have to be extracted in network and not exposed
        val headers: Map<String, String?>,
        val httpCode: Int
    ) : NetworkResponse<T>() {
        internal constructor(value: T, httpResponse: HttpResponse) : this(
            value,
            // small issue here where keys are converted to small case letters
            // this is an issue for ktor to solve
            httpResponse.headers.toMap()
                .mapKeys { headerEntry -> headerEntry.key.lowercase() }
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
// TODO(refactor): make internal
fun <T : Any> NetworkResponse<T>.isSuccessful(): Boolean {
    contract {
        returns(true) implies (this@isSuccessful is NetworkResponse.Success)
        returns(false) implies (this@isSuccessful is NetworkResponse.Error)
    }
    return this@isSuccessful is NetworkResponse.Success
}
