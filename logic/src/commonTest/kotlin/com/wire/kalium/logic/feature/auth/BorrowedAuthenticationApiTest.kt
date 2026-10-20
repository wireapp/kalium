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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.di.KaliumAuthenticationVerificationApi
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.TestGlobalDatabase
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BorrowedAuthenticationApiTest {
    @Test
    fun publicApiBorrowsTheServerProxyCacheAndItsMutableVerificationState() {
        val provider = AuthenticationScopeProvider("individual-api/test")
        val config = newServerConfig(18)
        val proxy = ProxyCredentials("user", "password")
        // These auth paths must not use storage; a closed real builder fails on any accidental query.
        val testDatabase = TestGlobalDatabase()
        val database = testDatabase.provider.also {
            it.close()
            testDatabase.delete()
        }
        val configs = KaliumConfigs(mockedRequests = emptyList())
        val first = provider.provide(config, proxy, database, configs)
        val same = provider.provide(config.copy(), proxy.copy(), database, configs)
        val otherProxy = provider.provide(config, proxy.copy(password = "other"), database, configs)
        val otherConfig = provider.provide(config.copy(links = config.links.copy(title = "other")), proxy, database, configs)
        val api: KaliumAuthenticationVerificationApi = first.individualVerificationApi
        assertSame(api, same.individualVerificationApi)
        assertNotSame(api, otherProxy.individualVerificationApi)
        assertNotSame(api, otherConfig.individualVerificationApi)
        assertNotSame(api.requestSecondFactorVerificationCode, api.requestSecondFactorVerificationCode)
        first.secondFactorVerificationRepository.storeVerificationCode("user@example.com", "123456")
        assertEquals("123456", same.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
        assertNull(otherProxy.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
        assertNull(otherConfig.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
    }

    @Test
    fun borrowedApiPreservesEagerNetworkInitializationWithoutOpeningStorageOrAnyGlobalOwner() {
        var networkConfigReads = 0
        val configs = KaliumConfigs(mockedRequests = emptyList())
        val inputs = object : AuthenticationGraphInputs {
            override val userAgent = "individual-api/test"
            override val serverConfig = newServerConfig(18)
            override val proxyCredentials: ProxyCredentials? = null
            override val globalDatabase: GlobalDatabaseBuilder get() = error("No storage should open")
            override val kaliumConfigs: KaliumConfigs
                get() = configs.also { networkConfigReads++ }
        }
        // This independent auth root accepts no CoreLogic/global/user runtime input.
        val graph = createGraphFactory<AuthenticationGraph.Factory>().create(inputs)
        assertEquals(0, networkConfigReads)
        val facade = AuthenticationScope(graph)
        assertTrue(networkConfigReads > 0)
        val reads = networkConfigReads
        val api = facade.individualVerificationApi
        assertSame(graph, api)
        assertSame(api, facade.individualVerificationApi)
        assertSame(graph.secondFactorVerificationRepository, facade.secondFactorVerificationRepository)
        assertNotSame(api.requestSecondFactorVerificationCode, api.requestSecondFactorVerificationCode)
        assertEquals(reads, networkConfigReads)
    }
}
