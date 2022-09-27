package com.wire.kalium.model

import com.wire.kalium.api.json.FaultyJsonProvider
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ServiceReferenceDTO

object ServiceReferenceDTOJson {
    val valid = ValidJsonProvider(
        ServiceReferenceDTO("ID", "provider")
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
