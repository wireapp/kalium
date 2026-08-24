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

package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCredentialRef
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.PkiHttpHeader
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.PkiHttpResponse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PreparedX509KeyPackages
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

internal data class E2EIAuthenticationRequest(
    val target: String,
    val keyAuth: String,
    val acmeAudience: String
)

@Serializable
internal enum class E2EIRotationPhase {
    INTENT,
    ACQUIRED,
    CREDENTIAL_INSTALLED,
    KEY_PACKAGES_PREPARED,
    BACKEND_REPLACED
}

@Serializable
internal data class E2EIRotationCheckpoint(
    val certificateChain: String? = null,
    val preExistingCredentialIds: List<String>,
    val previousCredentialId: String?,
    val newCredentialId: String? = null,
    val groupIds: List<String>,
    val migratedGroupIds: List<String> = emptyList(),
    val isNewClient: Boolean,
    val phase: E2EIRotationPhase = E2EIRotationPhase.INTENT,
    val keyPackages: List<String> = emptyList(),
    val cipherSuiteTag: Int? = null
)

internal interface E2EIRepository {
    suspend fun startCredentialAcquisition(
        isNewClient: Boolean = false
    ): Either<E2EIFailure, E2EIAuthenticationRequest>

    suspend fun resumeCredentialAcquisition(
        idToken: String,
        groupIdList: List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint>
    suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit>
    suspend fun fetchFederationCertificates(): Either<E2EIFailure, Unit>
    suspend fun checkCredentials(): Either<E2EIFailure, Unit>

    suspend fun rotateKeysAndMigrateConversations(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit>

    suspend fun clearCredentialAcquisition()
    suspend fun discoveryUrl(): Either<E2EIFailure, String>
}

@Suppress("LongParameterList")
internal class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val acmeApi: ACMEApi,
    private val pkiHttpClient: HttpClient,
    private val e2EIClientProvider: E2EIClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userConfigRepository: UserConfigRepository,
    private val selfUserId: UserId,
    private val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier,
) : E2EIRepository {

    // Keep configure + its dependent operation atomic across periodic refresh and acquisition.
    private val pkiEnvironmentMutex = Mutex()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun startCredentialAcquisition(
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIAuthenticationRequest> {
        loadRotationCheckpoint().fold(
            { return it.left() },
            { pendingRotation ->
                if (pendingRotation != null) {
                    return E2EIFailure.Generic(
                        IllegalStateException("An X.509 credential rotation is already pending")
                    ).left()
                }
            }
        )
        clearCredentialAcquisition()
        val discoveryUrl = discoveryUrl().fold({ return it.left() }, { it })
        val clientId = currentClientIdProvider().fold(
            { return E2EIFailure.GettingE2EIClient(it).left() },
            { it }
        )
        val coreCrypto = mlsClientProvider.getCoreCrypto(clientId).fold(
            { return E2EIFailure.MissingMLSClient(it).left() },
            { it }
        )
        val hooks = KaliumPkiEnvironmentHooks(
            httpClient = pkiHttpClient,
            acmeApi = acmeApi,
            e2EIApi = e2EIApi,
            currentClientIdProvider = currentClientIdProvider,
            userConfigRepository = userConfigRepository,
            idToken = null,
            directGetUrls = setOf(discoveryUrl)
        )

        return pkiEnvironmentMutex.withLock {
            try {
                coreCrypto.configurePkiEnvironment(hooks)
                fetchAndSetTrustAnchors(coreCrypto, discoveryUrl).fold({ return it.left() }, {})
                fetchFederationCertificates(coreCrypto, discoveryUrl).fold({ return it.left() }, {})
                val config = e2EIClientProvider.getX509CredentialAcquisitionConfig(discoveryUrl, clientId)
                    .fold({ return it.left() }, { it })
                val existingCredentialRef = if (isNewClient) {
                    null
                } else {
                    mlsClientProvider.getMLSClient(clientId).getOrFail {
                        return E2EIFailure.MissingMLSClient(it).left()
                    }.getCredentialRef(CredentialType.X509)
                }

                coreCrypto.startX509CredentialAcquisition(config, existingCredentialRef).close()
                E2EIFailure.Generic(
                    IllegalStateException("Core Crypto completed credential acquisition before authentication")
                ).left()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                hooks.authenticationRequest?.right() ?: E2EIFailure.Generic(exception).left()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun resumeCredentialAcquisition(
        idToken: String,
        groupIdList: List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint> {
        val clientId = currentClientIdProvider().fold(
            { return E2EIFailure.GettingE2EIClient(it).left() },
            { it }
        )
        val mlsClient = mlsClientProvider.getMLSClient(clientId).fold(
            { return E2EIFailure.MissingMLSClient(it).left() },
            { it }
        )
        val loadedCheckpoint = when (val result = loadRotationCheckpoint()) {
            is Either.Left -> return result.value.left()
            is Either.Right -> result.value
        }
        var checkpoint: E2EIRotationCheckpoint
        if (loadedCheckpoint == null) {
            val existingCredentialIds = when (val result = getX509CredentialIds(mlsClient)) {
                is Either.Left -> return result.value.left()
                is Either.Right -> result.value
            }
            checkpoint = E2EIRotationCheckpoint(
                preExistingCredentialIds = existingCredentialIds,
                previousCredentialId = if (isNewClient) null else existingCredentialIds.firstOrNull(),
                groupIds = groupIdList.map(GroupID::value).distinct(),
                isNewClient = isNewClient
            )
            persistRotationCheckpoint(checkpoint).fold({ return it.left() }, {})
        } else {
            checkpoint = loadedCheckpoint
            if (checkpoint.isNewClient != isNewClient) {
                return E2EIFailure.Generic(
                    IllegalStateException("The pending X.509 rotation belongs to another enrollment mode")
                ).left()
            }
            val mergedGroupIds = (checkpoint.groupIds + groupIdList.map(GroupID::value)).distinct()
            if (mergedGroupIds != checkpoint.groupIds) {
                checkpoint = checkpoint.copy(groupIds = mergedGroupIds)
                persistRotationCheckpoint(checkpoint).fold({ return it.left() }, {})
            }
        }

        if (checkpoint.phase == E2EIRotationPhase.CREDENTIAL_INSTALLED ||
            checkpoint.phase == E2EIRotationPhase.KEY_PACKAGES_PREPARED ||
            checkpoint.phase == E2EIRotationPhase.BACKEND_REPLACED
        ) {
            return deleteAcquisitionSnapshot().fold(
                { it.left() },
                { checkpoint.right() }
            )
        }

        val currentCredentialIds = when (val result = getX509CredentialIds(mlsClient)) {
            is Either.Left -> return result.value.left()
            is Either.Right -> result.value
        }
        val newlyInstalledCredentialIds = currentCredentialIds
            .filterNot(checkpoint.preExistingCredentialIds.toSet()::contains)
        if (newlyInstalledCredentialIds.size > 1) {
            return E2EIFailure.Generic(
                IllegalStateException("More than one new X.509 credential was installed during rotation recovery")
            ).left()
        }
        newlyInstalledCredentialIds.singleOrNull()?.let { recoveredCredentialId ->
            if (checkpoint.certificateChain == null) {
                return E2EIFailure.Generic(
                    IllegalStateException(
                        "The installed X.509 credential was recovered, but its certificate checkpoint is missing"
                    )
                ).left()
            }
            val installedCheckpoint = checkpoint.copy(
                newCredentialId = recoveredCredentialId,
                phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
            )
            persistRotationCheckpoint(installedCheckpoint).fold({ return it.left() }, {})
            return deleteAcquisitionSnapshot().fold(
                { it.left() },
                { installedCheckpoint.right() }
            )
        }

        val snapshot = userConfigRepository.getE2EIAcquisitionSnapshot().getOrFail {
            return E2EIFailure.GettingE2EIClient(it).left()
        } ?: return E2EIFailure.Generic(
            IllegalStateException("No paused X.509 credential acquisition is available")
        ).left()
        val coreCrypto = mlsClientProvider.getCoreCrypto(clientId).fold(
            { return E2EIFailure.MissingMLSClient(it).left() },
            { it }
        )
        val discoveryUrl = discoveryUrl().fold({ return it.left() }, { it })
        val hooks = KaliumPkiEnvironmentHooks(
            httpClient = pkiHttpClient,
            acmeApi = acmeApi,
            e2EIApi = e2EIApi,
            currentClientIdProvider = currentClientIdProvider,
            userConfigRepository = userConfigRepository,
            idToken = idToken,
            directGetUrls = setOf(discoveryUrl)
        )

        return pkiEnvironmentMutex.withLock {
            try {
                coreCrypto.configurePkiEnvironment(hooks)
                val credential = coreCrypto.resumeX509CredentialAcquisition(snapshot)
                try {
                    val acquiredCheckpoint = checkpoint.copy(
                        certificateChain = credential.exportPem(),
                        phase = E2EIRotationPhase.ACQUIRED
                    )

                    // Persist the certificate before installation. If the process dies after the
                    // credential is installed, set-difference recovery can restore its reference.
                    persistRotationCheckpoint(acquiredCheckpoint).fold(
                        { return@withLock it.left() },
                        {}
                    )

                    val newCredentialRef = coreCrypto.installCredential(credential)
                    val installedCheckpoint = try {
                        acquiredCheckpoint.copy(
                            newCredentialId = newCredentialRef.rotationCredentialId(),
                            phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
                        )
                    } finally {
                        newCredentialRef.close()
                    }
                    persistRotationCheckpoint(installedCheckpoint).fold(
                        { it.left() },
                        {
                            deleteAcquisitionSnapshot().fold(
                                { it.left() },
                                {
                                    cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
                                    installedCheckpoint.right()
                                }
                            )
                        }
                    )
                } finally {
                    credential.close()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                E2EIFailure.Generic(exception).left()
            }
        }
    }

    override suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit> = withConfiguredCoreCrypto { coreCrypto ->
        discoveryUrl().fold(
            { it.left() },
            { fetchAndSetTrustAnchors(coreCrypto, it) }
        )
    }

    private suspend fun fetchAndSetTrustAnchors(
        coreCrypto: CoreCryptoCentral,
        discoveryUrl: String
    ): Either<E2EIFailure, Unit> {
        return wrapApiRequest { acmeApi.getTrustAnchors(discoveryUrl) }.fold(
            { E2EIFailure.TrustAnchors(it).left() },
            { trustAnchors ->
                try {
                    coreCrypto.reconcilePkiTrustAnchors(trustAnchors.decodeToString())
                    userConfigRepository.setShouldFetchE2EITrustAnchors(shouldFetch = false)
                    cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
                    Unit.right()
                } catch (exception: Exception) {
                    E2EIFailure.Generic(exception).left()
                }
            }
        )
    }

    override suspend fun fetchFederationCertificates(): Either<E2EIFailure, Unit> = withConfiguredCoreCrypto { coreCrypto ->
        discoveryUrl().fold(
            { it.left() },
            { fetchFederationCertificates(coreCrypto, it) }
        )
    }

    private suspend fun fetchFederationCertificates(
        coreCrypto: CoreCryptoCentral,
        discoveryUrl: String
    ): Either<E2EIFailure, Unit> = wrapApiRequest {
        acmeApi.getACMEFederationCertificateChain(discoveryUrl)
    }.fold(
        { E2EIFailure.IntermediateCert(it).left() },
        { certificates ->
            certificates.foldToEitherWhileRight(Unit) { certificate, _ ->
                try {
                    coreCrypto.addPkiIntermediateCertificate(certificate)
                    cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
                    Unit.right()
                } catch (exception: Exception) {
                    E2EIFailure.Generic(exception).left()
                }
            }
        }
    )

    override suspend fun checkCredentials(): Either<E2EIFailure, Unit> = withConfiguredCoreCrypto { coreCrypto ->
        try {
            coreCrypto.checkCredentials()
            cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
            Unit.right()
        } catch (exception: Exception) {
            E2EIFailure.Generic(exception).left()
        }
    }

    override suspend fun rotateKeysAndMigrateConversations(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit> {
        val clientId = currentClientIdProvider().fold(
            { return E2EIFailure.RotationAndMigration(it).left() },
            { it }
        )
        val mlsClient = mlsClientProvider.getMLSClient(clientId).fold(
            { return E2EIFailure.MissingMLSClient(it).left() },
            { it }
        )
        val newCredentialId = checkpoint.newCredentialId ?: return E2EIFailure.Generic(
            IllegalStateException("The acquired X.509 credential has not been installed")
        ).left()
        val credentialRefs = try {
            mlsClient.getCredentialRefs(CredentialType.X509)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return E2EIFailure.Generic(exception).left()
        }
        val newCredentialRef = credentialRefs.firstOrNull { it.rotationCredentialId() == newCredentialId }
            ?: run {
                credentialRefs.forEach(CryptoCredentialRef::close)
                return E2EIFailure.Generic(
                    IllegalStateException("The installed X.509 credential is no longer available")
                ).left()
            }
        val previousCredentialRef = checkpoint.previousCredentialId?.let { previousId ->
            credentialRefs.firstOrNull { it.rotationCredentialId() == previousId }
        }

        try {
            var currentCheckpoint = checkpoint
            if (currentCheckpoint.isNewClient) {
                runRotationTransaction<Unit>(transactionProvider, "E2EISelectNewClientCredential") { mlsContext ->
                    mlsContext.selectCredential(newCredentialRef)
                }.fold({ return it.left() }, {})
                return deleteRotationCheckpoint()
            }

            currentCheckpoint.groupIds
                .filterNot(currentCheckpoint.migratedGroupIds::contains)
                .forEach { groupIdValue ->
                    val groupId = GroupID(groupIdValue)
                    runRotationTransaction<Unit>(transactionProvider, "E2EIMigrateConversation") { mlsContext ->
                        mlsConversationRepository.migrateConversationCredential(
                            mlsContext,
                            newCredentialRef,
                            groupId
                        )
                    }.fold({ return it.left() }, {})

                    currentCheckpoint = currentCheckpoint.copy(
                        migratedGroupIds = (currentCheckpoint.migratedGroupIds + groupIdValue).distinct()
                    )
                    persistRotationCheckpoint(currentCheckpoint).fold({ return it.left() }, {})
                }

            if (currentCheckpoint.phase == E2EIRotationPhase.CREDENTIAL_INSTALLED) {
                val preparedKeyPackages = runRotationTransaction(
                    transactionProvider,
                    "E2EIPrepareKeyPackages"
                ) { mlsContext ->
                    mlsConversationRepository.prepareX509KeyPackages(mlsContext, newCredentialRef)
                }.fold({ return it.left() }, { it })

                currentCheckpoint = currentCheckpoint.copy(
                    phase = E2EIRotationPhase.KEY_PACKAGES_PREPARED,
                    keyPackages = preparedKeyPackages.keyPackages.map(Base64::encode),
                    cipherSuiteTag = preparedKeyPackages.cipherSuite.tag
                )
                persistRotationCheckpoint(currentCheckpoint).fold({ return it.left() }, {})
            }

            if (currentCheckpoint.phase == E2EIRotationPhase.KEY_PACKAGES_PREPARED) {
                val cipherSuiteTag = currentCheckpoint.cipherSuiteTag ?: return E2EIFailure.Generic(
                    IllegalStateException("The key-package checkpoint has no cipher suite")
                ).left()
                val preparedKeyPackages = PreparedX509KeyPackages(
                    keyPackages = currentCheckpoint.keyPackages.map(Base64::decode),
                    cipherSuite = CipherSuite.fromTag(cipherSuiteTag)
                )
                mlsConversationRepository.replaceX509KeyPackages(clientId, preparedKeyPackages)
                    .fold({ return it.left() }, {})

                currentCheckpoint = currentCheckpoint.copy(phase = E2EIRotationPhase.BACKEND_REPLACED)
                persistRotationCheckpoint(currentCheckpoint).fold({ return it.left() }, {})
            }

            if (currentCheckpoint.phase == E2EIRotationPhase.BACKEND_REPLACED) {
                runRotationTransaction<Unit>(transactionProvider, "E2EICleanupPreviousCredential") { mlsContext ->
                    mlsConversationRepository.removePreviousX509Credential(
                        mlsContext,
                        newCredentialRef,
                        previousCredentialRef
                    )
                }.fold({ return it.left() }, {})
                return deleteRotationCheckpoint()
            }

            return Unit.right()
        } finally {
            credentialRefs.forEach(CryptoCredentialRef::close)
        }
    }

    override suspend fun clearCredentialAcquisition() {
        userConfigRepository.deleteE2EIAcquisitionSnapshot()
    }

    private suspend fun getX509CredentialIds(
        mlsClient: com.wire.kalium.cryptography.MLSClient
    ): Either<E2EIFailure, List<String>> {
        val refs = try {
            mlsClient.getCredentialRefs(CredentialType.X509)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return E2EIFailure.Generic(exception).left()
        }

        return try {
            refs.map { it.rotationCredentialId() }.right()
        } finally {
            refs.forEach(CryptoCredentialRef::close)
        }
    }

    private suspend fun deleteAcquisitionSnapshot(): Either<E2EIFailure, Unit> =
        userConfigRepository.deleteE2EIAcquisitionSnapshot().fold(
            { E2EIFailure.GettingE2EIClient(it).left() },
            { Unit.right() }
        )

    private suspend fun loadRotationCheckpoint(): Either<E2EIFailure, E2EIRotationCheckpoint?> =
        userConfigRepository.getE2EIRotationCheckpoint().fold(
            { E2EIFailure.GettingE2EIClient(it).left() },
            { bytes ->
                if (bytes == null) {
                    null.right()
                } else {
                    try {
                        Json.decodeFromString<E2EIRotationCheckpoint>(bytes.decodeToString()).right()
                    } catch (exception: Exception) {
                        E2EIFailure.Generic(exception).left()
                    }
                }
            }
        )

    private suspend fun persistRotationCheckpoint(
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit> {
        val encodedCheckpoint = try {
            Json.encodeToString(checkpoint).encodeToByteArray()
        } catch (exception: Exception) {
            return E2EIFailure.Generic(exception).left()
        }
        return userConfigRepository.setE2EIRotationCheckpoint(encodedCheckpoint).fold(
            { E2EIFailure.RotationAndMigration(it).left() },
            { Unit.right() }
        )
    }

    private suspend fun deleteRotationCheckpoint(): Either<E2EIFailure, Unit> =
        userConfigRepository.deleteE2EIRotationCheckpoint().fold(
            { E2EIFailure.RotationAndMigration(it).left() },
            { Unit.right() }
        )

    private suspend fun <T> runRotationTransaction(
        transactionProvider: CryptoTransactionProvider,
        name: String,
        block: suspend (MlsCoreCryptoContext) -> T
    ): Either<E2EIFailure, T> = wrapMLSRequest {
        transactionProvider.mlsTransaction<T>(name) { mlsContext ->
            // The Right is created only after every Core Crypto operation completed. Exceptions
            // escape the block and force the native transaction to roll back.
            Either.Right(block(mlsContext))
        }
    }.fold(
        { E2EIFailure.RotationAndMigration(it).left() },
        { transactionResult ->
            transactionResult.fold(
                { E2EIFailure.RotationAndMigration(it).left() },
                { it.right() }
            )
        }
    )

    private fun CryptoCredentialRef.rotationCredentialId(): String = Base64.encode(publicKeyHash())

    override suspend fun discoveryUrl(): Either<E2EIFailure, String> = userConfigRepository.getE2EISettings().fold(
        { E2EIFailure.MissingTeamSettings.left() },
        { settings ->
            when {
                !settings.isRequired -> E2EIFailure.Disabled.left()
                settings.discoverUrl.isNullOrBlank() -> E2EIFailure.MissingDiscoveryUrl.left()
                else -> settings.discoverUrl.right()
            }
        }
    )

    @Suppress("TooGenericExceptionCaught")
    private suspend fun withConfiguredCoreCrypto(
        block: suspend (CoreCryptoCentral) -> Either<E2EIFailure, Unit>
    ): Either<E2EIFailure, Unit> {
        val coreCrypto = mlsClientProvider.getCoreCrypto().fold(
            { return E2EIFailure.MissingMLSClient(it).left() },
            { it }
        )
        return pkiEnvironmentMutex.withLock {
            try {
                coreCrypto.configurePkiEnvironment(
                    KaliumPkiEnvironmentHooks(
                        httpClient = pkiHttpClient,
                        acmeApi = acmeApi,
                        e2EIApi = e2EIApi,
                        currentClientIdProvider = currentClientIdProvider,
                        userConfigRepository = userConfigRepository,
                        idToken = null,
                        directGetUrls = emptySet()
                    )
                )
                block(coreCrypto)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                E2EIFailure.Generic(exception).left()
            }
        }
    }
}

private class KaliumPkiEnvironmentHooks(
    private val httpClient: HttpClient,
    private val acmeApi: ACMEApi,
    private val e2EIApi: E2EIApi,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userConfigRepository: UserConfigRepository,
    private val idToken: String?,
    private val directGetUrls: Set<String>
) : PkiEnvironmentHooks {

    private val normalizedDirectGetUrls = directGetUrls.mapTo(mutableSetOf(), ::normalizePkiUrl)

    var authenticationRequest: E2EIAuthenticationRequest? = null
        private set

    override suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray
    ): PkiHttpResponse {
        if (method == PkiHttpMethod.GET && normalizePkiUrl(url) !in normalizedDirectGetUrls) {
            val settings = userConfigRepository.getE2EISettings().fold(
                { throw PkiHookFailure("Getting E2EI settings for the CRL request failed: $it") },
                { it }
            )
            val proxyUrl = settings.crlProxy?.takeIf { settings.shouldUseProxy && it.isNotBlank() }
            if (proxyUrl != null) {
                return wrapApiRequest { acmeApi.getClientDomainCRL(url, proxyUrl) }.fold(
                    { throw PkiHookFailure("Getting the CRL through the configured proxy failed: $it") },
                    { crl ->
                        PkiHttpResponse(
                            status = 200.toUShort(),
                            headers = emptyList(),
                            body = crl
                        )
                    }
                )
            }
        }

        val response = httpClient.request(url) {
            this.method = method.toKtor()
            headers {
                headers.forEach { header -> append(header.name, header.value) }
            }
            if (body.isNotEmpty()) setBody(body)
        }
        return PkiHttpResponse(
            status = response.status.value.toUShort(),
            headers = response.headers.entries().flatMap { (name, values) ->
                values.map { value -> PkiHttpHeader(name, value) }
            },
            body = response.bodyAsBytes()
        )
    }

    override suspend fun authenticate(
        idp: String,
        keyAuth: String,
        acmeAud: String,
        acquisitionSnapshot: ByteArray
    ): String {
        idToken?.let { return it }

        userConfigRepository.setE2EIAcquisitionSnapshot(acquisitionSnapshot).fold(
            { throw PkiHookFailure("Persisting the X.509 acquisition snapshot failed: $it") },
            {}
        )
        authenticationRequest = E2EIAuthenticationRequest(idp, keyAuth, acmeAud)
        throw PkiHookFailure(AUTHENTICATION_REQUIRED)
    }

    override suspend fun getBackendNonce(): String {
        val clientId = currentClientIdProvider().fold(
            { throw PkiHookFailure("Getting the client id for the backend nonce failed: $it") },
            { it }
        )
        return wrapApiRequest { e2EIApi.getWireNonce(clientId.value) }.fold(
            { throw PkiHookFailure("Getting the backend nonce failed: $it") },
            { it }
        )
    }

    override suspend fun fetchBackendAccessToken(dpop: String): String {
        val clientId = currentClientIdProvider().fold(
            { throw PkiHookFailure("Getting the client id for the backend access token failed: $it") },
            { it }
        )
        return wrapApiRequest { e2EIApi.getAccessToken(clientId.value, dpop) }.fold(
            { throw PkiHookFailure("Getting the backend access token failed: $it") },
            { it.token }
        )
    }

    private fun PkiHttpMethod.toKtor(): HttpMethod = when (this) {
        PkiHttpMethod.GET -> HttpMethod.Get
        PkiHttpMethod.POST -> HttpMethod.Post
        PkiHttpMethod.PUT -> HttpMethod.Put
        PkiHttpMethod.DELETE -> HttpMethod.Delete
        PkiHttpMethod.PATCH -> HttpMethod.Patch
        PkiHttpMethod.HEAD -> HttpMethod.Head
    }

    private companion object {
        const val AUTHENTICATION_REQUIRED = "Core Crypto credential acquisition requires IdP authentication"
    }
}

private fun normalizePkiUrl(value: String): String = URLBuilder(Url(value)).apply {
    // Rust's `url::Url` renders an authority-only HTTP(S) URL with a `/`, while Ktor keeps
    // the path empty. Treat those two spellings as the same exact discovery endpoint.
    if (encodedPath.isEmpty()) encodedPath = "/"
}.build().toString()

private class PkiHookFailure(message: String) : Exception(message)
