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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.config.AppLockConfigEntity
import com.wire.kalium.persistence.config.ClassifiedDomainsEntity
import com.wire.kalium.persistence.config.E2EISettingsEntity
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.IsGuestRoomLinkEnabledEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.APP_LOCK
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.DEFAULT_PROTOCOL
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.DEFAULT_USE_SFT_FOR_ONE_ON_ONE_CALLS_VALUE
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.E2EI_NOTIFICATION_TIME
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.E2EI_SETTINGS
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_CLASSIFIED_DOMAINS
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_CONFERENCE_CALLING
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_MLS
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_READ_RECEIPTS
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_SCREENSHOT_CENSORING
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.ENABLE_TYPING_INDICATOR
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.FILE_SHARING
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.GUEST_ROOM_LINK
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE
import com.wire.kalium.persistence.config.UserConfigStorageImpl.Companion.USE_SFT_FOR_ONE_ON_ONE_CALLS
import com.wire.kalium.util.time.Second
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
internal class UserPrefsDAO constructor(
    private val metadataDAO: MetadataDAO,
) : UserConfigStorage {
    override suspend fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Second,
        isStatusChanged: Boolean?
    ) {
        metadataDAO.putSerializable(
            APP_LOCK,
            AppLockConfigEntity(inactivityTimeoutSecs, isEnforced, isStatusChanged),
            AppLockConfigEntity.serializer(),
        )
    }

    override suspend fun appLockStatus(): AppLockConfigEntity? {
        return metadataDAO.getSerializable(APP_LOCK, AppLockConfigEntity.serializer())
    }

    override fun appLockFlow(): Flow<AppLockConfigEntity?> {
        return metadataDAO.observeSerializable(APP_LOCK, AppLockConfigEntity.serializer())
    }

    override suspend fun setTeamAppLockAsNotified() {
        val newValue =
            metadataDAO.getSerializable(APP_LOCK, AppLockConfigEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        metadataDAO.putSerializable(
            APP_LOCK,
            newValue,
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

    override suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity? {
        return metadataDAO.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())
    }

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> {
        return metadataDAO.observeSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())
    }

    override suspend fun setFileSharingAsNotified() {
        val newValue =
            metadataDAO.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())
                ?.copy(isStatusChanged = false)
                ?: return
        metadataDAO.putSerializable(
            FILE_SHARING,
            newValue,
            IsFileSharingEnabledEntity.serializer()
        )
    }

    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?> {
        return metadataDAO.observeSerializable(ENABLE_CLASSIFIED_DOMAINS, ClassifiedDomainsEntity.serializer())
    }

    override suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        metadataDAO.putSerializable(
            ENABLE_CLASSIFIED_DOMAINS,
            ClassifiedDomainsEntity(status, classifiedDomains),
            ClassifiedDomainsEntity.serializer()
        )
    }

    override suspend fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean) {
        metadataDAO.insertValue(value = isRequired.toString(), key = REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE)
    }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Boolean {
        return metadataDAO.valueByKey(REQUIRE_SECOND_FACTOR_PASSWORD_CHALLENGE)?.toBoolean() ?: false
    }

    override suspend fun persistDefaultProtocol(protocol: SupportedProtocolEntity) {
        metadataDAO.insertValue(value = protocol.name, key = DEFAULT_PROTOCOL)
    }

    override suspend fun defaultProtocol(): SupportedProtocolEntity {
        return metadataDAO.valueByKey(DEFAULT_PROTOCOL)?.let { SupportedProtocolEntity.valueOf(it) }
            ?: SupportedProtocolEntity.PROTEUS
    }

    override suspend fun enableMLS(enabled: Boolean) {
        metadataDAO.insertValue(value = enabled.toString(), key = ENABLE_MLS)
    }

    override suspend fun isMLSEnabled(): Boolean {
        return metadataDAO.valueByKey(ENABLE_MLS)?.toBoolean() ?: false
    }

    override suspend fun setE2EISettings(settingEntity: E2EISettingsEntity?) {
        if (settingEntity == null) {
            metadataDAO.deleteValue(E2EI_SETTINGS)
        } else {
            metadataDAO.putSerializable(
                E2EI_SETTINGS,
                settingEntity,
                E2EISettingsEntity.serializer()
            )
        }
    }

    override suspend fun getE2EISettings(): E2EISettingsEntity? {
        return metadataDAO.getSerializable(E2EI_SETTINGS, E2EISettingsEntity.serializer())
    }

    override fun e2EISettingsFlow(): Flow<E2EISettingsEntity?> {
        return metadataDAO.observeSerializable(E2EI_SETTINGS, E2EISettingsEntity.serializer())
    }

    override suspend fun persistConferenceCalling(enabled: Boolean) {
        metadataDAO.insertValue(value = enabled.toString(), key = ENABLE_CONFERENCE_CALLING)
    }

    override suspend fun isConferenceCallingEnabled(): Boolean {
        return metadataDAO.valueByKey(ENABLE_CONFERENCE_CALLING)?.toBoolean() ?: DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
    }

    override suspend fun isConferenceCallingEnabledFlow(): Flow<Boolean> {
        return metadataDAO.valueByKeyFlow(ENABLE_CONFERENCE_CALLING).map { value ->
            value?.toBoolean() ?: DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
        }
    }

    override suspend fun persistUseSftForOneOnOneCalls(shouldUse: Boolean) {
        metadataDAO.insertValue(value = shouldUse.toString(), key = USE_SFT_FOR_ONE_ON_ONE_CALLS)
    }

    override suspend fun shouldUseSftForOneOnOneCalls(): Boolean {
        return metadataDAO.valueByKey(USE_SFT_FOR_ONE_ON_ONE_CALLS)?.toBoolean() ?: DEFAULT_USE_SFT_FOR_ONE_ON_ONE_CALLS_VALUE
    }

    override suspend fun areReadReceiptsEnabled(): Flow<Boolean> {
        return metadataDAO.valueByKeyFlow(ENABLE_READ_RECEIPTS).map {
            it?.toBoolean() ?: true
        }
    }

    override suspend fun persistReadReceipts(enabled: Boolean) {
        metadataDAO.insertValue(value = enabled.toString(), key = ENABLE_READ_RECEIPTS)
    }

    override suspend fun isTypingIndicatorEnabled(): Flow<Boolean> {
        return metadataDAO.valueByKeyFlow(ENABLE_TYPING_INDICATOR).map {
            it?.toBoolean() ?: true
        }
    }

    override suspend fun persistTypingIndicator(enabled: Boolean) {
        metadataDAO.insertValue(value = enabled.toString(), key = ENABLE_TYPING_INDICATOR)
    }

    override suspend fun persistGuestRoomLinkFeatureFlag(status: Boolean, isStatusChanged: Boolean?) {
        metadataDAO.putSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity(status, isStatusChanged),
            IsGuestRoomLinkEnabledEntity.serializer()
        )
    }

    override suspend fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity? {
        return metadataDAO.getSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity.serializer()
        )
    }

    override fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?> {
        return metadataDAO.observeSerializable(
            GUEST_ROOM_LINK,
            IsGuestRoomLinkEnabledEntity.serializer()
        )
    }

    override suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean> {
        return metadataDAO.valueByKeyFlow(ENABLE_SCREENSHOT_CENSORING).map {
            it?.toBoolean() ?: false
        }
    }

    override suspend fun persistScreenshotCensoring(enabled: Boolean) {
        metadataDAO.insertValue(value = enabled.toString(), key = ENABLE_SCREENSHOT_CENSORING)
    }

    override suspend fun setIfAbsentE2EINotificationTime(timeStamp: Long) {
        getE2EINotificationTime().let { current ->
            if (current == null || current <= 0)
                metadataDAO.insertValue(value = timeStamp.toString(), key = E2EI_NOTIFICATION_TIME)
        }
    }

    override suspend fun getE2EINotificationTime(): Long? {
        return metadataDAO.valueByKey(E2EI_NOTIFICATION_TIME)?.toLong()
    }

    override suspend fun e2EINotificationTimeFlow(): Flow<Long?> {
        return metadataDAO.valueByKeyFlow(E2EI_NOTIFICATION_TIME).map {
            it?.toLong()
        }
    }

    override suspend fun updateE2EINotificationTime(timeStamp: Long) {
        metadataDAO.insertValue(value = timeStamp.toString(), key = E2EI_NOTIFICATION_TIME)
    }
}
