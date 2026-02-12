/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.api.v0.properties

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.network.api.authenticated.properties.LabelDTO
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.authenticated.properties.LabelTypeDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.v0.authenticated.PropertiesApiV0
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class PropertiesApiV0Test : ApiTest() {

    @Test
    fun givenPropertiesValuesRequest_whenGettingPropertiesValues_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = PROPERTIES_VALUES_RESPONSE.toJsonString(),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(PATH_PROPERTIES_VALUES)
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.getPropertiesValues()

        assertTrue(result is NetworkResponse.Success)
        assertEquals(PROPERTIES_VALUES_RESPONSE, result.value)
    }

    @Test
    fun givenReadReceiptsPropertyRequest_whenGettingProperty_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "1",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("$PATH_PROPERTIES/${PropertyKey.WIRE_RECEIPT_MODE.key}")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.getProperty(PropertyKey.WIRE_RECEIPT_MODE)

        assertTrue(result is NetworkResponse.Success)
        assertEquals(1, result.value)
    }

    @Test
    fun givenTypingIndicatorPropertyRequest_whenGettingProperty_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "0",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("$PATH_PROPERTIES/${PropertyKey.WIRE_TYPING_INDICATOR_MODE.key}")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.getProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE)

        assertTrue(result is NetworkResponse.Success)
        assertEquals(0, result.value)
    }

    @Test
    fun givenScreenshotCensoringPropertyRequest_whenGettingProperty_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "1",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("$PATH_PROPERTIES/${PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key}")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.getProperty(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE)

        assertTrue(result is NetworkResponse.Success)
        assertEquals(1, result.value)
    }

    @Test
    fun givenSetPropertyRequest_whenSettingReadReceipts_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPut()
                assertPathEqual("$PATH_PROPERTIES/${PropertyKey.WIRE_RECEIPT_MODE.key}")
                assertJsonBodyContent("1")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.setProperty(PropertyKey.WIRE_RECEIPT_MODE, 1)

        assertTrue(result is NetworkResponse.Success)
    }

    @Test
    fun givenDeletePropertyRequest_whenDeletingReadReceipts_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual("$PATH_PROPERTIES/${PropertyKey.WIRE_RECEIPT_MODE.key}")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.deleteProperty(PropertyKey.WIRE_RECEIPT_MODE)

        assertTrue(result is NetworkResponse.Success)
    }

    @Test
    fun givenGetLabelsRequest_whenGettingLabels_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = LABELS_RESPONSE.toJsonString(),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("$PATH_PROPERTIES/$PATH_LABELS")
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.getLabels()

        assertTrue(result is NetworkResponse.Success)
        assertEquals(LABELS_RESPONSE, result.value)
    }

    @Test
    fun givenUpdateLabelsRequest_whenUpdatingLabels_thenRequestShouldBeConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = "",
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertJson()
                assertPut()
                assertPathEqual("$PATH_PROPERTIES/$PATH_LABELS")
                assertJsonBodyContent(LABELS_RESPONSE.toJsonString())
            }
        )

        val propertiesApi: PropertiesApi = PropertiesApiV0(networkClient)
        val result = propertiesApi.updateLabels(LABELS_RESPONSE)

        assertTrue(result is NetworkResponse.Success)
    }

    private companion object {
        const val PATH_PROPERTIES = "properties"
        const val PATH_PROPERTIES_VALUES = "properties-values"
        const val PATH_LABELS = "labels"

        val PROPERTIES_VALUES_RESPONSE = JsonObject(
            mapOf(
                PropertyKey.WIRE_RECEIPT_MODE.key to JsonPrimitive(1),
                PropertyKey.WIRE_TYPING_INDICATOR_MODE.key to JsonPrimitive(0),
                PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key to JsonPrimitive(1),
            )
        )

        val LABELS_RESPONSE = LabelListResponseDTO(
            labels = listOf(
                LabelDTO(
                    id = "favorites-id",
                    name = "Favorites",
                    type = LabelTypeDTO.FAVORITE,
                    conversations = emptyList(),
                    qualifiedConversations = listOf(
                        QualifiedID(value = "conversation-1", domain = "wire.com")
                    )
                )
            )
        )
    }
}
