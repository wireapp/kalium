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

import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.util.KaliumDispatcherImpl
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class KaliumConsumerConcurrencyTest {
    @Test
    fun assemblyChecksGenerationInvalidationBeforeCancellationReachesItsConsumer() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val first = registry.createConsumer()
        val second = registry.createConsumer()
        val firstOwner = Job()
        first.assemble(create = { firstOwner }, onDiscard = {}, ownedJob = { it })
        val cancellingFirst = CountDownLatch(1)
        val finishCancellation = CountDownLatch(1)
        firstOwner.invokeOnCompletion {
            cancellingFirst.countDown()
            check(finishCancellation.await(5, TimeUnit.SECONDS))
        }
        val threads = Executors.newSingleThreadExecutor()
        var invalidation: Future<*>? = null
        var discards = 0
        try {
            assertFailsWith<CancellationException> {
                second.assemble(
                    create = {
                        invalidation = threads.submit { registry.invalidate() }
                        assertTrue(cancellingFirst.await(5, TimeUnit.SECONDS))
                        Any()
                    },
                    onDiscard = { discards++ },
                )
            }
            assertEquals(1, discards)
            finishCancellation.countDown()
            invalidation!!.get(5, TimeUnit.SECONDS)
            registry.invalidateAndJoin()
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishCancellation.countDown()
            threads.shutdownNow()
        }
    }

    @Test
    fun invalidationDuringExternalAssemblyWaitsForConstructionAndDiscardAndRejectsPublication() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        // This is deliberately after registry.createConsumer has returned, like Android assembly.
        val consumer = registry.createConsumer()
        val entered = CountDownLatch(1)
        val finishConstruction = CountDownLatch(1)
        val discarding = CountDownLatch(1)
        val finishDiscard = CountDownLatch(1)
        val threads = Executors.newSingleThreadExecutor()
        try {
            val result = threads.submit(Callable {
                runCatching {
                    consumer.assemble(
                        create = {
                            entered.countDown()
                            check(finishConstruction.await(5, TimeUnit.SECONDS))
                            Any()
                        },
                        onDiscard = {
                            discarding.countDown()
                            check(finishDiscard.await(5, TimeUnit.SECONDS))
                        },
                    )
                }
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            registry.invalidate()
            val drain = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { registry.invalidateAndJoin() }
            assertFalse(drain.isCompleted)
            finishConstruction.countDown()
            assertTrue(discarding.await(5, TimeUnit.SECONDS))
            assertFalse(drain.isCompleted)
            finishDiscard.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS).exceptionOrNull() is CancellationException)
            drain.await()
            assertTrue(dependencies.messages.isEmpty())
            assertTrue(dependencies.conversations.isEmpty())
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finishConstruction.countDown()
            finishDiscard.countDown()
            threads.shutdownNow()
        }
    }

    @Test
    fun concurrentFirstResolutionConstructsOneRepositoryAndFreshUseCases() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val registry = KaliumConsumerRegistry(dependencies.userGraph())
        val consumer = registry.createConsumer()
        val threads = Executors.newFixedThreadPool(12)
        try {
            val start = CyclicBarrier(12)
            val getters = (1..12).map {
                threads.submit(Callable<GetMessageByIdUseCase> {
                    start.await(5, TimeUnit.SECONDS)
                    consumer.getMessageById
                })
            }.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, dependencies.messages.size)
            getters.zipWithNext().forEach { (first, second) -> assertNotSame(first, second) }
        } finally {
            registry.invalidateAndJoin()
            threads.shutdownNow()
        }
    }

    @Test
    fun invalidationDuringCreationRejectsPublicationAndWaitsForConstructionToUnwind() = runBlocking {
        val dependencies = ConsumerTestDependencies(KaliumDispatcherImpl)
        val graph = dependencies.userGraph()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val threads = Executors.newSingleThreadExecutor()
        val registry = KaliumConsumerRegistry { work ->
            entered.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            graph.consumerGraphFactory.create(work)
        }
        try {
            val result = threads.submit(Callable { runCatching { registry.createConsumer() } })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            registry.invalidate()
            val drain = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { registry.invalidateAndJoin() }
            assertFalse(drain.isCompleted)
            assertFailsWith<IllegalStateException> { registry.createConsumer() }
            finish.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS).isFailure)
            drain.await()
            assertTrue(dependencies.messages.isEmpty())
            assertTrue(dependencies.conversations.isEmpty())
            assertEquals(0, dependencies.resources.closes)
        } finally {
            finish.countDown()
            threads.shutdownNow()
        }
    }
}
