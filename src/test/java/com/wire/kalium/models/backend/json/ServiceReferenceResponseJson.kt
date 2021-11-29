package com.wire.kalium.models.backend.json

import com.wire.kalium.models.backend.ServiceReferenceResponse

object ServiceReferenceResponseJson {
    val valid = ValidJsonProvider(
        ServiceReferenceResponse("ID", "provider")
    ) {
        """
        |{
        |   "id":"${it.id},
        |   "provider":"${it.provider}"
        |}
        """.trimMargin()
    }
}
