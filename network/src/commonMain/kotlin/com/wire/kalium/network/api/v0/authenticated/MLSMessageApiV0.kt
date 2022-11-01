package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

internal open class MLSMessageApiV0 internal constructor() : MLSMessageApi {

    override suspend fun sendMessage(message: MLSMessageApi.Message): NetworkResponse<SendMLSMessageResponse> = NetworkResponse.Error(
        APINotSupported("MLS: sendMessage api is only available on API V2")
    )

    override suspend fun sendWelcomeMessage(message: MLSMessageApi.WelcomeMessage): NetworkResponse<Unit> = NetworkResponse.Error(
        APINotSupported("MLS: sendWelcomeMessage api is only available on API V2")
    )

    override suspend fun sendCommitBundle(bundle: MLSMessageApi.CommitBundle): NetworkResponse<SendMLSMessageResponse> =
        NetworkResponse.Error(
            APINotSupported("MLS: sendCommitBundle api is only available on API V3")
        )
}
