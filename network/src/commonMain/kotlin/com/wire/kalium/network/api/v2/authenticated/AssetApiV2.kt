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

package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.base.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.api.v0.authenticated.StreamAssetContent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import okio.Source

internal open class AssetApiV2 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val selfUserId: UserId
) : AssetApiV0(authenticatedNetworkClient) {
    override fun buildAssetsPath(assetId: String, assetDomain: String?): String {
        val domain = if (assetDomain.isNullOrBlank()) {
            selfUserId.domain
        } else {
            assetDomain
        }
        return "$PATH_ASSETS/$domain/$assetId"
    }

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: () -> Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse> =
        wrapKaliumResponse {
            httpClient.post(PATH_ASSETS) {
                contentType(ContentType.MultiPart.Mixed)
                setBody(StreamAssetContent(metadata, encryptedDataSize, encryptedDataSource))
            }
        }

    private companion object {
        const val PATH_ASSETS = "assets"
    }
}
