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

import com.wire.kalium.cells.domain.model.WireCellsConfig
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapFlowStorageRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.CellsInternalConfigModel
import com.wire.kalium.logic.data.featureConfig.CollaboraEdition.Companion.fromString
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.legalhold.LegalHoldRequest
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toTeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.receiver.UserPropertiesConfigRepository
import com.wire.kalium.logic.sync.receiver.UserPropertiesConfigRepositoryImpl
import com.wire.kalium.logic.sync.receiver.handler.TrackingIdentifierStorage
import com.wire.kalium.logic.sync.receiver.handler.TrackingIdentifierStorageImpl
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
internal interface UserConfigRepository : UserPropertiesConfigRepository, FeatureConfigRepository {
    override suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    override suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig>
    fun observeAppLockConfig(): Flow<Either<StorageFailure, AppLockTeamConfig>>
    suspend fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit>
    override suspend fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    suspend fun setFileSharingAsNotified(): Either<StorageFailure, Unit>
    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    override suspend fun setClassifiedDomainsStatus(
        enabled: Boolean,
        domains: List<String>
    ): Either<StorageFailure, Unit>

    fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
    suspend fun isMLSEnabled(): Either<StorageFailure, Boolean>
    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    override suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings>
    fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>>
    override suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit>
    suspend fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit>
    override suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit>
    override suspend fun setSupportedCipherSuite(cipherSuite: SupportedCipherSuite): Either<StorageFailure, Unit>
    suspend fun getSupportedCipherSuite(): Either<StorageFailure, SupportedCipherSuite>
    suspend fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol>
    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit>
    override suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>>
    override suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean>
    fun observeConferenceCallingEnabled(): Flow<Either<StorageFailure, Boolean>>
    override suspend fun setUseSFTForOneOnOneCalls(shouldUse: Boolean): Either<StorageFailure, Unit>
    suspend fun shouldUseSFTForOneOnOneCalls(): Either<StorageFailure, Boolean>
    suspend fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit>
    suspend fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean>
    fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>>
    override suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>>
    override suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit>
    fun observeLinkPreviewsEnabled(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setLinkPreviewsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    override suspend fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    override suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus>
    fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>>
    suspend fun setScreenshotCensoringConfig(enabled: Boolean): Either<StorageFailure, Unit>
    fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>>

    override suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus>
    override suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus
    ): Either<StorageFailure, Unit>

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit>
    fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>>
    fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant>>
    override suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit>
    suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel>
    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit>
    suspend fun setLegalHoldRequest(
        clientId: String,
        lastPreKeyId: Int,
        lastPreKey: String
    ): Either<StorageFailure, Unit>

    fun observeLegalHoldRequest(): Flow<Either<StorageFailure, LegalHoldRequest>>
    suspend fun deleteLegalHoldRequest(): Either<StorageFailure, Unit>
    suspend fun setLegalHoldChangeNotified(isNotified: Boolean): Either<StorageFailure, Unit>
    fun observeLegalHoldChangeNotified(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setCRLExpirationTime(url: String, timestamp: ULong)
    suspend fun getCRLExpirationTime(url: String): ULong?
    fun observeCertificateExpirationTime(url: String): Flow<Either<StorageFailure, ULong>>
    suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean)
    fun observeShouldNotifyForRevokedCertificate(): Flow<Either<StorageFailure, Boolean>>
    suspend fun clearE2EISettings()
    suspend fun setCurrentTrackingIdentifier(newIdentifier: String)
    suspend fun getCurrentTrackingIdentifier(): String?
    fun observeCurrentTrackingIdentifier(): Flow<Either<StorageFailure, String>>
    suspend fun setPreviousTrackingIdentifier(identifier: String)
    suspend fun getPreviousTrackingIdentifier(): String?
    suspend fun deletePreviousTrackingIdentifier()
    suspend fun updateNextTimeForCallFeedback(valueMs: Long)
    suspend fun getNextTimeForCallFeedback(): Either<StorageFailure, Long>
    suspend fun setE2EIRotationCheckpoint(checkpoint: ByteArray): Either<StorageFailure, Unit>
    suspend fun getE2EIRotationCheckpoint(): Either<StorageFailure, ByteArray?>
    suspend fun deleteE2EIRotationCheckpoint(): Either<StorageFailure, Unit>
    override suspend fun setMlsConversationsResetEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isMlsConversationsResetEnabled(): Boolean
    suspend fun setAsyncNotificationsEnabled(isAsyncNotificationsEnabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isAsyncNotificationsEnabled(): Boolean
    override suspend fun setCellsEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isCellsEnabled(): Boolean
    suspend fun observeIsCellsEnabled(): Flow<Boolean>
    suspend fun setAppsEnabled(isAppsEnabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isAppsEnabled(): Boolean
    fun observeAppsEnabled(): Flow<Either<StorageFailure, Boolean>>
    override suspend fun setProfileQRCodeEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isProfileQRCodeEnabled(): Boolean
    override suspend fun setAssetAuditLogEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isAssetAuditLogEnabled(): Boolean
    override suspend fun setPreventAdminlessGroupsEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isPreventAdminlessGroupsEnabled(): Boolean
    suspend fun setWireCellsConfig(config: WireCellsConfig?): Either<StorageFailure, Unit>
    suspend fun getWireCellsConfig(): Either<StorageFailure, WireCellsConfig?>
    suspend fun isMLSFaultyKeysRepairExecuted(): Boolean
    suspend fun setMLSFaultyKeysRepairExecuted(repaired: Boolean): Either<StorageFailure, Unit>
    override suspend fun setMeetingsEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    override suspend fun isMeetingsEnabled(): Boolean
    fun observeIsMeetingsEnabled(): Flow<Boolean>
}

@Suppress("TooManyFunctions")
internal class UserConfigDataSource internal constructor(
    private val userConfigStorage: UserConfigStorage,
    private val userConfigDAO: UserConfigDAO,
    private val kaliumConfigs: KaliumConfigs,
    private val userPropertiesConfigRepository: UserPropertiesConfigRepository =
        UserPropertiesConfigRepositoryImpl(userConfigStorage),
    private val featureConfigRepository: FeatureConfigRepository = FeatureConfigRepositoryImpl(
        userConfigStorage,
        userConfigDAO,
        allowedFileTypesProvider = {
            (kaliumConfigs.fileRestrictionState.value as? BuildFileRestrictionState.AllowSome)?.allowedType
        },
    ),
    private val trackingIdentifierStorage: TrackingIdentifierStorage = TrackingIdentifierStorageImpl(userConfigDAO),
) : UserConfigRepository {

    override suspend fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> = featureConfigRepository.setFileSharingStatus(status, isStatusChanged)

    override suspend fun setFileSharingAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.setFileSharingAsNotified()
    }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> =
        featureConfigRepository.isFileSharingEnabled()

    override fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> =
        userConfigStorage.isFileSharingEnabledFlow()
            .wrapStorageRequest()
            .map { serverSideConfig ->
                val allowedFileTypes = when (val buildConfig = kaliumConfigs.fileRestrictionState.value) {
                    is BuildFileRestrictionState.AllowSome -> buildConfig.allowedType
                    BuildFileRestrictionState.NoRestriction -> null
                }
                serverSideConfig.map {
                    deriveFileSharingStatus(it.status, it.isStatusChanged, allowedFileTypes)
                }
            }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        featureConfigRepository.setClassifiedDomainsStatus(enabled, domains)

    override fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        userConfigStorage.isClassifiedDomainsEnabledFlow().wrapStorageRequest().map {
            it.map { classifiedDomain ->
                ClassifiedDomainsStatus(classifiedDomain.status, classifiedDomain.trustedDomains)
            }
        }

    override suspend fun isMLSEnabled(): Either<StorageFailure, Boolean> = withContext(Dispatchers.IO) {
        wrapStorageRequest { userConfigStorage.isMLSEnabled() }
    }

    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setMLSEnabled(enabled)

    override suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings> =
        featureConfigRepository.getE2EISettings()

    override fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>> =
        userConfigStorage.e2EISettingsFlow()
            .wrapStorageRequest()
            .mapRight { E2EISettings.fromEntity(it) }

    override suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit> =
        featureConfigRepository.setE2EISettings(setting)

    override fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant>> =
        userConfigStorage.e2EINotificationTimeFlow()
            .wrapStorageRequest()
            .mapRight { Instant.fromEpochMilliseconds(it) }

    override suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit> =
        featureConfigRepository.setE2EINotificationTime(instant)

    override suspend fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            val notifyUserAfterMs = DateTimeUtil.currentInstant().toEpochMilliseconds().plus(duration.inWholeMilliseconds)
            userConfigStorage.updateE2EINotificationTime(notifyUserAfterMs)
        }

    override suspend fun clearE2EISettings() {
        wrapStorageRequest {
            userConfigStorage.setE2EISettings(null)
            userConfigStorage.updateE2EINotificationTime(0)
            userConfigDAO.deleteE2EIRotationCheckpoint()
        }
    }

    override suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit> =
        featureConfigRepository.setDefaultProtocol(protocol)

    override suspend fun setSupportedCipherSuite(cipherSuite: SupportedCipherSuite): Either<StorageFailure, Unit> =
        featureConfigRepository.setSupportedCipherSuite(cipherSuite)

    override suspend fun getSupportedCipherSuite(): Either<StorageFailure, SupportedCipherSuite> = wrapStorageRequest {
        userConfigDAO.getDefaultCipherSuite()
    }.map {
        SupportedCipherSuite(
            supported = it.supported.map { tag -> CipherSuite.fromTag(tag) },
            default = CipherSuite.fromTag(it.default)
        )
    }

    override suspend fun setE2EIRotationCheckpoint(checkpoint: ByteArray): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setE2EIRotationCheckpoint(Base64.encode(checkpoint)) }

    override suspend fun getE2EIRotationCheckpoint(): Either<StorageFailure, ByteArray?> =
        wrapStorageRequest { userConfigDAO.getE2EIRotationCheckpoint()?.let(Base64::decode) }

    override suspend fun deleteE2EIRotationCheckpoint(): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.deleteE2EIRotationCheckpoint() }

    override suspend fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol> =
        wrapStorageRequest { userConfigStorage.defaultProtocol().toModel() }

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit> =
        featureConfigRepository.setSupportedProtocols(protocols)

    override suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>> =
        featureConfigRepository.getSupportedProtocols()

    override suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setConferenceCallingEnabled(enabled)

    override suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigStorage.isConferenceCallingEnabled()
        }

    override fun observeConferenceCallingEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isConferenceCallingEnabledFlow().wrapStorageRequest()

    override suspend fun setUseSFTForOneOnOneCalls(shouldUse: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setUseSFTForOneOnOneCalls(shouldUse)

    override suspend fun shouldUseSFTForOneOnOneCalls(): Either<StorageFailure, Boolean> = withContext(Dispatchers.IO) {
        wrapStorageRequest {
            userConfigStorage.shouldUseSftForOneOnOneCalls()
        }
    }

    override suspend fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistSecondFactorPasswordChallengeStatus(isRequired)
        }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }

    override fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.areReadReceiptsEnabled().wrapStorageRequest()

    override suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        userPropertiesConfigRepository.setReadReceiptsStatus(enabled)

    override fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isTypingIndicatorEnabled().wrapStorageRequest()

    override suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        userPropertiesConfigRepository.setTypingIndicatorStatus(enabled)

    override fun observeLinkPreviewsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isLinkPreviewsEnabled().wrapStorageRequest()

    override suspend fun setLinkPreviewsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigStorage.persistLinkPreviews(enabled)
        }

    override suspend fun setGuestRoomStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> = featureConfigRepository.setGuestRoomStatus(status, isStatusChanged)

    override suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus> =
        featureConfigRepository.getGuestRoomLinkStatus()

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
        featureConfigRepository.getTeamSettingsSelfDeletionStatus()

    override suspend fun setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus):
            Either<StorageFailure, Unit> = featureConfigRepository.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatus)

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.markTeamSettingsSelfDeletingMessagesStatusAsNotified()
        }

    override fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>> =
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

    override fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>> =
        userConfigStorage.isScreenshotCensoringEnabledFlow().wrapStorageRequest()

    override suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> = featureConfigRepository.setAppLockStatus(isAppLocked, timeout, isStatusChanged)

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

    override suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig> =
        featureConfigRepository.isTeamAppLockEnabled()

    override suspend fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.setTeamAppLockAsNotified()
    }

    override suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel> =
        wrapStorageRequest {
            userConfigDAO.getMigrationConfiguration()?.toModel()
        }

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit> =
        featureConfigRepository.setMigrationConfiguration(configuration)

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

    override fun observeLegalHoldChangeNotified(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.observeLegalHoldChangeNotified().wrapStorageRequest()

    override suspend fun setCRLExpirationTime(url: String, timestamp: ULong) {
        userConfigDAO.setCRLExpirationTime(url, timestamp)
    }

    override suspend fun getCRLExpirationTime(url: String): ULong? =
        userConfigDAO.getCRLsPerDomain(url)

    override fun observeCertificateExpirationTime(url: String): Flow<Either<StorageFailure, ULong>> =
        userConfigDAO.observeCertificateExpirationTime(url).wrapStorageRequest()

    override suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean) {
        userConfigDAO.setShouldNotifyForRevokedCertificate(shouldNotify)
    }

    override fun observeShouldNotifyForRevokedCertificate(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.observeShouldNotifyForRevokedCertificate().wrapStorageRequest()

    override suspend fun setCurrentTrackingIdentifier(newIdentifier: String) {
        trackingIdentifierStorage.setCurrentTrackingIdentifier(newIdentifier)
    }

    override suspend fun getCurrentTrackingIdentifier(): String? =
        trackingIdentifierStorage.getCurrentTrackingIdentifier()

    override fun observeCurrentTrackingIdentifier(): Flow<Either<StorageFailure, String>> =
        userConfigDAO.observeTrackingIdentifier().wrapStorageRequest()

    override suspend fun setPreviousTrackingIdentifier(identifier: String) {
        trackingIdentifierStorage.setPreviousTrackingIdentifier(identifier)
    }

    override suspend fun getPreviousTrackingIdentifier(): String? =
        userConfigDAO.getPreviousTrackingIdentifier()

    override suspend fun deletePreviousTrackingIdentifier() {
        wrapStorageRequest {
            userConfigDAO.deletePreviousTrackingIdentifier()
        }
    }

    override suspend fun updateNextTimeForCallFeedback(valueMs: Long) {
        userConfigDAO.setNextTimeForCallFeedback(valueMs)
    }

    override suspend fun getNextTimeForCallFeedback() = wrapStorageRequest { userConfigDAO.getNextTimeForCallFeedback() }

    override suspend fun setMlsConversationsResetEnabled(enabled: Boolean) =
        featureConfigRepository.setMlsConversationsResetEnabled(enabled)

    override suspend fun isMlsConversationsResetEnabled(): Boolean = userConfigDAO.getMlsConversationsResetEnabled()

    override suspend fun setAsyncNotificationsEnabled(isAsyncNotificationsEnabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.setAsyncNotificationsEnabled(isAsyncNotificationsEnabled)
        }

    override suspend fun isAsyncNotificationsEnabled(): Boolean = userConfigDAO.getAsyncNotificationsEnabled()
    override suspend fun setCellsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setCellsEnabled(enabled)

    override suspend fun persistInternalCellsConfig(config: CellsInternalConfigModel?): Either<StorageFailure, Unit> =
        featureConfigRepository.persistInternalCellsConfig(config)

    override suspend fun isCellsEnabled(): Boolean = userConfigDAO.isCellsEnabled()

    override suspend fun observeIsCellsEnabled(): Flow<Boolean> = userConfigDAO.observeIsCellsEnabled()

    override suspend fun setAppsEnabled(isAppsEnabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.setAppsEnabled(isAppsEnabled)
        }

    override suspend fun isAppsEnabled(): Boolean = userConfigDAO.getAppsEnabled()
    override fun observeAppsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.observeAppsEnabled().wrapStorageRequest()

    override suspend fun setProfileQRCodeEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setProfileQRCodeEnabled(enabled)

    override suspend fun isProfileQRCodeEnabled(): Boolean = userConfigDAO.isProfileQRCodeEnabled()

    override suspend fun setAssetAuditLogEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setAssetAuditLogEnabled(enabled)

    override suspend fun isAssetAuditLogEnabled(): Boolean = userConfigDAO.isAssetAuditLogEnabled()

    override suspend fun setPreventAdminlessGroupsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setPreventAdminlessGroupsEnabled(enabled)

    override suspend fun isPreventAdminlessGroupsEnabled(): Boolean = userConfigDAO.isPreventAdminlessGroupsEnabled()

    override suspend fun setWireCellsConfig(config: WireCellsConfig?): Either<StorageFailure, Unit> =
        featureConfigRepository.persistInternalCellsConfig(
            config?.let {
                CellsInternalConfigModel(
                    backendUrl = it.backendUrl,
                    collaboraEdition = it.collabora,
                    perUserQuotaBytes = it.teamQuotaBytes,
                )
            }
        )

    override suspend fun getWireCellsConfig(): Either<StorageFailure, WireCellsConfig?> = wrapStorageRequest {
        userConfigDAO.getWireCellsConfig()?.let { config ->
            WireCellsConfig(
                backendUrl = config.backendUrl,
                collabora = config.collabora.fromString(),
                teamQuotaBytes = config.teamQuotaBytes,
            )
        }
    }

    override suspend fun isMLSFaultyKeysRepairExecuted(): Boolean = userConfigDAO.isMlsFaultyKeysRepairExecuted()
    override suspend fun setMLSFaultyKeysRepairExecuted(repaired: Boolean): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigDAO.setMlsFaultyKeysRepairExecuted(repaired)
    }

    override suspend fun setMeetingsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        featureConfigRepository.setMeetingsEnabled(enabled)

    override suspend fun isMeetingsEnabled(): Boolean = featureConfigRepository.isMeetingsEnabled()
    override fun observeIsMeetingsEnabled(): Flow<Boolean> = userConfigDAO.observeIsMeetingsEnabled()
}
