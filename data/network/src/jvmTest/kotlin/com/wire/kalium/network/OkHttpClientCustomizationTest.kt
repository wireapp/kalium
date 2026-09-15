/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network

import okhttp3.Authenticator
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OkHttpClientCustomizationTest {

    @AfterTest
    fun resetCustomizer() {
        OkHttpClientCustomization.customizer = {}
    }

    @Test
    fun givenACustomizer_whenAClientIsBuilt_thenTheClientHasItsSettings() {
        val proxyAuthenticator = Authenticator { _, _ -> null }
        OkHttpClientCustomization.customizer = { it.proxyAuthenticator(proxyAuthenticator) }

        val client = buildOkhttpClient {}

        assertSame(proxyAuthenticator, client.proxyAuthenticator)
    }

    @Test
    fun givenACustomizer_whenAClientIsBuilt_thenItRunsAfterKaliumsOwnSettings() {
        OkHttpClientCustomization.customizer = { it.connectTimeout(CUSTOM_TIMEOUT_SECONDS, TimeUnit.SECONDS) }

        val client = buildOkhttpClient { connectTimeout(1, TimeUnit.SECONDS) }

        assertEquals(TimeUnit.SECONDS.toMillis(CUSTOM_TIMEOUT_SECONDS), client.connectTimeoutMillis.toLong())
    }

    private companion object {
        const val CUSTOM_TIMEOUT_SECONDS = 42L
    }
}
