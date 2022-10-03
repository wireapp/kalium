package com.wire.kalium.network.api.base.authenticated.serverpublickey

import com.wire.kalium.network.utils.NetworkResponse

interface MLSPublicKeyApi {
    /**
     * @return server public keys to consume as external-senders-keys in MLS conversations.
     */
    suspend fun getMLSPublicKeys(): NetworkResponse<MLSPublicKeysDTO>
}
