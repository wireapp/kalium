package com.wire.kalium.api.tools.json.api.api_version

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.versioning.VersionInfoDTO
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object VersionInfoDTOJson {
    private val defaultParametersJson = { serializable: VersionInfoDTO ->
        buildJsonObject {
            putJsonArray("supported") {
                serializable.supported.forEach { add(it) }
            }
            put("federation", serializable.federation)
            serializable.domain?.let { put("domain", it) }
        }.toString()
    }

    val valid404Result = VersionInfoDTO(null, false, listOf(0))

    val valid = ValidJsonProvider(
        VersionInfoDTO("wire.com", true, listOf(0, 1, 2)),
        defaultParametersJson
    )
}
