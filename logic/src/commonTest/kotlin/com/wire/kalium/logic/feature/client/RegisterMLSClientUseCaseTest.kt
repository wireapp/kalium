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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.keypackage.MLSMembershipAuditRepository
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseTest.Arrangement.Companion.E2EI_TEAM_SETTINGS
import com.wire.kalium.logic.feature.client.RegisterMLSClientUseCaseTest.Arrangement.Companion.MLS_CIPHER_SUITE
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.hooks.NoOpCryptoStateChangeHookNotifier
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RegisterMLSClientUseCaseTest {
    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsRequired_whenInvokedAndE2EIIsEnrolled_thenRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = true
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withIsMLSClientInitialisedReturns()
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withAuditAfterSlowSyncMarked()
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .withMLSTransaction<Unit>()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.Success>(result.value)

            verifySuspend(VerifyMode.order) {
                arrangement.clientRepository.registerMLSClient(
                    TestClient.CLIENT_ID,
                    Arrangement.MLS_PUBLIC_KEY,
                    MLS_CIPHER_SUITE.toModel()
                )
                arrangement.mlsMembershipAuditRepository.markAuditRequiredAfterSlowSync()
                arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, Arrangement.REFILL_AMOUNT)
            }

        }

    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsRequired_whenInvokedAndE2EIIsNotEnrolled_thenNotRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = true
            val e2eiIsEnrolled = false
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withIsMLSClientInitialisedReturns(false)
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withAuditAfterSlowSyncMarked()
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.E2EICertificateRequired>(result.value)

            verifySuspend(VerifyMode.not) {
                arrangement.mlsMembershipAuditRepository.markAuditRequiredAfterSlowSync()
                arrangement.clientRepository.registerMLSClient(
                    TestClient.CLIENT_ID,
                    Arrangement.MLS_PUBLIC_KEY,
                    MLS_CIPHER_SUITE.toModel()
                )
            }

            verifySuspend(VerifyMode.not) {
                arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, Arrangement.REFILL_AMOUNT)
            }
        }

    @Test
    fun givenRegisterMLSClientUseCaseAndE2EIIsNotRequired_whenInvoked_thenRegisterMLSClient() =
        runTest {
            val e2eiIsRequired = false
            val (arrangement, registerMLSClient) = Arrangement()
                .withGetMLSClientSuccessful()
                .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = e2eiIsRequired)))
                .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
                .withRegisterMLSClient(Either.Right(Unit))
                .withAuditAfterSlowSyncMarked()
                .withKeyPackageLimits(Arrangement.REFILL_AMOUNT)
                .withUploadKeyPackagesSuccessful()
                .withMLSTransaction<Unit>()
                .arrange()

            val result = registerMLSClient(TestClient.CLIENT_ID)

            result.shouldSucceed()

            assertIs<RegisterMLSClientResult.Success>(result.value)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.registerMLSClient(
                    TestClient.CLIENT_ID,
                    Arrangement.MLS_PUBLIC_KEY,
                    MLS_CIPHER_SUITE.toModel()
                )
            }

            verifySuspend {
                arrangement.keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, Arrangement.REFILL_AMOUNT)
            }
        }

    @Test
    fun givenAuditMarkerCannotBePersisted_whenInvoked_thenClientIsRegisteredAndPackagesAreNotUploaded() = runTest {
        val (arrangement, registerMLSClient) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGettingE2EISettingsReturns(Either.Right(E2EI_TEAM_SETTINGS.copy(isRequired = false)))
            .withGetPublicKey(Arrangement.MLS_PUBLIC_KEY, Arrangement.MLS_CIPHER_SUITE)
            .withRegisterMLSClient(Either.Right(Unit))
            .withAuditAfterSlowSyncMarkFailed()
            .arrange()

        registerMLSClient(TestClient.CLIENT_ID).shouldFail()

        verifySuspend(VerifyMode.order) {
            arrangement.clientRepository.registerMLSClient(
                TestClient.CLIENT_ID,
                Arrangement.MLS_PUBLIC_KEY,
                MLS_CIPHER_SUITE.toModel()
            )
            arrangement.mlsMembershipAuditRepository.markAuditRequiredAfterSlowSync()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.keyPackageRepository.uploadNewKeyPackages(any(), any(), any())
        }
    }

    private class Arrangement {
        val mlsClient: MLSClient = mock(mode = MockMode.autoUnit)
        val mlsContext: MlsCoreCryptoContext = mock(mode = MockMode.autoUnit)
        var mlsClientProvider: MLSClientProvider = mock(mode = MockMode.autoUnit)
        val clientRepository: ClientRepository = mock(mode = MockMode.autoUnit)
        val keyPackageRepository: KeyPackageRepository = mock(mode = MockMode.autoUnit)
        val keyPackageLimitsProvider: KeyPackageLimitsProvider = mock(mode = MockMode.autoUnit)
        val userConfigRepository: UserConfigRepository = mock(mode = MockMode.autoUnit)
        val mlsMembershipAuditRepository: MLSMembershipAuditRepository = mock(mode = MockMode.autoUnit)

        suspend fun withGettingE2EISettingsReturns(result: Either<StorageFailure, E2EISettings>) = apply {
            everySuspend {
                userConfigRepository.getE2EISettings()
            } returns result
        }

        suspend fun withIsMLSClientInitialisedReturns(result: Boolean = true) = apply {
            everySuspend {
                mlsClientProvider.isMLSClientInitialised()
            } returns result
        }

        suspend fun withRegisterMLSClient(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                clientRepository.registerMLSClient(any(), any(), any())
            } returns result
        }

        suspend fun withAuditAfterSlowSyncMarked() = apply {
            everySuspend {
                mlsMembershipAuditRepository.markAuditRequiredAfterSlowSync()
            } returns Either.Right(Unit)
        }

        suspend fun withAuditAfterSlowSyncMarkFailed() = apply {
            everySuspend {
                mlsMembershipAuditRepository.markAuditRequiredAfterSlowSync()
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        fun withKeyPackageLimits(refillAmount: Int) = apply {
            every {
                keyPackageLimitsProvider.refillAmount()
            } returns refillAmount
        }

        suspend fun withUploadKeyPackagesSuccessful() = apply {
            everySuspend {
                keyPackageRepository.uploadNewKeyPackages(any(), TestClient.CLIENT_ID, any())
            } returns Either.Right(Unit)
        }

        suspend fun withGetPublicKey(publicKey: ByteArray, cipherSuite: MLSCiphersuite) = apply {
            everySuspend {
                mlsClient.getPublicKey()
            } returns (publicKey to cipherSuite)
        }

        suspend fun withGetMLSClientSuccessful() = apply {
            everySuspend {
                mlsClientProvider.getMLSClient(any())
            } returns Either.Right(mlsClient)
        }

        suspend fun <R> withMLSTransaction() = apply {
            everySuspend {
                mlsClient.transaction<R>(any(), any())
            } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.args[1] as suspend (MlsCoreCryptoContext) -> R
                block(mlsContext)
            }
        }

        suspend fun arrange() = this to RegisterMLSClientUseCaseImpl(
            mlsClientProvider,
            clientRepository,
            keyPackageRepository,
            keyPackageLimitsProvider,
            userConfigRepository,
            TestUser.SELF.id,
            NoOpCryptoStateChangeHookNotifier,
            mlsMembershipAuditRepository,
        )

        companion object {
            val MLS_PUBLIC_KEY = "public_key".encodeToByteArray()
            val MLS_CIPHER_SUITE = MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            const val REFILL_AMOUNT = 100
            val RANDOM_URL = "https://random.rn"
            val E2EI_TEAM_SETTINGS = E2EISettings(
                true, RANDOM_URL, DateTimeUtil.currentInstant(), false, null
            )
        }
    }
}
