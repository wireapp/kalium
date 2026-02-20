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
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        verifySuspend {
            arrangement.propertiesApi.setProperty(any(), any())
        }

        verifySuspend {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(true))
        }
    }

    @Test
    fun whenUserDisablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.deleteReadReceiptsProperty()

        result.shouldSucceed()
        verifySuspend {
            arrangement.propertiesApi.deleteProperty(any())
        }

        verifySuspend {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(false))
        }
    }

    @Test
    fun whenUserReadReceiptsNotPresent_thenShouldReturnsReceiptsAsDefaultFalse() = runTest {
        val (arrangement, repository) = Arrangement()
            .withNullReadReceiptsStatus()
            .arrange()

        val result = repository.getReadReceiptsStatus()

        assertFalse(result)
        verifySuspend {
            arrangement.userConfigRepository.isReadReceiptsEnabled()
        }
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
        verifySuspend { arrangement.propertiesApi.getPropertiesValues() }
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true)) }
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
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false)) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndResponseIsEmptyJson_thenShouldPersistAllDefaults() = runTest {
        // readReceipts defaults to false, typingIndicator defaults to true, screenshotCensoring defaults to false
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(JsonObject(emptyMap()))
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false)) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndOnlyTypingIndicatorPresent_thenOthersShouldDefaultCorrectly() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(
                JsonObject(mapOf(PropertyKey.WIRE_TYPING_INDICATOR_MODE.key to JsonPrimitive(0)))
            )
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false)) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndOnlyScreenshotCensoringPresent_thenOthersShouldDefaultCorrectly() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(
                JsonObject(mapOf(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key to JsonPrimitive(1)))
            )
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true)) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndValuesAreStringNumbers_thenShouldPersistAllValues() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesReturning(
                JsonObject(
                    mapOf(
                        PropertyKey.WIRE_RECEIPT_MODE.key to JsonPrimitive("1"),
                        PropertyKey.WIRE_TYPING_INDICATOR_MODE.key to JsonPrimitive("0"),
                        PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key to JsonPrimitive("1"),
                    )
                )
            )
            .withUpdateReadReceiptsLocallySuccess()
            .withUpdateTypingIndicatorLocallySuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldSucceed()
        verifySuspend { arrangement.propertiesApi.getPropertiesValues() }
        verifySuspend { arrangement.userConfigRepository.setReadReceiptsStatus(eq(true)) }
        verifySuspend { arrangement.userConfigRepository.setTypingIndicatorStatus(eq(false)) }
        verifySuspend { arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true)) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndBulkEndpointFailsWithNonNotFound_thenShouldReturnFailureAndNotFallback() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesBadRequest()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldFail()
        verifySuspend { arrangement.propertiesApi.getPropertiesValues() }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setReadReceiptsStatus(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setTypingIndicatorStatus(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setScreenshotCensoringConfig(any()) }
    }

    @Test
    fun whenSyncingPropertiesStatusesAndBulkEndpointFailsWithNotFound_thenShouldReturnFailureAndNotFallback() = runTest {
        val (arrangement, repository) = Arrangement()
            .withGetPropertiesValuesNotFound()
            .arrange()

        val result = repository.syncPropertiesStatuses()

        result.shouldFail()
        verifySuspend { arrangement.propertiesApi.getPropertiesValues() }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setReadReceiptsStatus(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setTypingIndicatorStatus(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.setScreenshotCensoringConfig(any()) }
    }

    @Test
    fun whenUserEnablingScreenshotCensoring_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withSetScreenshotCensoringEnabledSuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.setScreenshotCensoringEnabled()

        result.shouldSucceed()
        verifySuspend {
            arrangement.propertiesApi.setProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE), eq(1))
        }
        verifySuspend {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(true))
        }
    }

    @Test
    fun whenUserDisablingScreenshotCensoring_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteScreenshotCensoringSuccess()
            .withUpdateScreenshotCensoringLocallySuccess()
            .arrange()

        val result = repository.deleteScreenshotCensoringProperty()

        result.shouldSucceed()
        verifySuspend {
            arrangement.propertiesApi.deleteProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
        }
        verifySuspend {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(false))
        }
    }

    private class Arrangement {
        val propertiesApi = mock<PropertiesApi>()
        val userConfigRepository = mock<UserConfigRepository>()

        private val userPropertyRepository = UserPropertyDataSource(
            readReceipts = ReadReceiptsPropertyDataSource(propertiesApi, userConfigRepository),
            typingIndicator = TypingIndicatorPropertyDataSource(propertiesApi, userConfigRepository),
            screenshotCensoring = ScreenshotCensoringPropertyDataSource(propertiesApi, userConfigRepository),
            userPropertiesSync = UserPropertiesSyncDataSource(propertiesApi, userConfigRepository),
            conversationFolders = ConversationFoldersPropertyDataSource(propertiesApi, TestUser.SELF.id),
        )

        suspend fun withUpdateReadReceiptsSuccess() = apply {
            everySuspend {
                propertiesApi.setProperty(eq(PropertyKey.WIRE_RECEIPT_MODE), eq(1))
            } returns NetworkResponse.Success(Unit, mapOf(), 200)
        }

        suspend fun withGetPropertiesValuesReturning(value: JsonObject) = apply {
            everySuspend {
                propertiesApi.getPropertiesValues()
            } returns NetworkResponse.Success(value, mapOf(), 200)
        }

        suspend fun withGetPropertiesValuesNotFound() = apply {
            everySuspend {
                propertiesApi.getPropertiesValues()
            } returns notFoundResponse()
        }

        suspend fun withGetPropertiesValuesBadRequest() = apply {
            everySuspend {
                propertiesApi.getPropertiesValues()
            } returns badRequestResponse()
        }

        suspend fun withDeleteReadReceiptsSuccess() = apply {
            everySuspend {
                propertiesApi.deleteProperty(eq(PropertyKey.WIRE_RECEIPT_MODE))
            } returns NetworkResponse.Success(Unit, mapOf(), 200)
        }

        suspend fun withSetScreenshotCensoringEnabledSuccess() = apply {
            everySuspend {
                propertiesApi.setProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE), eq(1))
            } returns NetworkResponse.Success(Unit, mapOf(), 200)
        }

        suspend fun withDeleteScreenshotCensoringSuccess() = apply {
            everySuspend {
                propertiesApi.deleteProperty(eq(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE))
            } returns NetworkResponse.Success(Unit, mapOf(), 200)
        }

        suspend fun withUpdateReadReceiptsLocallySuccess() = apply {
            everySuspend {
                userConfigRepository.setReadReceiptsStatus(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateTypingIndicatorLocallySuccess() = apply {
            everySuspend {
                userConfigRepository.setTypingIndicatorStatus(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateScreenshotCensoringLocallySuccess() = apply {
            everySuspend {
                userConfigRepository.setScreenshotCensoringConfig(any())
            } returns Either.Right(Unit)
        }

        suspend fun withNullReadReceiptsStatus() = apply {
            everySuspend {
                userConfigRepository.isReadReceiptsEnabled()
            } returns flowOf(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange() = this to userPropertyRepository

        private fun notFoundResponse(): NetworkResponse.Error = NetworkResponse.Error(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    code = HttpStatusCode.NotFound.value,
                    label = "not_found",
                    message = "not found"
                )
            )
        )

        private fun badRequestResponse(): NetworkResponse.Error = NetworkResponse.Error(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    code = HttpStatusCode.BadRequest.value,
                    label = "bad_request",
                    message = "bad request"
                )
            )
        )

    }
}
