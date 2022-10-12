package com.wire.kalium.logic.feature

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

interface ProteusClientProvider {
    suspend fun clearLocalFiles()
    suspend fun getOrCreate(): ProteusClient
    suspend fun getOrError(): Either<CoreFailure, ProteusClient>
}

class ProteusClientProviderImpl(private val rootProteusPath: String) : ProteusClientProvider {

    private var _proteusClient: ProteusClient? = null
    private val mutex = Mutex()

    @Throws(ProteusException::class)
    override suspend fun clearLocalFiles() {
        mutex.withLock {
            _proteusClient?.clearLocalFiles()
            _proteusClient = null
        }
    }

    @Throws(ProteusException::class, CancellationException::class)
    override suspend fun getOrCreate(): ProteusClient {
        mutex.withLock {
            return _proteusClient ?: ProteusClientImpl(rootProteusPath).also {
                _proteusClient = it
                it.open()
            }
        }
    }

    override suspend fun getOrError(): Either<CoreFailure, ProteusClient> {
        return mutex.withLock {
             _proteusClient?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
        }
    }
}
