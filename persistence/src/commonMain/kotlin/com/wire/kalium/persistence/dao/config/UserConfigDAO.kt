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
package com.wire.kalium.persistence.dao.config

import com.wire.kalium.persistence.dao.config.model.MLSMigrationEntity
import com.wire.kalium.persistence.dao.config.model.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.config.model.AppLockConfigEntity
import com.wire.kalium.persistence.dao.config.model.ClassifiedDomainsEntity
import com.wire.kalium.persistence.dao.config.model.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.dao.config.model.IsGuestRoomLinkEnabledEntity
import com.wire.kalium.util.time.Second
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.SetSerializer

@Suppress("TooManyFunctions")
interface UserConfigDAO {

    suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity?
    suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    )

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified()
    suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?>

    suspend fun getMigrationConfiguration(): MLSMigrationEntity?
    suspend fun setMigrationConfiguration(configuration: MLSMigrationEntity)

    suspend fun getSupportedProtocols(): Set<SupportedProtocolEntity>?
    suspend fun setSupportedProtocols(protocols: Set<SupportedProtocolEntity>)

    /**
     * save flag from the user settings to enforce and disable App Lock
     */
    suspend fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Second,
        isStatusChanged: Boolean?
    )

    /**
     * get the saved flag to know if App Lock is enforced or not
     */
    suspend fun appLockStatus(): AppLockConfigEntity?

    /**
     * returns a Flow of the saved App Lock status
     */
    suspend fun appLockFlow(): Flow<AppLockConfigEntity?>

    suspend fun setTeamAppLockAsNotified()

    /**
     * Save flag from the file sharing api, and if the status changes
     */
    suspend fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    /**
     * Get the saved flag that been saved to know if the file sharing is enabled or not with the flag
     * to know if there was a status change
     */
    suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity?

    /**
     * Returns the Flow of file sharing status
     */
    suspend fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

    suspend fun setFileSharingAsNotified()

    /**
     * Returns a Flow containing the status and list of classified domains
     */
    suspend fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?>

    /**
     *Save the flag and list of trusted domains
     */
    suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>)

    /**
     * Saves the flag that indicates whether a 2FA challenge is
     * required for some operations such as:
     * Login, Create Account, Register Client, etc.
     * @see isSecondFactorPasswordChallengeRequired
     */
    suspend fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean)

    /**
     * Checks if the 2FA challenge is
     * required for some operations such as:
     * Login, Create Account, Register Client, etc.
     * @see persistSecondFactorPasswordChallengeStatus
     */
    suspend fun isSecondFactorPasswordChallengeRequired(): Boolean

    /**
     * Save default protocol to use
     */
    suspend fun persistDefaultProtocol(protocol: SupportedProtocolEntity)

    /**
     * Gets default protocol to use. Defaults to PROTEUS if not default protocol has been saved.
     */
    suspend fun defaultProtocol(): SupportedProtocolEntity

    /**
     * Save flag from user settings to enable or disable Conference Calling
     */
    suspend fun persistConferenceCalling(enabled: Boolean)

    /**
     * Get the saved flag to know if Conference Calling is enabled or not
     */
    suspend fun isConferenceCallingEnabled(): Boolean

    /**
     * Get the saved flag to know whether user's Read Receipts are currently enabled or not
     */
    suspend fun areReadReceiptsEnabled(): Flow<Boolean>

    /**
     * Persist the flag to indicate if user's Read Receipts are enabled or not.
     */
    suspend fun persistReadReceipts(enabled: Boolean)

    /**
     * Get the saved global flag to know whether user's typing indicator is currently enabled or not.
     */
    suspend fun isTypingIndicatorEnabled(): Flow<Boolean>

    /**
     * Persist the flag to indicate whether user's typing indicator global flag is enabled or not.
     */
    suspend fun persistTypingIndicator(enabled: Boolean)

    suspend fun persistGuestRoomLinkFeatureFlag(status: Boolean, isStatusChanged: Boolean?)
    suspend fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity?
    suspend fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?>
    suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean>
    suspend fun persistScreenshotCensoring(enabled: Boolean)
}

@Suppress("TooManyFunctions")
internal class UserConfigDAOImpl internal constructor(
    private val metadataDAO: MetadataDAO
) : UserConfigDAO {

    override suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity? =
        metadataDAO.getSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())

    override suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatusEntity: TeamSettingsSelfDeletionStatusEntity
    ) {
        metadataDAO.putSerializable(
            key = SELF_DELETING_MESSAGES_KEY,
            value = teamSettingsSelfDeletionStatusEntity,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )
    }

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified() {
        metadataDAO.getSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())
            ?.copy(isStatusChanged = false)?.let { newValue ->
                metadataDAO.putSerializable(
                    SELF_DELETING_MESSAGES_KEY,
                    newValue,
                    TeamSettingsSelfDeletionStatusEntity.serializer()
                )
            }
    }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?> =
        metadataDAO.observeSerializable(SELF_DELETING_MESSAGES_KEY, TeamSettingsSelfDeletionStatusEntity.serializer())

    override suspend fun getMigrationConfiguration(): MLSMigrationEntity? =
        metadataDAO.getSerializable(MLS_MIGRATION_KEY, MLSMigrationEntity.serializer())

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationEntity) =
        metadataDAO.putSerializable(MLS_MIGRATION_KEY, configuration, MLSMigrationEntity.serializer())

    override suspend fun getSupportedProtocols(): Set<SupportedProtocolEntity>? =
        metadataDAO.getSerializable(SUPPORTED_PROTOCOLS_KEY, SetSerializer(SupportedProtocolEntity.serializer()))

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocolEntity>) =
        metadataDAO.putSerializable(SUPPORTED_PROTOCOLS_KEY, protocols, SetSerializer(SupportedProtocolEntity.serializer()))

    override suspend fun persistAppLockStatus(isEnforced: Boolean, inactivityTimeoutSecs: Second, isStatusChanged: Boolean?) {
        metadataDAO.putSerializable(
            APP_LOCK,
            AppLockConfigEntity(inactivityTimeoutSecs, isEnforced, isStatusChanged),
            AppLockConfigEntity.serializer()
        )
    }

    override suspend fun appLockStatus(): AppLockConfigEntity? = metadataDAO.getSerializable(
        APP_LOCK,
        AppLockConfigEntity.serializer()
    )

    override suspend fun appLockFlow(): Flow<AppLockConfigEntity?> = metadataDAO.observeSerializable(
        APP_LOCK,
        AppLockConfigEntity.serializer()
    )

    override suspend fun setTeamAppLockAsNotified() {
        metadataDAO.modifySerriedValue(
            APP_LOCK,
            { it.copy(isStatusChanged = false) },
            AppLockConfigEntity.serializer()
        )
    }

    override suspend fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?) {
        metadataDAO.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer()
        )
    }

    override suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity? = metadataDAO.getSerializable(
        FILE_SHARING,
        IsFileSharingEnabledEntity.serializer()
    )

    override suspend fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> = metadataDAO.observeSerializable(
        FILE_SHARING,
        IsFileSharingEnabledEntity.serializer()
    )

    override suspend fun setFileSharingAsNotified() {
        metadataDAO.modifySerriedValue(
            FILE_SHARING,
            { it.copy(isStatusChanged = false) },
            IsFileSharingEnabledEntity.serializer()
        )
    }

    override suspend fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?> = metadataDAO.observeSerializable(
        ENABLE_CLASSIFIED_DOMAINS,
        ClassifiedDomainsEntity.serializer()
    )

    override suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        metadataDAO.putSerializable(
            ENABLE_CLASSIFIED_DOMAINS,
            ClassifiedDomainsEntity(status, classifiedDomains),
            ClassifiedDomainsEntity.serializer()
        )
    }

    override suspend fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean) {
        metadataDAO.insertBooleanValue(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE, isRequired)
    }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Boolean = metadataDAO.getBooleanValue(
        REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE
    ) ?: false

    override suspend fun persistDefaultProtocol(protocol: SupportedProtocolEntity) {
        metadataDAO.putSerializable(DEFAULT_PROTOCOL, protocol, SupportedProtocolEntity.serializer())
    }

    override suspend fun defaultProtocol(): SupportedProtocolEntity = metadataDAO.getSerializable(
        DEFAULT_PROTOCOL,
        SupportedProtocolEntity.serializer()
    ) ?: SupportedProtocolEntity.PROTEUS

    override suspend fun persistConferenceCalling(enabled: Boolean) {
        metadataDAO.insertBooleanValue(ENABLE_CONFERENCE_CALLING, enabled)
    }

    override suspend fun isConferenceCallingEnabled(): Boolean = metadataDAO.getBooleanValue(
        ENABLE_CONFERENCE_CALLING
    ) ?: DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE

    override suspend fun areReadReceiptsEnabled(): Flow<Boolean> = metadataDAO.observeBooleanValue(
        ENABLE_READ_RECEIPTS,
        DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
    )

    override suspend fun persistReadReceipts(enabled: Boolean) {
        metadataDAO.insertBooleanValue(ENABLE_READ_RECEIPTS, enabled)
    }

    override suspend fun isTypingIndicatorEnabled(): Flow<Boolean> =
        metadataDAO.observeBooleanValue(ENABLE_TYPING_INDICATOR, true)

    override suspend fun persistTypingIndicator(enabled: Boolean) {
        metadataDAO.insertBooleanValue(ENABLE_TYPING_INDICATOR, enabled)
    }

    override suspend fun persistGuestRoomLinkFeatureFlag(status: Boolean, isStatusChanged: Boolean?) {
        metadataDAO.putSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity(status, isStatusChanged),
            IsGuestRoomLinkEnabledEntity.serializer()
        )
    }

    override suspend fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity? =
        metadataDAO.getSerializable(GUEST_ROOM_LINK, IsGuestRoomLinkEnabledEntity.serializer())

    override suspend fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?> = metadataDAO.observeSerializable(
        GUEST_ROOM_LINK,
        IsGuestRoomLinkEnabledEntity.serializer()
    )

    override suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean> =
        metadataDAO.observeBooleanValue(ENABLE_SCREENSHOT_CENSORING, false)

    override suspend fun persistScreenshotCensoring(enabled: Boolean) {
        metadataDAO.insertBooleanValue(ENABLE_SCREENSHOT_CENSORING, enabled)
    }

    private companion object {
        const val SELF_DELETING_MESSAGES_KEY = "SELF_DELETING_MESSAGES"
        const val MLS_MIGRATION_KEY = "MLS_MIGRATION"
        const val SUPPORTED_PROTOCOLS_KEY = "SUPPORTED_PROTOCOLS"
        const val FILE_SHARING = "file_sharing"
        const val GUEST_ROOM_LINK = "guest_room_link"
        const val ENABLE_CLASSIFIED_DOMAINS = "enable_classified_domains"
        const val ENABLE_CONFERENCE_CALLING = "enable_conference_calling"
        const val ENABLE_READ_RECEIPTS = "enable_read_receipts"
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
        const val REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE = "require_second_factor_password_challenge"
        const val ENABLE_SCREENSHOT_CENSORING = "enable_screenshot_censoring"
        const val ENABLE_TYPING_INDICATOR = "enable_typing_indicator"
        const val APP_LOCK = "app_lock"
        const val DEFAULT_PROTOCOL = "default_protocol"
    }
}
