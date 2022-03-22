package com.wire.kalium.network.api.keypackage

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KeyPackageApiImpl(private val httpClient: HttpClient) : KeyPackageApi {
    override suspend fun claimKeyPackages(user: UserId): NetworkResponse<ClaimedKeyPackageList> =
        wrapKaliumResponse {
            httpClient.post("$PATH_KEY_PACKAGES/$PATH_CLAIM/${user.domain}/${user.value}")
        }

    override suspend fun uploadKeyPackages(clientId: String, keyPackages: List<KeyPackage>): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId") {
                setBody(KeyPackageList(keyPackages))
            }
        }

    override suspend fun getAvailableKeyPackageCount(clientId: String): NetworkResponse<Int> =
        wrapKaliumResponse { httpClient.get("$PATH_KEY_PACKAGES/$PATH_SELF/$clientId/$PATH_COUNT") }

    private companion object {
        val PATH_KEY_PACKAGES = "mls/key-packages"
        val PATH_CLAIM = "claim"
        val PATH_SELF = "self"
        val PATH_COUNT = "count"
    }
}
