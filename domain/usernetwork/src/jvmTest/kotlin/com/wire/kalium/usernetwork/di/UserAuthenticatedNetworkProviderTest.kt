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

package com.wire.kalium.usernetwork.di

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class UserAuthenticatedNetworkProviderTest {
    private val testUserId: UserId = QualifiedID(
        value = "user-network-provider-test-user",
        domain = "wire.test"
    )

    @Test
    fun givenSameProvider_whenGetOrCreateSameUser_thenApisAreCreatedOnce() {
        val createCount = AtomicInteger(0)
        val provider = PlatformUserAuthenticatedNetworkProvider()

        val firstApis = provider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("first")
        }
        val secondApis = provider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("second")
        }

        assertEquals(1, createCount.get())
        assertSame(firstApis, secondApis)

        cleanup(provider)
    }

    @Test
    fun givenMultipleProviders_whenGetOrCreateSameUser_thenBehaviorMatchesCompileTimeMode() {
        val createCount = AtomicInteger(0)
        val firstProvider = PlatformUserAuthenticatedNetworkProvider()
        val secondProvider = PlatformUserAuthenticatedNetworkProvider()

        val firstApis = firstProvider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("first")
        }
        val secondApis = secondProvider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("second")
        }

        if (USE_GLOBAL_USER_NETWORK_API_CACHE) {
            assertEquals(1, createCount.get())
            assertSame(firstApis, secondApis)
            assertSame(firstApis, firstProvider.get(testUserId))
            assertSame(secondApis, secondProvider.get(testUserId))
        } else {
            assertEquals(2, createCount.get())
            assertNotSame(firstApis, secondApis)
            assertSame(firstApis, firstProvider.get(testUserId))
            assertSame(secondApis, secondProvider.get(testUserId))
        }

        cleanup(firstProvider, secondProvider)
    }

    @Test
    fun givenApisRemovedFromAnotherProvider_whenReadingAgain_thenBehaviorMatchesCompileTimeMode() {
        val createCount = AtomicInteger(0)
        val firstProvider = PlatformUserAuthenticatedNetworkProvider()
        val secondProvider = PlatformUserAuthenticatedNetworkProvider()

        val firstApis = firstProvider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("first")
        }
        val removedApis = secondProvider.remove(testUserId)
        val secondApis = secondProvider.getOrCreate(testUserId) {
            createCount.incrementAndGet()
            testApis("second")
        }

        if (USE_GLOBAL_USER_NETWORK_API_CACHE) {
            assertSame(firstApis, removedApis)
            assertEquals(2, createCount.get())
            assertSame(secondApis, firstProvider.get(testUserId))
        } else {
            assertNull(removedApis)
            assertEquals(2, createCount.get())
            assertSame(firstApis, firstProvider.get(testUserId))
            assertNotSame(firstApis, secondApis)
        }

        cleanup(firstProvider, secondProvider)
    }

    @Test
    fun givenGlobalMode_whenMultipleProvidersRaceToCreateSameUser_thenFirstWriterWins() {
        if (!USE_GLOBAL_USER_NETWORK_API_CACHE) {
            return
        }

        val firstProvider = PlatformUserAuthenticatedNetworkProvider()
        val secondProvider = PlatformUserAuthenticatedNetworkProvider()
        val firstCreatedApis = testApis("first")
        val secondCreatedApis = testApis("second")
        var secondCreatorCalled = false

        val firstResult = firstProvider.getOrCreate(testUserId) { firstCreatedApis }
        val secondResult = secondProvider.getOrCreate(testUserId) {
            secondCreatorCalled = true
            secondCreatedApis
        }

        assertSame(firstCreatedApis, firstResult)
        assertSame(firstCreatedApis, secondResult)
        assertFalse(secondCreatorCalled)

        cleanup(firstProvider, secondProvider)
    }

    private fun cleanup(vararg providers: UserAuthenticatedNetworkProvider) {
        providers.forEach { it.remove(testUserId) }
    }

    private fun testApis(name: String): UserAuthenticatedNetworkApis {
        val container = Proxy.newProxyInstance(
            AuthenticatedNetworkContainer::class.java.classLoader,
            arrayOf(AuthenticatedNetworkContainer::class.java)
        ) { _, method, _ ->
            error("Unexpected call to ${method.name} on $name test container.")
        } as AuthenticatedNetworkContainer
        return UserAuthenticatedNetworkApis(container)
    }
}
