package com.wire.kalium.logic.feature

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.wrapCryptoRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

interface ProteusClientProvider {
    suspend fun clearLocalFiles()

    /**
     * Returns the ProteusClient or creates new one if doesn't exists.
     */
    suspend fun getOrCreate(): ProteusClient

    /**
     * Returns the ProteusClient, retrieves it from local files or returns a failure if local files doesn't exist.
     */
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
                it.openOrCreate()
                _proteusClient = it
            }
        }
    }

    override suspend fun getOrError(): Either<CoreFailure, ProteusClient> {
        return mutex.withLock {
            _proteusClient?.let { Either.Right(it) } ?: run {
                wrapCryptoRequest {
                    ProteusClientImpl(rootProteusPath).also {
                        it.openOrError()
                        _proteusClient = it
                    }
                }.mapLeft {
                    if (it.proteusException.code == ProteusException.Code.LOCAL_FILES_NOT_FOUND) CoreFailure.MissingClientRegistration
                    else it
                }
            }
        }
    }
}
