package com.wire.kalium.api.tools.json.api.user.login

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.user.login.SSOResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SSOResponseJson {
    private val jsonProvider = { serializable: SSOResponse ->
        buildJsonObject {
            with(serializable) {
                uri?.let { put("uri", it) }
            }
        }.toString()
    }

    val valid = ValidJsonProvider(
        SSOResponse(uri = "sso.login.de"), jsonProvider
    )
}
