package com.wire.kalium.network.api.call

import com.wire.kalium.network.utils.NetworkResponse

interface CallApi {

    suspend fun getCallConfig(limit: Int?): NetworkResponse<String>
}
