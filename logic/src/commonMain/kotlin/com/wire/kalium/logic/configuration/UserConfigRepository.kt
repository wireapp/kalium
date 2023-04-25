/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
interface UserConfigRepository {
    fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    fun setFileSharingAsNotified(): Either<StorageFailure, Unit>
    fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>): Either<StorageFailure, Unit>
    fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
    fun isMLSEnabled(): Either<StorageFailure, Boolean>
    fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean>
    fun setSecondFactorPasswordChallengeStatus(required: Boolean): Either<StorageFailure, Unit>
    fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean>
    fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>>
    fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus>
    fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>>
    fun setSelfDeletingMessagesStatus(selfDeletingMessagesStatus: SelfDeletingMessagesStatus): Either<StorageFailure, Unit>
    fun setSelfDeletingMessagesAsNotified(): Either<StorageFailure, Unit>
    fun getSelfDeletingMessagesStatus(): Either<StorageFailure, SelfDeletingMessagesStatus>
    fun observeSelfDeletingMessagesStatus(): Flow<Either<StorageFailure, SelfDeletingMessagesStatus>>
}

@Suppress("TooManyFunctions")
class UserConfigDataSource(
    private val userConfigStorage: UserConfigStorage,
    private val kaliumConfigs: KaliumConfigs
) : UserConfigRepository {

    override fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistFileSharingStatus(status, isStatusChanged) }

    override fun setFileSharingAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.setFileSharingAsNotified()
    }

    override fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> {
        val serverSideConfig = wrapStorageRequest { userConfigStorage.isFileSharingEnabled() }
        val buildConfig = kaliumConfigs.fileRestrictionState
        return deriveFileSharingStatus(serverSideConfig, buildConfig)
    }

    override fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> =
        userConfigStorage.isFileSharingEnabledFlow()
            .wrapStorageRequest()
            .map {
                val buildConfig = kaliumConfigs.fileRestrictionState
                deriveFileSharingStatus(it, buildConfig)
            }

    private fun deriveFileSharingStatus(
        serverSideConfig: Either<StorageFailure, IsFileSharingEnabledEntity>,
        buildConfig: BuildFileRestrictionState
    ): Either<StorageFailure, FileSharingStatus> = when {
        serverSideConfig.isLeft() -> Either.Left(serverSideConfig.value)

        !serverSideConfig.value.status -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingState.Disabled
            )
        )

        buildConfig is BuildFileRestrictionState.RestrictSome -> Either.Right(
            FileSharingStatus(
                isStatusChanged = false,
                state = FileSharingState.EnabledSome(buildConfig.allowedType)
            )
        )

        buildConfig is BuildFileRestrictionState.NoRestriction -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingState.EnabledAll
            )
        )

        else -> error("Unknown file restriction state: buildConfig: $buildConfig , serverConfig: $serverSideConfig")
    }

    override fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        wrapStorageRequest { userConfigStorage.persistClassifiedDomainsStatus(enabled, domains) }

    override fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        userConfigStorage.isClassifiedDomainsEnabledFlow().wrapStorageRequest().map {
            it.map { classifiedDomain ->
                ClassifiedDomainsStatus(classifiedDomain.status, classifiedDomain.trustedDomains)
            }
        }

    override fun isMLSEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { userConfigStorage.isMLSEnabled() }

    override fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableMLS(enabled) }

    override fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistConferenceCalling(enabled)
        }

    override fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigStorage.isConferenceCallingEnabled()
        }

    override fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistSecondFactorPasswordChallengeStatus(isRequired)
        }

    override fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean> = wrapStorageRequest {
        userConfigStorage.isSecondFactorPasswordChallengeRequired()
    }

    override fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isReadReceiptsEnabled().wrapStorageRequest()

    override fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistReadReceipts(enabled)
        }

    override fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistGuestRoomLinkFeatureFlag(status, isStatusChanged)
        }

    override fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus> =
        wrapStorageRequest { userConfigStorage.isGuestRoomLinkEnabled() }.map {
            with(it) { GuestRoomLinkStatus(status, isStatusChanged) }
        }

    override fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>> =
        userConfigStorage.isGuestRoomLinkEnabledFlow()
            .wrapStorageRequest()
            .map {
                it.map { isGuestRoomLinkEnabledEntity ->
                    GuestRoomLinkStatus(isGuestRoomLinkEnabledEntity.status, isGuestRoomLinkEnabledEntity.isStatusChanged)
                }
            }

    override fun setSelfDeletingMessagesStatus(selfDeletingMessagesStatus: SelfDeletingMessagesStatus): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistSelfDeletingMessagesStatus(
                selfDeletingMessagesStatus.isEnabled,
                selfDeletingMessagesStatus.isStatusChanged,
                selfDeletingMessagesStatus.enforcedTimeoutInSeconds
            )
        }

    override fun setSelfDeletingMessagesAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.setSelfDeletingMessagesAsNotified()
    }

    override fun getSelfDeletingMessagesStatus(): Either<StorageFailure, SelfDeletingMessagesStatus> =
        wrapStorageRequest { userConfigStorage.isSelfDeletingMessagesEnabled() }.map {
            with(it) { SelfDeletingMessagesStatus(status, isStatusChanged, enforcedTimeoutInSeconds) }
        }

    override fun observeSelfDeletingMessagesStatus(): Flow<Either<StorageFailure, SelfDeletingMessagesStatus>> =
        userConfigStorage.isSelfDeletingMessagesEnabledFlow()
            .wrapStorageRequest()
            .map {
                it.map { isSelfDeletingMessagesEnabledEntity ->
                    SelfDeletingMessagesStatus(
                        isSelfDeletingMessagesEnabledEntity.status,
                        isSelfDeletingMessagesEnabledEntity.isStatusChanged,
                        isSelfDeletingMessagesEnabledEntity.enforcedTimeoutInSeconds
                    )
                }
            }
}
