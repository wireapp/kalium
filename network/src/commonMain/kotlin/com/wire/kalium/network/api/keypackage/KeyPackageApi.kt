package com.wire.kalium.network.api.keypackage

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface KeyPackageApi {

    /**
     * Claim a key package for each client of a given user.
     *
     * @param user user ID to claim key packages from.
     * @return a list claimed key packages.
     */
    suspend fun claimKeyPackages(user: UserId): NetworkResponse<ClaimedKeyPackageList>

    /**
     * Upload a batch fresh key packages from the self client
     *
     * @param clientId self client ID
     * @param keyPackages list of key packages
     */
    suspend fun uploadKeyPackages(clientId: String, keyPackages: List<KeyPackage>): NetworkResponse<Unit>

    /**
     * Get the number of available key packages for the self client
     *
     * @param clientId self client ID
     *
     * @return unclaimed key package count
     */
    suspend fun getAvailableKeyPackageCount(clientId: String): NetworkResponse<Int>
}

typealias KeyPackage = String
typealias KeyPackageRef = String
