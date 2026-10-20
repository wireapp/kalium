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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.KaliumMultiPlatformBackupApi
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class CapturedWaitUntilSyncLive(val value: suspend () -> Unit)

internal fun interface LegacyMPRestoreFactory {
    fun create(progressDispatcher: CoroutineDispatcher): RestoreMPBackupUseCase
}

internal abstract class MultiPlatformBackupFacadeLifetime private constructor()

@Qualifier
internal annotation class LegacyMultiPlatformBackupBinding(val value: String)

internal interface LegacyMultiPlatformBackupDependencies {
    val selfUserId: UserId
    val kaliumFileSystem: KaliumFileSystem
    val backupRepository: BackupRepository
    val userRepository: UserRepository
    val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
    val userSessionScope: CoroutineScope
    val waitUntilSyncLive: suspend () -> Unit
}

internal interface LegacyMultiPlatformBackupApi : KaliumMultiPlatformBackupApi {
    @get:LegacyMultiPlatformBackupBinding("selfUserId")
    val mpBackupSelfUserId: UserId

    @get:LegacyMultiPlatformBackupBinding("kaliumFileSystem")
    val mpBackupKaliumFileSystem: KaliumFileSystem

    @get:LegacyMultiPlatformBackupBinding("backupRepository")
    val mpBackupBackupRepository: BackupRepository

    @get:LegacyMultiPlatformBackupBinding("userRepository")
    val mpBackupUserRepository: UserRepository

    @get:LegacyMultiPlatformBackupBinding("refreshConversationsWithoutMetadata")
    val mpBackupRefreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase

    @get:LegacyMultiPlatformBackupBinding("userSessionScope")
    val mpBackupUserSessionScope: CoroutineScope

    @get:LegacyMultiPlatformBackupBinding("waitUntilSyncLive")
    val mpBackupWaitUntilSyncLive: CapturedWaitUntilSyncLive

    @get:LegacyMultiPlatformBackupBinding("create")
    override val mpBackupCreate: CreateMPBackupUseCase

    @get:LegacyMultiPlatformBackupBinding("restore")
    override val mpBackupRestore: RestoreMPBackupUseCase

    val mpRestoreFactory: LegacyMPRestoreFactory
}

/** Individual bindings retained only by an existing fresh facade owner. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface LegacyMultiPlatformBackupBindings : LegacyMultiPlatformBackupApi {
    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("selfUserId")
    fun provideMpBackupSelfUserId(dependencies: LegacyMultiPlatformBackupDependencies): UserId =
        dependencies.selfUserId

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("kaliumFileSystem")
    fun provideMpBackupKaliumFileSystem(dependencies: LegacyMultiPlatformBackupDependencies): KaliumFileSystem =
        dependencies.kaliumFileSystem

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("backupRepository")
    fun provideMpBackupBackupRepository(dependencies: LegacyMultiPlatformBackupDependencies): BackupRepository =
        dependencies.backupRepository

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("userRepository")
    fun provideMpBackupUserRepository(dependencies: LegacyMultiPlatformBackupDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("refreshConversationsWithoutMetadata")
    fun provideMpBackupRefreshConversationsWithoutMetadata(
        dependencies: LegacyMultiPlatformBackupDependencies,
    ): RefreshConversationsWithoutMetadataUseCase =
        dependencies.refreshConversationsWithoutMetadata

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("userSessionScope")
    fun provideMpBackupUserSessionScope(dependencies: LegacyMultiPlatformBackupDependencies): CoroutineScope =
        dependencies.userSessionScope

    @Provides
    @SingleIn(MultiPlatformBackupFacadeLifetime::class)
    @LegacyMultiPlatformBackupBinding("waitUntilSyncLive")
    fun provideMpBackupWaitUntilSyncLive(dependencies: LegacyMultiPlatformBackupDependencies): CapturedWaitUntilSyncLive =
        CapturedWaitUntilSyncLive(dependencies.waitUntilSyncLive)

    @Provides
    @LegacyMultiPlatformBackupBinding("create")
    fun provideMpBackupCreate(
        @LegacyMultiPlatformBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyMultiPlatformBackupBinding("backupRepository")
        backupRepository: BackupRepository,
        @LegacyMultiPlatformBackupBinding("userRepository")
        userRepository: UserRepository,
    ): CreateMPBackupUseCase =
        CreateMPBackupUseCaseImpl(
            backupRepository = backupRepository,
            userRepository = userRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    @Provides
    @LegacyMultiPlatformBackupBinding("restore")
    fun provideMpBackupRestore(
        @LegacyMultiPlatformBackupBinding("selfUserId")
        selfUserId: UserId,
        @LegacyMultiPlatformBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyMultiPlatformBackupBinding("backupRepository")
        backupRepository: BackupRepository,
        @LegacyMultiPlatformBackupBinding("refreshConversationsWithoutMetadata")
        refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase,
        @LegacyMultiPlatformBackupBinding("userSessionScope")
        userSessionScope: CoroutineScope,
        @LegacyMultiPlatformBackupBinding("waitUntilSyncLive")
        waitUntilSyncLive: CapturedWaitUntilSyncLive,
    ): RestoreMPBackupUseCase =
        RestoreMPBackupUseCaseImpl(
            selfUserId = selfUserId,
            refreshConversationsWithoutMetadata = refreshConversationsWithoutMetadata,
            userSessionScope = userSessionScope,
            waitUntilSyncLive = waitUntilSyncLive.value,
            backupRepository = backupRepository,
            kaliumFileSystem = kaliumFileSystem,
        )

    @Provides
    fun provideMPRestoreFactory(
        @LegacyMultiPlatformBackupBinding("selfUserId")
        selfUserId: UserId,
        @LegacyMultiPlatformBackupBinding("kaliumFileSystem")
        kaliumFileSystem: KaliumFileSystem,
        @LegacyMultiPlatformBackupBinding("backupRepository")
        backupRepository: BackupRepository,
        @LegacyMultiPlatformBackupBinding("refreshConversationsWithoutMetadata")
        refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase,
        @LegacyMultiPlatformBackupBinding("userSessionScope")
        userSessionScope: CoroutineScope,
        @LegacyMultiPlatformBackupBinding("waitUntilSyncLive")
        waitUntilSyncLive: CapturedWaitUntilSyncLive,
    ): LegacyMPRestoreFactory = LegacyMPRestoreFactory { progressDispatcher ->
        RestoreMPBackupUseCaseImpl(
            selfUserId = selfUserId,
            refreshConversationsWithoutMetadata = refreshConversationsWithoutMetadata,
            userSessionScope = userSessionScope,
            waitUntilSyncLive = waitUntilSyncLive.value,
            backupRepository = backupRepository,
            kaliumFileSystem = kaliumFileSystem,
            progressDispatcher = progressDispatcher,
        )
    }
}

@GraphExtension(MultiPlatformBackupFacadeLifetime::class)
internal interface LegacyMultiPlatformBackupGraph : LegacyMultiPlatformBackupBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyMultiPlatformBackupDependencies): LegacyMultiPlatformBackupGraph
    }
}
