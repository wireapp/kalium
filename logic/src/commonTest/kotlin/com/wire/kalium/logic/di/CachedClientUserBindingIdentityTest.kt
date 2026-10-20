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

package com.wire.kalium.logic.di

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.client.ClientScope
import com.wire.kalium.logic.feature.client.LegacyClientBindings
import com.wire.kalium.logic.feature.client.LegacyClientDependencies
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.server.GetTeamUrlUseCase
import com.wire.kalium.logic.feature.user.LegacyUserBindings
import com.wire.kalium.logic.feature.user.LegacyUserDependencies
import com.wire.kalium.logic.feature.user.UpdateSelfUserSupportedProtocolsUseCase
import com.wire.kalium.logic.feature.user.UserScope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.MetadataDAO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The test root compiles the production bindings without depending on the production root's factory inputs. */
@DependencyGraph(UserLifetime::class)
internal interface CachedClientUserIdentityGraph : LegacyClientBindings, LegacyUserBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides clients: LegacyClientDependencies,
            @Provides users: LegacyUserDependencies,
        ): CachedClientUserIdentityGraph
    }
}

class CachedClientUserBindingIdentityTest {
    @Test
    fun freshClientObserversShareTheCapturedLiveStateWithoutReadingUnrelatedPorts() = runTest {
        var captures = 0
        val clientIds = MutableStateFlow<ClientId?>(null)
        val repository = mock<ClientRepository>()
        everySuspend { repository.observeCurrentClientId() } returns clientIds
        val dependencies = object : LegacyClientDependencies by mock<LegacyClientDependencies>() {
            override val clientRepository: ClientRepository
                get() = repository.also { captures++ }
        }
        val graph = graph(dependencies)
        val facade = ClientScope(graph)
        assertEquals(0, captures)
        val first = facade.observeCurrentClientId
        val second = facade.observeCurrentClientId
        assertNotSame(first, second)
        assertEquals(1, captures)
        assertSame(clientIds, first())
        assertSame(first(), second())
        clientIds.value = ClientId("registered-client")
        assertEquals(ClientId("registered-client"), second().first())
        // All other port getters are strict mocks: an unrelated capture read fails the test.
        assertSame(repository, facade.clientRepository)
        assertEquals(1, captures)
    }

    @Test
    fun freshUserUseCasesUseOneCapturePerGraphAndNewGraphsCaptureFreshRepositories() = runTest {
        var captures = 0
        val repositories = mutableListOf<UserRepository>()
        val dependencies = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val userRepository: UserRepository
                get() = mock<UserRepository>().also { repository ->
                    captures++
                    everySuspend { repository.getSelfUser() } returns TestUser.SELF.right()
                    repositories.add(repository)
                }
        }
        val firstGraph = graph(users = dependencies)
        val secondGraph = graph(users = dependencies)
        val firstFacade = UserScope(firstGraph)
        val secondFacade = UserScope(secondGraph)
        assertEquals(0, captures)
        val first = firstFacade.getSelfUser
        val another = firstFacade.getSelfUser
        assertNotSame(first, another)
        assertEquals(TestUser.SELF, first())
        assertEquals(TestUser.SELF, another())
        assertEquals(1, captures)
        assertEquals(TestUser.SELF, secondFacade.getSelfUser())
        assertEquals(2, captures)
        assertSame(repositories[0], firstGraph.userUserRepository)
        assertSame(repositories[1], secondGraph.userUserRepository)
        assertNotSame(firstGraph.userUserRepository, secondGraph.userUserRepository)
    }

    @Test
    fun publicRefreshAndTeamUrlAndProtocolAccessorsReturnTheirCapturedAliases() {
        var refreshCaptures = 0
        var urlCaptures = 0
        var protocolCaptures = 0
        val dependencies = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
                get() = mock<RefreshUsersWithoutMetadataUseCase>().also { refreshCaptures++ }
            override val teamUrlUseCase: GetTeamUrlUseCase
                get() = GetTeamUrlUseCase(TestUser.SELF.id, mock<ServerConfigRepository>()).also { urlCaptures++ }
            override val updateSelfUserSupportedProtocolsUseCase: UpdateSelfUserSupportedProtocolsUseCase
                get() = mock<UpdateSelfUserSupportedProtocolsUseCase>().also { protocolCaptures++ }
        }
        val firstGraph = graph(users = dependencies)
        val secondGraph = graph(users = dependencies)
        val facade = UserScope(firstGraph)
        assertEquals(listOf(0, 0, 0), listOf(refreshCaptures, urlCaptures, protocolCaptures))
        assertSame(firstGraph.userRefreshUsersWithoutMetadata, facade.refreshUsersWithoutMetadata)
        assertSame(facade.refreshUsersWithoutMetadata, facade.refreshUsersWithoutMetadata)
        assertSame(firstGraph.userTeamUrlUseCase, facade.getTeamUrl)
        assertSame(facade.getTeamUrl, facade.getTeamUrl)
        assertSame(firstGraph.userUpdateSelfUserSupportedProtocolsUseCase, facade.updateSupportedProtocols)
        assertSame(facade.updateSupportedProtocols, facade.updateSupportedProtocols)
        assertEquals(listOf(1, 1, 1), listOf(refreshCaptures, urlCaptures, protocolCaptures))
        val other = UserScope(secondGraph)
        assertNotSame(facade.refreshUsersWithoutMetadata, other.refreshUsersWithoutMetadata)
        assertNotSame(facade.getTeamUrl, other.getTeamUrl)
        assertNotSame(facade.updateSupportedProtocols, other.updateSupportedProtocols)
        assertEquals(listOf(2, 2, 2), listOf(refreshCaptures, urlCaptures, protocolCaptures))
    }

    @Test
    fun clientAndUserCapturesOfTheSameFactoryRemainDistinct() {
        var creations = 0
        fun createRegistration(): RegisterMLSClientUseCase = mock<RegisterMLSClientUseCase>().also { creations++ }
        val clients = object : LegacyClientDependencies by mock<LegacyClientDependencies>() {
            override val registerMLSClientUseCase: RegisterMLSClientUseCase get() = createRegistration()
        }
        val users = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val registerMLSClientUseCase: RegisterMLSClientUseCase get() = createRegistration()
        }
        val first = graph(clients, users)
        val second = graph(clients, users)
        assertEquals(0, creations)
        assertSame(first.clientRegisterMLSClientUseCase, first.clientRegisterMLSClientUseCase)
        assertSame(first.userRegisterMLSClientUseCase, first.userRegisterMLSClientUseCase)
        assertNotSame(first.clientRegisterMLSClientUseCase, first.userRegisterMLSClientUseCase)
        assertEquals(2, creations)
        assertNotSame(first.clientRegisterMLSClientUseCase, second.clientRegisterMLSClientUseCase)
        assertNotSame(first.userRegisterMLSClientUseCase, second.userRegisterMLSClientUseCase)
        assertEquals(4, creations)
    }

    @Test
    fun separateDeferredTimestampConsumersRetainSeparateFactoryResults() {
        var metadataCaptures = 0
        val dependencies = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val metadataDAO: MetadataDAO
                get() = mock<MetadataDAO>().also { metadataCaptures++ }
        }
        val graph = graph(users = dependencies)
        // The key-package manager, MLS manager and slow-sync worker each own their own Lazy.
        val first = lazy { graph.userTimestampKeyRepository }
        val second = lazy { graph.userTimestampKeyRepository }
        assertEquals(0, metadataCaptures)
        assertSame(first.value, first.value)
        assertNotSame(first.value, second.value)
        assertNotSame(first.value, graph.userTimestampKeyRepository)
        assertEquals(1, metadataCaptures)
    }

    @Test
    fun personalToTeamSupportIsLazyAndRetainedPerUserGraph() {
        var sessions = 0
        var serverConfigs = 0
        var teamIds = 0
        val dependencies = object : LegacyUserDependencies by mock<LegacyUserDependencies>() {
            override val sessionManager: SessionManager
                get() = mock<SessionManager>().also { sessions++ }
            override val serverConfigRepository: ServerConfigRepository
                get() = mock<ServerConfigRepository>().also { serverConfigs++ }
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = mock<SelfTeamIdProvider>().also { teamIds++ }
        }
        val first = graph(users = dependencies)
        val second = graph(users = dependencies)
        val facade = UserScope(first)
        assertEquals(listOf(0, 0, 0), listOf(sessions, serverConfigs, teamIds))
        val support = facade.isPersonalToTeamAccountSupportedByBackend
        assertSame(support, facade.isPersonalToTeamAccountSupportedByBackend)
        assertSame(support, first.userIsPersonalToTeamAccountSupportedByBackend)
        assertEquals(listOf(1, 1, 1), listOf(sessions, serverConfigs, teamIds))
        assertNotSame(support, UserScope(second).isPersonalToTeamAccountSupportedByBackend)
        assertEquals(listOf(2, 2, 2), listOf(sessions, serverConfigs, teamIds))
    }

    private fun graph(
        clients: LegacyClientDependencies = mock<LegacyClientDependencies>(),
        users: LegacyUserDependencies = mock<LegacyUserDependencies>(),
    ): CachedClientUserIdentityGraph = createGraphFactory<CachedClientUserIdentityGraph.Factory>().create(clients, users)
}
