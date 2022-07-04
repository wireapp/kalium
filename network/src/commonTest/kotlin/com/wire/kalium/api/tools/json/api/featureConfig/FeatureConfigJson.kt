package com.wire.kalium.api.tools.json.api.featureConfig

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.featureConfigs.AppLock
import com.wire.kalium.network.api.featureConfigs.AppLockConfig
import com.wire.kalium.network.api.featureConfigs.ClassifiedDomains
import com.wire.kalium.network.api.featureConfigs.ClassifiedDomainsConfig
import com.wire.kalium.network.api.featureConfigs.ConfigsStatusDTO
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
import com.wire.kalium.network.api.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.featureConfigs.SelfDeletingMessages
import com.wire.kalium.network.api.featureConfigs.SelfDeletingMessagesConfig

object FeatureConfigJson {
    private val featureConfigResponseSerializer = { it: FeatureConfigResponse ->
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
            |}
        """.trimMargin()
    }

    val featureConfigResponseSerializerResponse = ValidJsonProvider(
        FeatureConfigResponse(
            AppLock(
                AppLockConfig(true, 0), FeatureFlagStatusDTO.ENABLED
            ),
            ClassifiedDomains(ClassifiedDomainsConfig(listOf()), FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            SelfDeletingMessages(SelfDeletingMessagesConfig(0), FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED),
            ConfigsStatusDTO(FeatureFlagStatusDTO.ENABLED)
        ), featureConfigResponseSerializer
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
