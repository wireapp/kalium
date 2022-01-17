package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.tools.json.FaultyJsonProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.login.LoginApi
import kotlinx.serialization.json.buildJsonObject

object LoginWithEmailRequestJson {
    private val jsonProvider = { serializable: LoginApi.LoginParam ->
        buildJsonObject {
            "password" to serializable.password
            "label" to serializable.label
            when(serializable) {
                is LoginApi.LoginParam.LoginWithEmail -> "email" to serializable.email
                is LoginApi.LoginParam.LoginWithHandel -> "handle" to serializable.handle
            }
        }.toString()
    }

    val validLoginWithEmail = ValidJsonProvider(
        LoginApi.LoginParam.LoginWithEmail(
            email = "user@email.de",
            label = "label",
            password = "password",
        ), jsonProvider
    )

    val validLoginWithHandel = ValidJsonProvider(
        LoginApi.LoginParam.LoginWithHandel(
            handle = "cool_user_name",
            label = "label",
            password = "password",
        ), jsonProvider
    )

    val missingEmailAndHandel = FaultyJsonProvider(
        """
        |{
        |  "label": "label",
        |  "password": "password",
        |}
        """.trimMargin()
    )


    val missingLabel = FaultyJsonProvider(
        """
        |{
        |  "email": "user@email.de",
        |  "password": "password",
        |}
        """.trimMargin()
    )

    val missingPassword = FaultyJsonProvider(
        """
        |{
        |  "label": "label",
        |  "email": "user@email.de",
        |}
        """.trimMargin()
    )
}
