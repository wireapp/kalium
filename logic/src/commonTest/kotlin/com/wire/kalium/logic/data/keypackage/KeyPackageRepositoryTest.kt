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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.keypackage.KeyPackageRepositoryTest.Arrangement.Companion.CIPHER_SUITE
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackage
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageRef
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KeyPackageRepositoryTest {

    @Test
    fun givenExistingClient_whenUploadingKeyPackages_thenKeyPackagesShouldBeGeneratedAndPassedToApi() = runTest {
        val (arrangement, keyPackageRepository) = Arrangement()
            .withMLSClient()
            .withGeneratingKeyPackagesSuccessful()
            .withUploadingKeyPackagesSuccessful()
            .arrange()

        keyPackageRepository.uploadNewKeyPackages(Arrangement.SELF_CLIENT_ID, 1)

        coVerify {
            arrangement.keyPackageApi.uploadKeyPackages(eq(Arrangement.SELF_CLIENT_ID.value), eq(Arrangement.KEY_PACKAGES_BASE64))
        }.wasInvoked(once)
    }

    @Test
    fun givenExistingClient_whenGettingAvailableKeyPackageCount_thenResultShouldBePropagated() = runTest {

        val (_, keyPackageRepository) = Arrangement()
            .withMLSClient()
            .withGetAvailableKeyPackageCountSuccessful()
            .arrange()

        val keyPackageCount = keyPackageRepository.getAvailableKeyPackageCount(Arrangement.SELF_CLIENT_ID)

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
                keyPackageResult.usersWithoutKeyPackagesAvailable
            )
        }
    }

    @Test
    fun givenAllUsersHaveNoKeyPackagesAvailable_whenClaimingKeyPackagesFromMultipleUsers_thenSuccessWitheEmptySuccessKeyPackages() = runTest {
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
            assertEquals(usersWithout, keyPackages.usersWithoutKeyPackagesAvailable)
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
            assertEquals(setOf(Arrangement.USER_ID), keyPackages.usersWithoutKeyPackagesAvailable)
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

    class Arrangement {

        @Mock
        val keyPackageApi = mock(KeyPackageApi::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        suspend fun withMLSClient() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(eq(SELF_CLIENT_ID))
            }.returns(Either.Right(MLS_CLIENT))
        }

        suspend fun withCurrentClientId() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(SELF_CLIENT_ID))
        }

        suspend fun withGeneratingKeyPackagesSuccessful() = apply {
            coEvery {
                MLS_CLIENT.generateKeyPackages(eq(1))
            }.returns(KEY_PACKAGES)
        }

        suspend fun withUploadingKeyPackagesSuccessful() = apply {
            coEvery {
                keyPackageApi.uploadKeyPackages(any(), any())
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withGetAvailableKeyPackageCountSuccessful() = apply {
            coEvery {
                keyPackageApi.getAvailableKeyPackageCount(eq(SELF_CLIENT_ID.value))
            }.returns(NetworkResponse.Success(KEY_PACKAGE_COUNT_DTO, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesSuccessful(userId: UserId) = apply {
            coEvery {
                keyPackageApi.claimKeyPackages(
                    eq(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), SELF_CLIENT_ID.value, CIPHER_SUITE.tag))
                )
            }.returns(NetworkResponse.Success(CLAIMED_KEY_PACKAGES, mapOf(), 200))
        }

        suspend fun withClaimKeyPackagesSuccessfulWithEmptyResponse(userId: UserId) = apply {
            coEvery {
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

        fun arrange() = this to KeyPackageDataSource(currentClientIdProvider, keyPackageApi, mlsClientProvider, SELF_USER_ID)

        internal companion object {
            const val KEY_PACKAGE_COUNT = 100
            val CIPHER_SUITE = CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
            val SELF_CLIENT_ID: ClientId = PlainId("client_self")
            val OTHER_CLIENT_ID: ClientId = PlainId("client_other")
            val USER_ID = UserId("user_id", "wire.com")
            val SELF_USER_ID = UserId("self_user_id", "wire.com")
            val KEY_PACKAGES = listOf("keypackage".encodeToByteArray())
            val KEY_PACKAGES_BASE64 = KEY_PACKAGES.map { it.encodeBase64() }
            val EMPTY_CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                emptyList()
            )
            val CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                listOf(
                    KeyPackageDTO(OTHER_CLIENT_ID.value, "wire.com", KeyPackage(), KeyPackageRef(), "user_id")
                )
            )

            @Mock
            val MLS_CLIENT = mock(MLSClient::class)
        }
    }
}
