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
package com.wire.kalium.logic.client

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class E2EIClientProviderTest {

    @Test
    fun givenUserAndMlsConfig_whenCreatingAcquisitionConfig_thenMapsAllCoreCryptoInputs() = runTest {
        val (arrangement, provider) = Arrangement().arrange()

        provider.getX509CredentialAcquisitionConfig(DISCOVERY_URL).shouldSucceed { config ->
            assertEquals(DISCOVERY_URL, config.acmeDirectoryUrl)
            assertEquals(MLSCiphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256, config.cipherSuite)
            assertEquals(TestUser.SELF.name, config.displayName)
            assertEquals(
                CryptoQualifiedClientId(TestClient.CLIENT_ID.value, TestUser.SELF.id.toCrypto()),
                config.clientId
            )
            assertEquals(TestUser.SELF.handle, config.handle)
            assertEquals(TestUser.SELF.teamId?.value, config.teamId)
            assertEquals(90.days, config.validity)
        }

        verifySuspend(VerifyMode.exactly(1)) { arrangement.currentClientIdProvider() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userRepository.getSelfUser() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.mlsClientProvider.getOrFetchMLSConfig() }
    }

    @Test
    fun givenExplicitClientId_whenCreatingAcquisitionConfig_thenDoesNotReadCurrentClientId() = runTest {
        val explicitClientId = ClientId("explicit-client")
        val (arrangement, provider) = Arrangement().arrange()

        provider.getX509CredentialAcquisitionConfig(DISCOVERY_URL, explicitClientId).shouldSucceed { config ->
            assertEquals(explicitClientId.value, config.clientId.value)
        }

        verifySuspend(VerifyMode.not) { arrangement.currentClientIdProvider() }
    }

    @Test
    fun givenDebugValidityOverride_whenCreatingAcquisitionConfig_thenUsesOverride() = runTest {
        val (_, provider) = Arrangement().arrange()

        provider.setDebugCertificateExpirationOverride(360)

        assertEquals(360L, provider.getDebugCertificateExpirationOverride())
        provider.getX509CredentialAcquisitionConfig(DISCOVERY_URL).shouldSucceed { config ->
            assertEquals(360.seconds, config.validity)
        }
    }

    @Test
    fun givenDebugValidityOverrideIsCleared_whenCreatingAcquisitionConfig_thenUsesDefault() = runTest {
        val (_, provider) = Arrangement().arrange()

        provider.setDebugCertificateExpirationOverride(360)
        provider.setDebugCertificateExpirationOverride(null)

        assertEquals(null, provider.getDebugCertificateExpirationOverride())
        provider.getX509CredentialAcquisitionConfig(DISCOVERY_URL).shouldSucceed { config ->
            assertEquals(90.days, config.validity)
        }
    }

    @Test
    fun givenSelfUserIsMissing_whenCreatingAcquisitionConfig_thenReturnsFailure() = runTest {
        val (_, provider) = Arrangement().arrange {
            withSelfUser(StorageFailure.DataNotFound.left())
        }

        provider.getX509CredentialAcquisitionConfig(DISCOVERY_URL).shouldFail()
    }

    private class Arrangement : E2EIClientProviderArrangement by E2EIClientProviderArrangementImpl() {
        suspend fun arrange(
            configure: suspend Arrangement.() -> Unit = {}
        ): Pair<Arrangement, E2EIClientProvider> {
            withCurrentClientId(TestClient.CLIENT_ID.right())
            withSelfUser(TestUser.SELF.right())
            withGetOrFetchMLSConfig(SUPPORTED_CIPHER_SUITES)
            configure()
            return this to EI2EIClientProviderImpl(
                currentClientIdProvider = currentClientIdProvider,
                mlsClientProvider = mlsClientProvider,
                userRepository = userRepository
            )
        }
    }

    private companion object {
        const val DISCOVERY_URL = "https://acme.example.test/directory"
        val SUPPORTED_CIPHER_SUITES = SupportedCipherSuite(
            supported = listOf(CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256),
            default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        )
    }
}
