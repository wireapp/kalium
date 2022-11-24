package com.wire.kalium.network.api.base.authenticated

import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.utils.NetworkResponse

interface AccessTokenApi {
    suspend fun getToken(refreshToken: String, clientId: String? = null): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>>
}
