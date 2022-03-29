package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSClientImpl
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.first
import java.security.SecureRandom

actual class MLSClientProviderImpl actual constructor(
    private val userRepository: UserRepository, // TODO we can remove this when have the qualifiedID on login
    private val clientRepository: ClientRepository,
    private val kaliumPreferences: KaliumPreferences
): MLSClientProvider {

    override suspend fun getMLSClient(clientId: ClientId?): Either<CoreFailure, MLSClient> = suspending {
        val userId = MapperProvider.idMapper().toCryptoModel(userRepository.getSelfUser().first().id)
        val mlsClient = clientId?.let { clientId ->
            Either.Right(MLSClientImpl(
                KEYSTORE_LOCATION,
                getOrGenerateSecretKey(),
                CryptoQualifiedClientId(clientId.value, userId)
            ))
        } ?: run {
            clientRepository.currentClientId().map { clientId ->
                MLSClientImpl(
                    KEYSTORE_LOCATION,
                    getOrGenerateSecretKey(),
                    CryptoQualifiedClientId(clientId.value, userId)
                )
            }
        }
        mlsClient
    }
    
    private fun getOrGenerateSecretKey(): String {
        val databaseKey = kaliumPreferences.getString(KEYSTORE_SECRET_KEY)

        return if (databaseKey == null) {
            val secretKey = generateSecretKey()
            kaliumPreferences.putString(KEYSTORE_SECRET_KEY, secretKey)
            secretKey
        } else {
            databaseKey
        }
    }

    private fun generateSecretKey(): String {
        // TODO review with security
        val password = ByteArray(KEYSTORE_SECRET_LENGTH)
        SecureRandom().nextBytes(password)
        return password.encodeBase64()
    }

    private companion object {
        const val KEYSTORE_SECRET_KEY = "keystoreSecret"
        const val KEYSTORE_SECRET_LENGTH = 48
        const val KEYSTORE_LOCATION = "keystore"
    }
}
