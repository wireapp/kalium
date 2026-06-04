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
import com.wire.kalium.network.config.KaliumNetworkConfig
import com.wire.kalium.network.config.TlsPolicy
import com.wire.kalium.network.supportedConnectionSpecs
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.junit.Assume.assumeTrue
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientConnectionSpecsTest {

    @Test
    fun givenKayanConfigIsGenerated_whenReadingTlsPolicy_thenPolicyIsConfigured() {
        assertTrue { KaliumNetworkConfig.KALIUM_TLS_POLICY in TlsPolicy.entries }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    fun givenTheHttpClientIsCreated_whenInspectingConnectionSpecs_thenOnlyTls13AndAllowedCipherSuitesArePresent() {
        assumeTls13OnlyPolicy()

        // given
        val validTlsVersions = listOf(TlsVersion.TLS_1_3)
        val notValidTlsVersions = listOf(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0, TlsVersion.SSL_3_0)
        val validCipherSuites = listOf(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_AES_128_CCM_SHA256
        )
        val notValidCipherSuites = listOf(
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA
        )

        // when
        val connectionSpecs = buildOkhttpClient { }.connectionSpecs

        // then
        with(connectionSpecs[0]) {
            assertEquals(validTlsVersions, tlsVersions)
            assertTrue { notValidTlsVersions.none { it in tlsVersions.orEmpty() } }
            assertEquals(validCipherSuites, cipherSuites)
            assertTrue { notValidCipherSuites.none { it in cipherSuites.orEmpty() } }
        }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    fun givenBackendOffersTls13WithAllowedCipherSuite_whenCheckingCompatibility_thenConnectionSpecIsCompatible() {
        assumeTls13OnlyPolicy()

        // given
        val backendSocket = sslSocketWith(
            tlsVersions = listOf(TlsVersion.TLS_1_3),
            cipherSuites = listOf(CipherSuite.TLS_AES_128_GCM_SHA256)
        )

        // when
        val connectionSpec = buildOkhttpClient { }.connectionSpecs.first()

        // then
        assertTrue { connectionSpec.isCompatible(backendSocket) }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    fun givenBackendOffersOnlyTls12_whenCheckingCompatibility_thenConnectionSpecIsNotCompatible() {
        assumeTls13OnlyPolicy()

        // given
        val backendSocket = sslSocketWith(
            tlsVersions = listOf(TlsVersion.TLS_1_2),
            cipherSuites = listOf(CipherSuite.TLS_AES_128_GCM_SHA256)
        )

        // when
        val connectionSpec = buildOkhttpClient { }.connectionSpecs.first()

        // then
        assertFalse { connectionSpec.isCompatible(backendSocket) }
    }

    @Test
    // This test conforms to the following testing standards:
    // @SF.Channel @TSFI.RESTfulAPI @S0.2 @S0.3 @S3
    fun givenBackendOffersTls13WithoutAllowedCipherSuite_whenCheckingCompatibility_thenConnectionSpecIsNotCompatible() {
        assumeTls13OnlyPolicy()

        // given
        val backendSocket = sslSocketWith(
            tlsVersions = listOf(TlsVersion.TLS_1_3),
            cipherSuites = listOf(
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
            )
        )

        // when
        val connectionSpec = buildOkhttpClient { }.connectionSpecs.first()

        // then
        assertFalse { connectionSpec.isCompatible(backendSocket) }
    }

    @Test
    fun givenTls12Tls13Policy_whenInspectingConnectionSpecs_thenTls12AndTls13AreAllowed() {
        // when
        val connectionSpec = supportedConnectionSpecs(TlsPolicy.TLS12_TLS13).first()

        // then
        assertTrue {
            connectionSpec.tlsVersions.orEmpty().containsAll(listOf(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3))
        }
    }

    @Test
    fun givenOkHttpSingleton_whenBuildingClearTextTrafficOkhttpClient_thenEnsureConnectionSpecClearText() {

        val connectionSpecs = buildClearTextTrafficOkhttpClient()

        assertEquals(ConnectionSpec.CLEARTEXT, connectionSpecs.connectionSpecs.first())
    }

    private fun assumeTls13OnlyPolicy() {
        assumeTrue(
            "TLS 1.3 security tests apply only when kalium_tls_policy is TLS13_ONLY",
            KaliumNetworkConfig.KALIUM_TLS_POLICY == TlsPolicy.TLS13_ONLY
        )
    }

    private fun sslSocketWith(
        tlsVersions: List<TlsVersion>,
        cipherSuites: List<CipherSuite>
    ): SSLSocket {
        val socket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        val supportedCipherSuites = socket.supportedCipherSuites.toSet()
        val enabledCipherSuites = cipherSuites.map { it.javaName }.filter { it in supportedCipherSuites }
        assertTrue(
            enabledCipherSuites.isNotEmpty(),
            "Test runtime does not support any of the requested cipher suites: $cipherSuites"
        )
        return socket.apply {
            setEnabledProtocols(tlsVersions.map { it.javaName }.toTypedArray())
            setEnabledCipherSuites(enabledCipherSuites.toTypedArray())
        }
    }
}
