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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.base.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

internal open class KeyPackageApiV0 internal constructor() : KeyPackageApi {
    override suspend fun claimKeyPackages(
        param: KeyPackageApi.Param
    ): NetworkResponse<ClaimedKeyPackageList> = NetworkResponse.Error(
        APINotSupported("MLS: claimKeyPackages api is only available on API V5")
    )

    override suspend fun uploadKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>
    ): NetworkResponse<Unit> = NetworkResponse.Error(
        APINotSupported("MLS: uploadKeyPackages api is only available on API V5")
    )

    override suspend fun replaceKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>,
        cipherSuite: Int
    ): NetworkResponse<Unit> = NetworkResponse.Error(
        APINotSupported("MLS: replaceKeyPackages api is only available on API V5")
    )

    override suspend fun getAvailableKeyPackageCount(clientId: String): NetworkResponse<KeyPackageCountDTO> =
        NetworkResponse.Error(
            APINotSupported("MLS: getAvailableKeyPackageCount api is only available on API V5")
        )
}
