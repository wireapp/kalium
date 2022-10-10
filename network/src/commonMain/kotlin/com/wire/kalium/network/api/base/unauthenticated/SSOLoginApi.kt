package com.wire.kalium.network.api.base.unauthenticated

import com.wire.kalium.network.api.base.model.AuthenticationResultDTO
import com.wire.kalium.network.utils.NetworkResponse

interface SSOLoginApi {

    sealed class InitiateParam(open val uuid: String) {
        data class WithoutRedirect(override val uuid: String) : InitiateParam(uuid)
        data class WithRedirect(val success: String, val error: String, override val uuid: String) : InitiateParam(uuid)
    }

    suspend fun initiate(param: InitiateParam): NetworkResponse<String>

    suspend fun finalize(cookie: String): NetworkResponse<String>

    suspend fun provideLoginSession(cookie: String): NetworkResponse<AuthenticationResultDTO>

    // TODO(web): ask about the response model since it's xml in swagger with no model
    suspend fun metaData(): NetworkResponse<String>

    suspend fun settings(): NetworkResponse<SSOSettingsResponse>
}
