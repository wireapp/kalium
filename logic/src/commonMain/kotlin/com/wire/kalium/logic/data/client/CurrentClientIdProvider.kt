package com.wire.kalium.logic.data.client

import app.cash.sqldelight.internal.Atomic
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CurrentClientIdProvider {
    suspend operator fun invoke(): Either<CoreFailure, ClientId>
    suspend fun clear()
}

internal class CurrentClientIdProviderImpl internal constructor(
    private val clientRepository: ClientRepository
) : CurrentClientIdProvider {

    private var _clientId: Atomic<ClientId?> = Atomic(null)
    private val lock: Mutex = Mutex()
    @OptIn(DelicateKaliumApi::class) // Use the uncached client ID in order to create the cache itself.
    override suspend fun invoke(): Either<CoreFailure, ClientId> = lock.withLock {
        _clientId.get()?.let { Either.Right(it) } ?: clientRepository.currentClientId().onSuccess { clientId ->
            _clientId.set(clientId)
        }
    }

    override suspend fun clear() = lock.withLock { _clientId.set(null) }
}
