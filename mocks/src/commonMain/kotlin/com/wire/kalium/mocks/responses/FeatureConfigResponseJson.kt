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
package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.ConferenceCallingConfig
import com.wire.kalium.network.api.authenticated.featureConfigs.E2EIConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.network.api.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

object FeatureConfigResponseJson {
    private const val VERIFICATION_EXPIRATION = 1_000_000L

    /**
     * JSON Response
     */
    private val featureConfigResponse = FeatureConfigResponse(
        FeatureConfigData.AppLock(
            AppLockConfigDTO(true, 0),
            FeatureFlagStatusDTO.ENABLED
        ),
        FeatureConfigData.ClassifiedDomains(
            ClassifiedDomainsConfigDTO(listOf("wire.com")),
            FeatureFlagStatusDTO.ENABLED
        ),
        FeatureConfigData.ConferenceCalling(FeatureFlagStatusDTO.ENABLED, ConferenceCallingConfig(false)),
        FeatureConfigData.ConversationGuestLinks(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.DigitalSignatures(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.FileSharing(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.Legalhold(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.SearchVisibility(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.SelfDeletingMessages(SelfDeletingMessagesConfigDTO(0), FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.SecondFactorPasswordChallenge(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.SSO(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.ValidateSAMLEmails(FeatureFlagStatusDTO.ENABLED),
        FeatureConfigData.MLS(
            MLSConfigDTO(
                SupportedProtocolDTO.MLS,
                listOf(SupportedProtocolDTO.MLS),
                emptyList(),
                1
            ),
            FeatureFlagStatusDTO.ENABLED
        ),
        FeatureConfigData.E2EI(
            E2EIConfigDTO("url", null, null, VERIFICATION_EXPIRATION),
            FeatureFlagStatusDTO.ENABLED
        ),
        FeatureConfigData.MLSMigration(
            MLSMigrationConfigDTO(
                Instant.DISTANT_FUTURE,
                Instant.DISTANT_FUTURE
            ),
            FeatureFlagStatusDTO.ENABLED
        )
    )
    val valid = KtxSerializer.json.encodeToString(featureConfigResponse)

}
