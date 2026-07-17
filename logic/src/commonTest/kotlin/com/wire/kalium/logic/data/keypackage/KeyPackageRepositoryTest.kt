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

package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.mls.KeyPackageClaimResult
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.keypackage.KeyPackageRepositoryTest.Arrangement.Companion.CIPHER_SUITE
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageRef
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class KeyPackageRepositoryTest {

    @Test
    fun givenExistingClient_whenUploadingKeyPackages_thenKeyPackagesShouldBeGeneratedAndPassedToApi() = runTest {
        val (arrangement, keyPackageRepository) = Arrangement()
            .withGeneratingKeyPackagesSuccessful()
            .withUploadingKeyPackagesSuccessful()
            .arrange()

        keyPackageRepository.uploadNewKeyPackages(arrangement.mlsContext, Arrangement.SELF_CLIENT_ID, 1)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageApi.uploadKeyPackages(eq(Arrangement.SELF_CLIENT_ID.value), eq(Arrangement.KEY_PACKAGES_BASE64))
        }
    }

    @Test
    fun givenExistingClient_whenGettingAvailableKeyPackageCount_thenResultShouldBePropagated() = runTest {
        val cipherSuite = CipherSuite.fromTag(1)
        val (_, keyPackageRepository) = Arrangement()
            .withGetAvailableKeyPackageCountSuccessful()
            .arrange()

        val keyPackageCount = keyPackageRepository.getAvailableKeyPackageCount(Arrangement.SELF_CLIENT_ID, cipherSuite)

        assertIs<Either.Right<KeyPackageCountDTO>>(keyPackageCount)
        assertEquals(Arrangement.KEY_PACKAGE_COUNT_DTO.count, keyPackageCount.value.count)
    }

    @Test
    fun givenExistingClient_whenClaimingKeyPackages_thenResultShouldBePropagated() = runTest {
        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesSuccessful(Arrangement.USER_ID)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(Arrangement.USER_ID), CIPHER_SUITE)

        result.shouldSucceed { keyPackageResult ->
            assertEquals(listOf(Arrangement.CLAIMED_KEY_PACKAGES.keyPackages[0]), keyPackageResult.successfullyFetchedKeyPackages)
        }
    }

    @Test
    fun givenUserHasKeyPackagesForMultipleClients_whenClaimingKeyPackages_thenAllClientKeyPackagesAreCollectedAndUserIsNotMissing() =
        runTest {
            val user = Arrangement.USER_ID
            val (_, keyPackageRepository) = Arrangement()
                .withCurrentClientId()
                .withClaimKeyPackagesForMultipleClients(user)
                .arrange()

            val result = keyPackageRepository.claimKeyPackages(listOf(user), CIPHER_SUITE)

            result.shouldSucceed { keyPackageResult ->
                // Every client's key package present in the response is collected.
                assertEquals(
                    Arrangement.MULTI_CLIENT_CLAIMED_KEY_PACKAGES.keyPackages,
                    keyPackageResult.successfullyFetchedKeyPackages
                )
                // A user is only reported as missing when the response is empty; a non-empty (incl. partial-client) response
                // counts as a success, so the user must not appear in either failure bucket.
                assertEquals(emptySet(), keyPackageResult.usersWithoutKeyPackages)
                assertEquals(emptySet(), keyPackageResult.usersWithUnreachableBackend)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMoreUsersThanConcurrencyLimit_whenClaimingKeyPackages_thenSemaphoreKeepsClaimsWithinLimit() = runTest {
        val users = List(3) { index -> Arrangement.USER_ID.copy(value = "user$index") }
        val claimStarted = List(users.size) { CompletableDeferred<Unit>() }
        val releaseClaim = List(users.size) { CompletableDeferred<Unit>() }
        val (arrangement, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .arrange(maxConcurrentClaims = 2)

        users.forEachIndexed { index, user ->
            everySuspend {
                arrangement.keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(user.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            } calls {
                claimStarted[index].complete(Unit)
                releaseClaim[index].await()
                NetworkResponse.Success(Arrangement.CLAIMED_KEY_PACKAGES, mapOf(), 200)
            }
        }

        val result = async {
            keyPackageRepository.claimKeyPackages(users, CIPHER_SUITE)
        }
        runCurrent()

        assertEquals(true, claimStarted[0].isCompleted)
        assertEquals(true, claimStarted[1].isCompleted)
        assertEquals(false, claimStarted[2].isCompleted)

        releaseClaim[0].complete(Unit)
        runCurrent()

        // A free semaphore permit starts the next claim without waiting for the other active claim.
        assertEquals(true, claimStarted[2].isCompleted)

        releaseClaim[1].complete(Unit)
        releaseClaim[2].complete(Unit)
        result.await().shouldSucceed { keyPackageResult ->
            assertEquals(users.size, keyPackageResult.successfullyFetchedKeyPackages.size)
        }
    }

    @Test
    fun givenSomeUsersHaveNoKeyPackagesAvailable_whenClaimingKeyPackages_thenSuccessShouldBePropagatedWithInformationMissing() = runTest {
        val userWith = Arrangement.USER_ID
        val userWithout = userWith.copy(value = "missingKP")
        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesSuccessful(userWith)
            .withClaimKeyPackagesSuccessfulWithEmptyResponse(userWithout)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(userWith, userWithout), CIPHER_SUITE)

        result.shouldSucceed { keyPackageResult ->
            assertEquals(
                listOf(Arrangement.CLAIMED_KEY_PACKAGES.keyPackages[0]),
                keyPackageResult.successfullyFetchedKeyPackages
            )
            assertEquals(
                setOf(userWithout),
                keyPackageResult.usersWithoutKeyPackages
            )
        }
    }

    @Test
    fun givenAllUsersHaveNoKeyPackagesAvailable_whenClaimingKeyPackagesFromMultipleUsers_thenSuccessWitheEmptySuccessKeyPackages() =
        runTest {
            val usersWithout = setOf(
                Arrangement.USER_ID.copy(value = "missingKP"),
                Arrangement.USER_ID.copy(value = "alsoMissingKP"),
            )
            val (_, keyPackageRepository) = Arrangement()
                .withCurrentClientId().also { arrangement ->
                    usersWithout.forEach { userWithout ->
                        arrangement.withClaimKeyPackagesSuccessfulWithEmptyResponse(userWithout)
                    }
                }
                .arrange()

            val result = keyPackageRepository.claimKeyPackages(usersWithout.toList(), CIPHER_SUITE)

            result.shouldSucceed { keyPackages ->
                assertEquals(emptyList(), keyPackages.successfullyFetchedKeyPackages)
                assertEquals(usersWithout, keyPackages.usersWithoutKeyPackages)
            }
        }

    @Test
    fun givenUserWithNoKeyPackages_whenClaimingKeyPackagesFromSingleUser_thenSuccessWitheEmptySuccessKeyPackages() = runTest {

        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesSuccessfulWithEmptyResponse(Arrangement.USER_ID)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(Arrangement.USER_ID), CIPHER_SUITE)

        result.shouldSucceed { keyPackages ->
            assertEquals(emptyList(), keyPackages.successfullyFetchedKeyPackages)
            assertEquals(setOf(Arrangement.USER_ID), keyPackages.usersWithoutKeyPackages)
        }
    }

    @Test
    fun givenFederationBackendUnreachable_whenClaimingKeyPackages_thenUserIsInUnreachableBackendSetNotMissingKeyPackagesSet() = runTest {
        val federatedUser = UserId("alice", "federated.com")
        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesFederationError(federatedUser)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(federatedUser), CIPHER_SUITE)

        result.shouldSucceed { keyPackageResult ->
            assertEquals(emptyList(), keyPackageResult.successfullyFetchedKeyPackages)
            assertEquals(emptySet(), keyPackageResult.usersWithoutKeyPackages)
            assertEquals(setOf(federatedUser), keyPackageResult.usersWithUnreachableBackend)
        }
    }

    @Test
    fun givenMixedFailures_whenClaimingKeyPackages_thenUsersAreSeparatedByFailureType() = runTest {
        val userWithNoKP = Arrangement.USER_ID.copy(value = "noKP")
        val userFedFailed = UserId("bob", "unreachable.com")
        val userSuccess = Arrangement.USER_ID
        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesSuccessful(userSuccess)
            .withClaimKeyPackagesSuccessfulWithEmptyResponse(userWithNoKP)
            .withClaimKeyPackagesFederationError(userFedFailed)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(userSuccess, userWithNoKP, userFedFailed), CIPHER_SUITE)

        result.shouldSucceed { keyPackageResult ->
            assertEquals(listOf(Arrangement.CLAIMED_KEY_PACKAGES.keyPackages[0]), keyPackageResult.successfullyFetchedKeyPackages)
            assertEquals(setOf(userWithNoKP), keyPackageResult.usersWithoutKeyPackages)
            assertEquals(setOf(userFedFailed), keyPackageResult.usersWithUnreachableBackend)
        }
    }

    @Test
    fun givenSelfUserWithNoKeyPackages_whenClaimingKeyPackages_thenResultShouldSucceed() = runTest {

        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesSuccessfulWithEmptyResponse(Arrangement.SELF_USER_ID)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(Arrangement.SELF_USER_ID), CIPHER_SUITE)

        result.shouldSucceed { keyPackages ->
            assertEquals(emptyList(), keyPackages.successfullyFetchedKeyPackages)
        }
    }

    @Test
    fun givenNoNetworkConnection_whenClaimingKeyPackages_thenOperationIsAbortedWithFailure() = runTest {
        val user1 = Arrangement.USER_ID.copy(value = "user1")
        val user2 = Arrangement.USER_ID.copy(value = "user2")
        val (arrangement, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesNoNetworkError(user1)
            .arrange(maxConcurrentClaims = 1)

        val result = keyPackageRepository.claimKeyPackages(listOf(user1, user2), CIPHER_SUITE)

        result.shouldFail { assertIs<NetworkFailure.NoNetworkConnection>(it) }
        verifySuspend(VerifyMode.not) {
            arrangement.keyPackageApi.claimKeyPackages(
                eq(KeyPackageApi.Param.SkipOwnClient(user2.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
            )
        }
    }

    @Test
    fun givenFatalFailure_whenClaimingKeyPackages_thenActiveClaimsFinishButWaitingClaimsDoNotStart() = runTest {
        val failingUser = Arrangement.USER_ID.copy(value = "failing")
        val activeUser = Arrangement.USER_ID.copy(value = "active")
        val waitingUser = Arrangement.USER_ID.copy(value = "waiting")
        val activeClaimStarted = CompletableDeferred<Unit>()
        val releaseActiveClaim = CompletableDeferred<Unit>()
        val (arrangement, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .arrange(maxConcurrentClaims = 2)

        everySuspend {
            arrangement.keyPackageApi.claimKeyPackages(
                eq(KeyPackageApi.Param.SkipOwnClient(failingUser.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
            )
        } calls {
            activeClaimStarted.await()
            NetworkResponse.Error(KaliumException.NoNetwork())
        }
        everySuspend {
            arrangement.keyPackageApi.claimKeyPackages(
                eq(KeyPackageApi.Param.SkipOwnClient(activeUser.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
            )
        } calls {
            activeClaimStarted.complete(Unit)
            releaseActiveClaim.await()
            NetworkResponse.Success(Arrangement.CLAIMED_KEY_PACKAGES, mapOf(), 200)
        }

        val result = async {
            keyPackageRepository.claimKeyPackages(listOf(failingUser, activeUser, waitingUser), CIPHER_SUITE)
        }
        runCurrent()

        assertEquals(false, result.isCompleted)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.keyPackageApi.claimKeyPackages(
                eq(KeyPackageApi.Param.SkipOwnClient(activeUser.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.keyPackageApi.claimKeyPackages(
                eq(KeyPackageApi.Param.SkipOwnClient(waitingUser.toApi(), Arrangement.SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
            )
        }

        releaseActiveClaim.complete(Unit)
        result.await().shouldFail { assertIs<NetworkFailure.NoNetworkConnection>(it) }
    }

    @Test
    fun givenMixedResults_whenClaimingAtDifferentConcurrencyLevels_thenResultsAreConsistent() = runTest {
        val successA = Arrangement.USER_ID.copy(value = "successA")
        val successB = Arrangement.USER_ID.copy(value = "successB")
        val noKeyPackages = Arrangement.USER_ID.copy(value = "noKP")
        val federated = UserId("alice", "unreachable.com")
        val serverError = Arrangement.USER_ID.copy(value = "serverError")
        val users = listOf(successA, noKeyPackages, federated, successB, serverError)

        suspend fun claimWith(maxConcurrentClaims: Int): KeyPackageClaimResult {
            val (_, keyPackageRepository) = Arrangement()
                .withCurrentClientId()
                .withClaimKeyPackagesSuccessful(successA)
                .withClaimKeyPackagesSuccessful(successB)
                .withClaimKeyPackagesSuccessfulWithEmptyResponse(noKeyPackages)
                .withClaimKeyPackagesFederationError(federated)
                .withClaimKeyPackagesServerError(serverError)
                .arrange(maxConcurrentClaims = maxConcurrentClaims)

            lateinit var claimResult: KeyPackageClaimResult
            keyPackageRepository.claimKeyPackages(users, CIPHER_SUITE).shouldSucceed { claimResult = it }
            return claimResult
        }

        // maxConcurrentClaims = 1 reproduces the previous sequential behaviour; higher values are the new batched behaviour.
        val sequentialResult = claimWith(maxConcurrentClaims = 1)
        listOf(2, 4, users.size + 1).forEach { concurrency ->
            assertEquals(
                sequentialResult,
                claimWith(maxConcurrentClaims = concurrency),
                "Result diverged at maxConcurrentClaims=$concurrency"
            )
        }

        assertEquals(2, sequentialResult.successfullyFetchedKeyPackages.size)
        assertEquals(setOf(noKeyPackages), sequentialResult.usersWithoutKeyPackages)
        assertEquals(setOf(federated, serverError), sequentialResult.usersWithUnreachableBackend)
    }

    @Test
    fun givenServerError_whenClaimingKeyPackages_thenUserIsInUnreachableBackendSetNotMissingKeyPackagesSet() = runTest {
        val failingUser = Arrangement.USER_ID.copy(value = "serverErrorUser")
        val (_, keyPackageRepository) = Arrangement()
            .withCurrentClientId()
            .withClaimKeyPackagesServerError(failingUser)
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(failingUser), CIPHER_SUITE)

        result.shouldSucceed { keyPackageResult ->
            assertEquals(emptyList(), keyPackageResult.successfullyFetchedKeyPackages)
            assertEquals(emptySet(), keyPackageResult.usersWithoutKeyPackages)
            assertEquals(setOf(failingUser), keyPackageResult.usersWithUnreachableBackend)
        }
    }

    class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val keyPackageApi = mock<KeyPackageApi>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()

        suspend fun withCurrentClientId() = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(SELF_CLIENT_ID))
        }

        suspend fun withGeneratingKeyPackagesSuccessful() = apply {
            everySuspend {
                mlsContext.generateKeyPackages(eq(1))
            }.returns(KEY_PACKAGES)
        }

        suspend fun withUploadingKeyPackagesSuccessful() = apply {
            everySuspend {
                keyPackageApi.uploadKeyPackages(any(), any())
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withGetAvailableKeyPackageCountSuccessful() = apply {
            everySuspend {
                keyPackageApi.getAvailableKeyPackageCount(eq(SELF_CLIENT_ID.value), any())
            }.returns(NetworkResponse.Success(KEY_PACKAGE_COUNT_DTO, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesSuccessful(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(NetworkResponse.Success(CLAIMED_KEY_PACKAGES, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesForMultipleClients(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(NetworkResponse.Success(MULTI_CLIENT_CLAIMED_KEY_PACKAGES, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesSuccessfulWithEmptyResponse(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(
                        KeyPackageApi.Param.SkipOwnClient(
                            userId.toApi(),
                            SELF_CLIENT_ID.value,
                            CIPHER_SUITE.tag
                        )
                    )
                )
            }.returns(NetworkResponse.Success(EMPTY_CLAIMED_KEY_PACKAGES, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesFederationError(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(
                NetworkResponse.Error(
                    FederationError(FederationErrorResponse.Unreachable(listOf(userId.domain)))
                )
            )
        }

        suspend fun withClaimKeyPackagesNoNetworkError(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(NetworkResponse.Error(KaliumException.NoNetwork()))
        }

        suspend fun withClaimKeyPackagesServerError(userId: UserId) = apply {
            everySuspend {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(
                NetworkResponse.Error(
                    KaliumException.ServerError(
                        com.wire.kalium.network.api.model.GenericAPIErrorResponse(500, "internal server error", "server-error")
                    )
                )
            )
        }

        fun arrange(maxConcurrentClaims: Int = 4) =
            this to KeyPackageDataSource(currentClientIdProvider, keyPackageApi, SELF_USER_ID, maxConcurrentClaims)

        internal companion object {
            const val KEY_PACKAGE_COUNT = 100
            val CIPHER_SUITE = CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
            val SELF_CLIENT_ID: ClientId = PlainId("client_self")
            val OTHER_CLIENT_ID: ClientId = PlainId("client_other")
            val USER_ID = UserId("user_id", "wire.com")
            val SELF_USER_ID = UserId("self_user_id", "wire.com")
            val KEY_PACKAGES = listOf("keypackage".encodeToByteArray())
            val KEY_PACKAGES_BASE64 = KEY_PACKAGES.map { Base64.encode(it) }
            val EMPTY_CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                emptyList()
            )
            val CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                listOf(
                    KeyPackageDTO(OTHER_CLIENT_ID.value, "wire.com", KeyPackage(), KeyPackageRef(), "user_id")
                )
            )
            val MULTI_CLIENT_CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                listOf(
                    KeyPackageDTO("client_a", "wire.com", "keyPackageA", "refA", USER_ID.value),
                    KeyPackageDTO("client_b", "wire.com", "keyPackageB", "refB", USER_ID.value),
                )
            )
        }
    }
}
