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
import com.wire.kalium.model.conversation.ConversationResponseJson
import com.wire.kalium.network.api.base.authenticated.client.ClientDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.AppLockConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.ClassifiedDomainsConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.AppLock
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.ClassifiedDomains
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.MLS
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigData.SelfDeletingMessages
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureFlagStatusDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.MLSConfigDTO
import com.wire.kalium.network.api.base.authenticated.featureConfigs.SelfDeletingMessagesConfigDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SupportedProtocolDTO
import kotlinx.datetime.Instant
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object NotificationEventsResponseJson {
    private val newClientSerializer = { eventData: EventContentDTO.User.NewClientDTO ->
        """
            |{
            |  "type": "user.client-add",
            |  "client": {
            |    "time": "${eventData.client.registrationTime}",
            |    "last_active": "${eventData.client.lastActive}",
            |    "model": "${eventData.client.model}",
            |    "id": "71ff8872e468a970",
            |    "type": "${eventData.client.type}",
            |    "class": "desktop",
            |    "capabilities": {
            |      "capabilities": []
            |    },
            |    "label": "${eventData.client.label}",
            |    "mls_public_keys": { "${eventData.client.mlsPublicKeys?.keys?.first()}": "${eventData.client.mlsPublicKeys?.values?.first()}" }
            |  }
            |}
        """.trimMargin()
    }

    private val clientAdd = ValidJsonProvider(
        EventContentDTO.User.NewClientDTO(
            ClientDTO(
                cookie = null,
                clientId = "id",
                registrationTime = "2022-02-15T12:54:30Z",
                lastActive = "2022-02-15T12:54:30Z",
                model = "Firefox (Temporary)",
                type = ClientTypeDTO.Permanent,
                deviceType = DeviceTypeDTO.Desktop,
                label = "OS X 10.15 10.15",
                capabilities = null,
                mlsPublicKeys = mapOf(Pair("key_variant", "public_key")),
            )
        ),
        newClientSerializer
    )

    private val mlsWelcomeSerializer = { eventData: EventContentDTO.Conversation.MLSWelcomeDTO ->
        """
            |{
            |  "type": "conversation.mls-welcome",
            |  "qualified_conversation": {
            |     "id": "${eventData.qualifiedConversation.value}",
            |     "domain": "${eventData.qualifiedConversation.domain}"
            |  },
            |  "qualified_from": {
            |     "id": "${eventData.qualifiedFrom.value}",
            |     "domain": "${eventData.qualifiedFrom.domain}"
            |  },
            |  "data": "${eventData.message}",
            |  "from": "${eventData.from}"
            |}
        """.trimMargin()
    }

    private val mlsWelcome = ValidJsonProvider(
        EventContentDTO.Conversation.MLSWelcomeDTO(
            ConversationId("e16babfa-308b-414e-b6e0-c59517f723db", "staging.zinfra.io"),
            QualifiedID("76ebeb16-a849-4be4-84a7-157654b492cf", "staging.zinfra.io"),
            "AQABAAAAibLvHZAyYCHDxb+y8axOIdEAILa77VeJo1Yd8AfJKE009zwUxXuu7mAamu",
            "71ff8872e468a970"
        ),
        mlsWelcomeSerializer
    )

    private val newMlsMessageSerializer = { eventData: EventContentDTO.Conversation.NewMLSMessageDTO ->
        """
        |{
        |  "data": "${eventData.message}",
        |  "qualified_conversation": {
        |    "domain": "${eventData.qualifiedConversation.domain}",
        |    "id": "${eventData.qualifiedConversation.value}"
        |  },
        |  "qualified_from": {
        |    "domain": "${eventData.qualifiedFrom.domain}",
        |    "id": "${eventData.qualifiedFrom.value}"
        |  },
        |  "time": "${eventData.time}",
        |  "type": "conversation.mls-message-add",
        |  "subconv": "${eventData.subconversation}"
        |}    
        """.trimMargin()
    }

    private val newMlsMessage = ValidJsonProvider(
        EventContentDTO.Conversation.NewMLSMessageDTO(
            ConversationId("e16babfa-308b-414e-b6e0-c59517f723db", "staging.zinfra.io"),
            QualifiedID("76ebeb16-a849-4be4-84a7-157654b492cf", "staging.zinfra.io"),
            Instant.parse("2022-04-12T13:57:02.414Z"),
            "AiDyKXJ/yTKaq4fIO2SXXkQIBVhU0uOiDHIfVP3Yb6HoWAAAAAAAAAABAQAAAAAo6sj3pAQr7tXmljXYG4+sRsn" +
                    "R2IKQVhhUIOSopJZ7N2wIVH3nh1Az0AAAAJBQsRZJea8cnIeR/DKmixvos3AHWHchXr5PvXModBjxTVx7wcbT4w" +
                    "CTBVXtZqcYJwySIoKxokYhUUE2+zMKGg96+CV7jdQvqYG/fxk/dSm4TdQypanbSuu7VsYXZSPKPV0E1wChqpLit" +
                    "X5luW7smQPNcmPwwbrK0MDIq3PVhYwI4Cfi1eO1Ii94zM5IfVApyR4=",
            "subconv"
        ),
        newMlsMessageSerializer
    )

    private val newConversationSerializer = { eventData: EventContentDTO.Conversation.NewConversationDTO ->
        """
        |{
        |  "from" : "fdf23116-42a5-472c-8316-e10655f5d11e",
        |  "qualified_conversation" : {
        |    "id" : "${eventData.qualifiedConversation.value}",
        |    "domain" : "${eventData.qualifiedConversation.domain}"
        |  },
        |  "qualified_from" : {
        |     "id" : "${eventData.qualifiedFrom.value}",
        |     "domain" : "${eventData.qualifiedFrom.domain}"
        |  }, 
        |  "data" : ${ConversationResponseJson.conversationResponseSerializer(eventData.data)},
        |  "time" : "2022-04-12T13:57:02.414Z",
        |  "type" : "conversation.create"
        |}
        """.trimMargin()
    }

    private val newConversation = ValidJsonProvider(
        EventContentDTO.Conversation.NewConversationDTO(
            ConversationId("e16babfa-308b-414e-b6e0-c59517f723db", "staging.zinfra.io"),
            QualifiedID("76ebeb16-a849-4be4-84a7-157654b492cf", "staging.zinfra.io"),
            Instant.parse("2022-04-12T13:57:02.414Z"),
            ConversationResponseJson.v3.serializableData
        ),
        newConversationSerializer
    )

    @OptIn(InternalSerializationApi::class)
    private val newFeatureConfigSerializer = { featureConfigUpdated: EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO ->
        """
        |{
        |  "data" : ${Json.encodeToString(featureConfigUpdated.data)},
        |  "time" : "2022-04-12T13:57:02.414Z",
        |  "name" : "${featureConfigUpdated.data::class.serializer().descriptor.serialName}",
        |  "type" : "feature-config.update"
        |}
        """.trimMargin()
    }

    private val newFileSharingFeatureConfigUpdate = ValidJsonProvider(
        EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(
            FeatureConfigData.FileSharing(FeatureFlagStatusDTO.ENABLED)
        ),
        newFeatureConfigSerializer
    )

    private val newMlsFeatureConfigUpdate = ValidJsonProvider(
        EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(
            MLS(
                MLSConfigDTO(SupportedProtocolDTO.MLS, listOf(SupportedProtocolDTO.PROTEUS), listOf(1), 1),
                FeatureFlagStatusDTO.ENABLED,
            )
        ),
        newFeatureConfigSerializer
    )

    private val newClassifiedDomainsFeatureConfigUpdate = ValidJsonProvider(
        EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(
            ClassifiedDomains(
                ClassifiedDomainsConfigDTO(emptyList()),
                FeatureFlagStatusDTO.ENABLED,
            )
        ),
        newFeatureConfigSerializer
    )

    private val newSelfDeletingMessagesFeatureConfigUpdate = ValidJsonProvider(
        EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(
            SelfDeletingMessages(
                SelfDeletingMessagesConfigDTO(60),
                FeatureFlagStatusDTO.ENABLED,
            )
        ),
        newFeatureConfigSerializer
    )

    private val newAppLockFeatureConfigUpdate = ValidJsonProvider(
        EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO(
            AppLock(
                AppLockConfigDTO(true, 60), FeatureFlagStatusDTO.ENABLED
            )
        ),
        newFeatureConfigSerializer
    )

    val notificationWithLastEvent = """
        {
            "payload": [
                {
                    "type": "aVeryWeirdEventType"
                }
            ],
            "id": "fcfb33e4-8037-11ec-8001-22000ac39a27"
    }
    """.trimIndent()

    val notificationsWithUnknownEventAtFirstPosition = """
        {
          "time": "2022-02-15T12:54:30Z",
          "has_more": false,
          "notifications": [
            {
              "payload": [
                {
                  "type": "aVeryWeirdEventType"
                }
              ],
              "id": "fcfb33e4-8037-11ec-8001-22000ac39a27"
            },
            {
              "payload": [
                ${clientAdd.rawJson}
              ],
              "id": "f484ad9b-8037-11ec-8001-22000a0fe467"
            },
            {
              "payload": [
                ${mlsWelcome.rawJson}
              ],
              "id": "7e676173-b715-11ec-8001-22000a252765"
            },
            {
              "payload": [
                ${newConversation.rawJson}
              ],
              "id": "6dd9dfd9-ba68-11ec-8001-22000a09a242"
            },
            {
              "payload": [
                ${newMlsMessage.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                {
                  "data" : {
                    "status": "enabled"
                  },
                  "time" : "2022-04-12T13:57:02.414Z",
                  "name" : "anUnknownFeatureConfig",
                  "type": "feature-config.update"
                }
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                ${newFileSharingFeatureConfigUpdate.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                ${newMlsFeatureConfigUpdate.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                ${newClassifiedDomainsFeatureConfigUpdate.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                ${newSelfDeletingMessagesFeatureConfigUpdate.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                ${newAppLockFeatureConfigUpdate.rawJson}
              ],
              "id": "855fc5f1-bc01-11ec-8001-22000ac2309b"
            },
            {
              "payload": [
                {
                  "qualified_conversation": {
                    "domain": "staging.zinfra.io",
                    "id": "e16babfa-308b-414e-b6e0-c59517f723db"
                  },
                  "conversation": "e16babfa-308b-414e-b6e0-c59517f723db",
                  "time": "2022-01-28T12:44:09.171Z",
                  "data": {
                    "text": "owABAaEAWCAC1bSub+4rRYL8KVS0/eOiG8Z6FJpHmSnUVsj0CAWGawJYxAKkAAMBoQBYIIQcfzflFns1VsKxWmbciCVDhMfH/ybjYEIY4G7mQ/9vAqEAoQBYIOwhuR5iy9vsN5IgUooQPLuKLuhP9xJGxXgWGIfh1mKyA6UAUGtyag+MIeE62VvwU5Q0fEMBAAIAA6EAWCDJRIC/cF0nV9seb7IHTVGcnlVomsKKuy0bcXPoMYz6GARYNJToUT9f47S7N1ZC5J2S+8xwaa5y0/+GI/ytCjmQF5YmPM+lAGWPspl+WcX8Vr6o0HYrUGg=",
                    "data": "",
                    "sender": "71ff8872e468a970",
                    "recipient": "b6bda0a4d6bf0e88"
                  },
                  "from": "76ebeb16-a849-4be4-84a7-157654b492cf",
                  "qualified_from": {
                    "domain": "staging.zinfra.io",
                    "id": "76ebeb16-a849-4be4-84a7-157654b492cf"
                  },
                  "type": "conversation.otr-message-add"
                }
              ],
              "id": "fca551f6-8037-11ec-8001-22000ac39a27"
            }
          ]
        }
    """.trimIndent()

    val notificationResponsePageWithSingleEvent = ValidJsonProvider(
        NotificationResponse(
            time = "someTime",
            hasMore = false,
            notifications = listOf(
                EventResponse(
                    id = "eventId",
                    payload = listOf(EventContentDTOJson.validUpdateReceiptMode.serializableData),
                    transient = false
                )
            )
        )
    ) {
        """
        {
          "time": "${it.time}",
          "has_more": ${it.hasMore},
          "notifications": [
            {
              "payload": [
                ${EventContentDTOJson.validUpdateReceiptMode.rawJson}
              ],
              "id": "${it.notifications.first().id}"
            }
          ]
        }
        """.trimIndent()
    }
}
