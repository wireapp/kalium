package com.wire.kalium.api.tools.json.api.notification

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.user.NewClientEventData
import com.wire.kalium.api.tools.json.api.conversation.ConversationResponseJson

object NotificationEventsResponseJson {
    private val newClientSerializer = { eventData: EventContentDTO.User.NewClientDTO ->
        """
            |{
            |  "type": "user.client-add",
            |  "client": {
            |    "time": "${eventData.client.registrationTime}",
            |    "model": "${eventData.client.model}",
            |    "id": "71ff8872e468a970",
            |    "type": "${eventData.client.deviceType}",
            |    "class": "desktop",
            |    "capabilities": {
            |      "capabilities": []
            |    },
            |    "label": "${eventData.client.label}"
            |  }
            |}
        """.trimMargin()
    }


    private val clientAdd = ValidJsonProvider(
        EventContentDTO.User.NewClientDTO(
            NewClientEventData(
                "id", "2022-02-15T12:54:30Z", "Firefox (Temporary)", "temporary",
                "desktop", "OS X 10.15 10.15"
            )
        ), newClientSerializer
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
            QualifiedID("76ebeb16-a849-4be4-84a7-157654b492cf","staging.zinfra.io"),
            "AQABAAAAibLvHZAyYCHDxb+y8axOIdEAILa77VeJo1Yd8AfJKE009zwUxXuu7mAamu",
            "71ff8872e468a970"
        ), mlsWelcomeSerializer
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
            QualifiedID("76ebeb16-a849-4be4-84a7-157654b492cf","staging.zinfra.io"),
            "2022-04-12T13:57:02.414Z",
            ConversationResponseJson.validGroup.serializableData
        ), newConversationSerializer
    )
    
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
}
