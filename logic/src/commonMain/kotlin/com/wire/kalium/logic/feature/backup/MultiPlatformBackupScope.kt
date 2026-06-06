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

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider

@Suppress("LongParameterList")
public class MultiPlatformBackupScope internal constructor(
    private val selfUserId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val kaliumFileSystem: KaliumFileSystem,
    private val backupRepository: BackupRepository,
    private val onlineBackupRepository: OnlineBackupRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val globalPreferences: GlobalPrefProvider,
) {
    private val backupRootKeyRepository: BackupRootKeyRepository
        get() = BackupRootKeyRepositoryImpl(
            selfUserId = selfUserId,
            passphraseStorage = globalPreferences.passphraseStorage,
        )

    public val create: CreateMPBackupUseCase
        get() = CreateMPBackupUseCaseImpl(
            backupRepository = backupRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    public val createFromRootKey: CreateBackupFromRootKeyUseCase
        get() = CreateBackupFromRootKeyUseCaseImpl(
            getBackupRootKey = GetBackupRootKeyUseCaseImpl(backupRootKeyRepository),
            generateBackupRootKey = GenerateBackupRootKeyUseCaseImpl(
                currentClientIdProvider = clientIdProvider,
                backupRootKeyRepository = backupRootKeyRepository,
            ),
            createMPBackup = create,
        )

    public val createOnline: CreateOnlineBackupUseCase
        get() = CreateOnlineBackupUseCaseImpl(
            selfUserId = selfUserId,
            clientIdProvider = clientIdProvider,
            onlineBackupRepository = onlineBackupRepository,
            messageRepository = messageRepository,
            createBackupFromRootKey = createFromRootKey,
            backupFileUploader = BackupFileUploaderImpl(),
        )

    public val restore: RestoreMPBackupUseCase
        get() = RestoreMPBackupUseCaseImpl(
            selfUserId = selfUserId,
            backupRepository = backupRepository,
            kaliumFileSystem = kaliumFileSystem,
        )
}
