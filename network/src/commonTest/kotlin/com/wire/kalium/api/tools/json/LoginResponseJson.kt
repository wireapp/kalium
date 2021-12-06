package com.wire.kalium.api.tools.json

import com.wire.kalium.network.api.user.login.LoginWithEmailResponse

object LoginResponseJson {
    private val jsonProvider = { serializable: LoginWithEmailResponse ->
        """
        |{
        |  "expires_in": ${serializable.expiresIn},
        |  "access_token": "${serializable.accessToken}",
        |  "user": "${serializable.userId}",
        |  "token_type": "${serializable.tokenType}"
        |}
        """.trimMargin()
    }
    val valid = ValidJsonProvider(
        LoginWithEmailResponse(
            userId = "user_id",
            accessToken = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                    "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
            expiresIn = 900,
            tokenType = "Bearer"
        ), jsonProvider
    )

    val missingAccessToken = FaultyJsonProvider(
        """
        |{
        |  "expires_in": 900,
        |  "user": "75ebeb16-a860-4be4-84a7-157654b492",
        |  "token_type": "Bearer"
        |}
        """.trimMargin()
    )
    val missingTokenType = FaultyJsonProvider(
        """
        |{
        |  "expires_in": 900,
        |  "access_token": "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939.t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
        |  "user": "75ebeb16-a860-4be4-84a7-157654b492"
        |}
        """.trimMargin()
    )
    val missingUser = FaultyJsonProvider(
        """
        |{
        |  "expires_in": 900,
        |  "access_token": "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939.t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
        |  "token_type": "Bearer"
        |}
        """.trimMargin()
    )
    val missingExpiration = FaultyJsonProvider(
        """
        |{
        |  "access_token": "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939.t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
        |  "user": "75ebeb16-a860-4be4-84a7-157654b492",
        |  "token_type": "Bearer"
        |}
        """.trimMargin()
    )
}
