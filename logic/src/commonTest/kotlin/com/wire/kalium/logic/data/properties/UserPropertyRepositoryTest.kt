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

package com.wire.kalium.logic.data.properties

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse

class UserPropertyRepositoryTest {

    @Test
    fun whenUserEnablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.setReadReceiptsEnabled()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.setProperty(any(), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserDisablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.deleteReadReceiptsProperty()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.deleteProperty(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserReadReceiptsNotPresent_thenShouldReturnsReceiptsAsDefaultFalse() = runTest {
        val (arrangement, repository) = Arrangement()
            .withNullReadReceiptsStatus()
            .arrange()

        val result = repository.getReadReceiptsStatus()

        assertFalse(result)
        coVerify {
            arrangement.userConfigRepository.isReadReceiptsEnabled()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenSyncingReadReceiptsAndPropertyExists_thenShouldPersistFetchedStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetReadReceiptsStatusReturning(1)
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.syncReadReceiptsStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenSyncingPropertiesStatusesAndValuesExist_thenShouldPersistAllWithSingleCall() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(
                JsonObject(
                    mapOf(
                        PropertyKey.WIRE_RECEIPT_MODE.key to JsonPrimitive(1),
                        PropertyKey.WIRE_TYPING_INDICATOR_MODE.key to JsonPrimitive(0),
                        PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key to JsonPrimitive(1),
                    )
                )
            )
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        coVerify { arrangement.propertiesApi.getPropertiesValues() }.wasInvoked(once)
        coVerify { arrangement.propertiesApi.getProperty(any()) }.wasNotInvoked()
        coVerify { arrangement.userConfigRepository.setReadReceiptsStatus(eq(true)) }.wasInvoked(once)
        coVerify { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(false)) }.wasInvoked(once)
        coVerify { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true)) }.wasInvoked(once)
    }

    @Test
    fun whenSyncingPropertiesStatusesAndSomeValuesMissing_thenShouldPersistDefaults() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(
                JsonObject(mapOf(PropertyKey.WIRE_RECEIPT_MODE.key to JsonPrimitive(1)))
            )
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        coVerify { arrangement.userConfigRepository.setReadReceiptsStatus(eq(true)) }.wasInvoked(once)
        coVerify { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(true)) }.wasInvoked(once)
        coVerify { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false)) }.wasInvoked(once)
    }

    @Test
    fun whenSyncingReadReceiptsAndPropertyMissing_thenShouldPersistDisabledStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetReadReceiptsStatusNotFound()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.syncReadReceiptsStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun whenSyncingTypingIndicatorAndPropertyExists_thenShouldPersistFetchedStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetTypingIndicatorStatusReturning(0)
            .withUpdateTypingIndicatorLocallySuccess()
            .arrange()

        val result = repository.syncTypingIndicatorStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_TYPING_INDICATOR_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setTypingIndicatorStatus(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun whenSyncingTypingIndicatorAndPropertyMissing_thenShouldPersistEnabledStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetTypingIndicatorStatusNotFound()
            .withUpdateTypingIndicatorLocallySuccess()
            .arrange()

        val result = repository.syncTypingIndicatorStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_TYPING_INDICATOR_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setTypingIndicatorStatus(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenSyncingScreenshotCensoringAndPropertyExists_thenShouldPersistFetchedStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetScreenshotCensoringStatusReturning(1)
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncScreenshotCensoringStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenSyncingScreenshotCensoringAndPropertyMissing_thenShouldPersistDisabledStatus() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetScreenshotCensoringStatusNotFound()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncScreenshotCensoringStatus()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.getProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserEnablingScreenshotCensoring_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withSetScreenshotCensoringEnabledSuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.setScreenshotCensoringEnabled()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.setProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE), eq(1))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserDisablingScreenshotCensoring_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteScreenshotCensoringSuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.deleteScreenshotCensoringProperty()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.deleteProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false))
        }.wasInvoked(once)
    }

    private class Arrangement {
        val propertiesApi = mock(PropertiesApi::class)
        val userConfigRepository = mock(UserConfigRepository::class)

        private val selfUserId = TestUser.SELF.id

        private val userPropertyRepository = UserPropertyDataSource(propertiesApi, userConfigRepository, selfUserId)

        suspend fun withUpdateReadReceiptsSuccess() = apply {
            coEvery {
                propertiesApi.setProperty(eq(PropertyKey.WIRE_RECEIPT_MODE), eq(1))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withGetReadReceiptsStatusReturning(value: Int) = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
            }.returns(NetworkResponse.Success(value, mapOf(), 200))
        }

        suspend fun withGetPropertiesValuesReturning(value: JsonObject) = apply {
            coEvery {
                propertiesApi.getPropertiesValues()
            }.returns(NetworkResponse.Success(value, mapOf(), 200))
        }

        suspend fun withGetReadReceiptsStatusNotFound() = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
            }.returns(
                NetworkResponse.Error(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            code = HttpStatusCode.NotFound.value,
                            label = "not_found",
                            message = "not found"
                        )
                    )
                )
            )
        }

        suspend fun withGetTypingIndicatorStatusReturning(value: Int) = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_TYPING_INDICATOR_MODE))
            }.returns(NetworkResponse.Success(value, mapOf(), 200))
        }

        suspend fun withGetTypingIndicatorStatusNotFound() = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_TYPING_INDICATOR_MODE))
            }.returns(
                NetworkResponse.Error(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            code = HttpStatusCode.NotFound.value,
                            label = "not_found",
                            message = "not found"
                        )
                    )
                )
            )
        }

        suspend fun withGetScreenshotCensoringStatusReturning(value: Int) = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
            }.returns(NetworkResponse.Success(value, mapOf(), 200))
        }

        suspend fun withGetScreenshotCensoringStatusNotFound() = apply {
            coEvery {
                propertiesApi.getProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
            }.returns(
                NetworkResponse.Error(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            code = HttpStatusCode.NotFound.value,
                            label = "not_found",
                            message = "not found"
                        )
                    )
                )
            )
        }

        suspend fun withDeleteReadReceiptsSuccess() = apply {
            coEvery {
                propertiesApi.deleteProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withSetScreenshotCensoringEnabledSuccess() = apply {
            coEvery {
                propertiesApi.setProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE), eq(1))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withDeleteScreenshotCensoringSuccess() = apply {
            coEvery {
                propertiesApi.deleteProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withUpdateReadReceiptsLocallySuccess() = apply {
            coEvery {
                userConfigRepository.setReadReceiptsStatus(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateTypingIndicatorLocallySuccess() = apply {
            coEvery {
                userConfigRepository.setTypingIndicatorStatus(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateScreenshotCensoringLocallySuccess() = apply {
            coEvery {
                userConfigRepository.setScreenshotCensoringConfig(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withNullReadReceiptsStatus() = apply {
            coEvery {
                userConfigRepository.isReadReceiptsEnabled()
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun arrange() = this to userPropertyRepository

    }
}
