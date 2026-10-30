/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v12.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v11.authenticated.AssetApiV11
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal open class AssetApiV12 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    selfUserId: UserId
) : AssetApiV11(authenticatedNetworkClient, selfUserId) {

    override fun buildUploadMetaData(metadata: AssetMetadataRequest): String = buildJsonObject {
        put("public", metadata.public)
        put("retention", metadata.retentionType.name.lowercase())
        metadata.conversationId?.let {
            putJsonObject("convId") {
                put("id", it.value)
                put("domain", it.domain)
            }
        }
        metadata.filename?.let { put("filename", it) }
        metadata.filetype?.let { put("filetype", it) }
    }.toString()
}
