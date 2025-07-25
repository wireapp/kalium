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

package com.wire.kalium.network.api.base.authenticated.keypackage

import com.wire.kalium.network.api.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable

@Mockable
interface KeyPackageApi {

    sealed class Param {

        abstract val user: UserId
        abstract val cipherSuite: Int
        abstract val selfClientId: String?

        /**
         * @param user user ID to claim key packages from.
         * @param selfClientId to skip selfClient key package.
         */
        data class SkipOwnClient(
            override val user: UserId,
            override val selfClientId: String,
            override val cipherSuite: Int
        ) : Param()

        /**
         * @param user user ID to claim key packages from.
         */
        data class IncludeOwnClient(
            override val user: UserId,
            override val cipherSuite: Int,
        ) : Param() {
            override val selfClientId: String? = null
        }
    }

    /**
     * Claim a key package for each client of a given user.
     *
     * @param param api params
     * @return a list claimed key packages.
     */
    suspend fun claimKeyPackages(param: Param): NetworkResponse<ClaimedKeyPackageList>

    /**
     * Upload a batch fresh key packages from the self client
     *
     * @param clientId self client ID
     * @param keyPackages list of key packages
     */
    suspend fun uploadKeyPackages(clientId: String, keyPackages: List<KeyPackage>): NetworkResponse<Unit>

    /**
     * Upload and replace a batch fresh key packages from the self client
     *
     * @param clientId client ID
     * @param keyPackages list of key packages
     *
     */
    suspend fun replaceKeyPackages(
        clientId: String,
        keyPackages: List<KeyPackage>,
        cipherSuite: Int
    ): NetworkResponse<Unit>

    /**
     * Get the number of available key packages for the self client
     *
     * @param clientId self client ID
     *
     * @return unclaimed key package count
     */
    suspend fun getAvailableKeyPackageCount(
        clientId: String,
        cipherSuite: Int,
    ): NetworkResponse<KeyPackageCountDTO>
}
