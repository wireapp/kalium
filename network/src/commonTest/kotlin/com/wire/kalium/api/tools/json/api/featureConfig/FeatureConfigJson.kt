package com.wire.kalium.api.tools.json.api.featureConfig

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.featureConfigs.AppLock
import com.wire.kalium.network.api.featureConfigs.AppLockConfig
import com.wire.kalium.network.api.featureConfigs.ClassifiedDomains
import com.wire.kalium.network.api.featureConfigs.ClassifiedDomainsConfig
import com.wire.kalium.network.api.featureConfigs.ConfigsStatus
import com.wire.kalium.network.api.featureConfigs.FeatureConfigResponse
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
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "classifiedDomains": {
            |    "config": {
            |      "domains": [
            |        "example.com"
            |      ]
            |    },
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "conferenceCalling": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "conversationGuestLinks": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "digitalSignatures": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "fileSharing": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "legalhold": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "searchVisibility": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "selfDeletingMessages": {
            |    "config": {
            |      "enforcedTimeoutSeconds": 2147483647
            |    },
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "sndFactorPasswordChallenge": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "sso": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  },
            |  "validateSAMLemails": {
            |    "lockStatus": "locked",
            |    "status": "enabled"
            |  }
            |}
        """.trimMargin()
    }

    val featureConfigResponseSerializerResponse = ValidJsonProvider(
        FeatureConfigResponse(
            AppLock(
                AppLockConfig(true, 0), "locked", "enabled"
            ),
            ClassifiedDomains(ClassifiedDomainsConfig(listOf()), "locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            SelfDeletingMessages(SelfDeletingMessagesConfig(0), "locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled"),
            ConfigsStatus("locked", "enabled")
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
