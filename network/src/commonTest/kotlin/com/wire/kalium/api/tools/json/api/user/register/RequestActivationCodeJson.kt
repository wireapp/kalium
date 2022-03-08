package com.wire.kalium.api.tools.json.api.user.register

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.register.RegisterApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RequestActivationCodeJson {

    private val jsonProvider = { serializable: RegisterApi.RequestActivationCodeParam ->
        buildJsonObject {
            when (serializable) {
                is RegisterApi.RequestActivationCodeParam.Email -> {
                    put("email", serializable.email)
                }
            }
        }.toString()
    }

    val validActivateEmail = ValidJsonProvider(
        RegisterApi.RequestActivationCodeParam.Email(email = "user@domain.de"), jsonProvider
    )
}
