package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

interface MLSClientProvider {

    fun getMLSClient(clientId: ClientId? = null): Either<CoreFailure, MLSClient>

}

expect class MLSClientProviderImpl(
    rootKeyStorePath: String,
    userId: UserId,
    clientRepository: ClientRepository,
    kaliumPreferences: KaliumPreferences) : MLSClientProvider
