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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.TestGlobalDatabase
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DependencyGraph(AuthenticationLifetime::class)
internal interface PendingLoginBindingTestGraph : AuthenticationBindings {
    val pendingLoginSystemSettingsRepository: PendingLoginSystemSettingsRepository

    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides inputs: AuthenticationGraphInputs): PendingLoginBindingTestGraph
    }
}

class AuthenticationBindingLifetimeTest {
    @Test
    fun cacheUsesFullServerConfigurationAndProxyAndKeepsMutableVerificationState() {
        val factory = createGraphFactory<AuthenticationGraph.Factory>()
        var creations = 0
        val countedFactory = object : AuthenticationGraph.Factory {
            override fun create(inputs: AuthenticationGraphInputs): AuthenticationGraph {
                creations++
                return factory.create(inputs)
            }
        }
        val provider = AuthenticationScopeProvider("test/useragent", countedFactory)
        val config = newServerConfig(18)
        val proxy = ProxyCredentials("user", "password")
        val database = closedGlobalDatabase()
        val configs = KaliumConfigs(mockedRequests = emptyList())
        val first = provider.provide(config, proxy, database, configs)
        val same = provider.provide(config.copy(), proxy.copy(), database, configs)
        val otherProxy = provider.provide(config, proxy.copy(password = "another"), database, configs)
        val otherConfig = provider.provide(config.copy(links = config.links.copy(title = "another")), proxy, database, configs)

        assertSame(first, same)
        assertNotSame(first, otherProxy)
        assertNotSame(first, otherConfig)
        assertEquals(3, creations)
        first.secondFactorVerificationRepository.storeVerificationCode("user@example.com", "123456")
        assertSame(first.secondFactorVerificationRepository, same.secondFactorVerificationRepository)
        assertEquals("123456", same.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
        assertNull(otherProxy.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
        assertNull(otherConfig.secondFactorVerificationRepository.getStoredVerificationCode("user@example.com"))
    }

    @Test
    fun facadeConstructionRetainsEagerNetworkButGetterBindingsAndChildFacadesStayFresh() {
        val config = newServerConfig(18)
        val configs = KaliumConfigs(mockedRequests = emptyList())
        var networkConfigReads = 0
        val inputs = object : AuthenticationGraphInputs {
            override val userAgent = "test/useragent"
            override val serverConfig = config
            override val proxyCredentials: ProxyCredentials? = null
            override val globalDatabase: GlobalDatabaseBuilder get() = error("This path must not open storage")
            override val kaliumConfigs: KaliumConfigs get() {
                networkConfigReads++
                return configs
            }
        }
        val graph = createGraphFactory<AuthenticationGraph.Factory>().create(inputs)
        assertEquals(0, networkConfigReads)
        val facade = AuthenticationScope(graph)
        assertTrue(networkConfigReads > 0)
        val eagerReads = networkConfigReads
        assertSame(facade.secondFactorVerificationRepository, graph.secondFactorVerificationRepository)
        assertNotSame(facade.login, facade.login)
        assertNotSame(facade.requestSecondFactorVerificationCode, facade.requestSecondFactorVerificationCode)
        assertNotSame(facade.ssoLoginRepository, facade.ssoLoginRepository)
        assertNotSame(facade.registerScope, facade.registerScope)
        assertNotSame(facade.ssoLoginScope, facade.ssoLoginScope)
        assertNotSame(facade.checkIfUpdateRequired, facade.checkIfUpdateRequired)
        assertNotSame(facade.isNomadProfilesEnabled, facade.isNomadProfilesEnabled)
        // SSO construction captures a pending-login repository but opens no temporary network.
        assertEquals(eagerReads, networkConfigReads)
    }

    @Test
    fun currentServerConfigKeepsOneLiveFunctionInsteadOfMemoizingItsResult() {
        val first = newServerConfig(16)
        val second = newServerConfig(18)
        var current = first
        val api = object : AuthenticationApi by mock<AuthenticationApi>() {
            override val serverConfig: ServerConfig get() = current
            override val secondFactorVerificationRepository =
                mock<SecondFactorVerificationRepository>()
        }
        val facade = AuthenticationScope(api)
        val getter = facade.currentServerConfig
        assertSame(getter, facade.currentServerConfig)
        assertSame(first, getter())
        current = second
        assertSame(second, getter())
    }

    @Test
    fun resolvingUnusedPendingLoginRepositoryDoesNotInitializeNetworkConfigurationOrStorage() {
        val graph = createGraphFactory<PendingLoginBindingTestGraph.Factory>().create(mock<AuthenticationGraphInputs>())
        assertNotSame(graph.pendingLoginSystemSettingsRepository, graph.pendingLoginSystemSettingsRepository)
        // Every input getter is strict. No resources exist to initialize or close during resolution.
    }

    private fun closedGlobalDatabase(): GlobalDatabaseBuilder {
        val database = TestGlobalDatabase()
        return database.provider.also {
            it.close()
            database.delete()
        }
    }
}
