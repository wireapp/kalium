package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.tools.json.FaultyJsonProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.login.LoginWithEmailRequest

object LoginWithEmailRequestJson {
    private val jsonProvider = { serializable: LoginWithEmailRequest ->
        """
        |{
        |  "email": ${serializable.email},
        |  "label": "${serializable.label}",
        |  "password": "${serializable.password}",
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        LoginWithEmailRequest(
            email = "user@email.de",
            label = "label",
            password = "password",
        ), jsonProvider
    )

    val missingEmail = FaultyJsonProvider(
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
