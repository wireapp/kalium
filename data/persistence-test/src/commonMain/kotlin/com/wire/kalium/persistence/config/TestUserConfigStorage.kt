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

import com.russhwolf.settings.MapSettings
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import kotlinx.coroutines.flow.Flow

fun inMemoryUserConfigStorage(): UserConfigStorage = UserConfigStorageImpl(
    KaliumPreferencesSettings(MapSettings())
)

class FakeUserConfigStorage(
    val backingStorage: MutableMap<String, Any> = mutableMapOf()
) : UserConfigStorage {
    override suspend fun persistAppLockStatus(
        isEnforced: Boolean,
        inactivityTimeoutSecs: Int,
        isStatusChanged: Boolean?
    ) {
        backingStorage["appLockStatus"] = AppLockConfigEntity(
            inactivityTimeoutSecs = 60,
            enforceAppLock = true,
            isStatusChanged = false,
        )
    }

    override suspend fun appLockStatus(): AppLockConfigEntity? {
        return backingStorage["appLockStatus"] as? AppLockConfigEntity
    }

    override fun appLockFlow(): Flow<AppLockConfigEntity?> {
        throw NotImplementedError()
    }

    override suspend fun setTeamAppLockAsNotified() {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity? {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun setFileSharingAsNotified() {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity?> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistSecondFactorPasswordChallengeStatus(isRequired: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Boolean {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistDefaultProtocol(protocol: SupportedProtocolEntity) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun defaultProtocol(): SupportedProtocolEntity {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun enableMLS(enabled: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isMLSEnabled(): Boolean {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun setE2EISettings(settingEntity: E2EISettingsEntity?) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun getE2EISettings(): E2EISettingsEntity? {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override fun e2EISettingsFlow(): Flow<E2EISettingsEntity?> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistConferenceCalling(enabled: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isConferenceCallingEnabled(): Boolean {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isConferenceCallingEnabledFlow(): Flow<Boolean> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistUseSftForOneOnOneCalls(shouldUse: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun shouldUseSftForOneOnOneCalls(): Boolean {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun areReadReceiptsEnabled(): Flow<Boolean> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistReadReceipts(enabled: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isTypingIndicatorEnabled(): Flow<Boolean> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistTypingIndicator(enabled: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistGuestRoomLinkFeatureFlag(status: Boolean, isStatusChanged: Boolean?) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isGuestRoomLinkEnabled(): IsGuestRoomLinkEnabledEntity? {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override fun isGuestRoomLinkEnabledFlow(): Flow<IsGuestRoomLinkEnabledEntity?> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun isScreenshotCensoringEnabledFlow(): Flow<Boolean> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun persistScreenshotCensoring(enabled: Boolean) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun setIfAbsentE2EINotificationTime(timeStamp: Long) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun getE2EINotificationTime(): Long? {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun e2EINotificationTimeFlow(): Flow<Long?> {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

    override suspend fun updateE2EINotificationTime(timeStamp: Long) {
        throw NotImplementedError("Implement your fake logic if needed using the map provided.")
    }

}
