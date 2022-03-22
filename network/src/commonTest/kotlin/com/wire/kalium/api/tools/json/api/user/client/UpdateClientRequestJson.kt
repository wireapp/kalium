package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.client.MLSPublicKeyTypeDTO
import com.wire.kalium.network.api.user.client.UpdateClientRequest
import io.ktor.util.encodeBase64

object UpdateClientRequestJson {

    val valid = ValidJsonProvider(
        UpdateClientRequest(
            mapOf(Pair(MLSPublicKeyTypeDTO.ED25519, "publickey"))
        )
    ) {
        """
        | {
        |   "mls_public_keys": {
        |     "ed25519": "${it.mlsPublicKeys.values.first().encodeBase64()}}"
        |   }
        | }
        """.trimMargin()
    }

}
