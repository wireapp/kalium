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
import junit.framework.TestCase.assertTrue
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import kotlin.test.Test
import kotlin.test.assertContains

class ClientConnectionSpecsTest {

    @Test
    fun givenTheHttpClientIsCreated_ThenEnsureSupportedSpecsArePresent() {
        val connectionSpecs = OkHttpSingleton.createNew {}.connectionSpecs
        with(connectionSpecs[0]) {
            tlsVersions?.let {
                assertContains(it, TlsVersion.TLS_1_2)
                assertContains(it, TlsVersion.TLS_1_3)
            }

            cipherSuites?.let {
                assertContains(it, CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
                assertContains(it, CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384)
            }
        }

        assertTrue(connectionSpecs[1] == ConnectionSpec.CLEARTEXT)
    }
}
