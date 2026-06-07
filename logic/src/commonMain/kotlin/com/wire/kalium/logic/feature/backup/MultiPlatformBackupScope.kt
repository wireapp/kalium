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

import com.wire.kalium.cells.domain.usecase.BackupCellFileUseCase
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.dao.MetadataDAO

@Suppress("LongParameterList")
public class MultiPlatformBackupScope internal constructor(
    private val selfUserId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val kaliumFileSystem: KaliumFileSystem,
    private val backupRepository: BackupRepository,
    private val onlineBackupRepository: OnlineBackupRepository,
    private val backupConversationResolver: BackupConversationResolver,
    private val backupCellFile: BackupCellFileUseCase,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val metadataDAO: MetadataDAO,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val messageSender: MessageSender,
) {
    private val backupRootKeyRepository: BackupRootKeyRepository
        get() = BackupRootKeyRepositoryImpl(
            metadataDAO = metadataDAO,
        )

    public val create: CreateMPBackupUseCase
        get() = CreateMPBackupUseCaseImpl(
            backupRepository = backupRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    public val createFromRootKey: CreateBackupFromRootKeyUseCase
        get() = CreateBackupFromRootKeyUseCaseImpl(
            getOrCreateSyncedBackupRootKey = getOrCreateSyncedBackupRootKey,
            createMPBackup = create,
        )

    public val getBackupRootKey: GetBackupRootKeyUseCase
        get() = GetBackupRootKeyUseCaseImpl(backupRootKeyRepository)

    public val generateBackupRootKey: GenerateBackupRootKeyUseCase
        get() = GenerateBackupRootKeyUseCaseImpl(
            currentClientIdProvider = clientIdProvider,
            backupRootKeyRepository = backupRootKeyRepository,
        )

    public val syncBackupRootKey: SyncBackupRootKeyUseCase
        get() = SyncBackupRootKeyUseCaseImpl(
            selfUserId = selfUserId,
            currentClientIdProvider = clientIdProvider,
            backupRootKeyRepository = backupRootKeyRepository,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = messageSender,
        )

    public val pushBackupRootKey: PushBackupRootKeyUseCase
        get() = PushBackupRootKeyUseCaseImpl(
            selfUserId = selfUserId,
            currentClientIdProvider = clientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = messageSender,
        )

    public val getOrCreateSyncedBackupRootKey: GetOrCreateSyncedBackupRootKeyUseCase
        get() = GetOrCreateSyncedBackupRootKeyUseCaseImpl(
            backupRootKeyRepository = backupRootKeyRepository,
            syncBackupRootKey = syncBackupRootKey,
            generateBackupRootKey = generateBackupRootKey,
            pushBackupRootKey = pushBackupRootKey,
        )

    public val generateAndForcePushBackupRootKey: GenerateAndForcePushBackupRootKeyUseCase
        get() = GenerateAndForcePushBackupRootKeyUseCaseImpl(
            generateBackupRootKey = generateBackupRootKey,
            pushBackupRootKey = pushBackupRootKey,
        )

    public val syncBackupRootKeyIfOnlineBackupExists: SyncBackupRootKeyIfOnlineBackupExistsUseCase
        get() = SyncBackupRootKeyIfOnlineBackupExistsUseCaseImpl(
            onlineBackupRepository = onlineBackupRepository,
            syncBackupRootKey = syncBackupRootKey,
        )

    public val createOnline: CreateOnlineBackupUseCase
        get() = CreateOnlineBackupUseCaseImpl(
            selfUserId = selfUserId,
            clientIdProvider = clientIdProvider,
            onlineBackupRepository = onlineBackupRepository,
            messageRepository = messageRepository,
            createBackupFromRootKey = createFromRootKey,
            backupFileUploader = BackupFileUploaderImpl(
                backupConversationResolver = backupConversationResolver,
                backupCellFile = backupCellFile,
            ),
        )

    public val restore: RestoreMPBackupUseCase
        get() = RestoreMPBackupUseCaseImpl(
            selfUserId = selfUserId,
            backupRepository = backupRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    public val restoreLatestOnline: RestoreLatestOnlineBackupUseCase
        get() = RestoreLatestOnlineBackupUseCaseImpl(
            selfUserId = selfUserId,
            backupRootKeyRepository = backupRootKeyRepository,
            syncBackupRootKey = syncBackupRootKey,
            onlineBackupRepository = onlineBackupRepository,
            backupEncryptionKeyDeriver = HkdfBackupEncryptionKeyDeriver,
            restoreMPBackup = restore,
            kaliumFileSystem = kaliumFileSystem,
        )
}
