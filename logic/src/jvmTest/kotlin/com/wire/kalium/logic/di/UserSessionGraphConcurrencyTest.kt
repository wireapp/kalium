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

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.feature.client.LegacyClientDependencies
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.userstorage.di.UserStorage
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.zacsweers.metro.createGraphFactory
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** Uses the actual JVM Kalium root and actual composed KaliumUserGraph, without a narrow test root.
 * Strict mocks reject every unrelated global/owner/legacy port read. These tests cover graph
 * composition and concurrent resolution; actual UserSessionScope adapters remain integration work.
 */
class UserSessionGraphConcurrencyTest {
    @Test
    fun concurrentInjectedCacheResolutionCapturesItsRepositoryOnce() {
        val selectors: List<(SelfConversationCacheGraph) -> Any> = listOf({ it.mls }, { it.proteus })
        selectors.forEach { select ->
            val gate = FirstUserBindingGate()
            val inputs = object : SelfConversationRepositoryInputs {
                override val repository: ConversationRepository
                    get() = gate.create { mock() }
            }
            val graph = createGraphFactory<SelfConversationCacheGraph.Factory>().create(inputs, mock())
            val providers = raceFirstUserResolution(gate) { select(graph) }
            providers.forEach { assertSame(providers.first(), it) }
            assertEquals(1, gate.calls.get())
        }
    }

    @Test
    fun concurrentFreshClientUseCasesCallTheirUserCaptureFactoryExactlyOnce() = runTest {
        val gate = FirstUserBindingGate()
        val ids = MutableStateFlow<ClientId?>(null)
        val clients = object : LegacyClientDependencies by mock<LegacyClientDependencies>() {
            override val clientRepository: ClientRepository
                get() = gate.create {
                    mock<ClientRepository>().also { repository ->
                        everySuspend { repository.observeCurrentClientId() } returns ids
                    }
                }
        }
        val graph = composedUserGraph(clients = clients)
        assertEquals(0, gate.calls.get())
        val useCases = raceFirstUserResolution(gate) { graph.clientObserveCurrentClientId }
        assertEquals(1, gate.calls.get())
        assertSame(graph.clientClientRepository, graph.clientClientRepository)
        for (index in useCases.indices) {
            for (earlier in 0 until index) assertNotSame(useCases[earlier], useCases[index])
            assertSame(ids, useCases[index]())
        }
        assertEquals(1, gate.calls.get())
    }

    @Test
    fun concurrentRootDraftResolutionConstructsOneUserCacheThroughTheActualParentGraph() = runTest {
        val database = TestUserDatabase(
            UserIDEntity(Uuid.random().toString(), "user-session-concurrency.example"),
            StandardTestDispatcher(testScheduler),
        )
        try {
            val gate = FirstUserBindingGate()
            val storage = UserStorage(database.builder)
            val resources = object : UserSessionResourceInputs by mock<UserSessionResourceInputs>() {
                override val userStorage: UserStorage get() = gate.create { storage }
            }
            val graph = composedUserGraph(resources = resources)
            assertEquals(0, gate.calls.get())
            val repositories = raceFirstUserResolution(gate) { graph.userSessionMessageDraftRepository }
            repositories.forEach { assertSame(repositories.first(), it) }
            assertSame(repositories.first(), graph.userSessionMessageDraftRepository)
            // This counts the sole resource-port read of the real MessageDraftDataSource constructor;
            // the separate capture test above counts a repository factory, rather than resource reads.
            assertEquals(1, gate.calls.get())
        } finally {
            // raceFirstUserResolution drains its executor before persistence can be closed.
            database.builder.close()
            database.delete()
        }
    }

    private fun composedUserGraph(
        clients: LegacyClientDependencies = mock(),
        resources: UserSessionResourceInputs = mock(),
    ): KaliumUserGraph = createKaliumGraph(
        mock<GlobalGraphInputs>(),
        GlobalGraphOwner { error("Unrelated global scope was requested") },
    ).userGraphFactory.create(
        dependencies = mock<UserGraphDependencies>(),
        legacyConversationDependencies = mock(),
        legacyMessageDependencies = mock(),
        legacyDebugDependencies = mock(),
        legacyMeetingDependencies = mock(),
        legacyChannelsDependencies = mock(),
        legacySearchDependencies = mock(),
        legacyUserDependencies = mock(),
        legacyClientDependencies = clients,
        userSessionResourceInputs = resources,
        userSessionStateInputs = mock(),
        userSessionOwnershipInputs = mock(),
        userSessionGlobalInputs = mock(),
    )
}

private class FirstUserBindingGate {
    val calls = AtomicInteger()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    fun <T> create(factory: () -> T): T {
        calls.incrementAndGet()
        entered.countDown()
        check(release.await(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS)) { "First user binding was never released" }
        return factory()
    }
}

private fun <T> raceFirstUserResolution(gate: FirstUserBindingGate, resolve: () -> T): List<T> {
    val executor = Executors.newFixedThreadPool(USER_BINDING_CONTENDERS)
    val ready = CountDownLatch(USER_BINDING_CONTENDERS)
    val start = CountDownLatch(1)
    val resolving = CountDownLatch(USER_BINDING_CONTENDERS)
    return try {
        val futures = List(USER_BINDING_CONTENDERS) {
            executor.submit(Callable {
                ready.countDown()
                check(start.await(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS)) { "Concurrent resolution did not start" }
                resolving.countDown()
                resolve()
            })
        }
        assertTrue(ready.await(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(resolving.await(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(gate.entered.await(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS))
        // The first factory remains blocked until every thread has reached the resolution call.
        // Incorrect non-atomic first caching can enter the same factory more than once here.
        assertEquals(1, gate.calls.get())
        gate.release.countDown()
        futures.map { it.get(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS) }
    } finally {
        start.countDown()
        gate.release.countDown()
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(USER_BINDING_WAIT_SECONDS, TimeUnit.SECONDS))
    }
}

private const val USER_BINDING_CONTENDERS = 8
private const val USER_BINDING_WAIT_SECONDS = 10L
