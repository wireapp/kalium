package com.wire.kalium.model

import com.wire.kalium.api.json.FaultyJsonProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.model.AccessTokenDTO

object AccessTokenDTOJson {
    private val jsonProvider = { serializable: AccessTokenDTO ->
        """
        |{
        |  "expires_in": ${serializable.expiresIn},
        |  "access_token": "${serializable.value}",
        |  "user": "${serializable.userId}",
        |  "token_type": "${serializable.tokenType}"
        |}
        """.trimMargin()
    }

    fun createValid(accessTokenDTO: AccessTokenDTO) = ValidJsonProvider(accessTokenDTO, jsonProvider)

    val valid = ValidJsonProvider(
        AccessTokenDTO(
            userId = "user_id",
            value = "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
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
