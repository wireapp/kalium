/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.X509CredentialAcquisitionConfig
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/** Builds the immutable input for Core Crypto v10's X.509 acquisition state machine. */
internal interface E2EIClientProvider {
    suspend fun getX509CredentialAcquisitionConfig(
        acmeDirectoryUrl: String,
        clientId: ClientId? = null
    ): Either<E2EIFailure, X509CredentialAcquisitionConfig>

    suspend fun setDebugCertificateExpirationOverride(seconds: Long?)
    suspend fun getDebugCertificateExpirationOverride(): Long?
}

internal class EI2EIClientProviderImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val userRepository: UserRepository,
) : E2EIClientProvider {

    private val defaultE2EIExpiry = 90.days
    private var debugE2EIExpiryOverride: Duration? = null
    private val mutex = Mutex()

    override suspend fun getX509CredentialAcquisitionConfig(
        acmeDirectoryUrl: String,
        clientId: ClientId?
    ): Either<E2EIFailure, X509CredentialAcquisitionConfig> {
        val currentClientId = clientId ?: currentClientIdProvider().fold(
            { return E2EIFailure.GettingE2EIClient(it).left() },
            { it }
        )
        val selfUser = getSelfUserInfo().fold({ return it.left() }, { it })
        val (_, defaultCipherSuite) = mlsClientProvider.getOrFetchMLSConfig().getOrElse {
            return E2EIFailure.GettingE2EIClient(it).left()
        }

        return X509CredentialAcquisitionConfig(
            acmeDirectoryUrl = acmeDirectoryUrl,
            cipherSuite = defaultCipherSuite.toCrypto(),
            displayName = requireNotNull(selfUser.name),
            clientId = CryptoQualifiedClientId(currentClientId.value, selfUser.id.toCrypto()),
            handle = requireNotNull(selfUser.handle),
            teamId = selfUser.teamId?.value,
            validity = currentE2EIExpiry()
        ).right()
    }

    override suspend fun setDebugCertificateExpirationOverride(seconds: Long?) {
        mutex.withLock {
            debugE2EIExpiryOverride = seconds?.seconds
        }
    }

    override suspend fun getDebugCertificateExpirationOverride(): Long? = mutex.withLock {
        debugE2EIExpiryOverride?.inWholeSeconds
    }

    private suspend fun getSelfUserInfo(): Either<E2EIFailure, SelfUser> = userRepository.getSelfUser()
        .mapLeft { E2EIFailure.GettingE2EIClient(StorageFailure.DataNotFound) }
        .flatMap { selfUser ->
            if (selfUser.name == null || selfUser.handle == null) {
                E2EIFailure.GettingE2EIClient(StorageFailure.DataNotFound).left()
            } else {
                selfUser.right()
            }
        }

    private suspend fun currentE2EIExpiry(): Duration = mutex.withLock {
        debugE2EIExpiryOverride ?: defaultE2EIExpiry
    }
}
