package com.wire.kalium.models.backend.json

import com.wire.kalium.models.backend.ServiceReferenceResponse

object ServiceReferenceResponseJson {
    val valid = ValidJsonProvider(
        ServiceReferenceResponse("ID", "provider")
    ) {
        """
        |{
        |   "id":"${it.id}",
        |   "provider":"${it.provider}"
        |}
        """.trimMargin()
    }
    val missingId = FaultyJsonProvider(
        """
        |{
        |   "provider":"123"
        |}
        """.trimMargin()
    )
    val wrongIdFormat = FaultyJsonProvider(
        """
        |{
        |   "id":123,
        |   "provider":"123"
        |}
        """.trimMargin()
    )
    val missingProvider = FaultyJsonProvider(
        """
        |{
        |   "id":"123"
        |}
        """.trimMargin()
    )
    val wrongProviderFormat = FaultyJsonProvider(
        """
        |{
        |   "id":"123",
        |   "provider":123
        |}
        """.trimMargin()
    )
}
