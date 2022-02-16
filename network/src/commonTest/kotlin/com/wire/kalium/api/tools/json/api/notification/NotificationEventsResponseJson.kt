package com.wire.kalium.api.tools.json.api.notification

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.user.NewClientEventData

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
