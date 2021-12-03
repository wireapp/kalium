package com.wire.kalium.tools.json

import com.wire.kalium.models.outbound.otr.PreKey

object PreKeyJson {
    val valid = ValidJsonProvider(
        PreKey(
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
