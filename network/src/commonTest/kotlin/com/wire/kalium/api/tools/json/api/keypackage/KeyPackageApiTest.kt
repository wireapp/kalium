package com.wire.kalium.api.tools.json.api.keypackage

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import com.wire.kalium.network.api.keypackage.KeyPackageApiImpl
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyPackageApiTest: ApiTest {

    @Test
    fun givenAValidClientId_whenCallingGetAvailableKeyPackageCountEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            KEY_PACKAGE_COUNT.toString(),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(KEY_PACKAGE_COUNT_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiImpl(networkClient)

        val response = keyPackageApi.getAvailableKeyPackageCount(VALID_CLIENT_ID)
        assertTrue(response.isSuccessful())
        assertEquals(response.value, KEY_PACKAGE_COUNT)
    }

    @Test
    fun givenAValidClientId_whenCallingUploadKeyPackagesEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.Created,
            assertion = {
                assertPost()
                assertJson()
                assertPathEqual(KEY_PACKAGE_UPLOAD_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiImpl(networkClient)

        val response = keyPackageApi.uploadKeyPackages(VALID_CLIENT_ID, listOf(VALID_KEY_PACKAGE))
        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenAValidClientId_whenCallingClaimKeyPackagesEndpoint_theRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            KeyPackageJson.valid.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual(KEY_PACKAGE_CLAIM_PATH)
            }
        )
        val keyPackageApi: KeyPackageApi = KeyPackageApiImpl(networkClient)

        val response = keyPackageApi.claimKeyPackages(VALID_USER_ID)
        assertTrue(response.isSuccessful())
        assertEquals(response.value, VALID_CLAIM_KEY_PACKAGES_RESPONSE.serializableData)
    }

    private companion object {
        const val KEY_PACKAGE_COUNT = 5
        val VALID_USER_ID = UserId("fdf23116-42a5-472c-8316-e10655f5d11e", "wire.com")
        const val VALID_CLIENT_ID = "defkrr8e7grgsoufhg8"
        const val VALID_KEY_PACKAGE = "BKqNPFDI7R0Ic6ACTtrGWOpfWw4="
        val VALID_CLAIM_KEY_PACKAGES_RESPONSE = KeyPackageJson.valid
        const val KEY_PACKAGE_COUNT_PATH = "/mls/key-packages/self/$VALID_CLIENT_ID/count"
        const val KEY_PACKAGE_UPLOAD_PATH = "/mls/key-packages/self/$VALID_CLIENT_ID"
        val KEY_PACKAGE_CLAIM_PATH = "/mls/key-packages/claim/${VALID_USER_ID.domain}/${VALID_USER_ID.value}"
    }

}
