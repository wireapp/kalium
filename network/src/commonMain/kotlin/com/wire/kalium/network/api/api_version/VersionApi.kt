package com.wire.kalium.network.api.api_version

import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.Url

interface VersionApi {
    suspend fun fetchServerConfig(baseApiUrl: Url): NetworkResponse<VersionInfoDTO>
}

class VersionApiImpl(
    private val httpClient: HttpClient
): VersionApi {
    override suspend fun fetchServerConfig(baseApiUrl: Url): NetworkResponse<VersionInfoDTO> =
        try {
            httpClient.get {
                setUrl(baseApiUrl, API_VERSION_PATH)
            }.let {
                NetworkResponse.Success(it.body(), it)
            }
        } catch (e: ResponseException) {
            when(e.response.status.value) {
                404 -> NetworkResponse.Success(VersionInfoDTO(), e.response)
                else -> wrapKaliumResponse { e.response }
            }
        } catch (e: Exception) {
            NetworkResponse.Error(KaliumException.GenericError(e))
        }


    private companion object {
        const val API_VERSION_PATH = "api-version"
    }

}
