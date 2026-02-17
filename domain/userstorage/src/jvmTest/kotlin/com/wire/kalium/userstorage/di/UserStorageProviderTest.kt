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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun givenMultipleProviderInstances_whenGetOrCreateSameUser_thenStorageIsCreatedOnce() {
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

        assertEquals(1, createCount.get())
        assertSame(firstStorage, secondStorage)

        cleanup(firstProvider, secondProvider)
    }

    @Test
    fun givenStorageRemovedFromOneProvider_whenReadingFromAnother_thenStorageIsGone() {
        val createCount = AtomicInteger(0)
        val firstProvider = TestUserStorageProvider(createCount)
        val secondProvider = TestUserStorageProvider(createCount)

        val storage = firstProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )
        val removedStorage = secondProvider.remove(testUserId)

        assertSame(storage, removedStorage)
        assertNull(firstProvider.get(testUserId))

        val recreatedStorage = secondProvider.getOrCreate(
            userId = testUserId,
            platformUserStorageProperties = testProperties,
            shouldEncryptData = false,
            dbInvalidationControlEnabled = false
        )

        assertEquals(2, createCount.get())
        assertSame(recreatedStorage, firstProvider.get(testUserId))

        cleanup(firstProvider, secondProvider)
    }

    private fun cleanup(firstProvider: UserStorageProvider, secondProvider: UserStorageProvider) {
        firstProvider.remove(testUserId)?.database?.close()
        secondProvider.remove(testUserId)?.database?.close()
        clearInMemoryDatabase(testUserIdEntity)
    }

    private class TestUserStorageProvider(
        private val createCount: AtomicInteger
    ) : UserStorageProvider() {

        override fun create(
            userId: UserId,
            shouldEncryptData: Boolean,
            platformProperties: PlatformUserStorageProperties,
            dbInvalidationControlEnabled: Boolean
        ): UserStorage {
            createCount.incrementAndGet()
            val userIdEntity = UserIDEntity(userId.value, userId.domain)
            return UserStorage(inMemoryDatabase(userIdEntity, KaliumDispatcherImpl.io))
        }
    }
}
