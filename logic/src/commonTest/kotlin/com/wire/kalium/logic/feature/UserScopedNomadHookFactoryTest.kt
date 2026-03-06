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

package com.wire.kalium.logic.feature

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.usernetwork.di.PlatformUserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class UserScopedNomadHookFactoryTest {

    @Test
    fun givenNullNomadUrl_whenCreatingHooks_thenNoHookIsRegistered() {
        val arrangement = Arrangement()

        val hooks = arrangement.factory.createIfConfigured(
            selfUserId = SELF_USER_ID,
            nomadServiceUrl = null,
            userStorageProvider = arrangement.userStorageProvider,
            userAuthenticatedNetworkProvider = arrangement.userAuthenticatedNetworkProvider,
            scope = arrangement.scope,
            backup = {}
        )

        assertNull(hooks)
        assertEquals(0, arrangement.persistenceHookCreations)
        assertEquals(0, arrangement.cryptoHookCreations)
    }

    @Test
    fun givenBlankNomadUrl_whenCreatingHooks_thenNoHookIsRegistered() {
        val arrangement = Arrangement()

        val hooks = arrangement.factory.createIfConfigured(
            selfUserId = SELF_USER_ID,
            nomadServiceUrl = "   ",
            userStorageProvider = arrangement.userStorageProvider,
            userAuthenticatedNetworkProvider = arrangement.userAuthenticatedNetworkProvider,
            scope = arrangement.scope,
            backup = {}
        )

        assertNull(hooks)
        assertEquals(0, arrangement.persistenceHookCreations)
        assertEquals(0, arrangement.cryptoHookCreations)
    }

    @Test
    fun givenNomadUrl_whenCreatingHooks_thenOnlyUserScopedHooksAreRegistered() {
        val arrangement = Arrangement()

        val hooks = arrangement.factory.createIfConfigured(
            selfUserId = SELF_USER_ID,
            nomadServiceUrl = "https://nomad.example.com/service",
            userStorageProvider = arrangement.userStorageProvider,
            userAuthenticatedNetworkProvider = arrangement.userAuthenticatedNetworkProvider,
            scope = arrangement.scope,
            backup = {}
        )

        assertSame(arrangement.persistenceHook, hooks?.persistence)
        assertSame(arrangement.cryptoHook, hooks?.crypto)
        assertEquals(listOf(SELF_USER_ID), arrangement.persistenceHookUsers)
        assertEquals(listOf(SELF_USER_ID), arrangement.cryptoHookUsers)
    }

    private class Arrangement {
        val scope = TestScope()
        val userStorageProvider = object : UserStorageProvider() {
            override fun create(
                userId: UserId,
                shouldEncryptData: Boolean,
                platformProperties: PlatformUserStorageProperties,
                dbInvalidationControlEnabled: Boolean
            ): UserStorage = error("Unused in test")
        }
        val userAuthenticatedNetworkProvider = PlatformUserAuthenticatedNetworkProvider()

        val persistenceHook = object : PersistenceEventHookNotifier {}
        val cryptoHook = CryptoStateChangeHookNotifier { _ -> }
        val persistenceHookUsers = mutableListOf<UserId>()
        val cryptoHookUsers = mutableListOf<UserId>()
        var persistenceHookCreations = 0
        var cryptoHookCreations = 0

        val factory = UserScopedNomadHookFactory(
            createPersistenceHook = { selfUserId, _, _, _ ->
                persistenceHookCreations += 1
                persistenceHookUsers += selfUserId
                persistenceHook
            },
            createCryptoHook = { selfUserId, _, _ ->
                cryptoHookCreations += 1
                cryptoHookUsers += selfUserId
                cryptoHook
            }
        )
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.test")
    }
}
