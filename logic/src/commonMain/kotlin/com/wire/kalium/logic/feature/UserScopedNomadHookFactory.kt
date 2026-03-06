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
import com.wire.kalium.nomaddevice.NomadAuthenticatedNetworkAccess
import com.wire.kalium.nomaddevice.NomadRemoteBackupDebouncedSyncConfig
import com.wire.kalium.nomaddevice.createUserScopedDebouncedNomadRemoteBackupChangeLogHookNotifier
import com.wire.kalium.nomaddevice.createUserScopedNomadCryptoStateChangeHookNotifier
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope

internal data class UserScopedNomadHooks(
    val persistence: PersistenceEventHookNotifier,
    val crypto: CryptoStateChangeHookNotifier,
)

@Suppress("LongParameterList")
internal class UserScopedNomadHookFactory(
    private val createPersistenceHook: (
        selfUserId: UserId,
        userStorageProvider: UserStorageProvider,
        nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
        scope: CoroutineScope,
    ) -> PersistenceEventHookNotifier = { selfUserId, userStorageProvider, nomadAuthenticatedNetworkAccess, scope ->
        createUserScopedDebouncedNomadRemoteBackupChangeLogHookNotifier(
            selfUserId = selfUserId,
            userStorageProvider = userStorageProvider,
            nomadAuthenticatedNetworkAccess = nomadAuthenticatedNetworkAccess,
            scope = scope,
            config = NomadRemoteBackupDebouncedSyncConfig(),
        )
    },
    private val createCryptoHook: (
        selfUserId: UserId,
        scope: CoroutineScope,
        backup: suspend () -> Unit,
    ) -> CryptoStateChangeHookNotifier = { selfUserId, scope, backup ->
        createUserScopedNomadCryptoStateChangeHookNotifier(
            selfUserId = selfUserId,
            scope = scope,
            backup = backup,
        )
    }
) {

    fun createIfConfigured(
        selfUserId: UserId,
        nomadServiceUrl: String?,
        userStorageProvider: UserStorageProvider,
        userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider,
        scope: CoroutineScope,
        backup: suspend () -> Unit,
    ): UserScopedNomadHooks? {
        if (nomadServiceUrl.isNullOrBlank()) {
            return null
        }

        val nomadAuthenticatedNetworkAccess = NomadAuthenticatedNetworkAccess(userAuthenticatedNetworkProvider)
        return UserScopedNomadHooks(
            persistence = createPersistenceHook(
                selfUserId,
                userStorageProvider,
                nomadAuthenticatedNetworkAccess,
                scope
            ),
            crypto = createCryptoHook(
                selfUserId,
                scope,
                backup
            )
        )
    }
}
