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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCredential
import com.wire.kalium.cryptography.CryptoCredentialRef
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.PkiHttpHeader
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.PkiHttpResponse
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

public data class E2EIAuthenticationRequest(
    public val target: String,
    public val keyAuth: String,
    public val acmeAudience: String
)

@Serializable
internal enum class E2EIRotationPhase {
    @SerialName("ACQUIRED")
    ACQUIRED,

    @SerialName("CREDENTIAL_INSTALLED")
    CREDENTIAL_INSTALLED,

    @SerialName("KEY_PACKAGES_PREPARED")
    KEY_PACKAGES_PREPARED,

    @SerialName("BACKEND_REPLACED")
    BACKEND_REPLACED
}

@Serializable
internal data class E2EIRotationCheckpoint(
    @SerialName("certificateChain")
    val certificateChain: String? = null,
    @SerialName("preExistingCredentialIds")
    val preExistingCredentialIds: List<String>,
    @SerialName("newCredentialId")
    val newCredentialId: String? = null,
    @SerialName("groupIds")
    val groupIds: List<String>,
    @SerialName("isNewClient")
    val isNewClient: Boolean,
    @SerialName("phase")
    val phase: E2EIRotationPhase = E2EIRotationPhase.ACQUIRED,
    @SerialName("keyPackages")
    val keyPackages: List<String> = emptyList(),
    @SerialName("cipherSuiteTag")
    val cipherSuiteTag: Int? = null
)

internal interface E2EIRepository {
    suspend fun acquireCredential(
        authenticate: suspend (E2EIAuthenticationRequest) -> String,
        groupIdListProvider: suspend () -> List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint>
    suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit>
    suspend fun fetchFederationCertificates(): Either<E2EIFailure, Unit>
    suspend fun checkCredentials(): Either<E2EIFailure, Unit>

    suspend fun rotateKeysAndMigrateConversations(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit>

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
    private val dependencies = E2EIRepositoryDependencies(
        e2EIApi = e2EIApi,
        acmeApi = acmeApi,
        pkiHttpClient = pkiHttpClient,
        e2EIClientProvider = e2EIClientProvider,
        mlsClientProvider = mlsClientProvider,
        currentClientIdProvider = currentClientIdProvider,
        mlsConversationRepository = mlsConversationRepository,
        userConfigRepository = userConfigRepository,
        selfUserId = selfUserId,
        cryptoStateChangeHookNotifier = cryptoStateChangeHookNotifier
    )
    private val checkpointStore = E2EIRotationCheckpointStore(userConfigRepository)
    private val acquisitionWorkflow = E2EICredentialAcquisitionWorkflow(
        dependencies = dependencies,
        checkpointStore = checkpointStore,
        pkiEnvironmentMutex = pkiEnvironmentMutex,
        fetchTrustAnchors = ::fetchAndSetTrustAnchors,
        fetchFederationCertificates = ::fetchFederationCertificates
    )
    private val rotationWorkflow = E2EICredentialRotationWorkflow(dependencies, checkpointStore)

    override suspend fun acquireCredential(
        authenticate: suspend (E2EIAuthenticationRequest) -> String,
        groupIdListProvider: suspend () -> List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint> = acquisitionWorkflow.acquire(
        authenticate = authenticate,
        groupIdListProvider = groupIdListProvider,
        isNewClient = isNewClient
    )

    override suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit> = withConfiguredCoreCrypto { coreCrypto ->
        discoveryUrl().fold(
            { it.left() },
            { fetchAndSetTrustAnchors(coreCrypto, it) }
        )
    }

    private suspend fun fetchAndSetTrustAnchors(
        coreCrypto: CoreCryptoCentral,
        discoveryUrl: String
    ): Either<E2EIFailure, Unit> = wrapApiRequest { acmeApi.getTrustAnchors(discoveryUrl) }.fold(
        { E2EIFailure.TrustAnchors(it).left() },
        { trustAnchors ->
            wrapCoreCryptoInterop {
                coreCrypto.reconcilePkiTrustAnchors(trustAnchors.decodeToString())
                cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
            }
        }
    )

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
                wrapCoreCryptoInterop {
                    coreCrypto.addPkiIntermediateCertificate(certificate)
                    cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
                }
            }
        }
    )

    override suspend fun checkCredentials(): Either<E2EIFailure, Unit> = withConfiguredCoreCrypto { coreCrypto ->
        wrapCoreCryptoInterop {
            coreCrypto.checkCredentials()
            cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
        }
    }

    override suspend fun rotateKeysAndMigrateConversations(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit> = rotationWorkflow.rotate(transactionProvider, checkpoint)

    override suspend fun discoveryUrl(): Either<E2EIFailure, String> = dependencies.discoveryUrl()

    private suspend fun withConfiguredCoreCrypto(
        block: suspend (CoreCryptoCentral) -> Either<E2EIFailure, Unit>
    ): Either<E2EIFailure, Unit> = mlsClientProvider.getCoreCrypto()
        .mapLeft(E2EIFailure::MissingMLSClient)
        .flatMap { coreCrypto ->
            pkiEnvironmentMutex.withLock {
                wrapCoreCryptoInterop {
                    coreCrypto.configurePkiEnvironment(dependencies.pkiHooks(directGetUrls = emptySet()))
                    block(coreCrypto)
                }.flatMap { it }
            }
        }
}

private data class E2EIRepositoryDependencies(
    val e2EIApi: E2EIApi,
    val acmeApi: ACMEApi,
    val pkiHttpClient: HttpClient,
    val e2EIClientProvider: E2EIClientProvider,
    val mlsClientProvider: MLSClientProvider,
    val currentClientIdProvider: CurrentClientIdProvider,
    val mlsConversationRepository: MLSConversationRepository,
    val userConfigRepository: UserConfigRepository,
    val selfUserId: UserId,
    val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier
) {
    suspend fun discoveryUrl(): Either<E2EIFailure, String> = userConfigRepository.getE2EISettings().fold(
        { E2EIFailure.MissingTeamSettings.left() },
        { settings ->
            when {
                !settings.isRequired -> E2EIFailure.Disabled.left()
                settings.discoverUrl.isNullOrBlank() -> E2EIFailure.MissingDiscoveryUrl.left()
                else -> settings.discoverUrl.right()
            }
        }
    )

    fun pkiHooks(
        directGetUrls: Set<String>,
        authenticate: suspend (E2EIAuthenticationRequest) -> String = {
            throw PkiHookFailure("PKI authentication is not configured for this operation")
        }
    ): KaliumPkiEnvironmentHooks = KaliumPkiEnvironmentHooks(this, authenticate, directGetUrls)
}

private class E2EIRotationCheckpointStore(
    private val userConfigRepository: UserConfigRepository
) {
    suspend fun load(): Either<E2EIFailure, E2EIRotationCheckpoint?> =
        userConfigRepository.getE2EIRotationCheckpoint().fold(
            { E2EIFailure.GettingE2EIClient(it).left() },
            { bytes ->
                if (bytes == null) {
                    null.right()
                } else {
                    try {
                        Json.decodeFromString<E2EIRotationCheckpoint>(bytes.decodeToString()).right()
                    } catch (exception: SerializationException) {
                        E2EIFailure.Generic(exception).left()
                    }
                }
            }
        )

    suspend fun persist(checkpoint: E2EIRotationCheckpoint): Either<E2EIFailure, Unit> {
        val encodedCheckpoint = try {
            Json.encodeToString(checkpoint).encodeToByteArray()
        } catch (exception: SerializationException) {
            return E2EIFailure.Generic(exception).left()
        }
        return userConfigRepository.setE2EIRotationCheckpoint(encodedCheckpoint).fold(
            { E2EIFailure.RotationAndMigration(it).left() },
            { Unit.right() }
        )
    }

    suspend fun delete(): Either<E2EIFailure, Unit> =
        userConfigRepository.deleteE2EIRotationCheckpoint().fold(
            { E2EIFailure.RotationAndMigration(it).left() },
            { Unit.right() }
        )

}

private class E2EICredentialAcquisitionWorkflow(
    private val dependencies: E2EIRepositoryDependencies,
    private val checkpointStore: E2EIRotationCheckpointStore,
    private val pkiEnvironmentMutex: Mutex,
    private val fetchTrustAnchors: suspend (CoreCryptoCentral, String) -> Either<E2EIFailure, Unit>,
    private val fetchFederationCertificates: suspend (CoreCryptoCentral, String) -> Either<E2EIFailure, Unit>
) {
    suspend fun acquire(
        authenticate: suspend (E2EIAuthenticationRequest) -> String,
        groupIdListProvider: suspend () -> List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint> = dependencies.currentClientIdProvider()
        .mapLeft(E2EIFailure::GettingE2EIClient)
        .flatMap { clientId ->
            dependencies.mlsClientProvider.getMLSClient(clientId)
                .mapLeft(E2EIFailure::MissingMLSClient)
                .flatMap { mlsClient ->
                    recoverPendingRotation(mlsClient, groupIdListProvider, isNewClient).flatMap { recoveredCheckpoint ->
                        recoveredCheckpoint?.right() ?: dependencies.discoveryUrl().flatMap { discoveryUrl ->
                            dependencies.mlsClientProvider.getCoreCrypto(clientId)
                                .mapLeft(E2EIFailure::MissingMLSClient)
                                .flatMap { coreCrypto ->
                                    startConfiguredAcquisition(
                                        context = CredentialAcquisitionContext(
                                            coreCrypto = coreCrypto,
                                            mlsClient = mlsClient,
                                            discoveryUrl = discoveryUrl,
                                            clientId = clientId,
                                            isNewClient = isNewClient
                                        ),
                                        authenticate = authenticate,
                                        groupIdListProvider = groupIdListProvider
                                    )
                                }
                        }
                    }
                }
        }

    private suspend fun recoverPendingRotation(
        mlsClient: MLSClient,
        groupIdListProvider: suspend () -> List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint?> = checkpointStore.load().flatMap { pendingCheckpoint ->
        if (pendingCheckpoint == null) {
            null.right()
        } else {
            wrapCoreCryptoInterop { groupIdListProvider() }.flatMap { groupIdList ->
                updateExistingCheckpoint(pendingCheckpoint, groupIdList, isNewClient).flatMap { updatedCheckpoint ->
                    recoverInstalledCheckpoint(mlsClient, updatedCheckpoint).flatMap { recoveredCheckpoint ->
                        if (recoveredCheckpoint == null) {
                            // An in-memory acquisition cannot be resumed after process loss. Only
                            // abandon the checkpoint after proving that installation never happened.
                            checkpointStore.delete().map { null }
                        } else {
                            recoveredCheckpoint.right()
                        }
                    }
                }
            }
        }
    }

    private suspend fun startConfiguredAcquisition(
        context: CredentialAcquisitionContext,
        authenticate: suspend (E2EIAuthenticationRequest) -> String,
        groupIdListProvider: suspend () -> List<GroupID>
    ): Either<E2EIFailure, E2EIRotationCheckpoint> = pkiEnvironmentMutex.withLock {
        wrapCoreCryptoInterop {
            context.coreCrypto.configurePkiEnvironment(
                dependencies.pkiHooks(
                    directGetUrls = setOf(context.discoveryUrl),
                    authenticate = authenticate
                )
            )
            startCoreCryptoAcquisition(
                context = context,
                groupIdListProvider = groupIdListProvider
            )
        }.flatMap { it }
    }

    private suspend fun startCoreCryptoAcquisition(
        context: CredentialAcquisitionContext,
        groupIdListProvider: suspend () -> List<GroupID>
    ): Either<E2EIFailure, E2EIRotationCheckpoint> = fetchTrustAnchors(context.coreCrypto, context.discoveryUrl).flatMap {
        fetchFederationCertificates(context.coreCrypto, context.discoveryUrl).flatMap {
            dependencies.e2EIClientProvider.getX509CredentialAcquisitionConfig(context.discoveryUrl, context.clientId)
                .flatMap { config ->
                    existingCredentialRef(context.mlsClient, context.isNewClient).flatMap { existingCredentialRef ->
                        val credential = context.coreCrypto.startX509CredentialAcquisition(config, existingCredentialRef)
                        installCredential(
                            coreCrypto = context.coreCrypto,
                            mlsClient = context.mlsClient,
                            credential = credential,
                            groupIdListProvider = groupIdListProvider,
                            isNewClient = context.isNewClient
                        )
                    }
                }
        }
    }

    private suspend fun existingCredentialRef(
        mlsClient: MLSClient,
        isNewClient: Boolean
    ): Either<E2EIFailure, CryptoCredentialRef?> = if (isNewClient) {
        null.right()
    } else {
        wrapCoreCryptoInterop {
            mlsClient.getCredentialRef(CredentialType.X509)
                ?: mlsClient.getCredentialRef(CredentialType.Basic)
        }
    }

    private suspend fun createAcquiredCheckpoint(
        mlsClient: MLSClient,
        groupIdList: List<GroupID>,
        isNewClient: Boolean,
        certificateChain: String
    ): Either<E2EIFailure, E2EIRotationCheckpoint> = getX509CredentialIds(mlsClient).flatMap { existingCredentialIds ->
        val checkpoint = E2EIRotationCheckpoint(
            certificateChain = certificateChain,
            preExistingCredentialIds = existingCredentialIds,
            groupIds = groupIdList.map(GroupID::value).distinct(),
            isNewClient = isNewClient,
        )
        checkpointStore.persist(checkpoint).map { checkpoint }
    }

    private suspend fun installCredential(
        coreCrypto: CoreCryptoCentral,
        mlsClient: MLSClient,
        credential: CryptoCredential,
        groupIdListProvider: suspend () -> List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint> {
        try {
            val certificateChain = credential.exportPem()
            return wrapCoreCryptoInterop { groupIdListProvider() }.flatMap { groupIdList ->
                createAcquiredCheckpoint(mlsClient, groupIdList, isNewClient, certificateChain).flatMap { checkpoint ->
                    val newCredentialRef = coreCrypto.installCredential(credential)
                    val installedCheckpoint = try {
                        checkpoint.copy(
                            newCredentialId = newCredentialRef.rotationCredentialId(),
                            phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
                        )
                    } finally {
                        newCredentialRef.close()
                    }
                    checkpointStore.persist(installedCheckpoint).map {
                        dependencies.cryptoStateChangeHookNotifier.onCryptoStateChanged(dependencies.selfUserId)
                        installedCheckpoint
                    }
                }
            }
        } finally {
            credential.close()
        }
    }

    private suspend fun updateExistingCheckpoint(
        checkpoint: E2EIRotationCheckpoint,
        groupIdList: List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIRotationCheckpoint> {
        if (checkpoint.isNewClient != isNewClient) {
            return E2EIFailure.Generic(
                IllegalStateException("The pending X.509 rotation belongs to another enrollment mode")
            ).left()
        }
        val mergedGroupIds = (checkpoint.groupIds + groupIdList.map(GroupID::value)).distinct()
        return if (mergedGroupIds == checkpoint.groupIds) {
            checkpoint.right()
        } else {
            checkpoint.copy(groupIds = mergedGroupIds).let { updatedCheckpoint ->
                checkpointStore.persist(updatedCheckpoint).map { updatedCheckpoint }
            }
        }
    }

    private suspend fun recoverInstalledCheckpoint(
        mlsClient: MLSClient,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, E2EIRotationCheckpoint?> = when (checkpoint.phase) {
        E2EIRotationPhase.CREDENTIAL_INSTALLED,
        E2EIRotationPhase.KEY_PACKAGES_PREPARED,
        E2EIRotationPhase.BACKEND_REPLACED -> checkpoint.right()
        E2EIRotationPhase.ACQUIRED -> recoverInstalledCredential(mlsClient, checkpoint)
    }

    private suspend fun recoverInstalledCredential(
        mlsClient: MLSClient,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, E2EIRotationCheckpoint?> = getX509CredentialIds(mlsClient).flatMap { currentCredentialIds ->
        val newlyInstalledCredentialIds = currentCredentialIds
            .filterNot(checkpoint.preExistingCredentialIds.toSet()::contains)
        when {
            newlyInstalledCredentialIds.size > 1 -> E2EIFailure.Generic(
                IllegalStateException("More than one new X.509 credential was installed during rotation recovery")
            ).left()
            newlyInstalledCredentialIds.isEmpty() -> null.right()
            checkpoint.certificateChain == null -> E2EIFailure.Generic(
                IllegalStateException(
                    "The installed X.509 credential was recovered, but its certificate checkpoint is missing"
                )
            ).left()
            else -> {
                val installedCheckpoint = checkpoint.copy(
                    newCredentialId = newlyInstalledCredentialIds.single(),
                    phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
                )
                checkpointStore.persist(installedCheckpoint).map { installedCheckpoint }
            }
        }
    }

    private suspend fun getX509CredentialIds(mlsClient: MLSClient): Either<E2EIFailure, List<String>> =
        wrapCoreCryptoInterop { mlsClient.getCredentialRefs(CredentialType.X509) }.flatMap { refs ->
            try {
                refs.map { it.rotationCredentialId() }.right()
            } finally {
                refs.forEach(CryptoCredentialRef::close)
            }
        }
}

private data class CredentialAcquisitionContext(
    val coreCrypto: CoreCryptoCentral,
    val mlsClient: MLSClient,
    val discoveryUrl: String,
    val clientId: ClientId,
    val isNewClient: Boolean
)

private class E2EICredentialRotationWorkflow(
    private val dependencies: E2EIRepositoryDependencies,
    private val checkpointStore: E2EIRotationCheckpointStore
) {
    suspend fun rotate(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, Unit> = dependencies.currentClientIdProvider()
        .mapLeft(E2EIFailure::RotationAndMigration)
        .flatMap { clientId ->
            dependencies.mlsClientProvider.getMLSClient(clientId)
                .mapLeft(E2EIFailure::MissingMLSClient)
                .flatMap { mlsClient ->
                    resolveCredentialRefs(mlsClient, checkpoint).flatMap { credentialRefs ->
                        try {
                            wrapMLSRequest { mlsClient.selectCredential(credentialRefs.new) }
                                .mapLeft(E2EIFailure::RotationAndMigration)
                                .flatMap {
                                    runRotationPhases(transactionProvider, clientId, checkpoint, credentialRefs)
                                }
                        } finally {
                            credentialRefs.all.forEach(CryptoCredentialRef::close)
                        }
                    }
                }
        }

    private suspend fun resolveCredentialRefs(
        mlsClient: MLSClient,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, RotationCredentialRefs> {
        val newCredentialId = checkpoint.newCredentialId ?: return E2EIFailure.Generic(
            IllegalStateException("The acquired X.509 credential has not been installed")
        ).left()
        return wrapCoreCryptoInterop { mlsClient.getCredentialRefs(CredentialType.X509) }.flatMap { refs ->
            val newCredentialRef = refs.firstOrNull { it.rotationCredentialId() == newCredentialId }
            if (newCredentialRef == null) {
                refs.forEach(CryptoCredentialRef::close)
                E2EIFailure.Generic(
                    IllegalStateException("The installed X.509 credential is no longer available")
                ).left()
            } else {
                RotationCredentialRefs(
                    all = refs,
                    new = newCredentialRef,
                    previous = checkpoint.preExistingCredentialIds.firstOrNull()?.let { previousId ->
                        refs.firstOrNull { it.rotationCredentialId() == previousId }
                    }
                ).right()
            }
        }
    }

    private suspend fun runRotationPhases(
        transactionProvider: CryptoTransactionProvider,
        clientId: ClientId,
        checkpoint: E2EIRotationCheckpoint,
        credentialRefs: RotationCredentialRefs
    ): Either<E2EIFailure, Unit> = if (checkpoint.isNewClient) {
        checkpointStore.delete()
    } else {
        migrateConversations(transactionProvider, checkpoint, credentialRefs.new).flatMap { migratedCheckpoint ->
            prepareKeyPackages(transactionProvider, migratedCheckpoint, credentialRefs.new).flatMap { preparedCheckpoint ->
                replaceKeyPackages(clientId, preparedCheckpoint).flatMap { replacedCheckpoint ->
                    cleanupPreviousCredential(transactionProvider, replacedCheckpoint, credentialRefs)
                }
            }
        }
    }

    private suspend fun migrateConversations(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint,
        newCredentialRef: CryptoCredentialRef
    ): Either<E2EIFailure, E2EIRotationCheckpoint> =
        if (checkpoint.phase != E2EIRotationPhase.CREDENTIAL_INSTALLED) {
            checkpoint.right()
        } else {
            checkpoint.groupIds.foldToEitherWhileRight(Unit) { groupIdValue, _ ->
                runRotationTransaction<Unit>(transactionProvider, "E2EIMigrateConversation") { mlsContext ->
                    dependencies.mlsConversationRepository.migrateConversationCredential(
                        mlsContext,
                        newCredentialRef,
                        GroupID(groupIdValue)
                    )
                }
            }.map { checkpoint }
        }

    private suspend fun prepareKeyPackages(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint,
        newCredentialRef: CryptoCredentialRef
    ): Either<E2EIFailure, E2EIRotationCheckpoint> =
        if (checkpoint.phase != E2EIRotationPhase.CREDENTIAL_INSTALLED) {
            checkpoint.right()
        } else {
            runRotationTransaction(transactionProvider, "E2EIPrepareKeyPackages") { mlsContext ->
                dependencies.mlsConversationRepository.prepareX509KeyPackages(mlsContext, newCredentialRef)
            }.flatMap { preparedKeyPackages ->
                checkpoint.copy(
                    phase = E2EIRotationPhase.KEY_PACKAGES_PREPARED,
                    keyPackages = preparedKeyPackages.keyPackages.map(Base64::encode),
                    cipherSuiteTag = preparedKeyPackages.cipherSuite.tag
                ).let { updatedCheckpoint ->
                    checkpointStore.persist(updatedCheckpoint).map { updatedCheckpoint }
                }
            }
        }

    private suspend fun replaceKeyPackages(
        clientId: ClientId,
        checkpoint: E2EIRotationCheckpoint
    ): Either<E2EIFailure, E2EIRotationCheckpoint> =
        if (checkpoint.phase != E2EIRotationPhase.KEY_PACKAGES_PREPARED) {
            checkpoint.right()
        } else {
            checkpoint.cipherSuiteTag?.let { cipherSuiteTag ->
                val preparedKeyPackages = PreparedX509KeyPackages(
                    keyPackages = checkpoint.keyPackages.map(Base64::decode),
                    cipherSuite = CipherSuite.fromTag(cipherSuiteTag)
                )
                dependencies.mlsConversationRepository.replaceX509KeyPackages(clientId, preparedKeyPackages)
                    .flatMap {
                        checkpoint.copy(phase = E2EIRotationPhase.BACKEND_REPLACED).let { updatedCheckpoint ->
                            checkpointStore.persist(updatedCheckpoint).map { updatedCheckpoint }
                        }
                    }
            } ?: E2EIFailure.Generic(
                IllegalStateException("The key-package checkpoint has no cipher suite")
            ).left()
        }

    private suspend fun cleanupPreviousCredential(
        transactionProvider: CryptoTransactionProvider,
        checkpoint: E2EIRotationCheckpoint,
        credentialRefs: RotationCredentialRefs
    ): Either<E2EIFailure, Unit> = if (checkpoint.phase == E2EIRotationPhase.BACKEND_REPLACED) {
        runRotationTransaction<Unit>(transactionProvider, "E2EICleanupPreviousCredential") { mlsContext ->
            credentialRefs.previous?.let {
                mlsContext.removeCredential(it)
                dependencies.cryptoStateChangeHookNotifier.onCryptoStateChanged(dependencies.selfUserId)
            }
        }.flatMap { checkpointStore.delete() }
    } else {
        Unit.right()
    }

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
}

private data class RotationCredentialRefs(
    val all: List<CryptoCredentialRef>,
    val new: CryptoCredentialRef,
    val previous: CryptoCredentialRef?
)

private fun CryptoCredentialRef.rotationCredentialId(): String = Base64.encode(publicKeyHash())

@Suppress("TooGenericExceptionCaught")
private suspend inline fun <T> wrapCoreCryptoInterop(
    block: suspend () -> T
): Either<E2EIFailure, T> = try {
    block().right()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    E2EIFailure.Generic(exception).left()
}

private class KaliumPkiEnvironmentHooks(
    private val dependencies: E2EIRepositoryDependencies,
    private val authenticationCallback: suspend (E2EIAuthenticationRequest) -> String,
    private val directGetUrls: Set<String>
) : PkiEnvironmentHooks {

    private val normalizedDirectGetUrls = directGetUrls.mapTo(mutableSetOf(), ::normalizePkiUrl)

    override suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray
    ): PkiHttpResponse {
        if (method == PkiHttpMethod.GET && normalizePkiUrl(url) !in normalizedDirectGetUrls) {
            val settings = dependencies.userConfigRepository.getE2EISettings().fold(
                { throw PkiHookFailure("Getting E2EI settings for the CRL request failed: $it") },
                { it }
            )
            val proxyUrl = settings.crlProxy?.takeIf { settings.shouldUseProxy && it.isNotBlank() }
            if (proxyUrl != null) {
                return wrapApiRequest { dependencies.acmeApi.getClientDomainCRL(url, proxyUrl) }.fold(
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

        val response = dependencies.pkiHttpClient.request(url) {
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
        acmeAud: String
    ): String = authenticationCallback(E2EIAuthenticationRequest(idp, keyAuth, acmeAud))

    override suspend fun getBackendNonce(): String {
        val clientId = dependencies.currentClientIdProvider().fold(
            { throw PkiHookFailure("Getting the client id for the backend nonce failed: $it") },
            { it }
        )
        return wrapApiRequest { dependencies.e2EIApi.getWireNonce(clientId.value) }.fold(
            { throw PkiHookFailure("Getting the backend nonce failed: $it") },
            { it }
        )
    }

    override suspend fun fetchBackendAccessToken(dpop: String): String {
        val clientId = dependencies.currentClientIdProvider().fold(
            { throw PkiHookFailure("Getting the client id for the backend access token failed: $it") },
            { it }
        )
        return wrapApiRequest { dependencies.e2EIApi.getAccessToken(clientId.value, dpop) }.fold(
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
}

private fun normalizePkiUrl(value: String): String = URLBuilder(Url(value)).apply {
    // Rust's `url::Url` renders an authority-only HTTP(S) URL with a `/`, while Ktor keeps
    // the path empty. Treat those two spellings as the same exact discovery endpoint.
    if (encodedPath.isEmpty()) encodedPath = "/"
}.build().toString()

private class PkiHookFailure(message: String) : Exception(message)
