package com.wire.kalium.api.tools.json.api.prekey

import com.wire.kalium.api.tools.json.FaultyJsonProvider
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.prekey.PreKey

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
