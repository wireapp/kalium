package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.keypackage.KeyPackage
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import com.wire.kalium.network.api.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.keypackage.KeyPackageRef
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
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

        verify(arrangement.keyPackageApi)
            .suspendFunction(arrangement.keyPackageApi::uploadKeyPackages)
            .with(eq(Arrangement.SELF_CLIENT_ID.value), eq(Arrangement.KEY_PACKAGES_BASE64))
            .wasInvoked(once)
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
            .withClaimKeyPackagesSuccessful()
            .arrange()

        val result = keyPackageRepository.claimKeyPackages(listOf(Arrangement.USER_ID))

        result.shouldSucceed { keyPackages ->
            assertEquals(listOf(Arrangement.CLAIMED_KEY_PACKAGES.keyPackages[0]), keyPackages)
        }
    }

    class Arrangement {

        @Mock
        val keyPackageApi = mock(classOf<KeyPackageApi>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val metadataDAO = mock(classOf<MetadataDAO>())

        fun withMLSClient() = apply {
            given(mlsClientProvider).suspendFunction(mlsClientProvider::getMLSClient).whenInvokedWith(eq(SELF_CLIENT_ID))
                .then { Either.Right(MLS_CLIENT) }
        }

        fun withCurrentClientId() = apply {
            given(clientRepository).suspendFunction(clientRepository::currentClientId).whenInvoked().then { Either.Right(SELF_CLIENT_ID) }
        }

        fun withGeneratingKeyPackagesSuccessful() = apply {
            given(MLS_CLIENT).function(MLS_CLIENT::generateKeyPackages).whenInvokedWith(eq(1)).then { KEY_PACKAGES }
        }

        fun withUploadingKeyPackagesSuccessful() = apply {
            given(keyPackageApi).suspendFunction(keyPackageApi::uploadKeyPackages).whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        fun withGetAvailableKeyPackageCountSuccessful() = apply {
            given(keyPackageApi).suspendFunction(keyPackageApi::getAvailableKeyPackageCount).whenInvokedWith(eq(SELF_CLIENT_ID.value))
                .thenReturn(NetworkResponse.Success(KEY_PACKAGE_COUNT_DTO, mapOf(), 200))
        }

        fun withClaimKeyPackagesSuccessful() = apply {
            given(keyPackageApi).suspendFunction(keyPackageApi::claimKeyPackages)
                .whenInvokedWith(eq(KeyPackageApi.Param.SkipOwnClient(MapperProvider.idMapper().toApiModel(USER_ID), SELF_CLIENT_ID.value)))
                .thenReturn(NetworkResponse.Success(CLAIMED_KEY_PACKAGES, mapOf(), 200))
        }

        fun arrange() = this to KeyPackageDataSource(clientRepository, keyPackageApi, mlsClientProvider, metadataDAO)

        internal companion object {
            const val KEY_PACKAGE_COUNT = 100
            val KEY_PACKAGE_COUNT_DTO = KeyPackageCountDTO(KEY_PACKAGE_COUNT)
            val SELF_CLIENT_ID: ClientId = PlainId("client_self")
            val OTHER_CLIENT_ID: ClientId = PlainId("client_other")
            val USER_ID = UserId("user_id", "wire.com")
            val KEY_PACKAGES = listOf("keypackage".encodeToByteArray())
            val KEY_PACKAGES_BASE64 = KEY_PACKAGES.map { it.encodeBase64() }
            val CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(
                listOf(
                    KeyPackageDTO(OTHER_CLIENT_ID.value, "wire.com", KeyPackage(), KeyPackageRef(), "user_id")
                )
            )

            @Mock
            val MLS_CLIENT = mock(classOf<MLSClient>())
        }
    }
}
