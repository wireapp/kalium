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

import com.wire.kalium.persistence.config.UserConfigStorage.Companion.DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
import com.wire.kalium.persistence.config.UserConfigStorage.Companion.DEFAULT_USE_SFT_FOR_ONE_ON_ONE_CALLS_VALUE
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.APP_LOCK
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.DEFAULT_PROTOCOL
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.E2EI_NOTIFICATION_TIME
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.E2EI_SETTINGS
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_CLASSIFIED_DOMAINS
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_CONFERENCE_CALLING
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_MLS
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_READ_RECEIPTS
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_SCREENSHOT_CENSORING
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.ENABLE_TYPING_INDICATOR
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.FILE_SHARING
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.GUEST_ROOM_LINK
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE
import com.wire.kalium.persistence.config.UserConfigStorage.UserPreferences.USE_SFT_FOR_ONE_ON_ONE_CALLS
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
    fun appLockFlow(): Flow<AppLockConfigEntity?>

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
    fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

    suspend fun setFileSharingAsNotified()

    /**
     * Returns a Flow containing the status and list of classified domains
     */
    fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?>

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
     * Save flag from the user settings to enable and disable MLS
     */
    suspend fun enableMLS(enabled: Boolean)

    /**
     * Get the saved flag to know if MLS enabled or not
     */
    suspend fun isMLSEnabled(): Boolean

    /**
     * Save MLSE2EISetting
     */
    suspend fun setE2EISettings(settingEntity: E2EISettingsEntity?)

    /**
     * Get MLSE2EISetting
     */
    suspend fun getE2EISettings(): E2EISettingsEntity?

    /**
     * Get Flow of the saved MLSE2EISetting
     */
    fun e2EISettingsFlow(): Flow<E2EISettingsEntity?>

    /**
     * Save flag from user settings to enable or disable Conference Calling
     */
    suspend fun persistConferenceCalling(enabled: Boolean)

    /**
     * Get the saved flag to know if Conference Calling is enabled or not
     */
    suspend fun isConferenceCallingEnabled(): Boolean

    /**
     * Get a flow of saved flag to know if conference calling is enabled or not
     */
    suspend fun isConferenceCallingEnabledFlow(): Flow<Boolean>

    suspend fun persistUseSftForOneOnOneCalls(shouldUse: Boolean)

    suspend fun shouldUseSftForOneOnOneCalls(): Boolean

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
    fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?>
    suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean>
    suspend fun persistScreenshotCensoring(enabled: Boolean)
    suspend fun setIfAbsentE2EINotificationTime(timeStamp: Long)
    suspend fun getE2EINotificationTime(): Long?
    suspend fun e2EINotificationTimeFlow(): Flow<Long?>
    suspend fun updateE2EINotificationTime(timeStamp: Long)

    enum class UserPreferences(val key: String) {
        FILE_SHARING("file_sharing"),
        GUEST_ROOM_LINK("guest_room_link"),
        ENABLE_CLASSIFIED_DOMAINS("enable_classified_domains"),
        ENABLE_MLS("enable_mls"),
        E2EI_SETTINGS("end_to_end_identity_settings"),
        E2EI_NOTIFICATION_TIME("end_to_end_identity_notification_time"),
        ENABLE_CONFERENCE_CALLING("enable_conference_calling"),
        USE_SFT_FOR_ONE_ON_ONE_CALLS("use_sft_for_one_on_one_calls"),
        ENABLE_READ_RECEIPTS("enable_read_receipts"),
        REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE("require_second_factor_password_challenge"),
        ENABLE_SCREENSHOT_CENSORING("enable_screenshot_censoring"),
        ENABLE_TYPING_INDICATOR("enable_typing_indicator"),
        APP_LOCK("app_lock"),
        DEFAULT_PROTOCOL("default_protocol")
    }

    companion object {
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
        const val DEFAULT_USE_SFT_FOR_ONE_ON_ONE_CALLS_VALUE = false
    }
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
    @SerialName("shouldUseProxy") val shouldUseProxy: Boolean?,
    @SerialName("crlProxy") val crlProxy: String?,
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

@Serializable
data class WireCellsConfigEntity(
    @Serializable val backendUrl: String?,
    @Serializable val collabora: String,
    @Serializable val teamQuotaBytes: Long?,
)

@Deprecated("Use UserPrefsDAO instead. This will be removed in future versions.", ReplaceWith("UserPrefsDAO"))
@Suppress("TooManyFunctions")
class UserConfigStorageImpl constructor(
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

    private val conferenceCallingEnabledFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    private val legalHoldRequestFlow =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    override suspend fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Second,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            APP_LOCK.key,
            AppLockConfigEntity(inactivityTimeoutSecs, isEnforced, isStatusChanged),
            AppLockConfigEntity.serializer(),
        ).also {
            appLockFlow.tryEmit(Unit)
        }
    }

    override suspend fun setTeamAppLockAsNotified() {
        val newValue =
            kaliumPreferences.getSerializable(APP_LOCK.key, AppLockConfigEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        kaliumPreferences.putSerializable(
            APP_LOCK.key,
            newValue,
            AppLockConfigEntity.serializer()
        ).also {
            appLockFlow.tryEmit(Unit)
        }
    }

    override suspend fun appLockStatus(): AppLockConfigEntity? =
        kaliumPreferences.getSerializable(APP_LOCK.key, AppLockConfigEntity.serializer())

    override fun appLockFlow(): Flow<AppLockConfigEntity?> = appLockFlow.map {
        appLockStatus()
    }.onStart {
        emit(appLockStatus())
    }

    override suspend fun persistFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            FILE_SHARING.key,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer()
        ).also {
            isFileSharingEnabledFlow.tryEmit(Unit)
        }
    }

    override suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity? =
        kaliumPreferences.getSerializable(FILE_SHARING.key, IsFileSharingEnabledEntity.serializer())

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> =
        isFileSharingEnabledFlow
            .map { isFileSharingEnabled() }
            .onStart { emit(isFileSharingEnabled()) }
            .distinctUntilChanged()

    override suspend fun setFileSharingAsNotified() {
        val newValue =
            kaliumPreferences.getSerializable(FILE_SHARING.key, IsFileSharingEnabledEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        kaliumPreferences.putSerializable(
            FILE_SHARING.key,
            newValue,
            IsFileSharingEnabledEntity.serializer()
        ).also {
            isFileSharingEnabledFlow.tryEmit(Unit)
        }
    }

    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?> {
        return isClassifiedDomainsEnabledFlow
            .map {
                kaliumPreferences.getSerializable(
                    ENABLE_CLASSIFIED_DOMAINS.key,
                    ClassifiedDomainsEntity.serializer()
                )!!
            }.onStart {
                emit(
                    kaliumPreferences.getSerializable(
                        ENABLE_CLASSIFIED_DOMAINS.key,
                        ClassifiedDomainsEntity.serializer()
                    )!!
                )
            }.distinctUntilChanged()
    }

    override suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        kaliumPreferences.putSerializable(
            ENABLE_CLASSIFIED_DOMAINS.key,
            ClassifiedDomainsEntity(status, classifiedDomains),
            ClassifiedDomainsEntity.serializer()
        ).also {
            isClassifiedDomainsEnabledFlow.tryEmit(Unit)
        }
    }

    override suspend fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean) {
        kaliumPreferences.putBoolean(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE.key, isRequired)
    }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Boolean =
        kaliumPreferences.getBoolean(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE.key, false)

    override suspend fun persistDefaultProtocol(protocol: SupportedProtocolEntity) {
        kaliumPreferences.putString(DEFAULT_PROTOCOL.key, protocol.name)
    }

    override suspend fun defaultProtocol(): SupportedProtocolEntity =
        kaliumPreferences.getString(DEFAULT_PROTOCOL.key)?.let { SupportedProtocolEntity.valueOf(it) }
            ?: SupportedProtocolEntity.PROTEUS

    override suspend fun enableMLS(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_MLS.key, enabled)
    }

    override suspend fun isMLSEnabled(): Boolean = kaliumPreferences.getBoolean(ENABLE_MLS.key, false)

    override suspend fun setE2EISettings(settingEntity: E2EISettingsEntity?) {
        if (settingEntity == null) {
            kaliumPreferences.remove(E2EI_SETTINGS.key)
        } else {
            kaliumPreferences.putSerializable(
                E2EI_SETTINGS.key,
                settingEntity,
                E2EISettingsEntity.serializer()
            ).also {
                e2EIFlow.tryEmit(Unit)
            }
        }
    }

    override suspend fun getE2EISettings(): E2EISettingsEntity? {
        return kaliumPreferences.getSerializable(E2EI_SETTINGS.key, E2EISettingsEntity.serializer())
    }

    override fun e2EISettingsFlow(): Flow<E2EISettingsEntity?> = e2EIFlow
        .map { getE2EISettings() }
        .onStart { emit(getE2EISettings()) }
        .distinctUntilChanged()

    override suspend fun setIfAbsentE2EINotificationTime(timeStamp: Long) {
        getE2EINotificationTime().let { current ->
            if (current == null || current <= 0)
                kaliumPreferences.putLong(E2EI_NOTIFICATION_TIME.key, timeStamp).also { e2EINotificationFlow.tryEmit(Unit) }
        }
    }

    override suspend fun updateE2EINotificationTime(timeStamp: Long) {
        kaliumPreferences.putLong(E2EI_NOTIFICATION_TIME.key, timeStamp).also { e2EINotificationFlow.tryEmit(Unit) }
    }

    override suspend fun getE2EINotificationTime(): Long? {
        return kaliumPreferences.getLong(E2EI_NOTIFICATION_TIME.key)
    }

    override suspend fun e2EINotificationTimeFlow(): Flow<Long?> = e2EINotificationFlow
        .map { getE2EINotificationTime() }
        .onStart { emit(getE2EINotificationTime()) }
        .distinctUntilChanged()

    override suspend fun persistConferenceCalling(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_CONFERENCE_CALLING.key, enabled)
        conferenceCallingEnabledFlow.tryEmit(Unit)
    }

    override suspend fun isConferenceCallingEnabled(): Boolean =
        kaliumPreferences.getBoolean(
            ENABLE_CONFERENCE_CALLING.key,
            DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
        )

    override suspend fun isConferenceCallingEnabledFlow(): Flow<Boolean> = conferenceCallingEnabledFlow
        .map { isConferenceCallingEnabled() }
        .onStart { emit(isConferenceCallingEnabled()) }

    override suspend fun persistUseSftForOneOnOneCalls(shouldUse: Boolean) {
        kaliumPreferences.putBoolean(USE_SFT_FOR_ONE_ON_ONE_CALLS.key, shouldUse)
    }

    override suspend fun shouldUseSftForOneOnOneCalls(): Boolean =
        kaliumPreferences.getBoolean(
            USE_SFT_FOR_ONE_ON_ONE_CALLS.key,
            DEFAULT_USE_SFT_FOR_ONE_ON_ONE_CALLS_VALUE
        )

    override suspend fun areReadReceiptsEnabled(): Flow<Boolean> = areReadReceiptsEnabledFlow
        .map { kaliumPreferences.getBoolean(ENABLE_READ_RECEIPTS.key, true) }
        .onStart { emit(kaliumPreferences.getBoolean(ENABLE_READ_RECEIPTS.key, true)) }
        .distinctUntilChanged()

    override suspend fun persistReadReceipts(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_READ_RECEIPTS.key, enabled).also {
            areReadReceiptsEnabledFlow.tryEmit(Unit)
        }
    }

    override suspend fun isTypingIndicatorEnabled(): Flow<Boolean> = isTypingIndicatorEnabledFlow
        .map { kaliumPreferences.getBoolean(ENABLE_TYPING_INDICATOR.key, true) }
        .onStart { emit(kaliumPreferences.getBoolean(ENABLE_TYPING_INDICATOR.key, true)) }
        .distinctUntilChanged()

    override suspend fun persistTypingIndicator(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_TYPING_INDICATOR.key, enabled).also {
            isTypingIndicatorEnabledFlow.tryEmit(Unit)
        }
    }

    override suspend fun persistGuestRoomLinkFeatureFlag(
        status: Boolean,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            GUEST_ROOM_LINK.key,
            IsGuestRoomLinkEnabledEntity(status, isStatusChanged),
            IsGuestRoomLinkEnabledEntity.serializer()
        ).also {
            isGuestRoomLinkEnabledFlow.tryEmit(Unit)
        }
    }

    override suspend fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity? =
        kaliumPreferences.getSerializable(
            GUEST_ROOM_LINK.key,
            IsGuestRoomLinkEnabledEntity.serializer()
        )

    override fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?> =
        isGuestRoomLinkEnabledFlow
            .map { isGuestRoomLinkEnabled() }
            .onStart { emit(isGuestRoomLinkEnabled()) }
            .distinctUntilChanged()

    override suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean> =
        isScreenshotCensoringEnabledFlow
            .map { kaliumPreferences.getBoolean(ENABLE_SCREENSHOT_CENSORING.key, false) }
            .onStart { emit(kaliumPreferences.getBoolean(ENABLE_SCREENSHOT_CENSORING.key, false)) }
            .distinctUntilChanged()

    override suspend fun persistScreenshotCensoring(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_SCREENSHOT_CENSORING.key, enabled).also {
            isScreenshotCensoringEnabledFlow.tryEmit(Unit)
        }
    }
}
