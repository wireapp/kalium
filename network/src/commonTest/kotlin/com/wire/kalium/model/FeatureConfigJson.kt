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

package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.AppLock
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.ClassifiedDomains
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.ConferenceCalling
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.ConversationGuestLinks
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.DigitalSignatures
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.FileSharing
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.Legalhold
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.MLS
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.SSO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.SearchVisibility
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.SecondFactorPasswordChallenge
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.SelfDeletingMessages
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.ValidateSAMLEmails
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.E2EIConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSMigrationConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import kotlinx.datetime.Instant

object FeatureConfigJson {
    private val featureConfigResponseSerializer = { _: FeatureConfigResponse ->
        """
            |{
            | "appLock": {
            |    "config": {
            |      "enforceAppLock": true,
            |      "inactivityTimeoutSecs": 2147483647
            |    },
            |    "status": "enabled"
            |  },
            |  "classifiedDomains": {
            |    "config": {
            |      "domains": [
            |        "example.com"
            |      ]
            |    },
            |    "status": "enabled"
            |  },
            |  "conferenceCalling": {
            |    "status": "enabled"
            |  },
            |  "conversationGuestLinks": {
            |    "status": "enabled"
            |  },
            |  "digitalSignatures": {
            |    "status": "enabled"
            |  },
            |  "fileSharing": {
            |    "status": "enabled"
            |  },
            |  "legalhold": {
            |    "status": "enabled"
            |  },
            |  "searchVisibility": {
            |    "status": "enabled"
            |  },
            |  "selfDeletingMessages": {
            |    "config": {
            |      "enforcedTimeoutSeconds": 2147483647
            |    },
            |    "status": "enabled"
            |  },
            |  "sndFactorPasswordChallenge": {
            |    "status": "enabled"
            |  },
            |  "sso": {
            |    "status": "enabled"
            |  },
            |  "validateSAMLemails": {
            |    "status": "enabled"
            |  }
            | "mls": {
            |    "status": "enabled"
            |    "config": {
            |       "protocolToggleUsers": ["60368759-d23f-4502-ba6f-68b10e926f7a"],
            |       "defaultProtocol": "proteus",
            |       "supportedProtocols": ["proteus", "mls"],
            |       "allowedCipherSuites": [1],
            |       "defaultCipherSuite": 1
            |    }
            |  }
            |}
        """.trimMargin()
    }

    val featureConfigResponseSerializerResponse = ValidJsonProvider(
        FeatureConfigResponse(
            AppLock(
                AppLockConfigDTO(true, 0), FeatureFlagStatusDTO.ENABLED
            ),
            ClassifiedDomains(ClassifiedDomainsConfigDTO(listOf()), FeatureFlagStatusDTO.ENABLED),
            ConferenceCalling(FeatureFlagStatusDTO.ENABLED),
            ConversationGuestLinks(FeatureFlagStatusDTO.ENABLED),
            DigitalSignatures(FeatureFlagStatusDTO.ENABLED),
            FileSharing(FeatureFlagStatusDTO.ENABLED),
            Legalhold(FeatureFlagStatusDTO.ENABLED),
            SearchVisibility(FeatureFlagStatusDTO.ENABLED),
            SelfDeletingMessages(SelfDeletingMessagesConfigDTO(0), FeatureFlagStatusDTO.ENABLED),
            SecondFactorPasswordChallenge(FeatureFlagStatusDTO.ENABLED),
            SSO(FeatureFlagStatusDTO.ENABLED),
            ValidateSAMLEmails(FeatureFlagStatusDTO.ENABLED),
            MLS(
                MLSConfigDTO(SupportedProtocolDTO.PROTEUS, listOf(SupportedProtocolDTO.PROTEUS), listOf(1), 1),
                FeatureFlagStatusDTO.ENABLED
            ),
            FeatureConfigData.E2EI(E2EIConfigDTO("url", 0L), FeatureFlagStatusDTO.ENABLED),
            FeatureConfigData.MLSMigration(
                MLSMigrationConfigDTO(Instant.DISTANT_FUTURE, Instant.DISTANT_FUTURE),
                FeatureFlagStatusDTO.ENABLED
            )
        ),
        featureConfigResponseSerializer
    )

    private val invalidJsonProvider = { serializable: ErrorResponse ->
        """
        |{
        |   "code": "${serializable.code}",
        |   "message": "${serializable.message}",
        |   "label": "${serializable.label}"
        |}
        """.trimMargin()
    }

    val insufficientPermissionsErrorResponse = ValidJsonProvider(
        ErrorResponse(403, "Insufficient permissions", "operation-denied"),
        invalidJsonProvider
    )

    val teamNotFoundErrorResponse = ValidJsonProvider(
        ErrorResponse(404, "Team not found", "no-team"),
        invalidJsonProvider
    )

}
