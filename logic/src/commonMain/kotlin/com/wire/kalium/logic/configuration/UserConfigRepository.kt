/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.toEntity
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.legalhold.LegalHoldRequest
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toSelfDeletionTimerEntity
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toTeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.wrapFlowStorageRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.unread.UserConfigDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
interface UserConfigRepository {
    fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig>
    fun observeAppLockConfig(): Flow<Either<StorageFailure, AppLockTeamConfig>>
    fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit>
    fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    fun setFileSharingAsNotified(): Either<StorageFailure, Unit>
    fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    fun setClassifiedDomainsStatus(
        enabled: Boolean,
        domains: List<String>
    ): Either<StorageFailure, Unit>

    fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
    fun isMLSEnabled(): Either<StorageFailure, Boolean>
    fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    fun getE2EISettings(): Either<StorageFailure, E2EISettings>
    fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>>
    fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit>
    fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit>
    fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit>
    fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol>
    suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit>
    suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>>
    fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean>
    fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit>
    fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean>
    fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>>
    fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>>
    fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit>
    fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus>
    fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>>
    suspend fun setScreenshotCensoringConfig(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>>

    suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus>
    suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus
    ): Either<StorageFailure, Unit>

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit>
    suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>>
    fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant>>
    fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit>
    suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel>
    suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit>
    suspend fun setLegalHoldRequest(
        clientId: String,
        lastPreKeyId: Int,
        lastPreKey: String
    ): Either<StorageFailure, Unit>

    fun observeLegalHoldRequest(): Flow<Either<StorageFailure, LegalHoldRequest>>
    suspend fun deleteLegalHoldRequest(): Either<StorageFailure, Unit>
    suspend fun setLegalHoldChangeNotified(isNotified: Boolean): Either<StorageFailure, Unit>
    suspend fun observeLegalHoldChangeNotified(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setShouldUpdateClientLegalHoldCapability(shouldUpdate: Boolean): Either<StorageFailure, Unit>
    suspend fun shouldCheckCrlForCurrentClient(): Boolean
    suspend fun setShouldCheckCrlForCurrentClient(shouldCheck: Boolean): Either<StorageFailure, Unit>
    suspend fun shouldUpdateClientLegalHoldCapability(): Boolean
    suspend fun setCRLExpirationTime(url: String, timestamp: ULong)
    suspend fun getCRLExpirationTime(url: String): ULong?
    suspend fun observeCertificateExpirationTime(url: String): Flow<Either<StorageFailure, ULong>>
    suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean)
    suspend fun observeShouldNotifyForRevokedCertificate(): Flow<Either<StorageFailure, Boolean>>
    suspend fun clearE2EISettings()
    fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean)
    fun getShouldFetchE2EITrustAnchor(): Boolean
}

@Suppress("TooManyFunctions")
internal class UserConfigDataSource internal constructor(
    private val userConfigStorage: UserConfigStorage,
    private val userConfigDAO: UserConfigDAO,
    private val kaliumConfigs: KaliumConfigs
) : UserConfigRepository {

    override fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
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
        serverSideConfig.isLeft() -> serverSideConfig

        serverSideConfig.value.status.not() -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingStatus.Value.Disabled
            )
        )

        buildConfig is BuildFileRestrictionState.AllowSome -> Either.Right(
            FileSharingStatus(
                isStatusChanged = false,
                state = FileSharingStatus.Value.EnabledSome(buildConfig.allowedType)
            )
        )

        buildConfig is BuildFileRestrictionState.NoRestriction -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingStatus.Value.EnabledAll
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

    override fun getE2EISettings(): Either<StorageFailure, E2EISettings> =
        wrapStorageRequest { userConfigStorage.getE2EISettings() }
            .map { E2EISettings.fromEntity(it) }

    override fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>> =
        userConfigStorage.e2EISettingsFlow()
            .wrapStorageRequest()
            .mapRight { E2EISettings.fromEntity(it) }

    override fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.setE2EISettings(setting.toEntity()) }

    override fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant>> =
        userConfigStorage.e2EINotificationTimeFlow()
            .wrapStorageRequest()
            .mapRight { Instant.fromEpochMilliseconds(it) }

    override fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.setIfAbsentE2EINotificationTime(instant.toEpochMilliseconds()) }

    override fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            getE2EINotificationTimeOrNull()?.let { current ->
                val notifyUserAfterMs = current.plus(duration.inWholeMilliseconds)
                userConfigStorage.updateE2EINotificationTime(notifyUserAfterMs)
            }
        }

    override suspend fun clearE2EISettings() {
        wrapStorageRequest {
            userConfigStorage.setE2EISettings(null)
            userConfigStorage.updateE2EINotificationTime(0)
        }
    }

    private fun getE2EINotificationTimeOrNull() =
        wrapStorageRequest { userConfigStorage.getE2EINotificationTime() }.getOrNull()

    override fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistDefaultProtocol(protocol.toDao()) }

    override fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol> =
        wrapStorageRequest { userConfigStorage.defaultProtocol().toModel() }

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setSupportedProtocols(protocols.toDao()) }

    override suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>> =
        wrapStorageRequest { userConfigDAO.getSupportedProtocols()?.toModel() }

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

    override fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }

    override fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.areReadReceiptsEnabled().wrapStorageRequest()

    override fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistReadReceipts(enabled)
        }

    override fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isTypingIndicatorEnabled().wrapStorageRequest()

    override fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistTypingIndicator(enabled)
        }

    override fun setGuestRoomStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
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
                    GuestRoomLinkStatus(
                        isGuestRoomLinkEnabledEntity.status,
                        isGuestRoomLinkEnabledEntity.isStatusChanged
                    )
                }
            }

    override suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus> =
        wrapStorageRequest {
            userConfigDAO.getTeamSettingsSelfDeletionStatus()
        }.map {
            with(it) {
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = isStatusChanged,
                    enforcedSelfDeletionTimer = selfDeletionTimerEntity.toTeamSelfDeleteTimer()
                )
            }
        }

    override suspend fun setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus):
            Either<StorageFailure, Unit> =
        wrapStorageRequest {
            val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
                selfDeletionTimerEntity = teamSettingsSelfDeletionStatus.enforcedSelfDeletionTimer.toSelfDeletionTimerEntity(),
                isStatusChanged = teamSettingsSelfDeletionStatus.hasFeatureChanged,
            )
            userConfigDAO.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        }

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.markTeamSettingsSelfDeletingMessagesStatusAsNotified()
        }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>> =
        userConfigDAO.observeTeamSettingsSelfDeletingStatus().wrapStorageRequest().map {
            it.map {
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = it.isStatusChanged,
                    enforcedSelfDeletionTimer = it.selfDeletionTimerEntity.toTeamSelfDeleteTimer()
                )
            }
        }

    override suspend fun setScreenshotCensoringConfig(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistScreenshotCensoring(enabled) }

    override suspend fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isScreenshotCensoringEnabledFlow().wrapStorageRequest()

    override fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistAppLockStatus(
                isAppLocked,
                timeout,
                isStatusChanged
            )
        }

    override fun observeAppLockConfig(): Flow<Either<StorageFailure, AppLockTeamConfig>> =
        wrapFlowStorageRequest {
            userConfigStorage.appLockFlow().map {
                it?.let { config ->
                    AppLockTeamConfig(
                        isEnforced = config.enforceAppLock,
                        timeout = config.inactivityTimeoutSecs.seconds,
                        isStatusChanged = config.isStatusChanged
                    )
                }
            }
        }

    override fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig> =
        wrapStorageRequest {
            userConfigStorage.appLockStatus()
        }.map {
            AppLockTeamConfig(
                isEnforced = it.enforceAppLock,
                timeout = it.inactivityTimeoutSecs.seconds,
                isStatusChanged = it.isStatusChanged
            )
        }

    override fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.setTeamAppLockAsNotified()
    }

    override suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel> =
        wrapStorageRequest {
            userConfigDAO.getMigrationConfiguration()?.toModel()
        }

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.setMigrationConfiguration(configuration.toEntity())
        }

    override suspend fun setLegalHoldRequest(
        clientId: String,
        lastPreKeyId: Int,
        lastPreKey: String
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigDAO.persistLegalHoldRequest(clientId, lastPreKeyId, lastPreKey)
    }

    override fun observeLegalHoldRequest(): Flow<Either<StorageFailure, LegalHoldRequest>> =
        userConfigDAO.observeLegalHoldRequest().wrapStorageRequest().mapRight {
            LegalHoldRequest(
                clientId = ClientId(it.clientId),
                lastPreKey = LastPreKey(
                    it.lastPreKey.id,
                    it.lastPreKey.key
                )
            )
        }

    override suspend fun deleteLegalHoldRequest(): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.clearLegalHoldRequest()
        }

    override suspend fun setLegalHoldChangeNotified(isNotified: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setLegalHoldChangeNotified(isNotified) }

    override suspend fun observeLegalHoldChangeNotified(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.observeLegalHoldChangeNotified().wrapStorageRequest()

    override suspend fun setShouldUpdateClientLegalHoldCapability(shouldUpdate: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setShouldUpdateClientLegalHoldCapability(shouldUpdate) }

    override suspend fun shouldUpdateClientLegalHoldCapability(): Boolean =
        userConfigDAO.shouldUpdateClientLegalHoldCapability()

    override suspend fun shouldCheckCrlForCurrentClient() = userConfigDAO.shouldCheckCrlForCurrentClient()

    override suspend fun setShouldCheckCrlForCurrentClient(shouldCheck: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setShouldCheckCrlForCurrentClient(shouldCheck) }

    override suspend fun setCRLExpirationTime(url: String, timestamp: ULong) {
        userConfigDAO.setCRLExpirationTime(url, timestamp)
    }

    override suspend fun getCRLExpirationTime(url: String): ULong? =
        userConfigDAO.getCRLsPerDomain(url)

    override suspend fun observeCertificateExpirationTime(url: String): Flow<Either<StorageFailure, ULong>> =
        userConfigDAO.observeCertificateExpirationTime(url).wrapStorageRequest()

    override suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean) {
        userConfigDAO.setShouldNotifyForRevokedCertificate(shouldNotify)
    }

    override suspend fun observeShouldNotifyForRevokedCertificate(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.observeShouldNotifyForRevokedCertificate().wrapStorageRequest()

    override fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean) {
        userConfigStorage.setShouldFetchE2EITrustAnchors(shouldFetch = shouldFetch)
    }

    override fun getShouldFetchE2EITrustAnchor(): Boolean = userConfigStorage.getShouldFetchE2EITrustAnchorHasRun()
}
