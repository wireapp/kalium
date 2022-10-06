package com.wire.kalium.model

import com.wire.kalium.api.json.FaultyJsonProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO

object PreKeyJson {
    val valid = ValidJsonProvider(
        PreKeyDTO(
            900,
            "preKeyData"
        )
    ) {
        """
        |{
        |  "id": ${it.id},
        |  "key": "${it.key}"
        |}
        """.trimMargin()
    }

    val missingId = FaultyJsonProvider(
        """
        |{
        |  "key": "preKeyData"
        |}
        """.trimMargin()
    )

    val wrongIdFormat = FaultyJsonProvider(
        """
        |{
        |  "id": "thisIsAStringInsteadOfAnInteger",
        |  "key": "preKeyData"
        |}
        """.trimMargin()
    )

    val missingKey = FaultyJsonProvider(
        """
        |{
        |  "id": 123
        |}
        """.trimMargin()
    )

    val wrongKeyFormat = FaultyJsonProvider(
        """
        |{
        |  "id": 900,
        |  "key": 42
        |}
        """.trimMargin()
    )
}
