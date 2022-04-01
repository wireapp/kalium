package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.keypackage.KeyPackage
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.keypackage.KeyPackageRef
import com.wire.kalium.network.utils.NetworkResponse
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

    @Mock
    private val keyPackageApi = mock(classOf<KeyPackageApi>())

    @Mock
    private val mlsClientProvider = mock(classOf<MLSClientProvider>())

    private lateinit var keyPackageRepository: KeyPackageRepository

    @BeforeTest
    fun setup() {
        keyPackageRepository = KeyPackageDataSource(keyPackageApi, mlsClientProvider)
    }

    @Test
    fun givenExistingClient_whenUploadingKeyPackages_thenKeyPackagesShouldBeGeneratedAndPassedToApi() = runTest {
        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getMLSClient)
            .whenInvokedWith(eq(CLIENT_ID))
            .then { Either.Right(MLS_CLIENT) }

        given(MLS_CLIENT)
            .function(MLS_CLIENT::generateKeyPackages)
            .whenInvokedWith(eq(1))
            .then { KEY_PACKAGES }

        given(keyPackageApi)
            .suspendFunction(keyPackageApi::uploadKeyPackages)
            .whenInvokedWith(anything(), anything())
            .thenReturn( NetworkResponse.Success(Unit, mapOf(), 200) )

        keyPackageRepository.uploadNewKeyPackages(CLIENT_ID, 1)

        verify(keyPackageApi)
            .suspendFunction(keyPackageApi::uploadKeyPackages)
            .with(eq(CLIENT_ID.value), eq(KEY_PACKAGES_BASE64))
            .wasInvoked(once)
    }

    @Test
    fun givenExistingClient_whenGettingAvailableKeyPackageCount_thenResultShouldBePropagated() = runTest {
        given(keyPackageApi)
            .suspendFunction(keyPackageApi::getAvailableKeyPackageCount)
            .whenInvokedWith(eq(CLIENT_ID.value))
            .thenReturn( NetworkResponse.Success(KEY_PACKAGE_COUNT, mapOf(), 200) )

        val keyPackageCount = keyPackageRepository.getAvailableKeyPackageCount(CLIENT_ID)

        assertIs<Either.Right<Int>>(keyPackageCount)
        assertEquals(KEY_PACKAGE_COUNT, keyPackageCount.value)
    }

    @Test
    fun givenExistingClient_whenClaimingKeyPackages_thenResultShouldBePropagated() = runTest {
        given(keyPackageApi)
            .suspendFunction(keyPackageApi::claimKeyPackages)
            .whenInvokedWith(eq(MapperProvider.idMapper().toApiModel(USER_ID)))
            .thenReturn( NetworkResponse.Success(CLAIMED_KEY_PACKAGES, mapOf(), 200) )

        val claimedKeyPackages = keyPackageRepository.claimKeyPackages(USER_ID)

        assertIs<Either.Right<ClaimedKeyPackageList>>(claimedKeyPackages)
        assertEquals(CLAIMED_KEY_PACKAGES, claimedKeyPackages.value)
    }

    private companion object {
        const val KEY_PACKAGE_COUNT = 100
        val CLIENT_ID: ClientId = PlainId("client_id")
        val USER_ID = UserId("user_id", "wire.com")
        val KEY_PACKAGES = listOf("keypackage".encodeToByteArray())
        val KEY_PACKAGES_BASE64 = KEY_PACKAGES.map { it.encodeBase64() }
        val CLAIMED_KEY_PACKAGES = ClaimedKeyPackageList(listOf(
            KeyPackageDTO("client123", "wire.com", KeyPackage(), KeyPackageRef(), "user_id")
        ))

        @Mock
        val MLS_CLIENT = mock(classOf<MLSClient>())
    }
}
