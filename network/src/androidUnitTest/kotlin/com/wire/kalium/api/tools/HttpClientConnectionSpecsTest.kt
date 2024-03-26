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
package com.wire.kalium.api.tools

import com.wire.kalium.network.buildClearTextTrafficOkhttpClient
import com.wire.kalium.network.buildOkhttpClient
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientConnectionSpecsTest {

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    fun givenTheHttpClientIsCreated_ThenEnsureOnlySupportedSpecsArePresent() {
        // given
        val validTlsVersions = listOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        val notValidTlsVersions = listOf(TlsVersion.TLS_1_1, TlsVersion.TLS_1_0, TlsVersion.SSL_3_0)
        val validCipherSuites = listOf(
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384
        )
        val notValidCipherSuites = listOf(
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA
        )

        // when
        val connectionSpecs = buildOkhttpClient {  }.connectionSpecs

        // then
        with(connectionSpecs[0]) {
            assertEquals(ConnectionSpec.RESTRICTED_TLS, this)

            tlsVersions?.let {
                assertTrue { it.containsAll(validTlsVersions) }
                assertFalse { it.containsAll(notValidTlsVersions) }
            }

            cipherSuites?.let {
                assertTrue { it.containsAll(validCipherSuites) }
                assertFalse { it.containsAll(notValidCipherSuites) }
            }
        }
    }

    @Test
    fun givenOkHttpSingleton_whenBuildingClearTextTrafficOkhttpClient_thenEnsureConnectionSpecClearText() {

        val connectionSpecs = buildClearTextTrafficOkhttpClient()

        assertEquals(ConnectionSpec.CLEARTEXT, connectionSpecs.connectionSpecs.first())
    }
}
