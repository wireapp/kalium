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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCredential
import com.wire.kalium.cryptography.CryptoCredentialRef
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.PkiEnvironmentHooks
import com.wire.kalium.cryptography.PkiHttpMethod
import com.wire.kalium.cryptography.X509CredentialAcquisitionConfig
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.PreparedX509KeyPackages
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.hooks.NoOpCryptoStateChangeHookNotifier
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class E2EIRepositoryTest {

    @Test
    fun givenCoreCryptoRequestsAuthentication_whenAcquiring_thenAwaitsCallbackAndInstallsCredential() = runTest {
        val (arrangement, repository) = Arrangement().arrange()
        var authenticationRequest: E2EIAuthenticationRequest? = null

        val result = repository.acquireCredential(
            authenticate = { request ->
                authenticationRequest = request
                ID_TOKEN
            },
            groupIdListProvider = { listOf(GroupID("group-1")) },
            isNewClient = false
        )

        result.shouldSucceed { checkpoint ->
            assertEquals(CERTIFICATE_CHAIN, checkpoint.certificateChain)
            assertEquals(PREVIOUS_CREDENTIAL_ID, checkpoint.previousCredentialId)
            assertEquals(NEW_CREDENTIAL_ID, checkpoint.newCredentialId)
            assertEquals(listOf("group-1"), checkpoint.groupIds)
            assertEquals(E2EIRotationPhase.CREDENTIAL_INSTALLED, checkpoint.phase)
        }
        assertEquals(E2EIAuthenticationRequest(IDP_URL, KEY_AUTH, ACME_AUDIENCE), authenticationRequest)
        assertEquals(ID_TOKEN, arrangement.returnedIdToken)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.coreCrypto.startX509CredentialAcquisition(
                eq(ACQUISITION_CONFIG),
                eq(arrangement.previousCredentialRef)
            )
            arrangement.coreCrypto.installCredential(eq(arrangement.credential))
        }
        assertTrue(
            arrangement.checkpointEvents.indexOf("persist-ACQUIRED") <
                    arrangement.checkpointEvents.indexOf("install")
        )
        assertTrue(
            arrangement.checkpointEvents.indexOf("install") <
                    arrangement.checkpointEvents.indexOf("persist-CREDENTIAL_INSTALLED")
        )
    }

    @Test
    fun givenProxyEnabled_whenCoreNormalizesHostOnlyDiscoveryUrl_thenDiscoveryGetRemainsDirect() = runTest {
        val (arrangement, repository) = Arrangement().arrange {
            everySuspend { userConfigRepository.getE2EISettings() } returns PROXIED_E2EI_SETTINGS.copy(
                discoverUrl = HOST_ONLY_DISCOVERY_URL
            ).right()
        }

        repository.acquireCredential({ ID_TOKEN }, { emptyList() }, isNewClient = false).shouldSucceed()
        val response = requireNotNull(arrangement.pkiHooks).httpRequest(
            PkiHttpMethod.GET,
            "$HOST_ONLY_DISCOVERY_URL/",
            emptyList(),
            byteArrayOf()
        )

        assertEquals(200.toUShort(), response.status)
        verifySuspend(VerifyMode.not) {
            arrangement.acmeApi.getClientDomainCRL(any(), any())
        }
    }

    @Test
    fun givenProxyEnabled_whenAcquiring_thenExactDiscoveryGetRemainsDirect() = runTest {
        val (arrangement, repository) = Arrangement().arrange {
            everySuspend { userConfigRepository.getE2EISettings() } returns PROXIED_E2EI_SETTINGS.right()
        }

        repository.acquireCredential({ ID_TOKEN }, { emptyList() }, isNewClient = false).shouldSucceed()
        val response = requireNotNull(arrangement.pkiHooks).httpRequest(
            PkiHttpMethod.GET,
            DISCOVERY_URL,
            emptyList(),
            byteArrayOf()
        )

        assertEquals(200.toUShort(), response.status)
        verifySuspend(VerifyMode.not) {
            arrangement.acmeApi.getClientDomainCRL(any(), any())
        }
    }

    @Test
    fun givenProxyEnabled_whenCredentialCheckFetchesCrl_thenUsesConfiguredProxy() = runTest {
        val (arrangement, repository) = Arrangement().arrange {
            everySuspend { userConfigRepository.getE2EISettings() } returns PROXIED_E2EI_SETTINGS.right()
            everySuspend {
                acmeApi.getClientDomainCRL(eq(CRL_URL), eq(CRL_PROXY_URL))
            } returns success(PROXIED_CRL)
        }

        repository.checkCredentials().shouldSucceed()
        val response = requireNotNull(arrangement.pkiHooks).httpRequest(
            PkiHttpMethod.GET,
            CRL_URL,
            emptyList(),
            byteArrayOf()
        )

        assertEquals(200.toUShort(), response.status)
        assertContentEquals(PROXIED_CRL, response.body)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.acmeApi.getClientDomainCRL(eq(CRL_URL), eq(CRL_PROXY_URL))
        }
    }

    @Test
    fun givenAcquiredCheckpointCannotBePersisted_whenAcquiring_thenDoesNotInstallCredential() = runTest {
        val (arrangement, repository) = Arrangement().arrange {
            rotationCheckpointWriteFailurePhase = E2EIRotationPhase.ACQUIRED
        }

        val result = repository.acquireCredential(
            authenticate = { ID_TOKEN },
            groupIdListProvider = { listOf(GroupID("group-1")) },
            isNewClient = false
        )

        kotlin.test.assertIs<com.wire.kalium.common.functional.Either.Left<E2EIFailure>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.coreCrypto.installCredential(any())
        }
    }

    @Test
    fun givenPersistedRotationCheckpoint_whenEnrollmentIsRetried_thenResumesRotationWithoutAcquiringAgain() = runTest {
        val checkpoint = installedCheckpoint()
        val (arrangement, repository) = Arrangement().arrange {
            persistedRotationCheckpoint = Json.encodeToString(E2EIRotationCheckpoint.serializer(), checkpoint).encodeToByteArray()
        }
        var authenticationCalled = false

        repository.acquireCredential(
            authenticate = {
                authenticationCalled = true
                ID_TOKEN
            },
            groupIdListProvider = { emptyList() },
            isNewClient = false
        ).shouldSucceed {
            assertEquals(checkpoint, it)
        }

        assertEquals(false, authenticationCalled)
        verifySuspend(VerifyMode.not) {
            arrangement.coreCrypto.startX509CredentialAcquisition(any(), any())
            arrangement.coreCrypto.installCredential(any())
        }
    }

    @Test
    fun givenCredentialWasInstalledBeforeCheckpointUpdate_whenRetried_thenRecoversByFullSetDifference() = runTest {
        val olderCredentialRef = mock<CryptoCredentialRef>(mode = MockMode.autoUnit)
        val olderCredentialId = kotlin.io.encoding.Base64.encode("older-credential".encodeToByteArray())
        every { olderCredentialRef.publicKeyHash() } returns "older-credential".encodeToByteArray()
        val acquiredCheckpoint = installedCheckpoint().copy(
            preExistingCredentialIds = listOf(PREVIOUS_CREDENTIAL_ID, olderCredentialId),
            newCredentialId = null,
            phase = E2EIRotationPhase.ACQUIRED
        )
        val (arrangement, repository) = Arrangement().arrange {
            persistedRotationCheckpoint = Json.encodeToString(
                E2EIRotationCheckpoint.serializer(),
                acquiredCheckpoint
            ).encodeToByteArray()
            everySuspend { mlsClient.getCredentialRefs(CredentialType.X509) } returns
                    listOf(newCredentialRef, previousCredentialRef, olderCredentialRef)
        }

        repository.acquireCredential(
            authenticate = { ID_TOKEN },
            groupIdListProvider = { listOf(GroupID("group-created-during-idp")) },
            isNewClient = false
        ).shouldSucceed { recovered ->
            assertEquals(NEW_CREDENTIAL_ID, recovered.newCredentialId)
            assertEquals(
                listOf("group-1", "group-created-during-idp"),
                recovered.groupIds
            )
            assertEquals(E2EIRotationPhase.CREDENTIAL_INSTALLED, recovered.phase)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.coreCrypto.startX509CredentialAcquisition(any(), any())
            arrangement.coreCrypto.installCredential(any())
        }
    }

    @Test
    fun givenPendingInstalledCheckpoint_whenAcquiring_thenReturnsItWithoutOverwritingState() = runTest {
        val checkpoint = installedCheckpoint()
        val (arrangement, repository) = Arrangement().arrange {
            persistedRotationCheckpoint = Json.encodeToString(
                E2EIRotationCheckpoint.serializer(),
                checkpoint
            ).encodeToByteArray()
        }

        val result = repository.acquireCredential({ ID_TOKEN }, { emptyList() }, isNewClient = false)

        result.shouldSucceed { assertEquals(checkpoint, it) }
        verifySuspend(VerifyMode.not) {
            arrangement.coreCrypto.startX509CredentialAcquisition(any(), any())
        }
    }

    @Test
    fun givenAbandonedPreInstallCheckpoint_whenStartingAnotherAcquisition_thenClearsItAndStartsAgain() = runTest {
        val checkpoint = installedCheckpoint().copy(
            certificateChain = null,
            newCredentialId = null,
            phase = E2EIRotationPhase.INTENT
        )
        val (arrangement, repository) = Arrangement().arrange {
            persistedRotationCheckpoint = Json.encodeToString(
                E2EIRotationCheckpoint.serializer(),
                checkpoint
            ).encodeToByteArray()
        }

        repository.acquireCredential({ ID_TOKEN }, { emptyList() }, isNewClient = false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.deleteE2EIRotationCheckpoint()
            arrangement.coreCrypto.startX509CredentialAcquisition(any(), any())
        }
    }

    @Test
    fun givenCredentialInstalledWithPreInstallCheckpoint_whenStartingAgain_thenRecoversAndPreservesCheckpoint() = runTest {
        val checkpoint = installedCheckpoint().copy(
            newCredentialId = null,
            phase = E2EIRotationPhase.ACQUIRED
        )
        val (arrangement, repository) = Arrangement().arrange {
            persistedRotationCheckpoint = Json.encodeToString(
                E2EIRotationCheckpoint.serializer(),
                checkpoint
            ).encodeToByteArray()
            everySuspend { mlsClient.getCredentialRefs(CredentialType.X509) } returns
                    listOf(newCredentialRef, previousCredentialRef)
        }

        val result = repository.acquireCredential({ ID_TOKEN }, { emptyList() }, isNewClient = false)

        result.shouldSucceed { recovered ->
            assertEquals(E2EIRotationPhase.CREDENTIAL_INSTALLED, recovered.phase)
            assertEquals(NEW_CREDENTIAL_ID, recovered.newCredentialId)
        }
        val recoveredCheckpoint = Json.decodeFromString(
            E2EIRotationCheckpoint.serializer(),
            requireNotNull(arrangement.persistedRotationCheckpoint).decodeToString()
        )
        assertEquals(E2EIRotationPhase.CREDENTIAL_INSTALLED, recoveredCheckpoint.phase)
        assertEquals(NEW_CREDENTIAL_ID, recoveredCheckpoint.newCredentialId)
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.deleteE2EIRotationCheckpoint()
            arrangement.coreCrypto.startX509CredentialAcquisition(any(), any())
        }
    }

    @Test
    fun givenOneGroupAlreadyCheckpointed_whenRotating_thenMigratesOnlyRemainingGroupAndCompletesPhases() = runTest {
        val groupOne = GroupID("group-1")
        val groupTwo = GroupID("group-2")
        val prepared = PreparedX509KeyPackages(
            keyPackages = listOf("key-package".encodeToByteArray()),
            cipherSuite = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        )
        val checkpoint = installedCheckpoint().copy(
            groupIds = listOf(groupOne.value, groupTwo.value),
            migratedGroupIds = listOf(groupOne.value)
        )
        val (arrangement, repository) = Arrangement().arrange {
            everySuspend { mlsClient.getCredentialRefs(CredentialType.X509) } returns
                    listOf(newCredentialRef, previousCredentialRef)
            everySuspend {
                mlsConversationRepository.migrateConversationCredential(any(), any(), any())
            } returns Unit
            everySuspend {
                mlsConversationRepository.prepareX509KeyPackages(any(), any())
            } returns prepared
            everySuspend {
                mlsConversationRepository.replaceX509KeyPackages(any(), any())
            } returns Unit.right()
            everySuspend {
                mlsConversationRepository.removePreviousX509Credential(any(), any(), any())
            } returns Unit
        }

        repository.rotateKeysAndMigrateConversations(arrangement.transactionProvider, checkpoint).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.migrateConversationCredential(
                eq(arrangement.mlsContext),
                eq(arrangement.newCredentialRef),
                eq(groupTwo)
            )
            arrangement.mlsConversationRepository.prepareX509KeyPackages(
                eq(arrangement.mlsContext),
                eq(arrangement.newCredentialRef)
            )
            arrangement.mlsConversationRepository.replaceX509KeyPackages(
                eq(TestClient.CLIENT_ID),
                eq(prepared)
            )
            arrangement.mlsConversationRepository.removePreviousX509Credential(
                eq(arrangement.mlsContext),
                eq(arrangement.newCredentialRef),
                eq(arrangement.previousCredentialRef)
            )
            arrangement.userConfigRepository.deleteE2EIRotationCheckpoint()
        }
    }

    @Test
    fun givenBackendReplacementFails_whenRetriedFromPreparedCheckpoint_thenKeepsCleanupPending() = runTest {
        val backendFailure = E2EIFailure.Generic(IllegalStateException("backend failed"))
        val keyPackage = "key-package".encodeToByteArray()
        val checkpoint = installedCheckpoint().copy(
            migratedGroupIds = listOf("group-1"),
            phase = E2EIRotationPhase.KEY_PACKAGES_PREPARED,
            keyPackages = listOf(kotlin.io.encoding.Base64.encode(keyPackage)),
            cipherSuiteTag = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256.tag
        )
        val (arrangement, repository) = Arrangement().arrange {
            everySuspend { mlsClient.getCredentialRefs(CredentialType.X509) } returns
                    listOf(newCredentialRef, previousCredentialRef)
            everySuspend {
                mlsConversationRepository.replaceX509KeyPackages(any(), any())
            } returns backendFailure.left()
        }

        val result = repository.rotateKeysAndMigrateConversations(arrangement.transactionProvider, checkpoint)

        kotlin.test.assertIs<com.wire.kalium.common.functional.Either.Left<E2EIFailure>>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.migrateConversationCredential(any(), any(), any())
            arrangement.mlsConversationRepository.prepareX509KeyPackages(any(), any())
            arrangement.mlsConversationRepository.removePreviousX509Credential(any(), any(), any())
            arrangement.userConfigRepository.deleteE2EIRotationCheckpoint()
        }
    }

    @Test
    fun givenE2EIIsDisabled_whenGettingDiscoveryUrl_thenReturnsDisabled() = runTest {
        val (_, repository) = Arrangement().arrange {
            everySuspend { userConfigRepository.getE2EISettings() } returns E2EI_SETTINGS.copy(isRequired = false).right()
        }

        val result = repository.discoveryUrl()

        result as com.wire.kalium.common.functional.Either.Left
        kotlin.test.assertIs<E2EIFailure.Disabled>(result.value)
    }

    @Test
    fun whenRefreshingTrustAnchors_thenReconcilesAuthoritativeBundle() = runTest {
        val (arrangement, repository) = Arrangement().arrange()

        repository.fetchAndSetTrustAnchors().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.acmeApi.getTrustAnchors(eq(DISCOVERY_URL))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.coreCrypto.reconcilePkiTrustAnchors(eq(TRUST_ANCHOR))
        }
    }

    private class Arrangement {
        val e2eiApi = mock<E2EIApi>()
        val acmeApi = mock<ACMEApi>()
        val e2eiClientProvider = mock<E2EIClientProvider>()
        val mlsClientProvider = mock<MLSClientProvider>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val mlsConversationRepository = mock<MLSConversationRepository>()
        val transactionProvider = mock<CryptoTransactionProvider>(mode = MockMode.autoUnit)
        val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val coreCrypto = mock<CoreCryptoCentral>(mode = MockMode.autoUnit)
        val mlsClient = mock<MLSClient>()
        val credential = mock<CryptoCredential>(mode = MockMode.autoUnit)
        val previousCredentialRef = mock<CryptoCredentialRef>(mode = MockMode.autoUnit)
        val newCredentialRef = mock<CryptoCredentialRef>(mode = MockMode.autoUnit)

        var pkiHooks: PkiEnvironmentHooks? = null
        var persistedRotationCheckpoint: ByteArray? = null
        var returnedIdToken: String? = null
        val checkpointEvents = mutableListOf<String>()
        var rotationCheckpointWriteFailurePhase: E2EIRotationPhase? = null

        suspend fun arrange(configure: suspend Arrangement.() -> Unit = {}): Pair<Arrangement, E2EIRepository> {
            everySuspend { userConfigRepository.deleteE2EIRotationCheckpoint() } calls {
                persistedRotationCheckpoint = null
                Unit.right()
            }
            everySuspend { userConfigRepository.getE2EISettings() } returns E2EI_SETTINGS.right()
            everySuspend { userConfigRepository.getE2EIRotationCheckpoint() } calls {
                persistedRotationCheckpoint.right()
            }
            everySuspend { userConfigRepository.setE2EIRotationCheckpoint(any()) } calls { invocation ->
                persistedRotationCheckpoint = invocation.args[0] as ByteArray
                val checkpoint = Json.decodeFromString(
                    E2EIRotationCheckpoint.serializer(),
                    requireNotNull(persistedRotationCheckpoint).decodeToString()
                )
                checkpointEvents += "persist-${checkpoint.phase.name}"
                if (checkpoint.phase == rotationCheckpointWriteFailurePhase) {
                    StorageFailure.Generic(IllegalStateException("checkpoint write failed")).left()
                } else {
                    Unit.right()
                }
            }
            everySuspend { currentClientIdProvider() } returns TestClient.CLIENT_ID.right()
            everySuspend { mlsClientProvider.getCoreCrypto(any()) } returns coreCrypto.right()
            everySuspend { mlsClientProvider.getMLSClient(any()) } returns mlsClient.right()
            everySuspend { mlsClient.getCredentialRef(CredentialType.X509) } returns previousCredentialRef
            everySuspend { mlsClient.getCredentialRefs(CredentialType.X509) } returns listOf(previousCredentialRef)
            every { previousCredentialRef.publicKeyHash() } returns PREVIOUS_CREDENTIAL_HASH
            every { newCredentialRef.publicKeyHash() } returns NEW_CREDENTIAL_HASH
            everySuspend { e2eiClientProvider.getX509CredentialAcquisitionConfig(any(), any()) } returns ACQUISITION_CONFIG.right()
            every { credential.exportPem() } returns CERTIFICATE_CHAIN
            everySuspend { coreCrypto.installCredential(any()) } calls {
                checkpointEvents += "install"
                newCredentialRef
            }
            everySuspend { transactionProvider.mlsTransaction<Unit>(any(), any()) } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.args[1] as suspend (MlsCoreCryptoContext) ->
                        com.wire.kalium.common.functional.Either<CoreFailure, Unit>
                block(mlsContext)
            }
            everySuspend {
                transactionProvider.mlsTransaction<PreparedX509KeyPackages>(any(), any())
            } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.args[1] as suspend (MlsCoreCryptoContext) ->
                        com.wire.kalium.common.functional.Either<CoreFailure, PreparedX509KeyPackages>
                block(mlsContext)
            }

            everySuspend { coreCrypto.configurePkiEnvironment(any()) } calls { invocation ->
                pkiHooks = invocation.args[0] as PkiEnvironmentHooks
            }
            everySuspend { acmeApi.getTrustAnchors(any()) } returns success(TRUST_ANCHOR.encodeToByteArray())
            everySuspend { acmeApi.getACMEFederationCertificateChain(any()) } returns success(listOf(INTERMEDIATE))
            everySuspend { coreCrypto.startX509CredentialAcquisition(any(), any()) } calls {
                returnedIdToken = requireNotNull(pkiHooks).authenticate(IDP_URL, KEY_AUTH, ACME_AUDIENCE)
                credential
            }
            configure()

            return this to E2EIRepositoryImpl(
                e2EIApi = e2eiApi,
                acmeApi = acmeApi,
                pkiHttpClient = HttpClient(MockEngine { respondOk() }),
                e2EIClientProvider = e2eiClientProvider,
                mlsClientProvider = mlsClientProvider,
                currentClientIdProvider = currentClientIdProvider,
                mlsConversationRepository = mlsConversationRepository,
                userConfigRepository = userConfigRepository,
                selfUserId = TestUser.SELF.id,
                cryptoStateChangeHookNotifier = NoOpCryptoStateChangeHookNotifier
            )
        }
    }

    private companion object {
        const val DISCOVERY_URL = "https://acme.example.test/directory"
        const val HOST_ONLY_DISCOVERY_URL = "https://acme.example.test"
        const val IDP_URL = "https://idp.example.test/authorize"
        const val KEY_AUTH = "key-authorization"
        const val ACME_AUDIENCE = "wire-acme"
        const val ID_TOKEN = "signed-id-token"
        const val CRL_URL = "https://client.example.test/client.crl"
        const val CRL_PROXY_URL = "https://crl-proxy.example.test"
        const val TRUST_ANCHOR = "trust-anchor-pem"
        const val INTERMEDIATE = "intermediate-pem"
        const val CERTIFICATE_CHAIN = "-----BEGIN CERTIFICATE-----\nleaf\n-----END CERTIFICATE-----"
        val PREVIOUS_CREDENTIAL_HASH = "previous-credential".encodeToByteArray()
        val NEW_CREDENTIAL_HASH = "new-credential".encodeToByteArray()
        val PREVIOUS_CREDENTIAL_ID = kotlin.io.encoding.Base64.encode(PREVIOUS_CREDENTIAL_HASH)
        val NEW_CREDENTIAL_ID = kotlin.io.encoding.Base64.encode(NEW_CREDENTIAL_HASH)
        val PROXIED_CRL = "proxied-crl".encodeToByteArray()
        val E2EI_SETTINGS = E2EISettings(
            isRequired = true,
            discoverUrl = DISCOVERY_URL,
            gracePeriodEnd = Instant.DISTANT_FUTURE,
            shouldUseProxy = false,
            crlProxy = null
        )
        val PROXIED_E2EI_SETTINGS = E2EI_SETTINGS.copy(
            shouldUseProxy = true,
            crlProxy = CRL_PROXY_URL
        )
        val ACQUISITION_CONFIG = X509CredentialAcquisitionConfig(
            acmeDirectoryUrl = DISCOVERY_URL,
            cipherSuite = MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
            displayName = TestUser.SELF.name!!,
            clientId = CryptoQualifiedClientId(TestClient.CLIENT_ID.value, TestUser.SELF.id.toCrypto()),
            handle = TestUser.SELF.handle!!,
            teamId = TestUser.SELF.teamId?.value,
            validity = 90.days
        )

        fun installedCheckpoint() = E2EIRotationCheckpoint(
            certificateChain = CERTIFICATE_CHAIN,
            preExistingCredentialIds = listOf(PREVIOUS_CREDENTIAL_ID),
            previousCredentialId = PREVIOUS_CREDENTIAL_ID,
            newCredentialId = NEW_CREDENTIAL_ID,
            groupIds = listOf("group-1"),
            isNewClient = false,
            phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
        )

        private fun <T : Any> success(value: T): NetworkResponse<T> = NetworkResponse.Success(
            value = value,
            headers = emptyMap(),
            httpCode = 200
        )
    }
}
