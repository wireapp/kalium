/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.fold
import com.wire.kalium.cryptography.PkiHttpHeader
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.PkiHttpResponse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath

/** The app's PKI HTTP policy, shared with owned receiving against existing credentials. */
class PkiHttpRequests(
    private val settings: UserConfigRepository,
    private val acmeApi: ACMEApi,
    private val client: HttpClient,
    directGetUrls: Set<String> = emptySet(),
) {
    private val normalizedDirectGetUrls = directGetUrls.mapTo(mutableSetOf(), ::normalizePkiUrl)

    suspend fun request(method: PkiHttpMethod, url: String, headers: List<PkiHttpHeader>, body: ByteArray): PkiHttpResponse {
        if (method == PkiHttpMethod.GET && normalizePkiUrl(url) !in normalizedDirectGetUrls) {
            val config = settings.getE2EISettings().fold(
                { throw PkiHookFailure("Getting E2EI settings for the CRL request failed: $it") },
                { it }
            )
            val proxyUrl = config.crlProxy?.takeIf { config.shouldUseProxy && it.isNotBlank() }
            if (proxyUrl != null) {
                return wrapApiRequest { acmeApi.getClientDomainCRL(url, proxyUrl) }.fold(
                    { throw PkiHookFailure("Getting the CRL through the configured proxy failed: $it") },
                    { PkiHttpResponse(200.toUShort(), emptyList(), it) }
                )
            }
        }
        val response = client.request(url) {
            this.method = method.toKtor()
            headers { headers.forEach { header -> append(header.name, header.value) } }
            if (body.isNotEmpty()) setBody(body)
        }
        return PkiHttpResponse(
            status = response.status.value.toUShort(),
            headers = response.headers.entries().flatMap { (name, values) -> values.map { PkiHttpHeader(name, it) } },
            body = response.bodyAsBytes()
        )
    }

    private fun PkiHttpMethod.toKtor(): HttpMethod = when (this) {
        PkiHttpMethod.GET -> HttpMethod.Get
        PkiHttpMethod.POST -> HttpMethod.Post
        PkiHttpMethod.PUT -> HttpMethod.Put
        PkiHttpMethod.DELETE -> HttpMethod.Delete
        PkiHttpMethod.PATCH -> HttpMethod.Patch
        PkiHttpMethod.HEAD -> HttpMethod.Head
    }
}

private fun normalizePkiUrl(value: String): String = URLBuilder(Url(value)).apply {
    // Rust adds `/` to authority-only HTTP(S) URLs; Ktor keeps their path empty.
    if (encodedPath.isEmpty()) encodedPath = "/"
}.build().toString()

class PkiHookFailure(message: String) : Exception(message)
