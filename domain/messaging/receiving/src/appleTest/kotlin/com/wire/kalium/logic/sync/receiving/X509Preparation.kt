/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.PkiHttpHeader
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.PkiHttpResponse
import com.wire.kalium.cryptography.X509CredentialAcquisitionConfig
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import kotlinx.coroutines.job
import kotlin.time.Duration.Companion.hours

/** Fake ACME authentication is used only while preparing real native credentials for tests. */
internal class X509Preparation : PkiEnvironmentHooks {
    private val http = UnboundNetworkContainerCommon("x509-fixture", false, emptyMap(), null, false).cellsClient

    suspend fun control(query: String) {
        http.request("$BASE/control?$query")
    }

    suspend fun configure(central: CoreCryptoCentral) {
        central.configurePkiEnvironment(this)
        central.addPkiTrustAnchors(http.request("$BASE/roots").bodyAsBytes().decodeToString())
    }

    suspend fun install(central: CoreCryptoCentral, user: UserId, clientId: String) {
        configure(central)
        central.installCredential(central.startX509CredentialAcquisition(X509CredentialAcquisitionConfig(
            acmeDirectoryUrl = "$BASE/directory",
            cipherSuite = MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
            displayName = "NSE $clientId",
            clientId = CryptoQualifiedClientId(clientId, user.toCrypto()),
            handle = "nse$clientId",
            teamId = null,
            validity = 24.hours,
        )))
        central.checkCredentials()
    }

    override suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray,
    ): PkiHttpResponse {
        val response = http.request(url) {
            this.method = HttpMethod(method.name)
            headers { headers.forEach { append(it.name, it.value) } }
            if (body.isNotEmpty()) setBody(body)
        }
        return PkiHttpResponse(response.status.value.toUShort(),
            response.headers.entries().flatMap { (name, values) -> values.map { PkiHttpHeader(name, it) } }, response.bodyAsBytes())
    }

    override suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String): String = "fixture-id-token"
    override suspend fun getBackendNonce(): String = "YmFja2VuZC1ub25jZS0x"
    override suspend fun fetchBackendAccessToken(dpop: String): String = "fixture-access-token"

    suspend fun close() {
        http.close()
        http.coroutineContext.job.join()
        http.engine.close()
        http.engine.coroutineContext.job.join()
    }

    companion object {
        const val BASE = "https://localhost:18444"
    }
}
