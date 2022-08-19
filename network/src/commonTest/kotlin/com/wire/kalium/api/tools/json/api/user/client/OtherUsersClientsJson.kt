package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.client.DeviceTypeDTO
import com.wire.kalium.network.api.user.client.OtherUserClientsItem

object OtherUsersClientsJson {

    private val otherUsersClientsResponseSerializer = { _: List<OtherUserClientsItem> ->
        """
                |[
                |  {
                |    "class": "desktop",
                |    "id": "79652f67ac5713e1"
                |  },
                |  {
                |    "class": "phone",
                |    "id": "89ba1b2e94b355c0"
                |  },
                |  {
                |    "class": "phone",
                |    "id": "c8e1f71ca209e1d5"
                |  }
                |]
        """.trimMargin()
    }

    val otherUsersClientsResponse = ValidJsonProvider(
        listOf(
            OtherUserClientsItem(DeviceTypeDTO.Desktop, "79652f67ac5713e1"),
            OtherUserClientsItem(DeviceTypeDTO.Phone, "89ba1b2e94b355c0"),
            OtherUserClientsItem(DeviceTypeDTO.Phone, "c8e1f71ca209e1d5"),
        ),
        otherUsersClientsResponseSerializer
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

    val domainOrUserNotFoundErrorResponse = ValidJsonProvider(
        ErrorResponse(404, "user not found", "no_user"),
        invalidJsonProvider
    )
}
