package com.wire.kalium.network.api.keypackage

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class KeyPackageApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : KeyPackageApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun claimKeyPackages(param: KeyPackageApi.Param): NetworkResponse<ClaimedKeyPackageList> =
        wrapKaliumResponse {
            httpClient.post("$PATH_KEY_PACKAGES/$PATH_CLAIM/${param.user.domain}/${param.user.value}") {
                if (param is KeyPackageApi.Param.SkipOwnClient) {
                    parameter(QUERY_SKIP_OWN, param.selfClientId)
                }
            }
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
        val QUERY_SKIP_OWN = "skip_own"
    }
}
