package com.wire.kalium.api.tools.json.api.user.register

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.register.RegisterApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ActivationRequestJson {

    private val jsonProvider = { serializable: RegisterApi.ActivationParam ->
        buildJsonObject {
            put("dryrun", serializable.dryRun)
            when (serializable) {
                is RegisterApi.ActivationParam.Email -> {
                    put("email", serializable.email)
                    put("code", serializable.code)
                }
            }
        }.toString()
    }

    val validActivateEmail = ValidJsonProvider(
        RegisterApi.ActivationParam.Email(email = "user@domain.de", code = "123456"), jsonProvider
    )
}
