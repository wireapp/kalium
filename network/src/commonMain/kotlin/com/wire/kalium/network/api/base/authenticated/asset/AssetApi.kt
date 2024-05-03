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

package com.wire.kalium.network.api.base.authenticated.asset

import com.wire.kalium.network.utils.NetworkResponse
import okio.Sink
import okio.Source

interface AssetApi {
    /**
     * Downloads an asset, this will try to consume api v4 (federated aware endpoint)
     * @param assetId the asset identifier
     * @param assetDomain the asset domain
     * @param assetToken the asset token, can be null in case of public assets
     * @return a [NetworkResponse] with a reference to an open Okio [Source] object from which one will be able to stream the data
     */
    suspend fun downloadAsset(assetId: String, assetDomain: String?, assetToken: String?, tempFileSink: Sink): NetworkResponse<Unit>

    /** Uploads an already encrypted asset
     * @param metadata the metadata associated to the asset that wants to be uploaded
     * @param encryptedDataSource the source of the encrypted data to be uploaded
     * @param encryptedDataSize the size in bytes of the asset to be uploaded
     */
    suspend fun uploadAsset(
        metadata: AssetMetadataRequest,
        encryptedDataSource: () -> Source,
        encryptedDataSize: Long
    ): NetworkResponse<AssetResponse>

    /**
     * Deletes an asset, this will try to consume api v4 (federated aware endpoint)
     * @param assetId the asset identifier
     * @param assetDomain the asset domain
     * @param assetToken the asset token, can be null in case of public assets
     */
    suspend fun deleteAsset(assetId: String, assetDomain: String?, assetToken: String?): NetworkResponse<Unit>
}
