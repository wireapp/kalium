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

package com.wire.kalium.logic.configuration

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.featureConfig.CellsInternalConfigModel
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.toEntity
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toSelfDeletionTimerEntity
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toTeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.persistence.config.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.config.WireCellsConfigEntity
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.persistence.model.SupportedCipherSuiteEntity
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/** File-sharing configuration required while receiving asset messages. */
public fun interface FileSharingStatusProvider {
    public suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
}

/** Cells configuration persistence required by feature-config handlers. */
public interface CellsConfigPersistence {
    public suspend fun setCellsEnabled(enabled: Boolean): Either<StorageFailure, Unit>

    public suspend fun persistInternalCellsConfig(config: CellsInternalConfigModel?): Either<StorageFailure, Unit>
}

/** Local configuration operations required by feature-config event handlers. */
@Suppress("TooManyFunctions")
public interface FeatureConfigRepository : FileSharingStatusProvider, CellsConfigPersistence {
    public suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    public override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    public suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>): Either<StorageFailure, Unit>
    public suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit>
    public suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit>
    public suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>>
    public suspend fun setSupportedCipherSuite(cipherSuite: SupportedCipherSuite): Either<StorageFailure, Unit>
    public suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setUseSFTForOneOnOneCalls(shouldUse: Boolean): Either<StorageFailure, Unit>
    public suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings>
    public suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit>
    public suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit>
    public suspend fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    public suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus>
    public suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus>
    public suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus
    ): Either<StorageFailure, Unit>
    public suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?,
    ): Either<StorageFailure, Unit>
    public suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig>
    public suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit>
    public suspend fun setMlsConversationsResetEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setProfileQRCodeEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setAssetAuditLogEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setPreventAdminlessGroupsEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setMeetingsEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun isMeetingsEnabled(): Boolean
}

/** Local feature-config persistence shared by the app and future bounded receivers. */
@Suppress("TooManyFunctions")
public class FeatureConfigRepositoryImpl public constructor(
    private val userConfigStorage: UserConfigStorage,
    private val userConfigDAO: UserConfigDAO,
    private val allowedFileTypesProvider: () -> List<String>?,
) : FeatureConfigRepository {
    override suspend fun setFileSharingStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistFileSharingStatus(status, isStatusChanged) }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> {
        val serverSideConfig = wrapStorageRequest { userConfigStorage.isFileSharingEnabled() }
        val allowedFileTypes = allowedFileTypesProvider()
        return serverSideConfig.map {
            deriveFileSharingStatus(it.status, it.isStatusChanged, allowedFileTypes)
        }
    }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistClassifiedDomainsStatus(enabled, domains) }

    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.enableMLS(enabled) }

    override suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistDefaultProtocol(protocol.toDao()) }

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setSupportedProtocols(protocols.toDao()) }

    override suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>> =
        wrapStorageRequest { userConfigDAO.getSupportedProtocols()?.toModel() }

    override suspend fun setSupportedCipherSuite(cipherSuite: SupportedCipherSuite): Either<StorageFailure, Unit> =
        SupportedCipherSuiteEntity(cipherSuite.supported.map { it.tag }, cipherSuite.default.tag).let { entity ->
            wrapStorageRequest { userConfigDAO.setDefaultCipherSuite(entity) }
        }

    override suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistConferenceCalling(enabled) }

    override suspend fun setUseSFTForOneOnOneCalls(shouldUse: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistUseSftForOneOnOneCalls(shouldUse) }

    override suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings> =
        wrapStorageRequest { userConfigStorage.getE2EISettings() }.map { E2EISettings.fromEntity(it) }

    override suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.setE2EISettings(setting.toEntity()) }

    override suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.setIfAbsentE2EINotificationTime(instant.toEpochMilliseconds()) }

    override suspend fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistGuestRoomLinkFeatureFlag(status, isStatusChanged) }

    override suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus> =
        wrapStorageRequest { userConfigStorage.isGuestRoomLinkEnabled() }.map {
            GuestRoomLinkStatus(it.status, it.isStatusChanged)
        }

    override suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus> =
        wrapStorageRequest { userConfigDAO.getTeamSettingsSelfDeletionStatus() }.map {
            TeamSettingsSelfDeletionStatus(it.isStatusChanged, it.selfDeletionTimerEntity.toTeamSelfDeleteTimer())
        }

    override suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigDAO.setTeamSettingsSelfDeletionStatus(
            TeamSettingsSelfDeletionStatusEntity(
                teamSettingsSelfDeletionStatus.enforcedSelfDeletionTimer.toSelfDeletionTimerEntity(),
                teamSettingsSelfDeletionStatus.hasFeatureChanged,
            )
        )
    }

    override suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigStorage.persistAppLockStatus(isAppLocked, timeout, isStatusChanged)
    }

    override suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig> =
        wrapStorageRequest { userConfigStorage.appLockStatus() }.map {
            AppLockTeamConfig(it.enforceAppLock, it.inactivityTimeoutSecs.seconds, it.isStatusChanged)
        }

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setMigrationConfiguration(configuration.toEntity()) }

    override suspend fun setMlsConversationsResetEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setMlsConversationsResetEnabled(enabled) }

    override suspend fun setCellsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setCellsEnabled(enabled) }

    override suspend fun persistInternalCellsConfig(
        config: CellsInternalConfigModel?
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        config?.let {
            userConfigDAO.setWireCellsConfig(
                WireCellsConfigEntity(it.backendUrl, it.collaboraEdition.name, it.perUserQuotaBytes)
            )
        } ?: userConfigDAO.removeWireCellsConfig()
    }

    override suspend fun setProfileQRCodeEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setProfileQRCodeEnabled(enabled) }

    override suspend fun setAssetAuditLogEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setAssetAuditLogEnabled(enabled) }

    override suspend fun setPreventAdminlessGroupsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setPreventAdminlessGroupsEnabled(enabled) }

    override suspend fun setMeetingsEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setMeetingsEnabled(enabled) }

    override suspend fun isMeetingsEnabled(): Boolean = userConfigDAO.isMeetingsEnabled()
}
