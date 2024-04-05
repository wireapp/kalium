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

package com.wire.kalium.persistence.config

import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import com.wire.kalium.util.time.Second
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Suppress("TooManyFunctions")
interface UserConfigStorage {

    /**
     * save flag from the user settings to enforce and disable App Lock
     */
    fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Second,
        isStatusChanged: Boolean?
    )

    /**
     * get the saved flag to know if App Lock is enforced or not
     */
    fun appLockStatus(): AppLockConfigEntity?

    /**
     * returns a Flow of the saved App Lock status
     */
    fun appLockFlow(): Flow<AppLockConfigEntity?>

    fun setTeamAppLockAsNotified()

    /**
     * Save flag from the file sharing api, and if the status changes
     */
    fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    /**
     * Get the saved flag that been saved to know if the file sharing is enabled or not with the flag
     * to know if there was a status change
     */
    fun isFileSharingEnabled(): IsFileSharingEnabledEntity?

    /**
     * Returns the Flow of file sharing status
     */
    fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

    fun setFileSharingAsNotified()

    /**
     * Returns a Flow containing the status and list of classified domains
     */
    fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity>

    /**
     *Save the flag and list of trusted domains
     */
    fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>)

    /**
     * Saves the flag that indicates whether a 2FA challenge is
     * required for some operations such as:
     * Login, Create Account, Register Client, etc.
     * @see isSecondFactorPasswordChallengeRequired
     */
    fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean)

    /**
     * Checks if the 2FA challenge is
     * required for some operations such as:
     * Login, Create Account, Register Client, etc.
     * @see persistSecondFactorPasswordChallengeStatus
     */
    fun isSecondFactorPasswordChallengeRequired(): Boolean

    /**
     * Save default protocol to use
     */
    fun persistDefaultProtocol(protocol: SupportedProtocolEntity)

    /**
     * Gets default protocol to use. Defaults to PROTEUS if not default protocol has been saved.
     */
    fun defaultProtocol(): SupportedProtocolEntity

    /**
     * Save flag from the user settings to enable and disable MLS
     */
    fun enableMLS(enabled: Boolean)

    /**
     * Get the saved flag to know if MLS enabled or not
     */
    fun isMLSEnabled(): Boolean

    /**
     * Save MLSE2EISetting
     */
    fun setE2EISettings(settingEntity: E2EISettingsEntity?)

    /**
     * Get MLSE2EISetting
     */
    fun getE2EISettings(): E2EISettingsEntity?

    /**
     * Get Flow of the saved MLSE2EISetting
     */
    fun e2EISettingsFlow(): Flow<E2EISettingsEntity?>

    /**
     * Save flag from user settings to enable or disable Conference Calling
     */
    fun persistConferenceCalling(enabled: Boolean)

    /**
     * Get the saved flag to know if Conference Calling is enabled or not
     */
    fun isConferenceCallingEnabled(): Boolean

    /**
     * Get the saved flag to know whether user's Read Receipts are currently enabled or not
     */
    fun areReadReceiptsEnabled(): Flow<Boolean>

    /**
     * Persist the flag to indicate if user's Read Receipts are enabled or not.
     */
    fun persistReadReceipts(enabled: Boolean)

    /**
     * Get the saved global flag to know whether user's typing indicator is currently enabled or not.
     */
    fun isTypingIndicatorEnabled(): Flow<Boolean>

    /**
     * Persist the flag to indicate whether user's typing indicator global flag is enabled or not.
     */
    fun persistTypingIndicator(enabled: Boolean)

    fun persistGuestRoomLinkFeatureFlag(status: Boolean, isStatusChanged: Boolean?)
    fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity?
    fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?>
    fun isScreenshotCensoringEnabledFlow(): Flow<Boolean>
    fun persistScreenshotCensoring(enabled: Boolean)
    fun setIfAbsentE2EINotificationTime(timeStamp: Long)
    fun getE2EINotificationTime(): Long?
    fun e2EINotificationTimeFlow(): Flow<Long?>
    fun updateE2EINotificationTime(timeStamp: Long)
    fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean)
    fun getShouldFetchE2EITrustAnchorHasRun(): Boolean
}

@Serializable
data class IsFileSharingEnabledEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

@Serializable
data class ClassifiedDomainsEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("trustedDomains") val trustedDomains: List<String>,
)

@Serializable
data class IsGuestRoomLinkEnabledEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

@Serializable
data class TeamSettingsSelfDeletionStatusEntity(
    @SerialName("selfDeletionTimer") val selfDeletionTimerEntity: SelfDeletionTimerEntity,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

@Serializable
data class E2EISettingsEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("discoverUrl") val discoverUrl: String?,
    @SerialName("gracePeriodEndMs") val gracePeriodEndMs: Long?,
)

@Serializable
data class AppLockConfigEntity(
    @SerialName("inactivityTimeoutSecs") val inactivityTimeoutSecs: Second,
    @SerialName("enforceAppLock") val enforceAppLock: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

@Serializable
data class LegalHoldRequestEntity(
    @SerialName("clientId") val clientId: String,
    @SerialName("lastPrekey") val lastPreKey: LastPreKey,
)

@Serializable
data class LastPreKey(
    @SerialName("id") val id: Int,
    @SerialName("key") val key: String,
)

@Serializable
data class CRLUrlExpirationList(
    @SerialName("crl_with_expiration_list") val cRLWithExpirationList: List<CRLWithExpiration>
)

@Serializable
data class CRLWithExpiration(
    @SerialName("url") val url: String,
    @SerialName("expiration") val expiration: ULong
)

@Serializable
sealed class SelfDeletionTimerEntity {

    @Serializable
    @SerialName("disabled")
    data object Disabled : SelfDeletionTimerEntity()

    @Serializable
    @SerialName("enabled")
    data object Enabled : SelfDeletionTimerEntity()

    @Serializable
    @SerialName("enforced")
    data class Enforced(val enforcedDuration: Duration) : SelfDeletionTimerEntity()
}

@Serializable
data class MLSMigrationEntity(
    @Serializable val status: Boolean,
    @Serializable val startTime: Instant?,
    @Serializable val endTime: Instant?,
)

@Suppress("TooManyFunctions")
class UserConfigStorageImpl(
    private val kaliumPreferences: KaliumPreferences
) : UserConfigStorage {

    private val areReadReceiptsEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val isTypingIndicatorEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val isFileSharingEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val isClassifiedDomainsEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val isGuestRoomLinkEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val e2EIFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val e2EINotificationFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val isScreenshotCensoringEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val appLockFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val legalHoldRequestFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    override fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Second,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            APP_LOCK,
            AppLockConfigEntity(inactivityTimeoutSecs, isEnforced, isStatusChanged),
            AppLockConfigEntity.serializer(),
        ).also {
            appLockFlow.tryEmit(Unit)
        }
    }

    override fun setTeamAppLockAsNotified() {
        val newValue =
            kaliumPreferences.getSerializable(APP_LOCK, AppLockConfigEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        kaliumPreferences.putSerializable(
            APP_LOCK,
            newValue,
            AppLockConfigEntity.serializer()
        ).also {
            appLockFlow.tryEmit(Unit)
        }
    }

    override fun appLockStatus(): AppLockConfigEntity? =
        kaliumPreferences.getSerializable(APP_LOCK, AppLockConfigEntity.serializer())

    override fun appLockFlow(): Flow<AppLockConfigEntity?> = appLockFlow.map {
        appLockStatus()
    }.onStart {
        emit(appLockStatus())
    }

    override fun persistFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer()
        ).also {
            isFileSharingEnabledFlow.tryEmit(Unit)
        }
    }

    override fun isFileSharingEnabled(): IsFileSharingEnabledEntity? =
        kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> =
        isFileSharingEnabledFlow
            .map { isFileSharingEnabled() }
            .onStart { emit(isFileSharingEnabled()) }
            .distinctUntilChanged()

    override fun setFileSharingAsNotified() {
        val newValue =
            kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            newValue,
            IsFileSharingEnabledEntity.serializer()
        ).also {
            isFileSharingEnabledFlow.tryEmit(Unit)
        }
    }

    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity> {
        return isClassifiedDomainsEnabledFlow
            .map {
                kaliumPreferences.getSerializable(
                    ENABLE_CLASSIFIED_DOMAINS,
                    ClassifiedDomainsEntity.serializer()
                )!!
            }.onStart {
                emit(
                    kaliumPreferences.getSerializable(
                        ENABLE_CLASSIFIED_DOMAINS,
                        ClassifiedDomainsEntity.serializer()
                    )!!
                )
            }.distinctUntilChanged()
    }

    override fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        kaliumPreferences.putSerializable(
            ENABLE_CLASSIFIED_DOMAINS,
            ClassifiedDomainsEntity(status, classifiedDomains),
            ClassifiedDomainsEntity.serializer()
        ).also {
            isClassifiedDomainsEnabledFlow.tryEmit(Unit)
        }
    }

    override fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean) {
        kaliumPreferences.putBoolean(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE, isRequired)
    }

    override fun isSecondFactorPasswordChallengeRequired(): Boolean =
        kaliumPreferences.getBoolean(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE, false)

    override fun persistDefaultProtocol(protocol: SupportedProtocolEntity) {
        kaliumPreferences.putString(DEFAULT_PROTOCOL, protocol.name)
    }

    override fun defaultProtocol(): SupportedProtocolEntity =
        kaliumPreferences.getString(DEFAULT_PROTOCOL)?.let { SupportedProtocolEntity.valueOf(it) }
            ?: SupportedProtocolEntity.PROTEUS

    override fun enableMLS(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_MLS, enabled)
    }

    override fun isMLSEnabled(): Boolean = kaliumPreferences.getBoolean(ENABLE_MLS, false)

    override fun setE2EISettings(settingEntity: E2EISettingsEntity?) {
        if (settingEntity == null) {
            kaliumPreferences.remove(E2EI_SETTINGS)
        } else {
            kaliumPreferences.putSerializable(
                E2EI_SETTINGS,
                settingEntity,
                E2EISettingsEntity.serializer()
            ).also {
                e2EIFlow.tryEmit(Unit)
            }
        }
    }

    override fun getE2EISettings(): E2EISettingsEntity? {
        return kaliumPreferences.getSerializable(E2EI_SETTINGS, E2EISettingsEntity.serializer())
    }

    override fun e2EISettingsFlow(): Flow<E2EISettingsEntity?> = e2EIFlow
        .map { getE2EISettings() }
        .onStart { emit(getE2EISettings()) }
        .distinctUntilChanged()

    override fun setIfAbsentE2EINotificationTime(timeStamp: Long) {
        getE2EINotificationTime().let { current ->
            if (current == null || current <= 0)
                kaliumPreferences.putLong(E2EI_NOTIFICATION_TIME, timeStamp).also { e2EINotificationFlow.tryEmit(Unit) }
        }
    }

    override fun updateE2EINotificationTime(timeStamp: Long) {
        kaliumPreferences.putLong(E2EI_NOTIFICATION_TIME, timeStamp).also { e2EINotificationFlow.tryEmit(Unit) }
    }

    override fun getE2EINotificationTime(): Long? {
        return kaliumPreferences.getLong(E2EI_NOTIFICATION_TIME)
    }

    override fun e2EINotificationTimeFlow(): Flow<Long?> = e2EINotificationFlow
        .map { getE2EINotificationTime() }
        .onStart { emit(getE2EINotificationTime()) }
        .distinctUntilChanged()

    override fun persistConferenceCalling(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_CONFERENCE_CALLING, enabled)
    }

    override fun isConferenceCallingEnabled(): Boolean =
        kaliumPreferences.getBoolean(
            ENABLE_CONFERENCE_CALLING,
            DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
        )

    override fun areReadReceiptsEnabled(): Flow<Boolean> = areReadReceiptsEnabledFlow
        .map { kaliumPreferences.getBoolean(ENABLE_READ_RECEIPTS, true) }
        .onStart { emit(kaliumPreferences.getBoolean(ENABLE_READ_RECEIPTS, true)) }
        .distinctUntilChanged()

    override fun persistReadReceipts(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_READ_RECEIPTS, enabled).also {
            areReadReceiptsEnabledFlow.tryEmit(Unit)
        }
    }

    override fun isTypingIndicatorEnabled(): Flow<Boolean> = isTypingIndicatorEnabledFlow
        .map { kaliumPreferences.getBoolean(ENABLE_TYPING_INDICATOR, true) }
        .onStart { emit(kaliumPreferences.getBoolean(ENABLE_TYPING_INDICATOR, true)) }
        .distinctUntilChanged()

    override fun persistTypingIndicator(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_TYPING_INDICATOR, enabled).also {
            isTypingIndicatorEnabledFlow.tryEmit(Unit)
        }
    }

    override fun persistGuestRoomLinkFeatureFlag(
        status: Boolean,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity(status, isStatusChanged),
            IsGuestRoomLinkEnabledEntity.serializer()
        ).also {
            isGuestRoomLinkEnabledFlow.tryEmit(Unit)
        }
    }

    override fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity? =
        kaliumPreferences.getSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity.serializer()
        )

    override fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?> =
        isGuestRoomLinkEnabledFlow
            .map { isGuestRoomLinkEnabled() }
            .onStart { emit(isGuestRoomLinkEnabled()) }
            .distinctUntilChanged()

    override fun isScreenshotCensoringEnabledFlow(): Flow<Boolean> =
        isScreenshotCensoringEnabledFlow
            .map { kaliumPreferences.getBoolean(ENABLE_SCREENSHOT_CENSORING, false) }
            .onStart { emit(kaliumPreferences.getBoolean(ENABLE_SCREENSHOT_CENSORING, false)) }
            .distinctUntilChanged()

    override fun persistScreenshotCensoring(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_SCREENSHOT_CENSORING, enabled).also {
            isScreenshotCensoringEnabledFlow.tryEmit(Unit)
        }
    }

    override fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean) {
        kaliumPreferences.putBoolean(SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS, shouldFetch)
    }

    override fun getShouldFetchE2EITrustAnchorHasRun(): Boolean =
        kaliumPreferences.getBoolean(SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS, true)

    private companion object {
        const val FILE_SHARING = "file_sharing"
        const val GUEST_ROOM_LINK = "guest_room_link"
        const val ENABLE_CLASSIFIED_DOMAINS = "enable_classified_domains"
        const val ENABLE_MLS = "enable_mls"
        const val E2EI_SETTINGS = "end_to_end_identity_settings"
        const val E2EI_NOTIFICATION_TIME = "end_to_end_identity_notification_time"
        const val ENABLE_CONFERENCE_CALLING = "enable_conference_calling"
        const val ENABLE_READ_RECEIPTS = "enable_read_receipts"
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
        const val REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE =
            "require_second_factor_password_challenge"
        const val ENABLE_SCREENSHOT_CENSORING = "enable_screenshot_censoring"
        const val ENABLE_TYPING_INDICATOR = "enable_typing_indicator"
        const val APP_LOCK = "app_lock"
        const val DEFAULT_PROTOCOL = "default_protocol"
        const val SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS = "should_fetch_e2ei_trust_anchors"
    }
}
