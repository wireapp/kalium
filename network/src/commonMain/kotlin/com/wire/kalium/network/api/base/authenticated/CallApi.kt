package com.wire.kalium.network.api.base.authenticated

import com.wire.kalium.network.utils.NetworkResponse

interface CallApi {

    suspend fun getCallConfig(limit: Int?): NetworkResponse<String>
    suspend fun connectToSFT(url: String, data: String): NetworkResponse<ByteArray>
}
