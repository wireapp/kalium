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

package com.wire.kalium.nomaddevice

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistMessageHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Clock

/**
 * Nomad-side [PersistMessageHookNotifier] that writes syncable MESSAGE_UPSERT events to the remote backup changelog.
 */
public class NomadRemoteBackupChangeLogHookNotifier internal constructor(
    private val onPersistedMessage: (PersistedMessageData, UserId) -> Unit
) : PersistMessageHookNotifier {

    public constructor(
        userStorageProvider: UserStorageProvider,
        coroutineScope: CoroutineScope,
        eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) : this(
        createNomadRemoteBackupChangeLogCallback(
            userStorageProvider = userStorageProvider,
            coroutineScope = coroutineScope,
            eventTimestampMsProvider = eventTimestampMsProvider
        )
    )

    public override fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        onPersistedMessage(message, selfUserId)
    }
}
