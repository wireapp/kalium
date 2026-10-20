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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.service.ServiceDataSource
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.feature.app.AppScope
import com.wire.kalium.logic.feature.app.LegacyAppDependencies
import com.wire.kalium.logic.feature.app.LegacyAppFacadeGraph
import com.wire.kalium.logic.feature.app.SyncAppsUseCase
import com.wire.kalium.logic.feature.connection.CancelConnectionRequestUseCaseResult
import com.wire.kalium.logic.feature.connection.ConnectionScope
import com.wire.kalium.logic.feature.connection.LegacyConnectionDependencies
import com.wire.kalium.logic.feature.connection.LegacyConnectionFacadeGraph
import com.wire.kalium.logic.feature.service.LegacyServiceDependencies
import com.wire.kalium.logic.feature.service.LegacyServiceFacadeGraph
import com.wire.kalium.logic.feature.service.ServiceScope
import com.wire.kalium.logic.feature.team.LegacyTeamDependencies
import com.wire.kalium.logic.feature.team.LegacyTeamFacadeGraph
import com.wire.kalium.logic.feature.team.TeamScope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.dao.ServiceDAO
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Instant

/**
 * Uses the production graph extensions under one generated parent, rather than copying providers
 * into a test graph. Unconfigured dependency getters throw if a narrow path reads unrelated inputs.
 * These tests check facade identity and effects, not session startup, provider cache policy or ABI.
 */
class FreshFacadeBindingIdentityTest {
    @Test
    fun teamGettersShareOnlyTheirOwnCapturesAndKeepUserOwnedStateSharedAcrossFreshFacades() = runTest {
        var repositoryCaptures = 0
        var slowSyncCaptures = 0
        var selfTeamCaptures = 0
        var syncCalls = 0
        val sharedSelfTeam = SelfTeamIdProvider { TEAM_ID.right() }
        val sharedSlowSync = mock<SlowSyncRepository>()
        every { sharedSlowSync.observeLastSlowSyncCompletionInstant() } returns flowOf(Instant.fromEpochSeconds(1))
        val dependencies = object : LegacyTeamDependencies by mock<LegacyTeamDependencies>() {
            override val teamRepository: TeamRepository
                get() = mock<TeamRepository>().also { repository ->
                    repositoryCaptures++
                    everySuspend { repository.syncTeam(TEAM_ID) } calls {
                        syncCalls++
                        Either.Left(StorageFailure.DataNotFound)
                    }
                }
            override val slowSyncRepository: SlowSyncRepository
                get() = sharedSlowSync.also { slowSyncCaptures++ }
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = sharedSelfTeam.also { selfTeamCaptures++ }
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val first = parent.teamFacadeGraphFactory.create(dependencies)
        val second = parent.teamFacadeGraphFactory.create(dependencies)
        val facade = TeamScope(first)
        assertEquals(0, repositoryCaptures)
        val sync = facade.syncSelfTeamInfoUseCase
        assertNotSame(sync, facade.syncSelfTeamInfoUseCase)
        assertEquals(1, repositoryCaptures)
        assertEquals(0, slowSyncCaptures)
        assertEquals(null, sync())
        assertEquals(true, facade.isSelfATeamMember())
        assertNotSame(facade.isSelfATeamMember, facade.isSelfATeamMember)
        assertEquals(1, selfTeamCaptures)
        assertEquals(1, slowSyncCaptures)
        assertSame(first.teamTeamRepository, first.teamTeamRepository)
        assertEquals(null, TeamScope(second).syncSelfTeamInfoUseCase())
        assertNotSame(first.teamTeamRepository, second.teamTeamRepository)
        assertSame(first.teamSelfTeamIdProvider, second.teamSelfTeamIdProvider)
        assertSame(first.teamSlowSyncRepository, second.teamSlowSyncRepository)
        assertEquals(2, repositoryCaptures)
        assertEquals(2, selfTeamCaptures)
        assertEquals(2, slowSyncCaptures)
        assertEquals(2, syncCalls)
    }

    @Test
    fun serviceFacadesCaptureDifferentRealRepositoryWrappersOverTheSameUserDao() = runTest {
        var captures = 0
        val sharedDao = mock<ServiceDAO>()
        every { sharedDao.getAllServices() } returns flowOf(emptyList())
        every { sharedDao.searchServicesByName("query") } returns flowOf(emptyList())
        val dependencies = object : LegacyServiceDependencies by mock<LegacyServiceDependencies>() {
            override val serviceRepository: ServiceRepository
                get() = ServiceDataSource(sharedDao).also { captures++ }
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val first = parent.serviceFacadeGraphFactory.create(dependencies)
        val second = parent.serviceFacadeGraphFactory.create(dependencies)
        val facade = ServiceScope(first)
        val observe = facade.observeAllServices
        assertNotSame(observe, facade.observeAllServices)
        assertEquals(emptyList(), observe().first())
        assertEquals(emptyList(), facade.searchServicesByName("query").first())
        assertEquals(1, captures)
        assertSame(first.serviceServiceRepository, first.serviceServiceRepository)
        assertEquals(emptyList(), ServiceScope(second).observeAllServices().first())
        assertNotSame(first.serviceServiceRepository, second.serviceServiceRepository)
        assertEquals(2, captures)
        verify(VerifyMode.exactly(2)) { sharedDao.getAllServices() }
        verify(VerifyMode.exactly(1)) { sharedDao.searchServicesByName("query") }
        // Team/config/self-team ports remain unconfigured: both read paths must stay independent.
    }

    @Test
    fun appApiVersionIsSnapshottedAtFacadeCreationBeforeItsFirstUseCaseResolution() = runTest {
        var captures = 0
        var selfTeamCaptures = 0
        var currentApiVersion = 14
        val requests = mutableListOf<Boolean>()
        val sharedSelfTeam = SelfTeamIdProvider { TEAM_ID.right() }
        val dependencies = object : LegacyAppDependencies by mock<LegacyAppDependencies>() {
            override val appRepository: AppRepository
                get() = mock<AppRepository>().also { repository ->
                    captures++
                    everySuspend { repository.syncApps(TEAM_ID, any()) } calls {
                        requests += it.args[1] as Boolean
                        Unit.right()
                    }
                }
            override val selfTeamIdProvider: SelfTeamIdProvider
                get() = sharedSelfTeam.also { selfTeamCaptures++ }
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val first = parent.appFacadeGraphFactory.create(dependencies, currentApiVersion)
        val facade = AppScope(first)
        currentApiVersion = 15
        val second = parent.appFacadeGraphFactory.create(dependencies, currentApiVersion)
        assertEquals(0, captures)
        assertEquals(14, first.appApiVersion)
        assertEquals(15, second.appApiVersion)
        val sync = facade.syncApps
        val nextSync = facade.syncApps
        assertNotSame(sync, nextSync)
        assertEquals(SyncAppsUseCase.Result.Success, sync())
        assertEquals(SyncAppsUseCase.Result.Success, nextSync())
        assertEquals(listOf(false, false), requests)
        assertEquals(1, captures)
        assertEquals(1, selfTeamCaptures)
        assertEquals(SyncAppsUseCase.Result.Success, AppScope(second).syncApps())
        assertEquals(listOf(false, false, true), requests)
        assertNotSame(first.appAppRepository, second.appAppRepository)
        assertSame(first.appSelfTeamIdProvider, second.appSelfTeamIdProvider)
        assertEquals(2, captures)
        assertEquals(2, selfTeamCaptures)
    }

    @Test
    fun appLookupDoesNotResolveTheSelfTeamCaptureOrSyncPath() = runTest {
        var captures = 0
        val dependencies = object : LegacyAppDependencies by mock<LegacyAppDependencies>() {
            override val appRepository: AppRepository
                get() = mock<AppRepository>().also { repository ->
                    captures++
                    everySuspend { repository.getAppById(TestUser.USER_ID) } returns Either.Right(null)
                }
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val facade = AppScope(parent.appFacadeGraphFactory.create(dependencies, 15))
        val get = facade.getAppById
        assertNotSame(get, facade.getAppById)
        assertEquals(null, get(TestUser.USER_ID))
        assertEquals(1, captures)
    }

    @Test
    fun connectionNotificationAndCancelShareTheirFacadeRepositoryAndKeepTheUserTransactionProvider() = runTest {
        var repositoryCaptures = 0
        var transactionCaptures = 0
        val sharedRepository = mock<ConnectionRepository>()
        val crypto = CryptoTransactionProviderArrangementImpl()
        crypto.withTransactionReturning(Unit.right())
        everySuspend { sharedRepository.setAllConnectionsAsNotified() } returns Unit
        everySuspend {
            sharedRepository.updateConnectionStatus(any(), TestUser.USER_ID, any())
        } returns Either.Left(StorageFailure.DataNotFound)
        val dependencies = object : LegacyConnectionDependencies by mock<LegacyConnectionDependencies>() {
            override val connectionRepository: ConnectionRepository
                get() = object : ConnectionRepository by sharedRepository {}.also { repositoryCaptures++ }
            override val transactionProvider: CryptoTransactionProvider
                get() = crypto.cryptoTransactionProvider.also { transactionCaptures++ }
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val first = parent.connectionFacadeGraphFactory.create(dependencies)
        val second = parent.connectionFacadeGraphFactory.create(dependencies)
        val facade = ConnectionScope(first)
        val mark = facade.markConnectionRequestAsNotified
        assertNotSame(mark, facade.markConnectionRequestAsNotified)
        mark(null)
        facade.markConnectionRequestAsNotified(null)
        assertEquals(1, repositoryCaptures)
        assertEquals(0, transactionCaptures)
        val cancel = facade.cancelConnectionRequest
        assertNotSame(cancel, facade.cancelConnectionRequest)
        assertEquals(CancelConnectionRequestUseCaseResult.Failure(StorageFailure.DataNotFound), cancel(TestUser.USER_ID))
        assertEquals(1, repositoryCaptures)
        assertEquals(1, transactionCaptures)
        ConnectionScope(second).markConnectionRequestAsNotified(null)
        assertNotSame(first.connectionConnectionRepository, second.connectionConnectionRepository)
        assertSame(first.connectionTransactionProvider, second.connectionTransactionProvider)
        assertEquals(2, repositoryCaptures)
        assertEquals(2, transactionCaptures)
        verifySuspend(VerifyMode.exactly(3)) { sharedRepository.setAllConnectionsAsNotified() }
        // Accept/send-only conversation, user, resolver, creator and fetch ports are never read.
    }

    @Test
    fun teamAndServiceKeepSeparateTeamRepositoryCapturesEvenUnderTheSameParent() {
        var captures = 0
        fun repository(): TeamRepository = mock<TeamRepository>().also { captures++ }
        val team = object : LegacyTeamDependencies by mock<LegacyTeamDependencies>() {
            override val teamRepository: TeamRepository get() = repository()
        }
        val service = object : LegacyServiceDependencies by mock<LegacyServiceDependencies>() {
            override val teamRepository: TeamRepository get() = repository()
        }
        val parent = createGraph<FreshFacadeTestGraph>()
        val teamGraph = parent.teamFacadeGraphFactory.create(team)
        val serviceGraph = parent.serviceFacadeGraphFactory.create(service)
        assertEquals(0, captures)
        assertSame(teamGraph.teamTeamRepository, teamGraph.teamTeamRepository)
        assertSame(serviceGraph.serviceTeamRepository, serviceGraph.serviceTeamRepository)
        assertNotSame(teamGraph.teamTeamRepository, serviceGraph.serviceTeamRepository)
        assertEquals(2, captures)
    }

    private companion object {
        val TEAM_ID = TeamId("fresh-facade-team")
    }
}

/** A platform-final test root exercises the production extensions and their real generated factories. */
@DependencyGraph(UserLifetime::class)
internal interface FreshFacadeTestGraph {
    val teamFacadeGraphFactory: LegacyTeamFacadeGraph.Factory
    val serviceFacadeGraphFactory: LegacyServiceFacadeGraph.Factory
    val appFacadeGraphFactory: LegacyAppFacadeGraph.Factory
    val connectionFacadeGraphFactory: LegacyConnectionFacadeGraph.Factory
}
