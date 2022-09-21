package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSClientImpl
import com.wire.kalium.cryptography.MlsDBSecret
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil

interface MLSClientProvider {
    suspend fun getMLSClient(clientId: ClientId? = null): Either<CoreFailure, MLSClient>
}

class MLSClientProviderImpl(
    private val rootKeyStorePath: String,
    private val userId: UserId,
    private val clientRepository: ClientRepository,
    private val passphraseStorage: PassphraseStorage
) : MLSClientProvider {

    private var mlsClient: MLSClient? = null

    override suspend fun getMLSClient(clientId: ClientId?): Either<CoreFailure, MLSClient> {
        val currentClientId = clientId ?: clientRepository.currentClientId().fold({ return Either.Left(it) }, { it })
        val cryptoUserId = CryptoUserID(value = userId.value, domain = userId.domain)

        val location = "$rootKeyStorePath/${currentClientId.value}".also {
            // TODO: migrate to okio solution once assert refactor is merged
            FileUtil.mkDirs(it)
        }

        return mlsClient?.let {
            Either.Right(it)
        } ?: run {
            val newClient = mlsClient(
                cryptoUserId,
                currentClientId,
                location,
                SecurityHelper(passphraseStorage).mlsDBSecret(userId)
            )
            mlsClient = newClient
            Either.Right(newClient)
        }
    }

    private fun mlsClient(userId: CryptoUserID, clientId: ClientId, location: String, passphrase: MlsDBSecret): MLSClient {
        return MLSClientImpl(
            "$location/$KEYSTORE_NAME",
            passphrase,
            CryptoQualifiedClientId(clientId.value, userId)
        )
    }

    private companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}
