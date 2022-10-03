package com.wire.kalium.model

import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object VersionInfoDTOJson {
    private val defaultParametersJson = { serializable: VersionInfoDTO ->
        buildJsonObject {
            serializable.developmentSupported?.let {
                putJsonArray("development") {
                    it.forEach { add(it) }
                }
            }
            putJsonArray("supported") {
                serializable.supported.forEach { add(it) }
            }
            put("federation", serializable.federation)
            serializable.domain?.let { put("domain", it) }
        }.toString()
    }

    val valid404Result = VersionInfoDTO(null, null, false, listOf(0))

    val valid = ValidJsonProvider(
        VersionInfoDTO(listOf(2), "wire.com", true, listOf(0, 1)),
        defaultParametersJson
    )
}
