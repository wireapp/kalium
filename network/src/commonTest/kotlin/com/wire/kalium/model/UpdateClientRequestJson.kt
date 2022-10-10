package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.authenticated.client.MLSPublicKeyTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.UpdateClientRequest
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
