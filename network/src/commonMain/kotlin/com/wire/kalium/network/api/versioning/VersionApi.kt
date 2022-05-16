package com.wire.kalium.network.api.versioning

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url

interface VersionApi {
    suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<VersionInfoDTO>
}

class VersionApiImpl internal constructor(
    // TODO(network): change to UnboundNetworkClient
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : VersionApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<VersionInfoDTO> = wrapKaliumResponse({
        if (it.status.value != HttpStatusCode.NotFound.value) null
        else {
            NetworkResponse.Success(VersionInfoDTO(), it)
        }
    }, {
        httpClient.get {
            setUrl(baseApiUrl, API_VERSION_PATH)
        }
    })


    private companion object {
        const val API_VERSION_PATH = "api-version"
    }

}
