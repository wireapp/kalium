/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package util

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
        VersionInfoDTO(listOf(1), "test.api.com", true, listOf(0, 1)),
        defaultParametersJson
    )
}
