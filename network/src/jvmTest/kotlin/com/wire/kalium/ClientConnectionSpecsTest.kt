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
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import kotlin.test.Test

class ClientConnectionSpecsTest {

    @Test
    fun givenTheHttpClientIsCreated_ThenEnsureSupportedSpecsArePresent() {
        val connectionSpecs = OkHttpSingleton.createNew {}.connectionSpecs
        with(connectionSpecs[0]) {
            tlsVersions?.let {
                assertTrue(it.contains(TlsVersion.TLS_1_2) && it.contains(TlsVersion.TLS_1_3))
                assertFalse(it.contains(TlsVersion.TLS_1_1) && it.contains(TlsVersion.TLS_1_0) && it.contains(TlsVersion.SSL_3_0))
            }
        }

        assertTrue(connectionSpecs[1] == ConnectionSpec.CLEARTEXT)
    }
}
