/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.backup

import kotlinx.coroutines.CoroutineScope
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository

@Suppress("LongParameterList")
public class MultiPlatformBackupScope internal constructor(
    private val selfUserId: UserId,
    private val kaliumFileSystem: KaliumFileSystem,
    private val backupRepository: BackupRepository,
    private val userRepository: UserRepository,
    private val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase,
    private val userSessionScope: CoroutineScope,
    private val waitUntilSyncLive: suspend () -> Unit,
) {

    public val create: CreateMPBackupUseCase
        get() = CreateMPBackupUseCaseImpl(
            backupRepository = backupRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    public val restore: RestoreMPBackupUseCase
        get() = RestoreMPBackupUseCaseImpl(
            selfUserId = selfUserId,
            refreshConversationsWithoutMetadata = refreshConversationsWithoutMetadata,
            userSessionScope = userSessionScope,
            waitUntilSyncLive = waitUntilSyncLive,
            backupRepository = backupRepository,
            kaliumFileSystem = kaliumFileSystem,
        )
}
