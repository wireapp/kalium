package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RegisterAccountJson {
    private val jsonProvider = { serializable: RegisterApi.RegisterParam.PersonalAccount ->
        buildJsonObject {
            put("name", serializable.name)
            put("password", serializable.password)
            put("email", serializable.email)
            put("email_code", serializable.emailCode)
            serializable.cookieLabel?.let { put("label", it) }
        }.toString()
    }

    val validPersonalAccountRegister = ValidJsonProvider(
        serializableData = RegisterApi.RegisterParam.PersonalAccount(
            "test@email.com",
            "123456",
            "private user",
            "password",
            "cookieLabel"
        ),
        jsonProvider = jsonProvider
    )
}
