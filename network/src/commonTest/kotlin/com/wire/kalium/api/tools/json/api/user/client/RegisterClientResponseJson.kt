package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.client.Capabilities
import com.wire.kalium.network.api.user.client.ClientCapability
import com.wire.kalium.network.api.user.client.ClientType
import com.wire.kalium.network.api.user.client.DeviceType
import com.wire.kalium.network.api.user.client.LocationResponse
import com.wire.kalium.network.api.user.client.RegisterClientResponse

object RegisterClientResponseJson {
    private val jsonProvider = { serializable: RegisterClientResponse ->
        """
        |{
        |   "id": "${serializable.clientId}",
        |   "type": "${serializable.type}",
        |   "time": "${serializable.registrationTime}",
        |   "class": "${serializable.deviceType}",
        |   "label": "${serializable.label}",
        |   "cookie": "${serializable.cookie}",
        |   "location": {
        |     "lat": ${serializable.location!!.latitude},
        |     "lon": ${serializable.location!!.longitude}
        |   },
        |   "model": "${serializable.model}",
        |   "capabilities": {
        |     "capabilities": [
        |        "${serializable.capabilities!!.capabilities[0]}"
        |     ]
        |  }
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        RegisterClientResponse(
            clientId = "defkrr8e7grgsoufhg8",
            type = ClientType.Permanent,
            deviceType = DeviceType.Phone,
            registrationTime = "2021-05-12T10:52:02.671Z",
            location = LocationResponse(latitude = "1.2345", longitude = "6.7890"),
            label = "label",
            cookie = "sldkfmdeklmwldwlek23kl44mntiuepfojfndkjd",
            capabilities = Capabilities(listOf(ClientCapability.LegalHoldImplicitConsent)),
            model = "model"
        ),
        jsonProvider
    )
}
