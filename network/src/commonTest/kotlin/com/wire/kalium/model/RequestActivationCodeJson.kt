package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
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
