package com.wire.kalium.models.backend.json

import com.wire.kalium.models.backend.Access
import java.util.UUID


object AccessJson {
    private val jsonProvider = { serializable: Access ->
        """
        |{
        |  "expires_in": ${serializable.expires_in},
        |  "access_token": "${serializable.accessToken}",
        |  "user": "${serializable.user}",
        |  "token_type": "${serializable.token_type}"
        |}
        """.trimMargin()
    }
    val valid = ValidJsonProvider(
        Access(
            UUID.randomUUID(),
            "Nlrhltkj-NgJUjEVevHz8Ilgy_pyWCT2b0kQb-GlnamyswanghN9DcC3an5RUuA7sh1_nC3hv2ZzMRlIhPM7Ag==.v=1.k=1.d=1637254939." +
                    "t=a.l=.u=75ebeb16-a860-4be4-84a7-157654b492cf.c=18401233206926541098",
            900,
            "Bearer"
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
