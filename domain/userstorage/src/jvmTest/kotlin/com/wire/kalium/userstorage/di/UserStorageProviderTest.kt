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

package com.wire.kalium.userstorage.di

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.clearInMemoryDatabase
import com.wire.kalium.persistence.db.inMemoryDatabase
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class UserStorageProviderTest {
    private val testUserId: UserId = QualifiedID(
        value = "userstorage-provider-test-user",
        domain = "wire.test"
    )
    private val testProperties = PlatformUserStorageProperties(
        rootPath = "test-root",
        databaseInfo = DatabaseStorageType.InMemory
    )
    private val testUserIdEntity = UserIDEntity(testUserId.value, testUserId.domain)

    @Test
    fun givenSameProvider_whenGetOrCreateSameUser_thenStorageIsCreatedOnce() {
        val createCount = AtomicInteger(0)
        val provider = TestUserStorageProvider(createCount)

        val firstStorage = provider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )
        val secondStorage = provider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )

        assertEquals(1, createCount.get())
        assertSame(firstStorage, secondStorage)

        cleanup(provider)
    }

    @Test
    fun givenMultipleProviders_whenGetOrCreateSameUser_thenBehaviorMatchesCompileTimeMode() {
        val createCount = AtomicInteger(0)
        val firstProvider = TestUserStorageProvider(createCount)
        val secondProvider = TestUserStorageProvider(createCount)

        val firstStorage = firstProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )
        val secondStorage = secondProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )

        if (PROVIDER_CACHE_SCOPE == ProviderCacheScope.GLOBAL) {
            assertEquals(1, createCount.get())
            assertSame(firstStorage, secondStorage)
            assertSame(firstStorage, firstProvider.get(testUserId))
            assertSame(secondStorage, secondProvider.get(testUserId))
        } else {
            assertEquals(2, createCount.get())
            assertNotSame(firstStorage, secondStorage)
            assertSame(firstStorage, firstProvider.get(testUserId))
            assertSame(secondStorage, secondProvider.get(testUserId))
        }

        cleanup(firstProvider, secondProvider)
    }

    @Test
    fun givenStorageRemovedFromAnotherProvider_whenReadingAgain_thenBehaviorMatchesCompileTimeMode() {
        val createCount = AtomicInteger(0)
        val firstProvider = TestUserStorageProvider(createCount)
        val secondProvider = TestUserStorageProvider(createCount)

        val firstStorage = firstProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )
        val removedStorage = secondProvider.remove(testUserId)
        val secondStorage = secondProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )

        if (PROVIDER_CACHE_SCOPE == ProviderCacheScope.GLOBAL) {
            assertSame(firstStorage, removedStorage)
            assertEquals(2, createCount.get())
            assertSame(secondStorage, firstProvider.get(testUserId))
        } else {
            assertNull(removedStorage)
            assertEquals(2, createCount.get())
            assertSame(firstStorage, firstProvider.get(testUserId))
            assertNotSame(firstStorage, secondStorage)
        }

        cleanup(firstProvider, secondProvider)
    }

    @Test
    fun givenNoPreparation_whenObservingStorage_thenStateIsNotStarted() = runBlocking {
        val createCount = AtomicInteger(0)
        val provider = TestUserStorageProvider(createCount)
        val state = provider.observe(testUserId)

        assertSame(state, provider.observe(testUserId))
        assertEquals(UserStorageState.NotStarted, state.first())
        assertEquals(0, createCount.get())

        cleanup(provider)
    }

    @Test
    fun givenMigrationStarts_whenPreparingStorage_thenOneStorageFlowReportsLifecycle() = runBlocking {
        val provider = TestUserStorageProvider(AtomicInteger(0), reportsMigration = true)
        val states = mutableListOf<UserStorageState>()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            provider.observe(testUserId).take(4).toList(states)
        }

        val result = provider.prepare(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false,
        )
        observer.join()
        val storage = assertIs<UserStoragePreparationResult.Success>(result).storage

        assertEquals(
            listOf(
                UserStorageState.NotStarted,
                UserStorageState.OpeningDatabase,
                UserStorageState.MigratingDatabase,
                UserStorageState.Ready(storage),
            ),
            states,
        )

        cleanup(provider)
    }

    @Test
    fun givenMigrationStartsThroughSynchronousAccessor_whenCreatingStorage_thenSameFlowReportsLifecycle() = runBlocking {
        val provider = TestUserStorageProvider(AtomicInteger(0), reportsMigration = true)
        val states = mutableListOf<UserStorageState>()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            provider.observe(testUserId).take(4).toList(states)
        }

        val storage = provider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false,
        )
        observer.join()

        assertEquals(
            listOf(
                UserStorageState.NotStarted,
                UserStorageState.OpeningDatabase,
                UserStorageState.MigratingDatabase,
                UserStorageState.Ready(storage),
            ),
            states,
        )

        cleanup(provider)
    }

    @Test
    fun givenConcurrentCallers_whenPreparingStorage_thenStorageIsCreatedOnce() = runBlocking {
        val createCount = AtomicInteger(0)
        val provider = TestUserStorageProvider(createCount)

        val first = async {
            provider.prepare(testUserId, testProperties, false, false)
        }
        val second = async {
            provider.prepare(testUserId, testProperties, false, false)
        }

        assertSame(first.await(), second.await())
        assertEquals(1, createCount.get())

        cleanup(provider)
    }

    @Test
    fun givenConcurrentCallers_whenOneCallerIsCancelled_thenSharedPreparationContinues() = runBlocking {
        val createCount = AtomicInteger(0)
        val preparationStarted = CountDownLatch(1)
        val continuePreparation = CountDownLatch(1)
        val provider = object : UserStorageProvider() {
            override fun create(
                userId: UserId,
                shouldEncryptData: Boolean,
                platformProperties: PlatformUserStorageProperties,
                dbInvalidationControlEnabled: Boolean,
            ): UserStorage {
                createCount.incrementAndGet()
                preparationStarted.countDown()
                continuePreparation.await()
                return UserStorage(inMemoryDatabase(testUserIdEntity, KaliumDispatcherImpl.io))
            }
        }

        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            provider.prepare(testUserId, testProperties, false, false)
        }
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            provider.prepare(testUserId, testProperties, false, false)
        }
        preparationStarted.await()
        cancelled.cancel()
        continuePreparation.countDown()

        assertIs<UserStoragePreparationResult.Success>(waiting.await())
        assertEquals(1, createCount.get())

        cleanup(provider)
    }

    @Test
    fun givenRetryableFailure_whenPreparingAgain_thenNewAttemptStarts() = runBlocking {
        val createCount = AtomicInteger(0)
        val expected = IllegalStateException("database is locked")
        val provider = TestUserStorageProvider(
            createCount,
            failuresRemaining = AtomicInteger(1),
            failure = expected,
        )

        val first = assertIs<UserStoragePreparationResult.Failure>(
            provider.prepare(testUserId, testProperties, false, false)
        )
        val second = provider.prepare(testUserId, testProperties, false, false)

        assertIs<UserStoragePreparationFailure.TemporarilyUnavailable>(first.failure)
        assertSame(expected, first.failure.exception)
        val storage = assertIs<UserStoragePreparationResult.Success>(second).storage
        assertSame(storage, provider.get(testUserId))
        assertEquals(2, createCount.get())

        cleanup(provider)
    }

    @Test
    fun givenFatalFailure_whenPreparingAgain_thenFailedAttemptIsReused() = runBlocking {
        val createCount = AtomicInteger(0)
        val expected = IllegalStateException("file is not a database")
        val provider = TestUserStorageProvider(
            createCount,
            failuresRemaining = AtomicInteger(1),
            failure = expected,
        )
        val states = mutableListOf<UserStorageState>()
        val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            provider.observe(testUserId).take(3).toList(states)
        }

        val first = assertIs<UserStoragePreparationResult.Failure>(
            provider.prepare(testUserId, testProperties, false, false)
        )
        observer.join()
        val second = assertIs<UserStoragePreparationResult.Failure>(
            provider.prepare(testUserId, testProperties, false, false)
        )

        assertIs<UserStoragePreparationFailure.SupportRequired>(first.failure)
        assertSame(first.failure, second.failure)
        assertSame(expected, first.failure.exception)
        assertEquals(UserStorageState.NotStarted, states[0])
        assertEquals(UserStorageState.OpeningDatabase, states[1])
        assertSame(first.failure, assertIs<UserStorageState.Failed>(states[2]).failure)
        assertEquals(1, createCount.get())

        cleanup(provider)
    }

    @Test
    fun givenNestedLockedError_whenClassifyingFailure_thenOriginalExceptionIsRetained() {
        val expected = IllegalStateException(
            "Could not open database",
            IllegalStateException("database is locked"),
        )
        val result = expected.toUserStoragePreparationFailure()

        assertIs<UserStoragePreparationFailure.TemporarilyUnavailable>(result)
        assertSame(expected, result.exception)
    }

    @Test
    fun givenDiskFullError_whenClassifyingFailure_thenOriginalExceptionIsRetained() {
        val expected = IllegalStateException("SQLITE_FULL: database or disk is full")
        val result = expected.toUserStoragePreparationFailure()

        assertIs<UserStoragePreparationFailure.InsufficientStorage>(result)
        assertSame(expected, result.exception)
    }

    @Test
    fun givenUnknownDatabaseError_whenClassifyingFailure_thenOriginalExceptionIsRetained() {
        val expected = IllegalStateException("file is not a database")
        val result = expected.toUserStoragePreparationFailure()

        assertIs<UserStoragePreparationFailure.SupportRequired>(result)
        assertSame(expected, result.exception)
    }

    private fun cleanup(vararg providers: UserStorageProvider) {
        providers.forEach { it.remove(testUserId)?.database?.nuke() }
        clearInMemoryDatabase(testUserIdEntity)
    }

    private class TestUserStorageProvider(
        private val createCount: AtomicInteger,
        private val reportsMigration: Boolean = false,
        private val failuresRemaining: AtomicInteger = AtomicInteger(0),
        private val failure: IllegalStateException = IllegalStateException("database is locked"),
    ) : UserStorageProvider() {

        override fun create(
            userId: UserId,
            shouldEncryptData: Boolean,
            platformProperties: PlatformUserStorageProperties,
            dbInvalidationControlEnabled: Boolean
        ): UserStorage {
            createCount.incrementAndGet()
            if (failuresRemaining.getAndUpdate { current -> (current - 1).coerceAtLeast(0) } > 0) {
                throw failure
            }
            val userIdEntity = UserIDEntity(userId.value, userId.domain)
            return UserStorage(inMemoryDatabase(userIdEntity, KaliumDispatcherImpl.io))
        }

        override fun create(
            userId: UserId,
            shouldEncryptData: Boolean,
            platformProperties: PlatformUserStorageProperties,
            dbInvalidationControlEnabled: Boolean,
            onMigrationStarted: () -> Unit,
        ): UserStorage {
            if (reportsMigration) {
                onMigrationStarted()
            }
            return create(userId, shouldEncryptData, platformProperties, dbInvalidationControlEnabled)
        }
    }
}
