package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeysDTO
import com.wire.kalium.network.api.v0.authenticated.MLSPublicKeyApiV0
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

class MLSPublicKeyApiV2 internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) :
    MLSPublicKeyApiV0(authenticatedNetworkClient) {
    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun getMLSPublicKeys(): NetworkResponse<MLSPublicKeysDTO> =
        wrapKaliumResponse { httpClient.get("$PATH_MLS/$PATH_PUBLIC_KEYS") }

    private companion object {
        const val PATH_PUBLIC_KEYS = "public-keys"
        const val PATH_MLS = "mls"
    }
}
