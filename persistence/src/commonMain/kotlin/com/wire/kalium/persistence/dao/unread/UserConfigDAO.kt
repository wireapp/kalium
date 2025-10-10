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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.persistence.dao.unread

import com.wire.kalium.persistence.config.LastPreKey
import com.wire.kalium.persistence.config.LegalHoldRequestEntity
import com.wire.kalium.persistence.config.MLSMigrationEntity
import com.wire.kalium.persistence.config.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.model.SupportedCipherSuiteEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.SetSerializer

@Mockable
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
    suspend fun persistLegalHoldRequest(clientId: String, lastPreKeyId: Int, lastPreKey: String)
    suspend fun clearLegalHoldRequest()
    fun observeLegalHoldRequest(): Flow<LegalHoldRequestEntity?>
    suspend fun setLegalHoldChangeNotified(isNotified: Boolean)
    suspend fun observeLegalHoldChangeNotified(): Flow<Boolean?>
    suspend fun setCRLExpirationTime(url: String, timestamp: ULong)
    suspend fun getCRLsPerDomain(url: String): ULong?
    suspend fun observeCertificateExpirationTime(url: String): Flow<ULong?>
    suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean)
    suspend fun observeShouldNotifyForRevokedCertificate(): Flow<Boolean?>
    suspend fun setDefaultCipherSuite(cipherSuite: SupportedCipherSuiteEntity)
    suspend fun getDefaultCipherSuite(): SupportedCipherSuiteEntity?
    suspend fun setTrackingIdentifier(identifier: String)
    suspend fun getTrackingIdentifier(): String?
    suspend fun observeTrackingIdentifier(): Flow<String?>
    suspend fun setPreviousTrackingIdentifier(identifier: String)
    suspend fun getPreviousTrackingIdentifier(): String?
    suspend fun deletePreviousTrackingIdentifier()
    suspend fun getNextTimeForCallFeedback(): Long?
    suspend fun setNextTimeForCallFeedback(timestamp: Long)
    suspend fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean)
    suspend fun getShouldFetchE2EITrustAnchorHasRun(): Boolean
    suspend fun setMlsConversationsResetEnabled(enabled: Boolean)
    suspend fun getMlsConversationsResetEnabled(): Boolean
    suspend fun setAsyncNotificationsEnabled(isAsyncNotificationsEnabled: Boolean)
    suspend fun getAsyncNotificationsEnabled(): Boolean
    suspend fun setCellsEnabled(enabled: Boolean)
    suspend fun isCellsEnabled(): Boolean
    suspend fun setAppsEnabled(isAppsEnabled: Boolean)
    suspend fun getAppsEnabled(): Boolean
    suspend fun observeAppsEnabled(): Flow<Boolean>
    suspend fun setChatBubblesEnabled(enabled: Boolean)
    suspend fun isChatBubblesEnabled(): Boolean
    suspend fun setProfileQRCodeEnabled(enabled: Boolean)
    suspend fun isProfileQRCodeEnabled(): Boolean
    suspend fun setAssetAuditLogEnabled(enabled: Boolean)
    suspend fun isAssetAuditLogEnabled(): Boolean
}

@Suppress("TooManyFunctions")
internal class UserConfigDAOImpl internal constructor(
    private val metadataDAO: MetadataDAO
) : UserConfigDAO {

    override suspend fun getTeamSettingsSelfDeletionStatus(): TeamSettingsSelfDeletionStatusEntity? =
        metadataDAO.getSerializable(
            SELF_DELETING_MESSAGES_KEY,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )

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
        metadataDAO.getSerializable(
            SELF_DELETING_MESSAGES_KEY,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )
            ?.copy(isStatusChanged = false)?.let { newValue ->
                metadataDAO.putSerializable(
                    SELF_DELETING_MESSAGES_KEY,
                    newValue,
                    TeamSettingsSelfDeletionStatusEntity.serializer()
                )
            }
    }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<TeamSettingsSelfDeletionStatusEntity?> =
        metadataDAO.observeSerializable(
            SELF_DELETING_MESSAGES_KEY,
            TeamSettingsSelfDeletionStatusEntity.serializer()
        )

    override suspend fun getMigrationConfiguration(): MLSMigrationEntity? =
        metadataDAO.getSerializable(MLS_MIGRATION_KEY, MLSMigrationEntity.serializer())

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationEntity) =
        metadataDAO.putSerializable(
            MLS_MIGRATION_KEY,
            configuration,
            MLSMigrationEntity.serializer()
        )

    override suspend fun getSupportedProtocols(): Set<SupportedProtocolEntity>? =
        metadataDAO.getSerializable(
            SUPPORTED_PROTOCOLS_KEY,
            SetSerializer(SupportedProtocolEntity.serializer())
        )

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocolEntity>) =
        metadataDAO.putSerializable(
            SUPPORTED_PROTOCOLS_KEY,
            protocols,
            SetSerializer(SupportedProtocolEntity.serializer())
        )

    override suspend fun persistLegalHoldRequest(
        clientId: String,
        lastPreKeyId: Int,
        lastPreKey: String
    ) {
        metadataDAO.putSerializable(
            LEGAL_HOLD_REQUEST,
            LegalHoldRequestEntity(clientId, LastPreKey(lastPreKeyId, lastPreKey)),
            LegalHoldRequestEntity.serializer()
        )
    }

    override suspend fun clearLegalHoldRequest() {
        metadataDAO.deleteValue(LEGAL_HOLD_REQUEST)
    }

    override fun observeLegalHoldRequest(): Flow<LegalHoldRequestEntity?> =
        metadataDAO.observeSerializable(LEGAL_HOLD_REQUEST, LegalHoldRequestEntity.serializer())

    override suspend fun setLegalHoldChangeNotified(isNotified: Boolean) {
        metadataDAO.insertValue(isNotified.toString(), LEGAL_HOLD_CHANGE_NOTIFIED)
    }

    override suspend fun observeLegalHoldChangeNotified(): Flow<Boolean?> =
        metadataDAO.valueByKeyFlow(LEGAL_HOLD_CHANGE_NOTIFIED).map { it?.toBoolean() }

    override suspend fun setCRLExpirationTime(url: String, timestamp: ULong) {
        metadataDAO.insertValue(
            key = url,
            value = timestamp.toString()
        )
    }

    override suspend fun getCRLsPerDomain(url: String): ULong? =
        metadataDAO.valueByKey(url)?.toULongOrNull()

    override suspend fun observeCertificateExpirationTime(url: String): Flow<ULong?> =
        metadataDAO.valueByKeyFlow(url).map { it?.toULongOrNull() }

    override suspend fun setShouldNotifyForRevokedCertificate(shouldNotify: Boolean) {
        metadataDAO.insertValue(shouldNotify.toString(), SHOULD_NOTIFY_FOR_REVOKED_CERTIFICATE)
    }

    override suspend fun observeShouldNotifyForRevokedCertificate(): Flow<Boolean?> =
        metadataDAO.valueByKeyFlow(SHOULD_NOTIFY_FOR_REVOKED_CERTIFICATE).map { it?.toBoolean() }

    override suspend fun setDefaultCipherSuite(cipherSuite: SupportedCipherSuiteEntity) {
        metadataDAO.putSerializable(DEFAULT_CIPHER_SUITE_KEY, cipherSuite, SupportedCipherSuiteEntity.serializer())
    }

    override suspend fun getDefaultCipherSuite(): SupportedCipherSuiteEntity? =
        metadataDAO.getSerializable(DEFAULT_CIPHER_SUITE_KEY, SupportedCipherSuiteEntity.serializer())

    override suspend fun setTrackingIdentifier(identifier: String) {
        metadataDAO.insertValue(
            key = ANALYTICS_TRACKING_IDENTIFIER_KEY,
            value = identifier
        )
    }

    override suspend fun getTrackingIdentifier(): String? =
        metadataDAO.valueByKey(key = ANALYTICS_TRACKING_IDENTIFIER_KEY)

    override suspend fun observeTrackingIdentifier(): Flow<String?> =
        metadataDAO.valueByKeyFlow(key = ANALYTICS_TRACKING_IDENTIFIER_KEY)

    override suspend fun setPreviousTrackingIdentifier(identifier: String) {
        metadataDAO.insertValue(
            key = ANALYTICS_TRACKING_IDENTIFIER_PREVIOUS_KEY,
            value = identifier
        )
    }

    override suspend fun getPreviousTrackingIdentifier(): String? =
        metadataDAO.valueByKey(key = ANALYTICS_TRACKING_IDENTIFIER_PREVIOUS_KEY)

    override suspend fun deletePreviousTrackingIdentifier() {
        metadataDAO.deleteValue(key = ANALYTICS_TRACKING_IDENTIFIER_PREVIOUS_KEY)
    }

    override suspend fun getNextTimeForCallFeedback(): Long? = metadataDAO.valueByKey(NEXT_TIME_TO_ASK_CALL_FEEDBACK)?.toLong()

    override suspend fun setNextTimeForCallFeedback(timestamp: Long) =
        metadataDAO.insertValue(timestamp.toString(), NEXT_TIME_TO_ASK_CALL_FEEDBACK)

    override suspend fun setShouldFetchE2EITrustAnchors(shouldFetch: Boolean) {
        metadataDAO.insertValue(value = shouldFetch.toString(), key = SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS)
    }

    override suspend fun getShouldFetchE2EITrustAnchorHasRun(): Boolean =
        metadataDAO.valueByKey(SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS)?.toBoolean() ?: true

    override suspend fun setMlsConversationsResetEnabled(enabled: Boolean) {
        metadataDAO.insertValue(enabled.toString(), MLS_CONVERSATIONS_RESET)
    }

    override suspend fun getMlsConversationsResetEnabled(): Boolean =
        metadataDAO.valueByKey(MLS_CONVERSATIONS_RESET)?.toBoolean() ?: false

    override suspend fun setAsyncNotificationsEnabled(isAsyncNotificationsEnabled: Boolean) {
        metadataDAO.insertValue(isAsyncNotificationsEnabled.toString(), ASYNC_NOTIFICATIONS_ENABLED)
    }

    override suspend fun getAsyncNotificationsEnabled(): Boolean =
        metadataDAO.valueByKey(ASYNC_NOTIFICATIONS_ENABLED)?.toBoolean() ?: false

    override suspend fun setCellsEnabled(enabled: Boolean) {
        metadataDAO.insertValue(enabled.toString(), CELLS_ENABLED)
    }

    override suspend fun isCellsEnabled(): Boolean =
        metadataDAO.valueByKey(CELLS_ENABLED)?.toBoolean() ?: false

    override suspend fun setProfileQRCodeEnabled(enabled: Boolean) {
        metadataDAO.insertValue(enabled.toString(), PROFILE_QR_CODE_ENABLED)
    }

    override suspend fun isProfileQRCodeEnabled(): Boolean =
        metadataDAO.valueByKey(PROFILE_QR_CODE_ENABLED)?.toBoolean() ?: true

    override suspend fun setAssetAuditLogEnabled(enabled: Boolean) {
        metadataDAO.insertValue(enabled.toString(), ASSET_AUDIT_LOG_ENABLED)
    }

    override suspend fun isAssetAuditLogEnabled(): Boolean =
        metadataDAO.valueByKey(ASSET_AUDIT_LOG_ENABLED)?.toBoolean() ?: false

    override suspend fun setAppsEnabled(isAppsEnabled: Boolean) {
        metadataDAO.insertValue(isAppsEnabled.toString(), APPS_ENABLED_KEY)
    }

    override suspend fun getAppsEnabled(): Boolean =
        metadataDAO.valueByKey(APPS_ENABLED_KEY)?.toBoolean() ?: false

    override suspend fun observeAppsEnabled(): Flow<Boolean> =
        metadataDAO.valueByKeyFlow(APPS_ENABLED_KEY).map { it?.toBoolean() ?: false }

    override suspend fun setChatBubblesEnabled(enabled: Boolean) {
        metadataDAO.insertValue(enabled.toString(), CHAT_BUBBLES_ENABLED)
    }

    override suspend fun isChatBubblesEnabled(): Boolean =
        metadataDAO.valueByKey(CHAT_BUBBLES_ENABLED)?.toBoolean() ?: false

    private companion object {
        private const val DEFAULT_CIPHER_SUITE_KEY = "DEFAULT_CIPHER_SUITE"
        private const val SELF_DELETING_MESSAGES_KEY = "SELF_DELETING_MESSAGES"
        private const val SHOULD_NOTIFY_FOR_REVOKED_CERTIFICATE = "should_notify_for_revoked_certificate"
        private const val MLS_MIGRATION_KEY = "MLS_MIGRATION"
        private const val SUPPORTED_PROTOCOLS_KEY = "SUPPORTED_PROTOCOLS"
        private const val NEXT_TIME_TO_ASK_CALL_FEEDBACK = "next_time_to_ask_for_feedback_about_call"
        const val LEGAL_HOLD_REQUEST = "legal_hold_request"
        const val LEGAL_HOLD_CHANGE_NOTIFIED = "legal_hold_change_notified"
        private const val ANALYTICS_TRACKING_IDENTIFIER_PREVIOUS_KEY = "analytics_tracking_identifier_previous"
        private const val ANALYTICS_TRACKING_IDENTIFIER_KEY = "analytics_tracking_identifier"
        const val SHOULD_FETCH_E2EI_GET_TRUST_ANCHORS = "should_fetch_e2ei_trust_anchors"
        const val MLS_CONVERSATIONS_RESET = "mls_conversations_reset"
        const val ASYNC_NOTIFICATIONS_ENABLED = "async_notifications_enabled"
        const val CELLS_ENABLED = "wire_cells"
        const val PROFILE_QR_CODE_ENABLED = "profile_qr_code_enabled"
        private const val APPS_ENABLED_KEY = "apps_enabled"
        private const val CHAT_BUBBLES_ENABLED = "chat_bubbles"
        private const val ASSET_AUDIT_LOG_ENABLED = "asset_audit_log"
    }
}
