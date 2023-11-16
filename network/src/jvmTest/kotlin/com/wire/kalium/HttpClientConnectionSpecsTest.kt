/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium

import com.wire.kalium.network.OkHttpSingleton
import okhttp3.CipherSuite
import okhttp3.CipherSuite.Companion.TLS_CHACHA20_POLY1305_SHA256
import okhttp3.CipherSuite.Companion.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256
import okhttp3.CipherSuite.Companion.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
import okhttp3.CipherSuite.Companion.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
import okhttp3.CipherSuite.Companion.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_3DES_EDE_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_128_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_128_CBC_SHA256
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_128_GCM_SHA256
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_256_CBC_SHA
import okhttp3.CipherSuite.Companion.TLS_RSA_WITH_AES_256_GCM_SHA384
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
        val connectionSpecs = OkHttpSingleton.createNew {}.connectionSpecs
        with(connectionSpecs[0]) {
            tlsVersions?.let {
                assertTrue { validTlsVersions.containsAll(it) }
                assertFalse { notValidTlsVersions.containsAll(it) }
            }

            cipherSuites?.let {
                assertTrue { validCipherSuites.containsAll(it) }
                assertFalse { notValidCipherSuites.containsAll(it) }
            }
        }

        assertEquals(connectionSpecs[1], ConnectionSpec.CLEARTEXT)
    }

    private companion object {
        val validTlsVersions = listOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        val notValidTlsVersions = listOf(TlsVersion.TLS_1_1, TlsVersion.TLS_1_0, TlsVersion.SSL_3_0)

        val notValidCipherSuites = listOf(
            TLS_CHACHA20_POLY1305_SHA256,
            TLS_RSA_WITH_3DES_EDE_CBC_SHA,
            TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            TLS_RSA_WITH_AES_128_CBC_SHA256,
            TLS_RSA_WITH_AES_128_CBC_SHA,
            TLS_RSA_WITH_AES_128_GCM_SHA256,
            TLS_RSA_WITH_AES_256_CBC_SHA,
            TLS_RSA_WITH_AES_256_GCM_SHA384,
            TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
        )

        val validCipherSuites = listOf(
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384
        )
    }
}
